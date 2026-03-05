package com.ilanp13.shabbatalertdismisser

data class DismissRecord(
    val timestampMs: Long,
    val packageName: String,
    val buttonText: String,
    val windowText: String
)
