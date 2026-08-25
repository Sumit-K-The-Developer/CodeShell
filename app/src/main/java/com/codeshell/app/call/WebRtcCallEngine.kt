package com.codeshell.app.call

import android.content.Context
import android.content.Intent
import android.util.Log
import com.codeshell.app.data.model.CallType
import com.codeshell.app.data.model.SessionDescriptionData
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpSender
import org.webrtc.RtpParameters
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack

class WebRtcCallEngine(
    context: Context,
    private val callType: CallType,
    private val onIceCandidate: (IceCandidate) -> Unit,
    private val onConnectionState: (PeerConnection.PeerConnectionState) -> Unit,
) {
    val eglBase: EglBase = EglBase.create()

    private val appContext = context.applicationContext
    private val factory: PeerConnectionFactory
    private val audioSource: AudioSource
    private val audioTrack: AudioTrack
    private var videoCapturer: CameraVideoCapturer? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var videoSender: RtpSender? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var peerConnection: PeerConnection? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var remoteAudioTrack: AudioTrack? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var isMuted = false
    private var isRemoteAudioEnabled = true
    private var isVideoEnabled = true
    private var isFrontCamera = true
    private var isScreenSharing = false
    private var isScreenSharePaused = false
    private var localMirror = true

    init {
        val initOptions = PeerConnectionFactory.InitializationOptions.builder(appContext)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initOptions)

        val factoryBuilder = PeerConnectionFactory.builder()
        if (callType == CallType.VIDEO) {
            factoryBuilder
                .setVideoEncoderFactory(
                    DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
                )
                .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
        }
        factory = factoryBuilder.createPeerConnectionFactory()

        val audioConstraints = MediaConstraints()
        audioSource = factory.createAudioSource(audioConstraints)
        audioTrack = factory.createAudioTrack(AUDIO_TRACK_ID, audioSource)

        if (callType == CallType.VIDEO) {
            startCameraCapture()
        }
    }

    fun createPeerConnection(): PeerConnection? {
        val rtcConfig = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }
        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                state?.let { mapIceState(it) }?.let(onConnectionState)
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let(onIceCandidate)
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
            override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
            override fun onDataChannel(channel: org.webrtc.DataChannel?) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(
                receiver: org.webrtc.RtpReceiver?,
                streams: Array<out org.webrtc.MediaStream>?,
            ) {
                val track = receiver?.track() ?: return
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    remoteRenderer?.let { renderer ->
                        track.addSink(renderer)
                    }
                } else if (track is AudioTrack) {
                    remoteAudioTrack = track
                    track.setEnabled(isRemoteAudioEnabled)
                }
            }
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                newState?.let(onConnectionState)
            }
        })
        peerConnection?.addTrack(audioTrack, listOf(STREAM_ID))
        videoTrack?.let {
            videoSender = peerConnection?.addTrack(it, listOf(STREAM_ID))
            applyVideoQuality(VideoQuality.BALANCED)
        }
        return peerConnection
    }

    fun initRenderer(renderer: SurfaceViewRenderer, mirror: Boolean) {
        renderer.init(eglBase.eglBaseContext, null)
        renderer.setMirror(mirror)
        renderer.setEnableHardwareScaler(true)
        renderer.setZOrderMediaOverlay(mirror)
    }

    fun bindLocalRenderer(renderer: SurfaceViewRenderer) {
        localRenderer = renderer
        initRenderer(renderer, mirror = localMirror)
        videoTrack?.addSink(renderer)
    }

    fun bindRemoteRenderer(renderer: SurfaceViewRenderer) {
        remoteRenderer = renderer
        initRenderer(renderer, mirror = false)
        remoteVideoTrack?.addSink(renderer)
    }

    fun createOffer(onResult: (Result<SessionDescriptionData>) -> Unit) {
        val receiveVideo = callType == CallType.VIDEO
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", receiveVideo.toString()))
        }
        peerConnection?.createOffer(sdpObserver(onResult), constraints)
    }

    fun createAnswer(onResult: (Result<SessionDescriptionData>) -> Unit) {
        val receiveVideo = callType == CallType.VIDEO
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", receiveVideo.toString()))
        }
        peerConnection?.createAnswer(sdpObserver(onResult), constraints)
    }

    fun setRemoteDescription(data: SessionDescriptionData, onResult: (Result<Unit>) -> Unit) {
        val type = when (data.type.lowercase()) {
            "offer" -> SessionDescription.Type.OFFER
            "answer" -> SessionDescription.Type.ANSWER
            else -> {
                onResult(Result.failure(IllegalArgumentException("Unknown SDP type")))
                return
            }
        }
        val description = SessionDescription(type, data.sdp)
        peerConnection?.setRemoteDescription(object : org.webrtc.SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) = Unit
            override fun onSetSuccess() {
                onResult(Result.success(Unit))
            }
            override fun onCreateFailure(error: String?) {
                onResult(Result.failure(IllegalStateException(error ?: "setRemoteDescription failed")))
            }
            override fun onSetFailure(error: String?) {
                onResult(Result.failure(IllegalStateException(error ?: "setRemoteDescription failed")))
            }
        }, description)
    }

    fun addIceCandidate(candidate: IceCandidateData) {
        peerConnection?.addIceCandidate(
            IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.candidate)
        )
    }

    fun setMuted(muted: Boolean) {
        isMuted = muted
        audioTrack.setEnabled(!muted)
    }

    fun isMuted(): Boolean = isMuted

    fun setRemoteAudioEnabled(enabled: Boolean) {
        isRemoteAudioEnabled = enabled
        remoteAudioTrack?.setEnabled(enabled)
    }

    fun setVideoEnabled(enabled: Boolean) {
        if (isScreenSharing) return
        isVideoEnabled = enabled
        videoTrack?.setEnabled(enabled)
        if (enabled) {
            videoCapturer?.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
        } else {
            runCatching { videoCapturer?.stopCapture() }
        }
    }

    fun isVideoEnabled(): Boolean = isVideoEnabled

    /**
     * Permission grants can become visible a moment after the activity result
     * callback. Retry the capturer before SDP is created so the first video
     * call includes a real video track.
     */
    fun ensureCameraCapture(): Boolean {
        if (callType != CallType.VIDEO) return true
        if (videoTrack != null && videoCapturer != null) return true
        startCameraCapture()
        return videoTrack != null && videoCapturer != null
    }

    fun switchCamera() {
        if (isScreenSharing) return
        val capturer = videoCapturer ?: return
        capturer.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFrontFacing: Boolean) {
                isFrontCamera = isFrontFacing
            }
            override fun onCameraSwitchError(errorDescription: String?) = Unit
        })
    }

    fun isFrontCamera(): Boolean = isFrontCamera

    fun isScreenSharing(): Boolean = isScreenSharing

    fun startScreenShare(resultCode: Int, data: Intent): Boolean {
        if (callType != CallType.VIDEO || isScreenSharing) return false
        Log.d(TAG, "Starting screen share")
        val screenCapturer = ScreenCapturerAndroid(data, object : android.media.projection.MediaProjection.Callback() {
            override fun onStop() {
                Log.d(TAG, "MediaProjection stopped")
                stopScreenShare()
            }
        })
        return switchVideoCapturer(
            capturer = screenCapturer,
            isScreencast = true,
            helperThreadName = "ScreenCaptureThread",
            mirrorLocal = false,
            startCapture = {
                screenCapturer.startCapture(
                    SCREEN_SHARE_WIDTH,
                    SCREEN_SHARE_HEIGHT,
                    SCREEN_SHARE_FPS,
                )
            },
        ).also { started ->
            if (started) {
                Log.d(TAG, "Screen share capturer started")
                this.screenCapturer = screenCapturer
                videoCapturer = null
                isScreenSharing = true
                isScreenSharePaused = false
                isVideoEnabled = true
            } else {
                Log.w(TAG, "Screen share capturer failed to start")
            }
        }
    }

    fun stopScreenShare() {
        if (!isScreenSharing) return
        isScreenSharing = false
        startCameraCapture()
        screenCapturer = null
        isVideoEnabled = true
    }

    fun setScreenSharePaused(paused: Boolean) {
        if (!isScreenSharing) return
        isScreenSharePaused = paused
        videoTrack?.setEnabled(!paused)
    }

    fun isScreenSharePaused(): Boolean = isScreenSharePaused

    fun dispose() {
        stopScreenCaptureOnly()
        runCatching { videoCapturer?.stopCapture() }
        videoCapturer?.dispose()
        videoCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        localRenderer?.let { videoTrack?.removeSink(it) }
        remoteRenderer?.let { remoteVideoTrack?.removeSink(it) }
        localRenderer?.release()
        remoteRenderer?.release()
        localRenderer = null
        remoteRenderer = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        videoSender = null
        videoTrack?.dispose()
        videoSource?.dispose()
        videoTrack = null
        videoSource = null
        remoteVideoTrack = null
        remoteAudioTrack = null
        audioTrack.dispose()
        audioSource.dispose()
        factory.dispose()
        eglBase.release()
    }

    private fun startCameraCapture() {
        val enumerator = Camera2Enumerator(appContext)
        val deviceName = enumerator.deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            ?: enumerator.deviceNames.firstOrNull()
            ?: return
        isFrontCamera = enumerator.isFrontFacing(deviceName)
        val capturer = enumerator.createCapturer(deviceName, null) ?: return
        if (switchVideoCapturer(
                capturer = capturer,
                isScreencast = capturer.isScreencast,
                helperThreadName = "VideoCaptureThread",
                mirrorLocal = true,
                startCapture = { capturer.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS) },
            )
        ) {
            videoCapturer = capturer
        }
    }

    private fun switchVideoCapturer(
        capturer: VideoCapturer,
        isScreencast: Boolean,
        helperThreadName: String,
        mirrorLocal: Boolean,
        startCapture: () -> Unit,
    ): Boolean {
        val previousHelper = surfaceTextureHelper
        val previousCamera = videoCapturer
        val previousScreen = screenCapturer
        val previousCapturer = previousCamera ?: previousScreen
        val hadVideoSource = videoSource != null

        val helper = SurfaceTextureHelper.create(helperThreadName, eglBase.eglBaseContext)
        val source = videoSource ?: factory.createVideoSource(isScreencast).also { videoSource = it }
        return runCatching {
            runCatching { previousCapturer?.stopCapture() }
            capturer.initialize(helper, appContext, source.capturerObserver)
            startCapture()
            val track = videoTrack ?: factory.createVideoTrack(VIDEO_TRACK_ID, source).also { createdTrack ->
                videoTrack = createdTrack
                videoSender = peerConnection?.addTrack(createdTrack, listOf(STREAM_ID))
            }
            track.setEnabled(true)
            surfaceTextureHelper = helper
            localMirror = mirrorLocal
            applyVideoQuality(if (isScreencast) VideoQuality.SCREEN_SHARE else VideoQuality.BALANCED)
            previousCamera?.dispose()
            previousScreen?.dispose()
            previousHelper?.dispose()
            localRenderer?.let { renderer ->
                track.removeSink(renderer)
                renderer.setMirror(mirrorLocal)
                track.addSink(renderer)
            }
            true
        }.getOrElse {
            Log.w(TAG, "Video capturer switch failed", it)
            runCatching { capturer.stopCapture() }
            capturer.dispose()
            helper.dispose()
            runCatching { previousCapturer?.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS) }
            if (!hadVideoSource) {
                videoSource = null
                source.dispose()
            }
            false
        }
    }

    private fun stopScreenCaptureOnly() {
        runCatching { screenCapturer?.stopCapture() }
        screenCapturer?.dispose()
        screenCapturer = null
        isScreenSharing = false
        isScreenSharePaused = false
    }

    private fun sdpObserver(
        onResult: (Result<SessionDescriptionData>) -> Unit,
    ): org.webrtc.SdpObserver = object : org.webrtc.SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) {
            if (description == null) {
                onResult(Result.failure(IllegalStateException("Null SDP")))
                return
            }
            peerConnection?.setLocalDescription(object : org.webrtc.SdpObserver {
                override fun onCreateSuccess(p0: SessionDescription?) = Unit
                override fun onSetSuccess() {
                    onResult(Result.success(description.toData()))
                }
                override fun onCreateFailure(error: String?) {
                    onResult(Result.failure(IllegalStateException(error ?: "setLocalDescription failed")))
                }
                override fun onSetFailure(error: String?) {
                    onResult(Result.failure(IllegalStateException(error ?: "setLocalDescription failed")))
                }
            }, description)
        }
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) {
            onResult(Result.failure(IllegalStateException(error ?: "create SDP failed")))
        }
        override fun onSetFailure(error: String?) = Unit
    }

    private fun mapIceState(state: PeerConnection.IceConnectionState): PeerConnection.PeerConnectionState {
        return when (state) {
            PeerConnection.IceConnectionState.CONNECTED,
            PeerConnection.IceConnectionState.COMPLETED -> PeerConnection.PeerConnectionState.CONNECTED
            PeerConnection.IceConnectionState.FAILED -> PeerConnection.PeerConnectionState.FAILED
            PeerConnection.IceConnectionState.DISCONNECTED -> PeerConnection.PeerConnectionState.DISCONNECTED
            else -> PeerConnection.PeerConnectionState.CONNECTING
        }
    }

    private fun SessionDescription.toData() =
        SessionDescriptionData(sdp = description, type = type.canonicalForm())

    fun useReconnectQuality() {
        applyVideoQuality(VideoQuality.LOW)
    }

    fun useBalancedQuality() {
        applyVideoQuality(if (isScreenSharing) VideoQuality.SCREEN_SHARE else VideoQuality.BALANCED)
    }

    private fun applyVideoQuality(quality: VideoQuality) {
        val sender = videoSender ?: return
        val parameters = sender.parameters ?: return
        val encodings = parameters.encodings
        if (encodings.isEmpty()) return
        encodings.forEach { encoding ->
            encoding.maxBitrateBps = quality.maxBitrateBps
            encoding.maxFramerate = quality.fps
            encoding.scaleResolutionDownBy = quality.scaleDownBy
        }
        sender.parameters = parameters
    }

    data class IceCandidateData(
        val sdpMid: String,
        val sdpMLineIndex: Int,
        val candidate: String,
    )

    companion object {
        private const val AUDIO_TRACK_ID = "audio0"
        private const val VIDEO_TRACK_ID = "video0"
        private const val STREAM_ID = "stream0"
        private const val TAG = "WebRtcCallEngine"
        private const val VIDEO_WIDTH = 1280
        private const val VIDEO_HEIGHT = 720
        private const val VIDEO_FPS = 30
        private const val SCREEN_SHARE_WIDTH = 1920
        private const val SCREEN_SHARE_HEIGHT = 1080
        private const val SCREEN_SHARE_FPS = 18
        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
        )
    }

    private enum class VideoQuality(
        val maxBitrateBps: Int,
        val fps: Int,
        val scaleDownBy: Double,
    ) {
        LOW(maxBitrateBps = 700_000, fps = 24, scaleDownBy = 1.3333333333333333),
        BALANCED(maxBitrateBps = 1_800_000, fps = 30, scaleDownBy = 1.0),
        SCREEN_SHARE(maxBitrateBps = 3_500_000, fps = 18, scaleDownBy = 1.0),
    }
}
