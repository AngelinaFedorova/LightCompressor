package com.abedelazizshe.lightcompressorlibrary

import androidx.annotation.MainThread
import androidx.annotation.WorkerThread

/**
 * Created by AbedElaziz Shehadeh on 27 Jan, 2020
 * elaziz.shehadeh@gmail.com
 */
interface CompressionListener {
    @MainThread
    fun onStart(index: Int, key: String)

    @MainThread
    fun onSuccess(index: Int, key: String, size: Long, path: String?)

    @MainThread
    fun onFailure(index: Int, key: String, failureMessage: String)

    @WorkerThread
    fun onProgress(index: Int, key: String, percent: Float)

    @WorkerThread
    fun onCancelled(index: Int, key: String)
}

interface CompressionProgressListener {
    fun onProgressChanged(index: Int, key: String, percent: Float)

    fun onProgressCancelled(index: Int, key: String)
}
