package com.github.fourtalk.ffmpegandroid

import android.content.Context

// !warn : names are compatible with WritingMinds/ffmpeg-android-java project

class FFmpegCommandAlreadyRunningException(message: String) : Exception(message)

class FFmpegNotSupportedException(message: String) : Exception(message)

interface FFmpegLoadBinaryResponseHandler {
    /**
     * on Fail
     */
    fun onFailure() {}

    /**
     * on Success
     */
    fun onSuccess() {}
}

interface FFmpegExecuteResponseHandler {
    /**
     * on Start
     */
    fun onStart() {}

    /**
     * on Finish
     */
    fun onFinish() {}

    /**
     * on Success
     * @param message complete output of the FFmpeg command
     */
    fun onSuccess(message: String?)

    /**
     * on Progress
     * @param message current output of FFmpeg command
     */
    fun onProgress(message: String?)

    /**
     * on Failure
     * @param message complete output of the FFmpeg command
     */
    fun onFailure(message: String?)
}

internal interface FFmpegInterface {
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

}

class FFmpeg private constructor(context: Context) : FFmpegInterface {

    private val context: Context = context.applicationContext
    private var ffmpegExecuteAsyncTask: FFmpegExecuteAsyncTask? = null
    private var timeout = java.lang.Long.MAX_VALUE

    override fun execute(cmd: List<String>, environvenmentVars: Map<String, String>?, handler: FFmpegExecuteResponseHandler?) {
        if (!NativeCpuHelper.supportsFFmpeg)
            throw FFmpegNotSupportedException("Device not supported")

        if (ffmpegExecuteAsyncTask?.isProcessCompleted == false)
            throw FFmpegCommandAlreadyRunningException("FFmpeg command is already running, you are only allowed to run single command at a time")

        if (cmd.isEmpty())
            throw IllegalArgumentException("shell command cannot be empty")

        val command = mutableListOf(Utils.getFFmpeg(context))
        command.addAll(cmd)
        ffmpegExecuteAsyncTask = FFmpegExecuteAsyncTask(
                context = context,
                initBinary = true,
                cmd = command,
                envp = Utils.getFFmpegDefaultEnvironment(context, environvenmentVars),
                timeout = timeout,
                handler = handler)
        ffmpegExecuteAsyncTask!!.execute()
    }

    // if unable to find version then return "" to avoid NPE
    @Throws(FFmpegCommandAlreadyRunningException::class)
    override fun deviceFFmpegVersion(): String {
        val commandResult = FFmpegExecuteAsyncTask.runWaitFor(listOf(Utils.getFFmpeg(context), "-version"), null)
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

        fun getInstance(context: Context): FFmpeg {
            instance = instance ?: FFmpeg(context)
            return instance!!
        }
    }
}
