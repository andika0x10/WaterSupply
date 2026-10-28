package com.andika.watersupply.widget

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class WidgetStateTest {
    @Test fun emptyDayIsLoadedData() = runTest {
        assertEquals(WidgetState.Ready(WidgetProgress(0, 16, false)),
            loadWidgetState { WidgetProgress(0, 16, false) })
    }

    @Test fun failureCanBeRetriedWithoutInventingEmptyProgress() = runTest {
        var unavailable = true
        val load: suspend () -> WidgetProgress = {
            check(!unavailable) { "Database unavailable" }
            WidgetProgress(7, 20, true)
        }
        assertEquals(WidgetState.Failed, loadWidgetState(load = load))
        unavailable = false
        assertEquals(WidgetState.Ready(WidgetProgress(7, 20, true)), loadWidgetState(load = load))
    }

    @Test fun stalledReadBecomesRecoverableAfterTenSeconds() = runTest {
        val before = testScheduler.currentTime
        assertEquals(WidgetState.Failed, loadWidgetState { awaitCancellation() })
        assertEquals(10_000L, testScheduler.currentTime - before)
    }

    @Test fun parentCancellationIsNotConvertedToAnErrorScreen() = runTest {
        val cancelled = CancellationException("Session closed")
        try {
            loadWidgetState { throw cancelled }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertEquals(cancelled.message, actual.message)
        }
    }
}
