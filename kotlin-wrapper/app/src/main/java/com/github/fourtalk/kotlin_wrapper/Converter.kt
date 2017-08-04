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

    // singleton helper
    private var ffmpeg: FFmpeg? = null
    private const val VIDEO_SIZE = 360

    /**
     * Builds web optimized h264 480p / aac video from [file]
     *
     * @param file source video file (absolute path)
     * @param target output video file (absolute path)
     * @param callback events
     */
    fun convertVideo(context: Context, file: File, target: File, callback: (success: Boolean, error: String?, convertedFile: File) -> Unit) {
        //if (!hasPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, CONVERTER_PERMISSION_REQUEST))
        //    return

        //MediaScannerConnection.scanFile(context, arrayOf(file.path), null) { path, uri ->
        //    Log.i(TAG, "Scanned $path : $uri")
        //}

        Log.i(TAG, "#convertVideo $file")

        try {
            if (target.exists())
                target.delete() // FFmpeg hangs if output file exists

            val info = FFmpeg.getVideoInfo(context, target.withFileUriPrefix())
            ffmpeg = ffmpeg ?: FFmpeg(context)
            // Log.i(TAG, ffmpeg.deviceFFmpegVersion())

            val cmd = mutableListOf("-noautorotate", // copy displaymatrix to output
                    "-i", file.path)

            // video-codec
            //cmd.addAll(arrayOf("-vcodec", "copy")) // low-end device
            cmd.addAll(arrayOf(
                    "-vcodec", "libopenh264",
                    "-profile:v", "high", "-level", "4.0", // -profile:v main -level 3.1
                    "-b:v", "2M", "-maxrate", "4M", "-bufsize", "4M"))

            // scale filter
            if (info.streamWidth > VIDEO_SIZE && info.streamHeight > VIDEO_SIZE) {
                if (info.streamWidth >= info.streamHeight)
                    cmd.addAll(arrayOf("-vf", "scale=-2:$VIDEO_SIZE"))
                else
                    cmd.addAll(arrayOf("-vf", "scale=$VIDEO_SIZE:-2"))
            }

            // audio-codec
            cmd.addAll(arrayOf("-acodec", "aac" /*, "-b:a", "128k" */))

            // web format & etc.
            cmd.addAll(arrayOf(
                    "-threads", "1",
                    "-movflags", "+faststart", // metadata fragmentation
                    target.path))

            ffmpeg?.execute(file.path, cmd, null, object : TaskResponseHandler {
                override fun onStart() {
                    Log.i(TAG, "#convertVideo.convertVideo onStart")
                }

                override fun onProgress(message: String?) {
                    /*// frame=   99 fps= 18 q=-0.0 size=     768kB time=00:00:03.28 bitrate=1915.8kbits/s speed=0.586x
                    val millisec = message?.substringAfter("time=")?.substringBefore(" ").millisecFromTimeValue(null)
                    if (millisec != null) {
                        progress = if (info.duration < 1)
                            1f
                        else
                            Math.min(1f, millisec.toFloat() / info.duration)
                    }*/
                    Log.d(TAG, "#convertVideo.onProgress $message")
                }

                override fun onFailure(message: String?) {
                    Log.e(TAG, "#convertVideo.onFailure $message")
                    callback(false, message, target)
                    if (target.exists())
                        target.delete()
                }

                override fun onSuccess(message: String?) {
                    Log.i(TAG, "#convertVideo.onSuccess $message")
                    callback(target.exists(), message, target)
                }

                override fun onFinish() {
                    Log.i(TAG, "#convertVideo.onFinish")
                    if (ffmpeg?.isEmpty() == true)
                        ffmpeg = null
                }
            })
        } catch (e: FFmpegException) {
            Log.e(TAG, "#convertVideo " + e.message, e)
            callback(false, e.message, target)
            if (target.exists())
                target.delete()
        }
    }
}