package com.itsazni.notificationforwarder.relay

import android.app.PendingIntent
import android.app.RemoteInput

/**
 * A live reply action captured off an active StatusBarNotification's Notification.Action.
 * PendingIntents die with the notification (or the process) -- they can't be persisted across
 * restarts, so ReplyActionRegistry is intentionally in-memory-only and rebuilt as notifications
 * arrive. This is the checkpoint 8 conditional_reply semantics: the relay can only answer
 * notifications that are still active or were recently seen, never ones from a previous app run.
 */
data class RegisteredReplyAction(
    val notificationKey: String,
    val actionIntent: PendingIntent,
    val remoteInputs: Array<RemoteInput>,
    val registeredAt: Long
)
