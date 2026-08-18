package com.itsazni.notificationforwarder.service

import android.app.Notification
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.google.gson.Gson
import com.itsazni.notificationforwarder.data.NotificationRepository
import com.itsazni.notificationforwarder.relay.RegisteredReplyAction
import com.itsazni.notificationforwarder.relay.RelayServer
import com.itsazni.notificationforwarder.relay.ReplyActionRegistry
import com.itsazni.notificationforwarder.settings.SettingsStore
import com.itsazni.notificationforwarder.worker.WorkerScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gson = Gson()

    // Fas 2 checkpoint 8: registry of currently-repliable notifications + the local relay
    // server that fires their RemoteInput on dm-hub's behalf. Both are tied to the listener's
    // own lifecycle (onListenerConnected/onListenerDisconnected below) -- no new process, no
    // new foreground service (task 4).
    private val replyRegistry = ReplyActionRegistry()
    private var relayServer: RelayServer? = null

    private data class RecentEvent(
        val contentHash: Int,
        val postedAt: Long,
        val seenAt: Long
    )

    private val dedupLock = Any()
    private val recentEvents = LinkedHashMap<String, RecentEvent>(MAX_RECENT_EVENTS, 0.75f, true)

    override fun onListenerConnected() {
        super.onListenerConnected()
        val port = SettingsStore(applicationContext).relayPort
        val server = RelayServer(applicationContext, replyRegistry, port)
        server.start()
        relayServer = server
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        relayServer?.stop()
        relayServer = null
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn?.key?.let { replyRegistry.remove(it) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val item = sbn ?: return
        if (item.packageName == packageName) {
            return
        }

        val notification: Notification = item.notification
        val extras = notification.extras
        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras?.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()

        if (shouldSkip(item, notification, title, text, bigText)) {
            return
        }

        // MessagingStyle extraction (Fas 2 checkpoint 5, task 2): pull every message
        // the notification currently exposes, plus reply-capable actions. Falls back
        // to the legacy single title/text/bigText message when there's no
        // MessagingStyle (most apps outside WhatsApp/Messenger/Telegram-style chat UIs).
        val extraction = NotificationExtractor.extract(notification)
        val conversationTitle = extraction.conversationTitle ?: title
        val actionsJson = gson.toJson(extraction.actions)
        val outgoing = extraction.messages.ifEmpty {
            listOf(ExtractedMessage(text = bigText.ifBlank { text }, sender = "", timestampMs = item.postTime))
        }

        registerReplyAction(item, notification)

        serviceScope.launch {
            val repository = NotificationRepository(applicationContext)
            val appName = resolveAppName(item.packageName)
            // One enqueue() per extracted message: DB-level dedup (QueueItem.dedupKey)
            // handles reposts of already-seen messages, so re-processing the whole
            // rolling MessagingStyle window on every post is safe and simple -- no
            // separate "seen messages" tracking needed (task 1).
            outgoing.forEach { message ->
                repository.enqueue(
                    packageName = item.packageName,
                    appName = appName,
                    title = conversationTitle,
                    text = message.text,
                    postedAt = message.timestampMs,
                    notificationKey = item.key,
                    senderName = message.sender.ifBlank { null },
                    actionsJson = actionsJson
                )
            }
            WorkerScheduler.enqueueImmediate(applicationContext)
        }
    }

    private fun resolveAppName(pkg: String): String {
        return runCatching {
            val pm = packageManager
            val applicationInfo = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(applicationInfo).toString()
        }.getOrDefault(pkg)
    }

    // Fas 2 checkpoint 8, task 1: mirror of the first RemoteInput-capable action into the
    // in-memory registry the relay server reads from. Keyed by notificationKey (sbn.key) --
    // same key the queue/relay contract already uses -- so a reply POST from dm-hub can find
    // the live PendingIntent for a notification it only knows by that key.
    private fun registerReplyAction(sbn: StatusBarNotification, notification: Notification) {
        val replyAction = notification.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }
        val actionIntent = replyAction?.actionIntent ?: return
        val remoteInputs = replyAction.remoteInputs ?: return
        replyRegistry.register(
            sbn.key,
            RegisteredReplyAction(
                notificationKey = sbn.key,
                actionIntent = actionIntent,
                remoteInputs = remoteInputs,
                registeredAt = System.currentTimeMillis()
            )
        )
    }

    private fun shouldSkip(
        sbn: StatusBarNotification,
        notification: Notification,
        title: String,
        text: String,
        bigText: String
    ): Boolean {
        val isGroupSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (isGroupSummary) {
            return true
        }

        val stableKey = buildStableKey(sbn)
        val contentHash = listOf(title, text, bigText).joinToString("\u001f").hashCode()
        val now = System.currentTimeMillis()

        synchronized(dedupLock) {
            val previous = recentEvents[stableKey]
            if (previous != null) {
                val sameContent = previous.contentHash == contentHash
                val samePostTime = previous.postedAt == sbn.postTime
                val burstUpdate = now - previous.seenAt <= DUPLICATE_WINDOW_MS
                if (sameContent && (samePostTime || burstUpdate)) {
                    return true
                }
            }

            recentEvents[stableKey] = RecentEvent(
                contentHash = contentHash,
                postedAt = sbn.postTime,
                seenAt = now
            )
            trimRecentEvents()
        }

        return false
    }

    private fun buildStableKey(sbn: StatusBarNotification): String {
        val fallback = buildString {
            append(sbn.packageName)
            append('|')
            append(sbn.id)
            append('|')
            append(sbn.tag ?: "")
            append('|')
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                append(sbn.user.hashCode())
            } else {
                append("legacy-user")
            }
        }
        return sbn.key.ifBlank { fallback }
    }

    private fun trimRecentEvents() {
        while (recentEvents.size > MAX_RECENT_EVENTS) {
            val firstKey = recentEvents.entries.firstOrNull()?.key ?: return
            recentEvents.remove(firstKey)
        }
    }

    companion object {
        private const val DUPLICATE_WINDOW_MS = 500L
        private const val MAX_RECENT_EVENTS = 512
    }
}
