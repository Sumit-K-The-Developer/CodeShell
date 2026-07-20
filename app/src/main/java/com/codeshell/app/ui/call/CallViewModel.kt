package com.codeshell.app.ui.call

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.codeshell.app.call.CallAudioRoute
import com.codeshell.app.R
import com.codeshell.app.call.CallAudioRouteController
import com.codeshell.app.call.CallAudioRouteType
import com.codeshell.app.call.CallForegroundServiceController
import com.codeshell.app.call.CallProximityController
import com.codeshell.app.call.CallRingbackPlayer
import com.codeshell.app.call.CallScreenShareActions
import com.codeshell.app.call.PendingCallStore
import com.codeshell.app.call.ScreenShareAction
import com.codeshell.app.call.WebRtcCallEngine
import com.codeshell.app.data.model.CallType
import com.codeshell.app.data.model.SessionLogStatus
import com.codeshell.app.data.model.VoiceCall
import com.codeshell.app.data.model.VoiceCallState
import com.codeshell.app.data.repository.CallRepository
import com.codeshell.app.data.repository.MessageRepository
import com.codeshell.app.util.NetworkConnectivity
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection
import org.webrtc.SurfaceViewRenderer

enum class CallUiPhase {
    Hidden,
    OutgoingRinging,
    IncomingRinging,
    Connecting,
    InCall,
    StatusMessage,
}

data class CallUiState(
    val phase: CallUiPhase = CallUiPhase.Hidden,
    val callId: String? = null,
    val chatId: String? = null,
    val callType: CallType = CallType.VOICE,
    val otherDisplayName: String = "",
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isVideoEnabled: Boolean = true,
    val isFrontCamera: Boolean = true,
    val isScreenSharing: Boolean = false,
    val isScreenSharePaused: Boolean = false,
    val isMinimized: Boolean = false,
    val audioRoutes: List<CallAudioRoute> = emptyList(),
    val selectedAudioRoute: CallAudioRouteType = CallAudioRouteType.EARPIECE,
    val hasExternalAudioRoute: Boolean = false,
    val otherIncomingCallType: CallType? = null,
    val statusMessage: String? = null,
    val callDurationSec: Long = 0,
)

