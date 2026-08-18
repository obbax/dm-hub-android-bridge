package com.itsazni.notificationforwarder.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DedupKeyTest {

    @Test
    fun identicalInputsProduceIdenticalKeys() {
        val a = DedupKey.build("notif-1", "Alice", 1000L, "hello")
        val b = DedupKey.build("notif-1", "Alice", 1000L, "hello")
        assertEquals(a, b)
    }

    @Test
    fun retryWithIdenticalContentAndTimestampDedupsToSameKey() {
        // Simulates Android re-posting the same unchanged notification.
        val originalPost = DedupKey.build("notif-1", "Alice", 1000L, "hello")
        val retryPost = DedupKey.build("notif-1", "Alice", 1000L, "hello")
        assertEquals(originalPost, retryPost)
    }

    @Test
    fun differentMessageTextOnSameNotificationProducesDifferentKeys() {
        val first = DedupKey.build("notif-1", "Alice", 1000L, "hello")
        val second = DedupKey.build("notif-1", "Alice", 2000L, "how are you")
        assertNotEquals(first, second)
    }

    @Test
    fun differentSenderOnSameNotificationProducesDifferentKeys() {
        // Group chat: two different people posting into the same conversation notification.
        val alice = DedupKey.build("notif-1", "Alice", 1000L, "hello")
        val bob = DedupKey.build("notif-1", "Bob", 1000L, "hello")
        assertNotEquals(alice, bob)
    }

    @Test
    fun differentNotificationKeyProducesDifferentKeysEvenWithSameContent() {
        val first = DedupKey.build("notif-1", "Alice", 1000L, "hello")
        val second = DedupKey.build("notif-2", "Alice", 1000L, "hello")
        assertNotEquals(first, second)
    }

    @Test
    fun threeDistinctMessagesOnOneNotificationProduceThreeDistinctKeys() {
        // Acceptance criterion: three new messages in the same conversation must
        // become three separate queue rows, not one.
        val m1 = DedupKey.build("notif-1", "Alice", 1000L, "one")
        val m2 = DedupKey.build("notif-1", "Alice", 2000L, "two")
        val m3 = DedupKey.build("notif-1", "Alice", 3000L, "three")
        val keys = setOf(m1, m2, m3)
        assertEquals(3, keys.size)
    }
}
