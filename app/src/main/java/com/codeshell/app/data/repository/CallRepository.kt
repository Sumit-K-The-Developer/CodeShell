package com.codeshell.app.data.repository

import com.codeshell.app.data.model.CallType
import com.codeshell.app.data.model.IceCandidateData
import com.codeshell.app.data.model.SessionDescriptionData
import com.codeshell.app.data.model.VoiceCall
import com.codeshell.app.data.model.VoiceCallState
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

class CallRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    private fun callRef(callId: String) = firestore.collection(COLLECTION_CALLS).document(callId)

    suspend fun createRingingCall(
        chatId: String,
        callerId: String,
        calleeId: String,
        callType: CallType = CallType.VOICE,
    ): Result<VoiceCall> {
        return try {
            val callId = UUID.randomUUID().toString()
            val now = Timestamp.now()
            val data = mapOf(
                FIELD_CHAT_ID to chatId,
                FIELD_CALLER_ID to callerId,
                FIELD_CALLEE_ID to calleeId,
                FIELD_CALL_TYPE to callType.name.lowercase(),
                FIELD_STATE to VoiceCallState.RINGING.name.lowercase(),
                FIELD_CREATED_AT to now,
                FIELD_UPDATED_AT to now,
            )
            callRef(callId).set(data).await()
            Result.success(
                VoiceCall(
                    id = callId,
                    chatId = chatId,
                    callerId = callerId,
                    calleeId = calleeId,
                    callType = callType,
                    state = VoiceCallState.RINGING,
                    createdAt = now,
                    updatedAt = now,
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateCallState(
        callId: String,
        state: VoiceCallState,
        endedReason: String? = null,
    ): Result<Unit> {
        return try {
            val updates = mutableMapOf<String, Any>(
                FIELD_STATE to state.name.lowercase(),
                FIELD_UPDATED_AT to Timestamp.now(),
            )
            if (endedReason != null) {
                updates[FIELD_ENDED_REASON] = endedReason
            }
            callRef(callId).update(updates).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveOffer(callId: String, offer: SessionDescriptionData): Result<Unit> {
        return try {
            callRef(callId).update(
                mapOf(
                    FIELD_OFFER to mapOf(FIELD_SDP to offer.sdp, FIELD_TYPE to offer.type),
                    FIELD_STATE to VoiceCallState.CONNECTING.name.lowercase(),
                    FIELD_UPDATED_AT to Timestamp.now(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun saveAnswer(callId: String, answer: SessionDescriptionData): Result<Unit> {
        return try {
            callRef(callId).update(
                mapOf(
                    FIELD_ANSWER to mapOf(FIELD_SDP to answer.sdp, FIELD_TYPE to answer.type),
                    FIELD_UPDATED_AT to Timestamp.now(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addIceCandidate(
        callId: String,
        userId: String,
        candidate: String,
        sdpMid: String,
        sdpMLineIndex: Int,
    ): Result<Unit> {
        return try {
            val id = UUID.randomUUID().toString()
            callRef(callId).collection(COLLECTION_CANDIDATES).document(id).set(
                mapOf(
                    FIELD_USER_ID to userId,
                    FIELD_CANDIDATE to candidate,
                    FIELD_SDP_MID to sdpMid,
                    FIELD_SDP_MLINE_INDEX to sdpMLineIndex,
                    FIELD_CREATED_AT to Timestamp.now(),
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeCall(callId: String): Flow<VoiceCall?> = callbackFlow {
        val registration = callRef(callId).addSnapshotListener { snapshot, error ->
            if (error != null) {
                close(error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toVoiceCall())
        }
        awaitClose { registration.remove() }
    }

    fun observeIncomingRinging(chatId: String, calleeId: String): Flow<VoiceCall?> = callbackFlow {
        val registration = firestore.collection(COLLECTION_CALLS)
            .whereEqualTo(FIELD_CHAT_ID, chatId)
            .whereEqualTo(FIELD_CALLEE_ID, calleeId)
            .whereEqualTo(FIELD_STATE, VoiceCallState.RINGING.name.lowercase())
            .limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val call = snapshot?.documents?.firstOrNull()?.toVoiceCall()
                trySend(call)
            }
        awaitClose { registration.remove() }
    }

    fun observeIncomingRingingForCallee(calleeId: String): Flow<Map<String, CallType>> = callbackFlow {
        val registration = firestore.collection(COLLECTION_CALLS)
            .whereEqualTo(FIELD_CALLEE_ID, calleeId)
            .whereEqualTo(FIELD_STATE, VoiceCallState.RINGING.name.lowercase())
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val incoming = snapshot?.documents?.mapNotNull { doc ->
                    val call = doc.toVoiceCall() ?: return@mapNotNull null
                    call.chatId to call.callType
                }?.toMap().orEmpty()
                trySend(incoming)
            }
        awaitClose { registration.remove() }
    }

    fun observeIceCandidates(
        callId: String,
        currentUserId: String,
        onCandidate: (IceCandidateData) -> Unit,
    ): ListenerRegistration {
        return callRef(callId).collection(COLLECTION_CANDIDATES)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                snapshot.documentChanges.forEach { change ->
                    if (change.type != com.google.firebase.firestore.DocumentChange.Type.ADDED) return@forEach
                    val data = change.document.data
                    val ownerId = data[FIELD_USER_ID] as? String ?: return@forEach
                    if (ownerId == currentUserId) return@forEach
                    onCandidate(
                        IceCandidateData(
                            id = change.document.id,
                            userId = ownerId,
                            candidate = data[FIELD_CANDIDATE] as? String ?: return@forEach,
                            sdpMid = data[FIELD_SDP_MID] as? String ?: "",
                            sdpMLineIndex = (data[FIELD_SDP_MLINE_INDEX] as? Long)?.toInt() ?: 0,
                        )
                    )
                }
            }
    }

    private fun com.google.firebase.firestore.DocumentSnapshot.toVoiceCall(): VoiceCall? {
        if (!exists()) return null
        val offerMap = get(FIELD_OFFER) as? Map<*, *>
        val answerMap = get(FIELD_ANSWER) as? Map<*, *>
        return VoiceCall(
            id = id,
            chatId = getString(FIELD_CHAT_ID).orEmpty(),
            callerId = getString(FIELD_CALLER_ID).orEmpty(),
            calleeId = getString(FIELD_CALLEE_ID).orEmpty(),
            callType = CallType.fromStorage(getString(FIELD_CALL_TYPE)),
            state = VoiceCallState.fromStorage(getString(FIELD_STATE)),
            offer = offerMap?.let {
                SessionDescriptionData(
                    sdp = it[FIELD_SDP] as? String ?: "",
                    type = it[FIELD_TYPE] as? String ?: "",
                )
            },
            answer = answerMap?.let {
                SessionDescriptionData(
                    sdp = it[FIELD_SDP] as? String ?: "",
                    type = it[FIELD_TYPE] as? String ?: "",
                )
            },
            endedReason = getString(FIELD_ENDED_REASON),
            createdAt = getTimestamp(FIELD_CREATED_AT),
            updatedAt = getTimestamp(FIELD_UPDATED_AT),
        )
    }

    companion object {
        const val COLLECTION_CALLS = "calls"
        const val COLLECTION_CANDIDATES = "candidates"
        const val FIELD_CHAT_ID = "chatId"
        const val FIELD_CALLER_ID = "callerId"
        const val FIELD_CALLEE_ID = "calleeId"
        const val FIELD_CALL_TYPE = "callType"
        const val FIELD_STATE = "state"
        const val FIELD_OFFER = "offer"
        const val FIELD_ANSWER = "answer"
        const val FIELD_SDP = "sdp"
        const val FIELD_TYPE = "type"
        const val FIELD_ENDED_REASON = "endedReason"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UPDATED_AT = "updatedAt"
        const val FIELD_USER_ID = "userId"
        const val FIELD_CANDIDATE = "candidate"
        const val FIELD_SDP_MID = "sdpMid"
        const val FIELD_SDP_MLINE_INDEX = "sdpMLineIndex"
    }
}