class CallViewModel(
    application: Application,
    private val callRepository: CallRepository = CallRepository(),
    private val messageRepository: MessageRepository = MessageRepository(),
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    private var chatId: String? = null
    private var currentUserId: String? = null
    private var otherUserId: String? = null
    private var isCaller = false
    private var activeCallType = CallType.VOICE
    private var webRtc: WebRtcCallEngine? = null
    private var callObserverJob: Job? = null
    private var incomingObserverJob: Job? = null
    private var ringTimeoutJob: Job? = null
    private var reconnectTimeoutJob: Job? = null
    private var allIncomingObserverJob: Job? = null
    private var screenShareActionJob: Job? = null
    private var iceListener: ListenerRegistration? = null
    private var handledOffer = false
    private var handledAnswer = false
    private var connectedNotified = false
    private var inCallStartedAt: Long? = null
    private var durationJob: Job? = null
    private val loggedCallIds = mutableSetOf<String>()
    private var proximityController: CallProximityController? = null
    private var audioRouteController: CallAudioRouteController? = null
    private var ringbackPlayer: CallRingbackPlayer? = null
    private var pendingLocalRenderer: SurfaceViewRenderer? = null
    private var pendingRemoteRenderer: SurfaceViewRenderer? = null

    init {
        screenShareActionJob = viewModelScope.launch {
            CallScreenShareActions.events.collect { action ->
                when (action) {
                    ScreenShareAction.STOP -> stopScreenShare()
                    ScreenShareAction.TOGGLE_PAUSE -> toggleScreenSharePaused()
                }
            }
        }
    }

    fun bindChat(
        chatId: String,
        currentUserId: String,
        otherUserId: String,
        otherDisplayName: String,
    ) {
        if (this.chatId == chatId && this.currentUserId == currentUserId) {
            _uiState.update { it.copy(otherDisplayName = otherDisplayName) }
            return
        }
        teardownObservers()
        this.chatId = chatId
        this.currentUserId = currentUserId
        this.otherUserId = otherUserId
        _uiState.update { it.copy(otherDisplayName = otherDisplayName, chatId = chatId) }

        val pending = PendingCallStore.peek(getApplication())
        if (pending != null && pending.first == chatId) {
            PendingCallStore.clear(getApplication())
            observeCall(pending.second)
        }

        incomingObserverJob = viewModelScope.launch {
            callRepository.observeIncomingRinging(chatId, currentUserId)
                .catch { /* ignore */ }
                .collect { call ->
                    if (call == null) return@collect
                    if (_uiState.value.phase != CallUiPhase.Hidden &&
                        _uiState.value.callId != null &&
                        _uiState.value.callId != call.id
                    ) {
                        viewModelScope.launch {
                            callRepository.updateCallState(call.id, VoiceCallState.FAILED, endedReason = END_REASON_BUSY)
                        }
                        return@collect
                    }
                    if (_uiState.value.phase == CallUiPhase.Hidden) {
                        isCaller = false
                        activeCallType = call.callType
                        observeCall(call.id)
                        startProximityControl()
                        _uiState.update {
                            it.copy(
                                phase = CallUiPhase.IncomingRinging,
                                callId = call.id,
                                callType = call.callType,
                                isSpeakerOn = call.callType == CallType.VIDEO,
                                selectedAudioRoute = if (call.callType == CallType.VIDEO) {
                                    CallAudioRouteType.SPEAKER
                                } else {
                                    CallAudioRouteType.EARPIECE
                                },
                                isMinimized = false,
                                statusMessage = null,
                            )
                        }
                        if (call.callType == CallType.VIDEO) {
                            viewModelScope.launch { ensureWebRtc() }
                        }
                    }
                }
        }
        allIncomingObserverJob = viewModelScope.launch {
            callRepository.observeIncomingRingingForCallee(currentUserId)
                .catch { /* ignore */ }
                .collect { incomingByChat ->
                    _uiState.update {
                        it.copy(otherIncomingCallType = incomingByChat.entries
                            .firstOrNull { entry -> entry.key != chatId }
                            ?.value)
                    }
                }
        }
    }

    fun startOutgoingCall(callType: CallType) {
        val userId = currentUserId ?: return
        val callee = otherUserId ?: return
        val chat = chatId ?: return
        if (NetworkConnectivity.isOffline(getApplication())) {
            emitSnackbar(getApplication<Application>().getString(R.string.no_internet_action_blocked))
            return
        }
        if (_uiState.value.phase != CallUiPhase.Hidden) return

        viewModelScope.launch {
            val result = callRepository.createRingingCall(chat, userId, callee, callType)
            result.fold(
                onSuccess = { call ->
                    isCaller = true
                    activeCallType = callType
                    handledOffer = false
                    handledAnswer = false
                    connectedNotified = false
                    _uiState.update {
                        it.copy(
                            phase = CallUiPhase.OutgoingRinging,
                            callId = call.id,
                            callType = callType,
                            isSpeakerOn = callType == CallType.VIDEO,
                            isVideoEnabled = true,
                            isFrontCamera = true,
                            isScreenSharing = false,
                            isMinimized = false,
                            selectedAudioRoute = if (callType == CallType.VIDEO) {
                                CallAudioRouteType.SPEAKER
                            } else {
                                CallAudioRouteType.EARPIECE
                            },
                            statusMessage = null,
                        )
                    }
                    startProximityControl()
                    startOutgoingRingback()
                    if (callType == CallType.VIDEO) {
                        viewModelScope.launch { ensureWebRtc() }
                    }
                    observeCall(call.id)
                    startRingTimeout(call.id)
                },
                onFailure = {
                    val message = if (callType == CallType.VIDEO) {
                        getApplication<Application>().getString(R.string.video_call_start_failed)
                    } else {
                        getApplication<Application>().getString(R.string.call_start_failed)
                    }
                    emitSnackbar(message)
                },
            )
        }
    }

    fun acceptIncomingCall() {
        val callId = _uiState.value.callId ?: return
        if (NetworkConnectivity.isOffline(getApplication())) {
            emitSnackbar(getApplication<Application>().getString(R.string.no_internet_action_blocked))
            return
        }
        ringTimeoutJob?.cancel()
        viewModelScope.launch {
            callRepository.updateCallState(callId, VoiceCallState.ACCEPTED)
            _uiState.update { it.copy(phase = CallUiPhase.Connecting) }
            startProximityControl()
            ensureWebRtc()
        }
    }

    fun declineIncomingCall() {
        endCall(
            state = VoiceCallState.DECLINED,
            message = callTypeMessage(R.string.call_declined, R.string.video_call_declined),
            logStatus = SessionLogStatus.DECLINED,
            endedReason = END_REASON_DECLINED,
        )
    }

    fun endActiveCall() {
        endCall(
            state = VoiceCallState.ENDED,
            message = callTypeMessage(R.string.call_ended, R.string.video_call_ended),
            logStatus = SessionLogStatus.COMPLETED,
            endedReason = END_REASON_LOCAL_ENDED,
        )
    }

    fun minimizeCall() {
        if (_uiState.value.phase == CallUiPhase.StatusMessage) return
        _uiState.update { it.copy(isMinimized = true) }
    }

    fun expandCall() {
        _uiState.update { it.copy(isMinimized = false) }
    }

    fun refreshAudioRoutes() {
        ensureAudioRouteController()
        updateAudioRouteState()
    }

    fun selectAudioRoute(route: CallAudioRouteType) {
        ensureAudioRouteController()
        audioRouteController?.selectRoute(route)
        applySpeakerAndProximity()
        updateAudioRouteState()
    }

    fun toggleSpeakerRoute() {
        ensureAudioRouteController()
        val current = audioRouteController?.selectedRoute() ?: _uiState.value.selectedAudioRoute
        val next = if (current == CallAudioRouteType.SPEAKER) {
            CallAudioRouteType.EARPIECE
        } else {
            CallAudioRouteType.SPEAKER
        }
        selectAudioRoute(next)
    }

    fun toggleMute() {
        val muted = !_uiState.value.isMuted
        webRtc?.setMuted(muted)
        _uiState.update { it.copy(isMuted = muted) }
    }

    fun toggleVideo() {
        if (_uiState.value.callType != CallType.VIDEO) return
        if (_uiState.value.isScreenSharing) return
        val enabled = !_uiState.value.isVideoEnabled
        webRtc?.setVideoEnabled(enabled)
        _uiState.update { it.copy(isVideoEnabled = enabled) }
    }

    fun switchCamera() {
        if (_uiState.value.callType != CallType.VIDEO) return
        if (_uiState.value.isScreenSharing) return
        webRtc?.switchCamera()
        viewModelScope.launch {
            delay(300)
            _uiState.update { it.copy(isFrontCamera = webRtc?.isFrontCamera() ?: it.isFrontCamera) }
        }
    }

    fun startScreenShare(resultCode: Int, data: Intent) {
        if (_uiState.value.callType != CallType.VIDEO || _uiState.value.phase != CallUiPhase.InCall) return
        viewModelScope.launch {
            CallForegroundServiceController.start(
                context = getApplication(),
                callType = CallType.VIDEO,
                isScreenSharing = true,
            )
            delay(MEDIA_PROJECTION_FOREGROUND_DELAY_MS)
            if (_uiState.value.callType != CallType.VIDEO || _uiState.value.phase != CallUiPhase.InCall) return@launch
            val started = webRtc?.startScreenShare(resultCode, data) == true
            if (started) {
                _uiState.update {
                    it.copy(
                        isScreenSharing = true,
                        isScreenSharePaused = false,
                        isVideoEnabled = true,
                        isFrontCamera = false,
                    )
                }
            } else {
                CallForegroundServiceController.start(
                    context = getApplication(),
                    callType = CallType.VIDEO,
                    isScreenSharing = false,
                )
                emitSnackbar(getApplication<Application>().getString(R.string.call_screen_share_failed))
            }
        }
    }

    fun stopScreenShare() {
        webRtc?.stopScreenShare()
        _uiState.update {
            it.copy(
                isScreenSharing = false,
                isScreenSharePaused = false,
                isVideoEnabled = true,
                isFrontCamera = webRtc?.isFrontCamera() ?: true,
            )
        }
        if (_uiState.value.phase == CallUiPhase.InCall) {
            CallForegroundServiceController.start(
                context = getApplication(),
                callType = _uiState.value.callType,
                isScreenSharing = false,
            )
        }
    }

    fun toggleScreenSharePaused() {
        if (!_uiState.value.isScreenSharing) return
        val paused = !_uiState.value.isScreenSharePaused
        webRtc?.setScreenSharePaused(paused)
        _uiState.update { it.copy(isScreenSharePaused = paused) }
        CallForegroundServiceController.start(
            context = getApplication(),
            callType = CallType.VIDEO,
            isScreenSharing = true,
            isScreenSharePaused = paused,
        )
    }

    fun reportPermissionDenied() {
        val callId = _uiState.value.callId ?: return
        if (_uiState.value.phase == CallUiPhase.IncomingRinging) {
            viewModelScope.launch {
                callRepository.updateCallState(callId, VoiceCallState.FAILED, endedReason = END_REASON_PERMISSION_DENIED)
                showStatus(getApplication<Application>().getString(R.string.call_permission_denied_remote))
                cleanupCall()
            }
        }
    }

    fun bindLocalRenderer(renderer: SurfaceViewRenderer) {
        pendingLocalRenderer = renderer
        webRtc?.bindLocalRenderer(renderer)
    }

    fun bindRemoteRenderer(renderer: SurfaceViewRenderer) {
        pendingRemoteRenderer = renderer
        webRtc?.bindRemoteRenderer(renderer)
    }

    fun dismissStatus() {
        if (_uiState.value.phase == CallUiPhase.StatusMessage) {
            _uiState.update { it.copy(phase = CallUiPhase.Hidden, statusMessage = null, callId = null) }
        }
    }

    fun onChatScreenClosed() {
        when (_uiState.value.phase) {
            CallUiPhase.OutgoingRinging,
            CallUiPhase.IncomingRinging,
            CallUiPhase.Connecting -> endActiveCall()
            else -> Unit
        }
    }

    private fun observeCall(callId: String) {
        callObserverJob?.cancel()
        callObserverJob = viewModelScope.launch {
            callRepository.observeCall(callId)
                .catch { /* ignore */ }
                .collect { call ->
                    if (call == null) return@collect
                    handleCallUpdate(call)
                }
        }
    }

    private suspend fun handleCallUpdate(call: VoiceCall) {
        activeCallType = call.callType
        if (_uiState.value.callType != call.callType) {
            _uiState.update { it.copy(callType = call.callType) }
        }
        when (call.state) {
            VoiceCallState.RINGING -> Unit
            VoiceCallState.ACCEPTED -> {
                if (isCaller && _uiState.value.phase == CallUiPhase.OutgoingRinging) {
                    stopOutgoingRingback()
                    ringTimeoutJob?.cancel()
                    _uiState.update { it.copy(phase = CallUiPhase.Connecting) }
                    startProximityControl()
                    ensureWebRtc()
                    createAndSendOffer(call.id)
                }
            }
            VoiceCallState.CONNECTING -> {
                if (!isCaller && call.offer != null && !handledOffer) {
                    handledOffer = true
                    _uiState.update { it.copy(phase = CallUiPhase.Connecting) }
                    startProximityControl()
                    ensureWebRtc()
                    handleOffer(call)
                }
                if (isCaller && call.answer != null && !handledAnswer) {
                    handledAnswer = true
                    handleAnswer(call)
                }
            }
            VoiceCallState.IN_CALL -> {
                if (_uiState.value.phase != CallUiPhase.InCall) {
                    onConnected()
                }
            }
            VoiceCallState.DECLINED -> {
                if (isCaller) {
                    showStatus(callTypeMessage(R.string.call_declined, R.string.video_call_declined))
                }
                cleanupCall()
            }
            VoiceCallState.MISSED -> {
                if (isCaller) showStatus(getApplication<Application>().getString(R.string.call_no_answer))
                cleanupCall()
            }
            VoiceCallState.FAILED -> {
                val message = failedCallMessage(call.endedReason)
                showStatus(message)
                cleanupCall()
            }
            VoiceCallState.ENDED -> {
                if (_uiState.value.phase != CallUiPhase.StatusMessage) {
                    showStatus(callTypeMessage(R.string.call_ended_by_remote, R.string.video_call_ended_by_remote))
                }
                cleanupCall()
            }
        }
    }

    private fun startRingTimeout(callId: String) {
        ringTimeoutJob?.cancel()
        ringTimeoutJob = viewModelScope.launch {
            delay(RING_TIMEOUT_MS)
            val phase = _uiState.value.phase
            if (phase == CallUiPhase.OutgoingRinging && _uiState.value.callId == callId) {
                callRepository.updateCallState(callId, VoiceCallState.MISSED, endedReason = END_REASON_MISSED_TIMEOUT)
                logSessionToChat(SessionLogStatus.MISSED)
                showStatus(getApplication<Application>().getString(R.string.call_no_answer))
                cleanupCall()
            }
        }
    }

    private suspend fun ensureWebRtc() {
        if (webRtc != null) return
        val userId = currentUserId ?: return
        val callId = _uiState.value.callId ?: return
        webRtc = WebRtcCallEngine(
            context = getApplication(),
            callType = activeCallType,
            onIceCandidate = { candidate ->
                viewModelScope.launch {
                    callRepository.addIceCandidate(
                        callId = callId,
                        userId = userId,
                        candidate = candidate.sdp,
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex,
                    )
                }
            },
            onConnectionState = { state ->
                viewModelScope.launch {
                    when (state) {
                        PeerConnection.PeerConnectionState.CONNECTED -> {
                            reconnectTimeoutJob?.cancel()
                            reconnectTimeoutJob = null
                            webRtc?.useBalancedQuality()
                            if (_uiState.value.phase == CallUiPhase.InCall &&
                                _uiState.value.statusMessage == getApplication<Application>().getString(R.string.call_reconnecting)
                            ) {
                                _uiState.update { it.copy(statusMessage = null) }
                            }
                            onConnected()
                        }
                        PeerConnection.PeerConnectionState.FAILED -> {
                            if (_uiState.value.phase == CallUiPhase.InCall ||
                                _uiState.value.phase == CallUiPhase.Connecting
                            ) {
                                failCall()
                            }
                        }
                        PeerConnection.PeerConnectionState.DISCONNECTED -> startReconnectGrace()
                        else -> Unit
                    }
                }
            },
        ).also { engine ->
            engine.createPeerConnection()
            pendingLocalRenderer?.let(engine::bindLocalRenderer)
            pendingRemoteRenderer?.let(engine::bindRemoteRenderer)
            iceListener?.remove()
            iceListener = callRepository.observeIceCandidates(callId, userId) { data ->
                engine.addIceCandidate(
                    WebRtcCallEngine.IceCandidateData(
                        sdpMid = data.sdpMid,
                        sdpMLineIndex = data.sdpMLineIndex,
                        candidate = data.candidate,
                    )
                )
            }
        }
    }

    private suspend fun createAndSendOffer(callId: String) {
        val engine = webRtc ?: return
        engine.createOffer { result ->
            viewModelScope.launch {
                result.fold(
                    onSuccess = { offer ->
                        callRepository.saveOffer(callId, offer)
                    },
                    onFailure = { failCall() },
                )
            }
        }
    }

    private suspend fun handleOffer(call: VoiceCall) {
        val offer = call.offer ?: return
        val engine = webRtc ?: return
        engine.setRemoteDescription(offer) { result ->
            viewModelScope.launch {
                if (result.isFailure) {
                    failCall()
                    return@launch
                }
                engine.createAnswer { answerResult ->
                    viewModelScope.launch {
                        answerResult.fold(
                            onSuccess = { answer ->
                                callRepository.saveAnswer(call.id, answer)
                            },
                            onFailure = { failCall() },
                        )
                    }
                }
            }
        }
    }

    private suspend fun handleAnswer(call: VoiceCall) {
        val answer = call.answer ?: return
        val engine = webRtc ?: return
        engine.setRemoteDescription(answer) { result ->
            if (result.isFailure) {
                viewModelScope.launch { failCall() }
            }
        }
    }

    private fun startOutgoingRingback() {
        if (!isCaller) return
        ringbackPlayer?.stop()
        ringbackPlayer = CallRingbackPlayer().also { it.start() }
    }

    private fun stopOutgoingRingback() {
        ringbackPlayer?.stop()
        ringbackPlayer = null
    }

    private fun startCallAudio() {
        ensureAudioRouteController()
        audioRouteController?.beginCallAudio()
        val preferredRoute = audioRouteController?.preferredDefaultRoute(_uiState.value.callType == CallType.VIDEO)
            ?: _uiState.value.selectedAudioRoute
        audioRouteController?.selectRoute(preferredRoute)
        applySpeakerAndProximity()
        updateAudioRouteState()
    }

    private fun applySpeakerAndProximity() {
        val route = audioRouteController?.selectedRoute() ?: _uiState.value.selectedAudioRoute
        val speakerOn = route == CallAudioRouteType.SPEAKER
        if (speakerOn || _uiState.value.callType == CallType.VIDEO) {
            stopProximityWakeLock()
        } else {
            startProximityWakeLock()
        }
        _uiState.update {
            it.copy(
                isSpeakerOn = speakerOn,
                selectedAudioRoute = route,
            )
        }
    }

    private fun startProximityWakeLock() {
        if (proximityController == null) {
            proximityController = CallProximityController(getApplication())
        }
        proximityController?.start()
    }

    private fun stopProximityWakeLock() {
        proximityController?.stop()
    }

    private fun startProximityControl() {
        startCallAudio()
    }

    private fun stopProximityControl() {
        stopProximityWakeLock()
        proximityController = null
        audioRouteController?.endCallAudio()
        audioRouteController = null
    }

    private fun ensureAudioRouteController() {
        if (audioRouteController == null) {
            audioRouteController = CallAudioRouteController(getApplication())
        }
    }

    private fun updateAudioRouteState() {
        val controller = audioRouteController ?: return
        val selected = controller.selectedRoute()
        _uiState.update {
            it.copy(
                audioRoutes = controller.availableRoutes(),
                selectedAudioRoute = selected,
                isSpeakerOn = selected == CallAudioRouteType.SPEAKER,
                hasExternalAudioRoute = controller.hasExternalRoute(),
            )
        }
    }

    private fun startReconnectGrace() {
        if (_uiState.value.phase != CallUiPhase.InCall) {
            failCall()
            return
        }
        if (reconnectTimeoutJob?.isActive == true) return
        webRtc?.useReconnectQuality()
        _uiState.update {
            it.copy(statusMessage = getApplication<Application>().getString(R.string.call_reconnecting))
        }
        reconnectTimeoutJob = viewModelScope.launch {
            delay(RECONNECT_TIMEOUT_MS)
            if (_uiState.value.phase == CallUiPhase.InCall) {
                failCall(END_REASON_NETWORK_FAILED)
            }
        }
    }

    private suspend fun onConnected() {
        if (connectedNotified) return
        connectedNotified = true
        val callId = _uiState.value.callId ?: return
        inCallStartedAt = System.currentTimeMillis()
        callRepository.updateCallState(callId, VoiceCallState.IN_CALL)
        CallForegroundServiceController.start(getApplication(), _uiState.value.callType, _uiState.value.isScreenSharing)
        startProximityControl()
        _uiState.update { it.copy(phase = CallUiPhase.InCall, statusMessage = null) }
        startDurationTicker()
    }

    private fun failCall(endedReason: String = END_REASON_WEBRTC_FAILED) {
        val callId = _uiState.value.callId ?: return
        viewModelScope.launch {
            logSessionToChat(SessionLogStatus.FAILED)
            callRepository.updateCallState(callId, VoiceCallState.FAILED, endedReason = endedReason)
            val message = failedCallMessage(endedReason)
            showStatus(message)
            cleanupCall()
        }
    }

    private fun endCall(
        state: VoiceCallState,
        message: String,
        logStatus: SessionLogStatus? = null,
        endedReason: String,
    ) {
        val callId = _uiState.value.callId
        viewModelScope.launch {
            logStatus?.let { status ->
                val durationSec = if (status == SessionLogStatus.COMPLETED) {
                    inCallStartedAt?.let {
                        ((System.currentTimeMillis() - it) / 1000).coerceAtLeast(1)
                    }
                } else {
                    null
                }
                logSessionToChat(status, durationSec)
            }
            callId?.let { callRepository.updateCallState(it, state, endedReason = endedReason) }
            showStatus(message)
            cleanupCall()
        }
    }

    private suspend fun logSessionToChat(status: SessionLogStatus, durationSec: Long? = null) {
        val chat = chatId ?: return
        val userId = currentUserId ?: return
        val recipient = otherUserId ?: return
        val callId = _uiState.value.callId ?: return
        if (callId in loggedCallIds) return
        loggedCallIds.add(callId)
        messageRepository.sendSessionLogMessage(
            chatId = chat,
            senderId = userId,
            recipientId = recipient,
            status = status,
            durationSec = durationSec,
            callType = activeCallType,
            callInitiatorId = if (isCaller) userId else recipient,
        )
    }

    private fun callTypeMessage(voiceRes: Int, videoRes: Int): String {
        val res = if (activeCallType == CallType.VIDEO) videoRes else voiceRes
        return getApplication<Application>().getString(res)
    }

    private fun failedCallMessage(reason: String?): String {
        val res = when (reason) {
            END_REASON_BUSY -> R.string.call_user_busy
            END_REASON_PERMISSION_DENIED -> R.string.call_permission_denied_remote
            END_REASON_NETWORK_FAILED -> R.string.call_network_failed
            else -> if (activeCallType == CallType.VIDEO) {
                R.string.video_call_connect_failed
            } else {
                R.string.call_connect_failed
            }
        }
        return getApplication<Application>().getString(res)
    }

    private fun startDurationTicker() {
        durationJob?.cancel()
        durationJob = viewModelScope.launch {
            while (true) {
                val startedAt = inCallStartedAt ?: break
                val elapsed = ((System.currentTimeMillis() - startedAt) / 1000).coerceAtLeast(0)
                _uiState.update { it.copy(callDurationSec = elapsed) }
                delay(1_000)
            }
        }
    }

    private fun showStatus(message: String) {
        _uiState.update {
            it.copy(phase = CallUiPhase.StatusMessage, statusMessage = message)
        }
        viewModelScope.launch {
            delay(STATUS_DISMISS_MS)
            dismissStatus()
        }
    }

    private fun emitSnackbar(message: String) {
        _snackbarEvents.tryEmit(message)
    }

    private fun cleanupCall() {
        ringTimeoutJob?.cancel()
        reconnectTimeoutJob?.cancel()
        reconnectTimeoutJob = null
        stopOutgoingRingback()
        webRtc?.stopScreenShare()
        iceListener?.remove()
        iceListener = null
        webRtc?.dispose()
        webRtc = null
        pendingLocalRenderer = null
        pendingRemoteRenderer = null
        handledOffer = false
        handledAnswer = false
        connectedNotified = false
        inCallStartedAt = null
        durationJob?.cancel()
        durationJob = null
        activeCallType = CallType.VOICE
        stopProximityControl()
        CallForegroundServiceController.stop(getApplication())
        if (_uiState.value.phase != CallUiPhase.StatusMessage) {
            _uiState.update {
                it.copy(
                    phase = CallUiPhase.Hidden,
                    callId = null,
                    callType = CallType.VOICE,
                    isMuted = false,
                    isSpeakerOn = false,
                    isVideoEnabled = true,
                    isFrontCamera = true,
                    isScreenSharing = false,
                    isScreenSharePaused = false,
                    isMinimized = false,
                    audioRoutes = emptyList(),
                    selectedAudioRoute = CallAudioRouteType.EARPIECE,
                    hasExternalAudioRoute = false,
                    callDurationSec = 0,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    callId = null,
                    callType = CallType.VOICE,
                    isMuted = false,
                    isSpeakerOn = false,
                    isVideoEnabled = true,
                    isFrontCamera = true,
                    isScreenSharing = false,
                    isScreenSharePaused = false,
                    isMinimized = false,
                    audioRoutes = emptyList(),
                    selectedAudioRoute = CallAudioRouteType.EARPIECE,
                    hasExternalAudioRoute = false,
                    callDurationSec = 0,
                )
            }
        }
    }

    private fun teardownObservers() {
        callObserverJob?.cancel()
        incomingObserverJob?.cancel()
        allIncomingObserverJob?.cancel()
    }

    override fun onCleared() {
        teardownObservers()
        screenShareActionJob?.cancel()
        cleanupCall()
        super.onCleared()
    }

    companion object {
        private const val RING_TIMEOUT_MS = 45_000L
        private const val RECONNECT_TIMEOUT_MS = 12_000L
        private const val MEDIA_PROJECTION_FOREGROUND_DELAY_MS = 650L
        private const val STATUS_DISMISS_MS = 2_500L
        private const val END_REASON_LOCAL_ENDED = "local_ended"
        private const val END_REASON_DECLINED = "declined"
        private const val END_REASON_MISSED_TIMEOUT = "missed_timeout"
        private const val END_REASON_BUSY = "busy"
        private const val END_REASON_PERMISSION_DENIED = "permission_denied"
        private const val END_REASON_NETWORK_FAILED = "network_failed"
        private const val END_REASON_WEBRTC_FAILED = "webrtc_failed"
    }
}
