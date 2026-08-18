package com.itsazni.notificationforwarder.relay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LruRegistryTest {

    @Test
    fun putThenGetReturnsTheStoredValue() {
        val registry = LruRegistry<String>(capacity = 3)
        registry.put("a", "action-a")
        assertEquals("action-a", registry.get("a"))
    }

    @Test
    fun getOnMissingKeyReturnsNull() {
        val registry = LruRegistry<String>(capacity = 3)
        assertNull(registry.get("missing"))
    }

    @Test
    fun sizeReflectsNumberOfEntries() {
        val registry = LruRegistry<String>(capacity = 5)
        registry.put("a", "1")
        registry.put("b", "2")
        assertEquals(2, registry.size())
    }

    @Test
    fun exceedingCapacityEvictsTheLeastRecentlyUsedEntry() {
        val registry = LruRegistry<String>(capacity = 2)
        registry.put("a", "1")
        registry.put("b", "2")
        registry.put("c", "3")
        assertNull(registry.get("a"))
        assertEquals("2", registry.get("b"))
        assertEquals("3", registry.get("c"))
        assertEquals(2, registry.size())
    }

    @Test
    fun gettingAnEntryRefreshesItsRecencySoItSurvivesEviction() {
        val registry = LruRegistry<String>(capacity = 2)
        registry.put("a", "1")
        registry.put("b", "2")
        // Touch "a" so it becomes the most-recently-used, making "b" the eviction target.
        registry.get("a")
        registry.put("c", "3")
        assertEquals("1", registry.get("a"))
        assertNull(registry.get("b"))
        assertEquals("3", registry.get("c"))
    }

    @Test
    fun removeDeletesTheEntryAndReturnsItsOldValue() {
        val registry = LruRegistry<String>(capacity = 3)
        registry.put("a", "1")
        val removed = registry.remove("a")
        assertEquals("1", removed)
        assertNull(registry.get("a"))
        assertEquals(0, registry.size())
    }

    @Test
    fun removeOnMissingKeyReturnsNullAndIsANoOp() {
        val registry = LruRegistry<String>(capacity = 3)
        registry.put("a", "1")
        val removed = registry.remove("missing")
        assertNull(removed)
        assertEquals(1, registry.size())
    }

    @Test
    fun containsKeyReflectsPresence() {
        val registry = LruRegistry<String>(capacity = 3)
        registry.put("a", "1")
        assertTrue(registry.containsKey("a"))
        registry.remove("a")
        assertTrue(!registry.containsKey("a"))
    }

    @Test
    fun overwritingAnExistingKeyDoesNotGrowSize() {
        val registry = LruRegistry<String>(capacity = 3)
        registry.put("a", "1")
        registry.put("a", "2")
        assertEquals(1, registry.size())
        assertEquals("2", registry.get("a"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroCapacityIsRejected() {
        LruRegistry<String>(capacity = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeCapacityIsRejected() {
        LruRegistry<String>(capacity = -1)
    }

    @Test
    fun capacityAroundTwoHundredMatchesTheProductionRegistryDefault() {
        // Mirrors ReplyActionRegistry.DEFAULT_CAPACITY -- registering 201 notifications must
        // evict exactly the oldest one, not thrash or drop more than necessary.
        val registry = LruRegistry<Int>(capacity = 200)
        for (i in 1..201) {
            registry.put("notif-$i", i)
        }
        assertEquals(200, registry.size())
        assertNull(registry.get("notif-1"))
        assertEquals(2, registry.get("notif-2"))
        assertEquals(201, registry.get("notif-201"))
    }
}
