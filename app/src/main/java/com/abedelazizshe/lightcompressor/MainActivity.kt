package com.abedelazizshe.lightcompressor

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.abedelazizshe.lightcompressor.databinding.ActivityMainBinding
import com.abedelazizshe.lightcompressorlibrary.CompressionListener
import com.abedelazizshe.lightcompressorlibrary.VideoCompressor
import com.abedelazizshe.lightcompressorlibrary.VideoQuality
import com.abedelazizshe.lightcompressorlibrary.config.Configuration
import com.abedelazizshe.lightcompressorlibrary.config.SaveLocation
import com.abedelazizshe.lightcompressorlibrary.config.SharedStorageConfiguration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.util.UUID

/**
 * Created by AbedElaziz Shehadeh on 26 Jan, 2020
 * elaziz.shehadeh@gmail.com
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    companion object {
        const val REQUEST_SELECT_VIDEO = 0
        const val REQUEST_CAPTURE_VIDEO = 1
    }

    private val uris = mutableListOf<Uri>()
    private val uris2 = mutableMapOf<String, Uri>()
    private val withKeyProcess = true
    private lateinit var adapter: RecyclerViewAdapter


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setReadStoragePermission()

        binding.pickVideo.setOnClickListener {
            pickVideo()
        }

        binding.recordVideo.setOnClickListener {
            dispatchTakeVideoIntent()
        }

        binding.cancel.setOnClickListener {
            if (!withKeyProcess) {
                VideoCompressor.cancel()
            } else {
                adapter.list.forEach {
                    VideoCompressor.cancel(it.key)
                }
            }
        }

        val recyclerview = findViewById<RecyclerView>(R.id.recyclerview)
        recyclerview.layoutManager = LinearLayoutManager(this)
        adapter = RecyclerViewAdapter(applicationContext, mutableListOf()) {
            if (withKeyProcess) VideoCompressor.cancel(it)
        }
        recyclerview.adapter = adapter
    }

    //Pick a video file from device
    private fun pickVideo() {
        val intent = Intent()
        intent.apply {
            type = "video/*"
            action = Intent.ACTION_PICK
        }
        intent.putExtra(
            Intent.EXTRA_ALLOW_MULTIPLE,
            true
        )
        startActivityForResult(Intent.createChooser(intent, "Select video"), REQUEST_SELECT_VIDEO)
    }

    private fun dispatchTakeVideoIntent() {
        Intent(MediaStore.ACTION_VIDEO_CAPTURE).also { takeVideoIntent ->
            takeVideoIntent.resolveActivity(packageManager)?.also {
                startActivityForResult(takeVideoIntent, REQUEST_CAPTURE_VIDEO)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    override fun onActivityResult(requestCode: Int, resultCode: Int, intent: Intent?) {

        reset()

        if (resultCode == Activity.RESULT_OK)
            if (requestCode == REQUEST_SELECT_VIDEO || requestCode == REQUEST_CAPTURE_VIDEO) {
                handleResult(intent)
            }

        super.onActivityResult(requestCode, resultCode, intent)
    }

    private fun handleResult(data: Intent?) {
        val rootDir = this.applicationContext.filesDir.path
        val dir = "$rootDir/localFiles"
        createDirectory(dir)
        val clipData: ClipData? = data?.clipData
        if (clipData != null) {
            for (i in 0 until clipData.itemCount) {
                if (withKeyProcess) {
                    val videoItem = clipData.getItemAt(i)
                    val fileName = File(videoItem.uri.path).name
                    val to = "$dir/$fileName"
                    copyMedia(videoItem.uri.toString(), to)
                    val key = UUID.randomUUID().toString()
                    uris2[key] = Uri.fromFile(File(to))
                } else {
                    val videoItem = clipData.getItemAt(i)
                    uris.add(videoItem.uri)
                }
            }
            processVideo()
        } else if (data != null && data.data != null) {
            val uri = data.data
            if (withKeyProcess) {
                if (uri != null) {
                    val fileName = File(uri.path).name
                    val to = "$dir/$fileName"
                    copyMedia(uri.toString(), to)
                    val key = UUID.randomUUID().toString()
                    uris2[key] = Uri.fromFile(File(to))
                }
            } else {
                uris.add(uri!!)
            }
            processVideo()
        }
    }

    private fun createDirectory(url: String) {
        if (!File(url).exists()) {
            File(url).mkdirs()
        }
    }

    private fun copyMedia(from: String, to: String) {
        val readOnlyMode = "r"
        val fromUri = Uri.parse(from)
        this.contentResolver.openFileDescriptor(fromUri, readOnlyMode).use { pfd ->
            FileInputStream(pfd?.fileDescriptor).copyTo(File(to).outputStream())
        }
    }

    private fun reset() {
        uris.clear()
        uris2.clear()
        if (!withKeyProcess) {
            binding.mainContents.visibility = View.GONE
            adapter.list.clear()
            adapter.notifyDataSetChanged()
        }
    }

    private fun setReadStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.READ_MEDIA_VIDEO,
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.READ_MEDIA_VIDEO),
                        1
                    )
                }
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                ) != PackageManager.PERMISSION_GRANTED
            ) {

                if (!ActivityCompat.shouldShowRequestPermissionRationale(
                        this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                ) {
                    ActivityCompat.requestPermissions(
                        this,
                        arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                        1
                    )
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun processVideo() {
        binding.mainContents.visibility = View.VISIBLE

        val videoNames = if (withKeyProcess) {
            uris2.map { entry -> entry.value.pathSegments.last()}
        } else {
            uris.map { uri -> uri.pathSegments.last() }
        }
        val configureWith = Configuration(
            quality = VideoQuality.LOW,
            videoNames = videoNames,
            isMinBitrateCheckEnabled = true,
        )
        val sharedStorageConfiguration = SharedStorageConfiguration(
            saveAt = SaveLocation.movies,
            subFolderName = "my-demo-videos"
        )
        val listener = object : CompressionListener {
            override fun onProgress(index: Int, key: String, percent: Float) {
                //Update UI
                if (percent <= 100)
                    runOnUiThread {
                        if (withKeyProcess) {
                            val newIndex = adapter.list.indexOfFirst { it.key == key }
                            val uri = uris2[key]
                            adapter.list[newIndex] = VideoDetailsModel(
                                key,
                                "",
                                uri,
                                "",
                                percent
                            )
                            adapter.notifyItemChanged(newIndex)
                        } else {
                            adapter.list[index] = VideoDetailsModel(
                                "",
                                "",
                                uris[index],
                                "",
                                percent
                            )
                            adapter.notifyDataSetChanged()
                        }
                    }
            }

            override fun onStart(index: Int, key: String) {
                if (withKeyProcess) {
                    val notifyAll = adapter.list.size == 0
                    val hasItem = adapter.list.indexOfFirst { it.key == key } > -1
                    if (!hasItem) {
                        val newIndex = adapter.list.lastIndex + 1
                        val uri = uris2[key]
                        adapter.list.add(
                            newIndex,
                            VideoDetailsModel(key, "", uri, "")
                        )
                        if (notifyAll) {
                            adapter.notifyDataSetChanged()
                        } else {
                            adapter.notifyItemInserted(newIndex)
                        }
                    }
                } else {
                    adapter.list.add(
                        index,
                        VideoDetailsModel("", "", uris[index], "")
                    )
                    adapter.notifyDataSetChanged()
                }
            }

            override fun onSuccess(index: Int, key: String, size: Long, path: String?) {
                if (withKeyProcess) {
                    val newIndex = adapter.list.indexOfFirst { it.key == key }
                    val uri = uris2[key]
                    adapter.list[newIndex] = VideoDetailsModel(
                        key,
                        path,
                        uri,
                        getFileSize(size),
                        100F
                    )
                    adapter.notifyItemChanged(newIndex)
                } else {
                    adapter.list[index] = VideoDetailsModel(
                        "",
                        path,
                        uris[index],
                        getFileSize(size),
                        100F
                    )
                    adapter.notifyDataSetChanged()
                }
            }

            override fun onFailure(index: Int, key: String, failureMessage: String) {
                Log.wtf("failureMessage", failureMessage)
            }

            override fun onCancelled(index: Int, key: String) {
                Log.wtf("TAG", "compression has been cancelled")
                // make UI changes, cleanup, etc
            }
        }
        lifecycleScope.launch {
            if (withKeyProcess) {

                VideoCompressor.start(
                    applicationContext,
                    Dispatchers.IO,
                    uris2,
                    isStreamable = false,
                    sharedStorageConfiguration = sharedStorageConfiguration,
                    configureWith = configureWith,
                    listener = listener,
                    )
            } else {
                VideoCompressor.start(
                    context = applicationContext,
                    uris,
                    isStreamable = false,
                    sharedStorageConfiguration = sharedStorageConfiguration,
                    configureWith = configureWith,
                    listener = listener,
                )
            }
        }
    }
}
