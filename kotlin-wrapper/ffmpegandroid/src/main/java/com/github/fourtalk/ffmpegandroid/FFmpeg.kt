package com.github.fourtalk.ffmpegandroid

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.FileNotFoundException
import java.util.concurrent.Executors

open class FFmpegException(message: String) : Exception(message)
class FFmpegNotSupportedException(message: String) : FFmpegException(message)
class FFmpegDuplicateTaskIdException(message: String) : FFmpegException(message)

/**
 * FFmpeg wrapper
 * usage sample : com.github.fourtalk.kotlin_wrapper.Converter
 */
class FFmpeg(context: Context) {

    class VideoInfo(
            val fileSize: Long,
            val streamWidth: Int,
            val streamHeight: Int,
            val rotation: Int,
            val duration: Long
    ) {
        val displayWidth: Int = if (rotation == 90 || rotation == 270) streamHeight else streamWidth
        val displayHeight: Int = if (rotation == 90 || rotation == 270) streamWidth else streamHeight
    }

    private val context: Context = context.applicationContext
    private var timeout = java.lang.Long.MAX_VALUE
    private val executor = Executors.newSingleThreadExecutor()

    companion object {
        private const val MINIMUM_TIMEOUT = 10000L

        /**
         * Get [VideoInfo] of [uri]
         * @param uri content uri to video
         */
        @Throws(FileNotFoundException::class)
        fun getVideoInfo(context: Context, uri: Uri): VideoInfo {
            val inputSize = openContentInputStream(context, uri)?.use {
                it.getInputStreamReadLength()
            }?.toLong() ?: 0L

            val duration: Long
            val w: Int
            val h: Int
            val r: Int
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION).toLongOrNull() ?: 0L
                r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION).toIntOrNull() ?: 0
                else
                    0
                w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toIntOrNull() ?: 0
                h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toIntOrNull() ?: 0
            } finally {
                retriever.release()
            }

            return VideoInfo(inputSize, w, h, r, duration)
        }
    }

    private val tasks = mutableMapOf<String, FFmpegExecuteAsyncTask>()

    fun isEmpty() = tasks.isEmpty()

    private val tasksHandler = object : FFmpegExecuteAsyncTask.Handler {
        override fun onEndTask(task: FFmpegExecuteAsyncTask) {
            tasks.remove(task.id)
        }
    }

    /**
     * Load binary to the device according to archituecture. This also updates FFmpeg binary if the binary on device have old version.
     * Executes a command
     * @param environvenmentVars Environment variables (after default values of LD_LIBRARY_PATH, ANDROID_DATA, ANDROID_ROOT)
     * @param cmd command to execute
     * @param ffmpegExecuteResponseHandler [FFmpegExecuteResponseHandler]
     * @throws FFmpegNotSupportedException
     * @throws IllegalArgumentException
     * @throws FFmpegDuplicateTaskIdException
     */
    fun execute(taskId: String, cmd: List<String>, environvenmentVars: Map<String, String>?, handler: TaskResponseHandler?) {
        if (!NativeCpuHelper.supportsFFmpeg)
            throw FFmpegNotSupportedException("Device not supported")

        if (cmd.isEmpty())
            throw IllegalArgumentException("shell command cannot be empty")

        if (tasks.containsKey(taskId))
            throw FFmpegDuplicateTaskIdException("taskId $taskId already exists in queue")

        val command = mutableListOf(getFFmpeg(context))
        command.addAll(cmd)
        val task = FFmpegExecuteAsyncTask(
                id = taskId,
                context = context,
                initBinary = true,
                cmd = command,
                envp = getFFmpegDefaultEnvironment(context, environvenmentVars),
                timeout = timeout,
                handler = handler,
                internalHandler = tasksHandler)

        tasks.put(taskId, task)
        task.executeOnExecutor(executor)
    }

    /**
     * Tells FFmpeg version currently on device
     * @return FFmpeg version currently on device
     */
    fun deviceFFmpegVersion(): String {
        val commandResult = FFmpegExecuteAsyncTask.runWaitFor(listOf(getFFmpeg(context), "-version"), null)
        return if (commandResult.success)
            (commandResult.output?.split(" ")?.get(2) ?: "")
        else
            ""
    }

    /**
     * Checks if FFmpeg command is Currently running
     * @return true if FFmpeg command is running
     */
    fun isFFmpegCommandRunning(taskId: String): Boolean = tasks.get(taskId)?.isProcessCompleted == false

    /**
     * Kill Running FFmpeg process
     * @return true if process is killed successfully
     */
    fun killRunningProcesses(taskId: String): Boolean {
        val task = tasks[taskId]
        tasks.remove(taskId)
        return task?.kill() ?: true
    }

    /**
     * Timeout for FFmpeg process, should be minimum of 10 seconds
     * @param timeout in milliseconds
     */
    fun setTimeout(timeout: Long) {
        if (timeout >= MINIMUM_TIMEOUT)
            this.timeout = timeout
    }
}
