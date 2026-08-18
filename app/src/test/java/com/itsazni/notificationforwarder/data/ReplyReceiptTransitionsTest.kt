package com.itsazni.notificationforwarder.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyReceiptTransitionsTest {

    @Test
    fun confirmFromSentAttemptedYieldsConfirmed() {
        assertEquals(
            ReplyReceiptStatus.CONFIRMED,
            ReplyReceiptTransitions.confirm(ReplyReceiptStatus.SENT_ATTEMPTED)
        )
    }

    @Test
    fun failFromSentAttemptedYieldsFailed() {
        assertEquals(
            ReplyReceiptStatus.FAILED,
            ReplyReceiptTransitions.fail(ReplyReceiptStatus.SENT_ATTEMPTED)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun confirmFromConfirmedIsRejected() {
        ReplyReceiptTransitions.confirm(ReplyReceiptStatus.CONFIRMED)
    }

    @Test(expected = IllegalArgumentException::class)
    fun confirmFromFailedIsRejected() {
        ReplyReceiptTransitions.confirm(ReplyReceiptStatus.FAILED)
    }

    @Test(expected = IllegalArgumentException::class)
    fun failFromConfirmedIsRejected() {
        ReplyReceiptTransitions.fail(ReplyReceiptStatus.CONFIRMED)
    }

    @Test(expected = IllegalArgumentException::class)
    fun failFromFailedIsRejected() {
        ReplyReceiptTransitions.fail(ReplyReceiptStatus.FAILED)
    }
}
