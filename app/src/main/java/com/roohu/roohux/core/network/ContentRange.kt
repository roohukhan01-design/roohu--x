package com.roohu.roohux.core.network

data class ContentRange(val start: Long, val end: Long, val total: Long?) {
    companion object {
        private val pattern = Regex("bytes\\s+(\\d+)-(\\d+)/(\\d+|\\*)")

        fun parse(header: String?): ContentRange? {
            val m = pattern.matchEntire(header?.trim() ?: return null) ?: return null
            val total = m.groupValues[3].toLongOrNull()
            return ContentRange(m.groupValues[1].toLong(), m.groupValues[2].toLong(), total)
        }
    }
}
