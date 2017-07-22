/*
 * 4talk Messenger Communication Platform.
 * Copyright © 2016 4talk Global Inc. All rights reserved.
 * 
 * Created by Alexander Semushev on 22.07.2017.
 */
package com.github.fourtalk.kotlin_wrapper

import android.content.Context
import android.net.Uri
import android.util.Log
import com.github.fourtalk.ffmpegandroid.*
import java.io.File

object Converter {
    private const val TAG = "Converter"

    /**
     * Builds web optimized h264 480p / aac video from [file]
     *
     * @param file source video file (absolute path)
     * @param fileUri content uri for [file]
     * @param callback events
     */
    fun convertVideo(context: Context, file: File, fileUri: Uri, callback: (success: Boolean, error: String?, convertedFile: File) -> Unit) {
        //if (!hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, CONVERTER_PERMISSION_REQUEST))
        //    return

        //MediaScannerConnection.scanFile(context, arrayOf(file.path), null) { path, uri ->
        //    Log.i(TAG, "Scanned $path : $uri")
        //}

        Log.i(TAG, "#convertVideo $file")
        val dst = File(file.parent, file.nameWithoutExtension + ".converted.mp4")

        try {
            val ffmpeg = FFmpeg.getInstance(context)

            val cmd = mutableListOf("-i", file.path)

            // video-codec
            //cmd.addAll(arrayOf("-vcodec", "copy")) // low-end device
            cmd.addAll(arrayOf(
                    "-vcodec", "libopenh264",
                    "-profile:v", "high", "-level", "4.0", // -profile:v main -level 3.1
                    "-b:v", "2M", "-maxrate", "2M", "-bufsize", "2M"))

            // audio-codec
            cmd.addAll(arrayOf("-acodec", "aac", "-b:a", "128k"))

            // scale filter
            val info = ffmpeg.getVideoInfo(context, fileUri)
            val scale = if (info.width > 480 && info.height > 480) {
                if (info.width >= info.height)
                    "scale=-2:480"
                else
                    "scale=480:-2"
            } else
                ""
            if (scale.isNotEmpty())
                cmd.addAll(arrayOf("-vf", scale))

            // web format & etc.
            cmd.addAll(arrayOf(
                    "-threads", "1",
                    "-movflags", "+faststart",
                    dst.path))

            // Log.i(TAG, ffmpeg.deviceFFmpegVersion())
            ffmpeg.execute(cmd, handler = object : FFmpegExecuteResponseHandler {
                override fun onStart() {
                    Log.i(TAG, "#convertVideo.convertVideo onStart")
                }

                override fun onProgress(message: String?) {
                    Log.d(TAG, "#convertVideo.onProgress $message")
                }

                override fun onFailure(message: String?) {
                    Log.e(TAG, "#convertVideo.onFailure $message")
                    callback(false, message, dst)
                    if (dst.exists())
                        dst.delete()
                }

                override fun onSuccess(message: String?) {
                    Log.i(TAG, "#convertVideo.onSuccess $message")
                    callback(dst.exists(), message, dst)
                }

                override fun onFinish() {
                    Log.i(TAG, "#convertVideo.onFinish")
                }
            })
        } catch (e: FFmpegException) {
            Log.e(TAG, "#convertVideo " + e.message, e)
            callback(false, e.message, dst)
            if (dst.exists())
                dst.delete()
        }
    }
}