package com.roohu.roohux.features.downloads.data

import com.roohu.roohux.core.database.DownloadError

class DownloadException(
    val error: DownloadError,
    val detail: String? = null,
    val retryable: Boolean = false,
    /** The partial file was discarded; retrying immediately from byte 0 doesn't count against the retry budget. */
    val restartClean: Boolean = false,
) : Exception(detail)
