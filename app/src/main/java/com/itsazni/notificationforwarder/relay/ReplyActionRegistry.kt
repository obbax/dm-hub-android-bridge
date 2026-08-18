package com.itsazni.notificationforwarder.relay

/**
 * Android-typed wrapper around LruRegistry -- see LruRegistryTest for the tested LRU mechanics
 * (cap + eviction + removal). This class itself has no JVM unit test: it only stores
 * android.app.PendingIntent/RemoteInput references, which the plain JUnit classpath can't
 * safely construct (see NotificationExtractor's doc comment for the same constraint elsewhere
 * in this repo). Registered in AppNotificationListenerService.onNotificationPosted, cleared in
 * onNotificationRemoved.
 */
class ReplyActionRegistry(capacity: Int = DEFAULT_CAPACITY) {
    private val registry = LruRegistry<RegisteredReplyAction>(capacity)

    fun register(notificationKey: String, action: RegisteredReplyAction) {
        registry.put(notificationKey, action)
    }

    fun find(notificationKey: String): RegisteredReplyAction? = registry.get(notificationKey)

    fun remove(notificationKey: String) {
        registry.remove(notificationKey)
    }

    fun size(): Int = registry.size()

    companion object {
        const val DEFAULT_CAPACITY = 200
    }
}
