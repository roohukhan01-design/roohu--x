package com.roohu.roohux

import com.roohu.roohux.core.network.RangeOutcome
import com.roohu.roohux.core.network.RangePolicy
import org.junit.Assert.*
import org.junit.Test

class RangePolicyTest {
    @Test fun freshOkStartsFromZero() =
        assertEquals(RangeOutcome.FromStart(1000), RangePolicy.evaluate(200, 0, null, 1000))

    @Test fun unknownLengthIsMinusOne() {
        assertEquals(RangeOutcome.FromStart(-1), RangePolicy.evaluate(200, 0, null, -1))
        assertEquals(RangeOutcome.FromStart(-1), RangePolicy.evaluate(200, 0, null, 0))
    }

    @Test fun serverIgnoringRangeRestartsInsteadOfAppending() =
        assertEquals(RangeOutcome.FromStart(1000), RangePolicy.evaluate(200, 100, null, 1000))

    @Test fun validPartialContinues() =
        assertEquals(RangeOutcome.Continue(100, 1000), RangePolicy.evaluate(206, 100, "bytes 100-999/1000", 900))

    @Test fun partialWithUnknownTotalUsesBodyLength() {
        assertEquals(RangeOutcome.Continue(100, 1000), RangePolicy.evaluate(206, 100, "bytes 100-999/*", 900))
        assertEquals(RangeOutcome.Continue(100, -1), RangePolicy.evaluate(206, 100, "bytes 100-999/*", -1))
    }

    @Test fun partialAtWrongOffsetIsDiscarded() =
        assertTrue(RangePolicy.evaluate(206, 100, "bytes 0-999/1000", 1000) is RangeOutcome.DiscardAndRetry)

    @Test fun partialWithoutContentRangeIsDiscarded() =
        assertTrue(RangePolicy.evaluate(206, 100, null, 900) is RangeOutcome.DiscardAndRetry)

    @Test fun partialWithMismatchedBodyLengthIsDiscarded() =
        assertTrue(RangePolicy.evaluate(206, 100, "bytes 100-999/1000", 800) is RangeOutcome.DiscardAndRetry)

    @Test fun partialBeyondTotalIsDiscarded() =
        assertTrue(RangePolicy.evaluate(206, 100, "bytes 100-1000/1000", 901) is RangeOutcome.DiscardAndRetry)

    @Test fun rangeNotSatisfiableWhenWeHoldEverythingIsComplete() =
        assertEquals(RangeOutcome.AlreadyComplete, RangePolicy.evaluate(416, 500, "bytes */500", -1))

    @Test fun rangeNotSatisfiableOtherwiseRestarts() {
        assertTrue(RangePolicy.evaluate(416, 500, "bytes */900", -1) is RangeOutcome.DiscardAndRetry)
        assertTrue(RangePolicy.evaluate(416, 500, null, -1) is RangeOutcome.DiscardAndRetry)
    }

    @Test fun rangeNotSatisfiableWithNothingOnDiskIsRejected() =
        assertTrue(RangePolicy.evaluate(416, 0, "bytes */900", -1) is RangeOutcome.Reject)

    @Test fun otherStatusIsRejected() =
        assertTrue(RangePolicy.evaluate(500, 0, null, -1) is RangeOutcome.Reject)
}
