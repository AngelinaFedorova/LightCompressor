package com.abedelazizshe.lightcompressorlibrary.video

data class Result(
    val index: Int,
    val key: String,
    val success: Boolean,
    val failureMessage: String?,
    val size: Long = 0,
    val path: String? = null,
)
