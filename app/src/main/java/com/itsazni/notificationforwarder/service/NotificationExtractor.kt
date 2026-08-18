package com.itsazni.notificationforwarder.service

import android.app.Notification
import androidx.core.app.NotificationCompat

data class ExtractedMessage(
    val text: String,
    val sender: String,
    val timestampMs: Long
)

data class ExtractedAction(
    val label: String,
    val hasRemoteInput: Boolean,
    val remoteInputKey: String?
)

data class NotificationExtraction(
    val conversationTitle: String?,
    // Empty when the notification has no MessagingStyle -- caller falls back to
    // legacy EXTRA_TITLE/EXTRA_TEXT/EXTRA_BIG_TEXT (Fas 2 checkpoint 5, task 2).
    val messages: List<ExtractedMessage>,
    val actions: List<ExtractedAction>
)

/**
 * Pulls MessagingStyle (messages, sender, conversation title) and reply-capable
 * actions (RemoteInput) out of a posted Notification.
 *
 * Framework-bound (needs a real android.app.Notification, no Robolectric in this
 * repo) so this has no JVM unit test -- see DedupKeyTest/LeaseRecoveryTest for the
 * parts of this checkpoint that ARE plain-Kotlin testable.
 */
object NotificationExtractor {
    fun extract(notification: Notification): NotificationExtraction {
        val style = NotificationCompat.MessagingStyle
            .extractMessagingStyleFromNotification(notification)

        val messages = style?.messages.orEmpty().map { message ->
            ExtractedMessage(
                text = message.text?.toString().orEmpty(),
                sender = message.person?.name?.toString().orEmpty(),
                timestampMs = message.timestamp
            )
        }

        val actions = notification.actions.orEmpty().map { action ->
            val remoteInputs = action.remoteInputs
            ExtractedAction(
                label = action.title?.toString().orEmpty(),
                hasRemoteInput = !remoteInputs.isNullOrEmpty(),
                remoteInputKey = remoteInputs?.firstOrNull()?.resultKey
            )
        }

        return NotificationExtraction(
            conversationTitle = style?.conversationTitle?.toString(),
            messages = messages,
            actions = actions
        )
    }
}
