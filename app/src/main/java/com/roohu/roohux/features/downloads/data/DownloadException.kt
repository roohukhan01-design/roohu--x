package com.roohu.roohux.features.downloads.data

import com.roohu.roohux.core.database.DownloadError

class DownloadException(
    val error: DownloadError,
    val detail: String? = null,
    val retryable: Boolean = false,
) : Exception(detail)
