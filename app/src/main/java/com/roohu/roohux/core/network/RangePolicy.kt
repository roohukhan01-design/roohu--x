package com.roohu.roohux.core.network

/** What to do with an HTTP response to a download request (fresh or Range-resume). Pure and unit-tested. */
sealed interface RangeOutcome {
    /** Append the body at [start] (== bytes already on disk). [total] is -1 when the server did not say. */
    data class Continue(val start: Long, val total: Long) : RangeOutcome

    /** Write the body from byte 0, discarding anything already on disk (fresh 200, or a 200 to a Range request). */
    data class FromStart(val total: Long) : RangeOutcome

    /** 416 and the server says the file is exactly what we already hold: publish it. */
    data object AlreadyComplete : RangeOutcome

    /** The partial file cannot be trusted. Delete it and request again from byte 0. */
    data class DiscardAndRetry(val reason: String) : RangeOutcome

    /** Not a usable response for this request (e.g. 416 with nothing on disk). */
    data class Reject(val reason: String) : RangeOutcome
}

object RangePolicy {
    private val unsatisfied = Regex("bytes\\s+\\*/(\\d+)")

    /**
     * @param code HTTP status (already known to be 200, 206 or 416; other codes are handled by the caller)
     * @param existing bytes currently in the partial file (0 for a fresh download)
     * @param contentRange raw Content-Range header or null
     * @param contentLength body length from Content-Length, or -1 when unknown
     */
    fun evaluate(code: Int, existing: Long, contentRange: String?, contentLength: Long): RangeOutcome = when (code) {
        416 -> when {
            existing <= 0 -> RangeOutcome.Reject("Range not satisfiable for an empty file")
            unsatisfied.find(contentRange.orEmpty())?.groupValues?.get(1)?.toLongOrNull() == existing -> RangeOutcome.AlreadyComplete
            else -> RangeOutcome.DiscardAndRetry("Range not satisfiable; restarting")
        }
        206 -> {
            val r = ContentRange.parse(contentRange)
            when {
                r == null -> RangeOutcome.DiscardAndRetry("Partial response without a valid Content-Range")
                r.start != existing -> RangeOutcome.DiscardAndRetry("Server resumed at ${r.start}, expected $existing")
                r.end < r.start -> RangeOutcome.DiscardAndRetry("Invalid byte range")
                r.total != null && r.end >= r.total -> RangeOutcome.DiscardAndRetry("Byte range exceeds total size")
                contentLength >= 0 && contentLength != r.end - r.start + 1 ->
                    RangeOutcome.DiscardAndRetry("Body length does not match Content-Range")
                else -> RangeOutcome.Continue(existing, r.total ?: if (contentLength > 0) existing + contentLength else -1L)
            }
        }
        200 -> RangeOutcome.FromStart(contentLength.takeIf { it > 0 } ?: -1L)
        else -> RangeOutcome.Reject("Unexpected status $code")
    }
}
