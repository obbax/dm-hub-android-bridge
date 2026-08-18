package com.itsazni.notificationforwarder.relay

/**
 * Minimal LRU-capped key/value registry. Framework-agnostic (no Android imports) so it's
 * plain-JVM-testable; ReplyActionRegistry wraps this with the real Android Notification.Action
 * payload type, which the plain JUnit classpath can't safely construct (see
 * NotificationExtractor's doc comment for the same constraint elsewhere in this repo).
 * Access-ordered LinkedHashMap: get() counts as a "use" for eviction ordering, matching the
 * recentEvents cache already used in AppNotificationListenerService.
 */
class LruRegistry<V>(private val capacity: Int) {
    init {
        require(capacity > 0) { "capacity must be > 0" }
    }

    private val map = LinkedHashMap<String, V>(capacity, 0.75f, true)

    @Synchronized
    fun put(key: String, value: V) {
        map[key] = value
        trim()
    }

    @Synchronized
    fun get(key: String): V? = map[key]

    @Synchronized
    fun remove(key: String): V? = map.remove(key)

    @Synchronized
    fun containsKey(key: String): Boolean = map.containsKey(key)

    @Synchronized
    fun size(): Int = map.size

    private fun trim() {
        while (map.size > capacity) {
            val eldest = map.entries.firstOrNull()?.key ?: return
            map.remove(eldest)
        }
    }
}
