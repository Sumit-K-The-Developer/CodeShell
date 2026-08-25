package com.codeshell.app.call

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

class CallAudioRouteController(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var savedAudioMode: Int = AudioManager.MODE_NORMAL
    private var savedSpeakerOn: Boolean = false
    private var isActive: Boolean = false
    private var selectedRouteType: CallAudioRouteType = CallAudioRouteType.EARPIECE

    fun beginCallAudio() {
        if (isActive) return
        isActive = true
        savedAudioMode = audioManager.mode
        savedSpeakerOn = audioManager.isSpeakerphoneOn
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    fun setSpeakerphoneOn(enabled: Boolean) {
        selectRoute(if (enabled) CallAudioRouteType.SPEAKER else CallAudioRouteType.EARPIECE)
    }

    fun isSpeakerphoneOn(): Boolean = audioManager.isSpeakerphoneOn

    fun availableRoutes(): List<CallAudioRoute> {
        val routes = mutableListOf(
            CallAudioRoute(CallAudioRouteType.EARPIECE, "Phone"),
            CallAudioRoute(CallAudioRouteType.SPEAKER, "Speaker"),
        )
        val outputDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        outputDevices.firstOrNull { it.isWiredOutput() }?.let { device ->
            routes += CallAudioRoute(
                CallAudioRouteType.WIRED_HEADSET,
                device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Headset",
            )
        }
        outputDevices.firstOrNull { it.isBluetoothOutput() }?.let { device ->
            routes += CallAudioRoute(
                CallAudioRouteType.BLUETOOTH,
                device.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth",
            )
        }
        return routes.distinctBy { it.type }
    }

    fun preferredDefaultRoute(videoCall: Boolean): CallAudioRouteType {
        val routes = availableRoutes().map { it.type }
        return when {
            CallAudioRouteType.WIRED_HEADSET in routes -> CallAudioRouteType.WIRED_HEADSET
            CallAudioRouteType.BLUETOOTH in routes -> CallAudioRouteType.BLUETOOTH
            videoCall -> CallAudioRouteType.SPEAKER
            else -> CallAudioRouteType.EARPIECE
        }
    }

    fun hasExternalRoute(): Boolean =
        availableRoutes().any {
            it.type == CallAudioRouteType.WIRED_HEADSET || it.type == CallAudioRouteType.BLUETOOTH
        }

    fun selectedRoute(): CallAudioRouteType {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.communicationDevice?.let { device ->
                return when {
                    device.isSpeaker() -> CallAudioRouteType.SPEAKER
                    device.isBluetoothOutput() -> CallAudioRouteType.BLUETOOTH
                    device.isWiredOutput() -> CallAudioRouteType.WIRED_HEADSET
                    else -> CallAudioRouteType.EARPIECE
                }
            }
        }
        return if (audioManager.isSpeakerphoneOn) CallAudioRouteType.SPEAKER else selectedRouteType
    }

    fun selectRoute(type: CallAudioRouteType): Boolean {
        if (!isActive) beginCallAudio()
        selectedRouteType = type
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val target = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.matchesRoute(type) }
            if (target != null && audioManager.setCommunicationDevice(target)) {
                audioManager.isSpeakerphoneOn = type == CallAudioRouteType.SPEAKER
                return true
            }
            if (type == CallAudioRouteType.EARPIECE) {
                audioManager.clearCommunicationDevice()
                audioManager.isSpeakerphoneOn = false
                return true
            }
        }
        audioManager.isSpeakerphoneOn = type == CallAudioRouteType.SPEAKER
        return type == CallAudioRouteType.SPEAKER || type == CallAudioRouteType.EARPIECE
    }

    fun endCallAudio() {
        if (!isActive) return
        isActive = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            audioManager.clearCommunicationDevice()
        }
        audioManager.isSpeakerphoneOn = savedSpeakerOn
        audioManager.mode = savedAudioMode
    }

    private fun AudioDeviceInfo.matchesRoute(type: CallAudioRouteType): Boolean {
        return when (type) {
            CallAudioRouteType.EARPIECE -> this.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
            CallAudioRouteType.SPEAKER -> isSpeaker()
            CallAudioRouteType.WIRED_HEADSET -> isWiredOutput()
            CallAudioRouteType.BLUETOOTH -> isBluetoothOutput()
        }
    }

    private fun AudioDeviceInfo.isSpeaker(): Boolean =
        type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER

    private fun AudioDeviceInfo.isWiredOutput(): Boolean =
        type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            type == AudioDeviceInfo.TYPE_USB_HEADSET

    private fun AudioDeviceInfo.isBluetoothOutput(): Boolean =
        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                type == AudioDeviceInfo.TYPE_BLE_HEADSET)
}

enum class CallAudioRouteType {
    EARPIECE,
    SPEAKER,
    WIRED_HEADSET,
    BLUETOOTH,
}

data class CallAudioRoute(
    val type: CallAudioRouteType,
    val label: String,
)
