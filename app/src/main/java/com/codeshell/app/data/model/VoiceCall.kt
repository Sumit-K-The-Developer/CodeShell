package com.codeshell.app.data.model

import com.google.firebase.Timestamp

enum class CallType {
    VOICE,
    VIDEO;

    companion object {
        fun fromStorage(value: String?): CallType {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: VOICE
        }
    }
}

enum class VoiceCallState {
    RINGING,
    ACCEPTED,
    CONNECTING,
    IN_CALL,
    ENDED,
    DECLINED,
    MISSED,
    FAILED;

    companion object {
        fun fromStorage(value: String?): VoiceCallState {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: ENDED
        }
    }
}

data class SessionDescriptionData(
    val sdp: String = "",
    val type: String = "",
)

data class VoiceCall(
    val id: String,
    val chatId: String,
    val callerId: String,
    val calleeId: String,
    val callType: CallType = CallType.VOICE,
    val state: VoiceCallState,
    val offer: SessionDescriptionData? = null,
    val answer: SessionDescriptionData? = null,
    val endedReason: String? = null,
    val createdAt: Timestamp? = null,
    val updatedAt: Timestamp? = null,
) {
    fun otherUserId(currentUserId: String): String =
        if (callerId == currentUserId) calleeId else callerId

    fun isParticipant(userId: String): Boolean =
        callerId == userId || calleeId == userId
}

data class IceCandidateData(
    val id: String,
    val userId: String,
    val candidate: String,
    val sdpMid: String,
    val sdpMLineIndex: Int,
)
