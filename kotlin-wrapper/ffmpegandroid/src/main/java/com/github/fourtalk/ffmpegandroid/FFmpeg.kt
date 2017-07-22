package com.github.fourtalk.ffmpegandroid

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.FileNotFoundException

// !warn : names are compatible with WritingMinds/ffmpeg-android-java project

open class FFmpegException(message: String) : Exception(message)

class FFmpegCommandAlreadyRunningException(message: String) : FFmpegException(message)

class FFmpegNotSupportedException(message: String) : FFmpegException(message)

interface FFmpegExecuteResponseHandler {
    fun onStart() {}

    fun onFinish() {}

    /**
     * @param message complete output of the FFmpeg command
     */
    fun onSuccess(message: String?)

    /**
     * @param message current output of FFmpeg command
     */
    fun onProgress(message: String?)

    /**
     * @param message complete output of the FFmpeg command
     */
    fun onFailure(message: String?)
}

/**
 * FFmpeg wrapper
 * usage sample : com.github.fourtalk.kotlin_wrapper.Converter
 */
interface FFmpegInterface {
    /**
     * Load binary to the device according to archituecture. This also updates FFmpeg binary if the binary on device have old version.
     * Executes a command
     * @param environvenmentVars Environment variables (after default values of LD_LIBRARY_PATH, ANDROID_DATA, ANDROID_ROOT)
     * @param cmd command to execute
     * @param ffmpegExecuteResponseHandler [FFmpegExecuteResponseHandler]
     * @throws FFmpegNotSupportedException
     * @throws FFmpegCommandAlreadyRunningException
     */
    @Throws(FFmpegCommandAlreadyRunningException::class)
    fun execute(cmd: List<String>, environvenmentVars: Map<String, String>? = null, handler: FFmpegExecuteResponseHandler? = null)

    /**
     * Tells FFmpeg version currently on device
     * @return FFmpeg version currently on device
     * @throws FFmpegCommandAlreadyRunningException
     */
    fun deviceFFmpegVersion(): String

    /**
     * Checks if FFmpeg command is Currently running
     * @return true if FFmpeg command is running
     */
    fun isFFmpegCommandRunning(): Boolean

    /**
     * Kill Running FFmpeg process
     * @return true if process is killed successfully
     */
    fun killRunningProcesses(): Boolean

    /**
     * Timeout for FFmpeg process, should be minimum of 10 seconds
     * @param timeout in milliseconds
     */
    fun setTimeout(timeout: Long)

    class VideoInfo(
            val fileSize: Long,
            val width: Int,
            val height: Int,
            val rotation: Int,
            val duration: Long
    )

    /**
     * Get [VideoInfo] of [url]
     */
    @Throws(FileNotFoundException::class)
    fun getVideoInfo(context: Context, url: Uri): VideoInfo
}

class FFmpeg private constructor(context: Context) : FFmpegInterface {

    private val context: Context = context.applicationContext
    private var ffmpegExecuteAsyncTask: FFmpegExecuteAsyncTask? = null
    private var timeout = java.lang.Long.MAX_VALUE

    override fun getVideoInfo(context: Context, url: Uri): FFmpegInterface.VideoInfo {
        val inputSize = openContentInputStream(context, url).use {
            it.getInputStreamReadLength()
        }.toLong()

        var duration: Long = 0L
        var w: Int = 0
        var h: Int = 0
        var r: Int = 0
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, url)
            duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0L
            r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toIntOrNull() ?: 0
            else
                0
            if (r == 90 || r == 270) {
                h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toIntOrNull() ?: 0
                w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toIntOrNull() ?: 0
            } else {
                w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toIntOrNull() ?: 0
                h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toIntOrNull() ?: 0
            }
        } finally {
            retriever.release()
        }

        return FFmpegInterface.VideoInfo(inputSize, w, h, r, duration)
    }

    override fun execute(cmd: List<String>, environvenmentVars: Map<String, String>?, handler: FFmpegExecuteResponseHandler?) {
        if (!NativeCpuHelper.supportsFFmpeg)
            throw FFmpegNotSupportedException("Device not supported")

        if (ffmpegExecuteAsyncTask?.isProcessCompleted == false)
            throw FFmpegCommandAlreadyRunningException("FFmpeg command is already running, you are only allowed to run single command at a time")

        if (cmd.isEmpty())
            throw IllegalArgumentException("shell command cannot be empty")

        val command = mutableListOf(getFFmpeg(context))
        command.addAll(cmd)
        ffmpegExecuteAsyncTask = FFmpegExecuteAsyncTask(
                context = context,
                initBinary = true,
                cmd = command,
                envp = getFFmpegDefaultEnvironment(context, environvenmentVars),
                timeout = timeout,
                handler = handler)
        ffmpegExecuteAsyncTask!!.execute()
    }

    // if unable to find version then return "" to avoid NPE
    @Throws(FFmpegCommandAlreadyRunningException::class)
    override fun deviceFFmpegVersion(): String {
        val commandResult = FFmpegExecuteAsyncTask.runWaitFor(listOf(getFFmpeg(context), "-version"), null)
        return if (commandResult.success)
            (commandResult.output?.split(" ")?.get(2) ?: "")
        else
            ""
    }

    override fun isFFmpegCommandRunning(): Boolean = ffmpegExecuteAsyncTask?.isProcessCompleted == false

    override fun killRunningProcesses(): Boolean = ffmpegExecuteAsyncTask?.kill() ?: true

    override fun setTimeout(timeout: Long) {
        if (timeout >= MINIMUM_TIMEOUT)
            this.timeout = timeout
    }

    companion object {
        private val MINIMUM_TIMEOUT = (10 * 1000).toLong()
        private var instance: FFmpeg? = null

        fun getInstance(context: Context): FFmpegInterface {
            instance = instance ?: FFmpeg(context)
            return instance!!
        }
    }
}
