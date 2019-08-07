package com.github.fourtalk.ffmpegandroid

import android.content.Context
import android.os.AsyncTask
import android.os.Build
import android.os.HandlerThread
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeoutException

interface TaskResponseHandler {
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

internal class FFmpegExecuteAsyncTask(
        val id: String,
        private val context: Context,
        private var initBinary: Boolean,
        private val cmd: List<String>,
        private val envp: List<String>,
        private val timeout: Long,
        private val handler: TaskResponseHandler?,
        private val internalHandler: Handler)
    : AsyncTask<Void, String, FFmpegExecuteAsyncTask.CommandResult>() {

    internal interface Handler {
        fun onEndTask(task: FFmpegExecuteAsyncTask)
    }

    private var startTime: Long = 0
    private var process: Process? = null
    private var output = ""
    private var progressTime: Long = 0
    private val progressTimeout = 5000L

    class CommandResult(val success: Boolean, val output: String?) {
        companion object {
            fun getDummyFailureResponse() = CommandResult(false, "")

            fun getOutputFromProcess(process: Process): CommandResult {
                process.waitFor()
                val output = if (success(process.exitValue()))
                    convertInputStreamToString(process.inputStream)
                else
                    convertInputStreamToString(process.errorStream)
                return CommandResult(success(process.exitValue()), output)
            }

            fun success(exitValue: Int?): Boolean = exitValue == 0
        }
    }

    override fun onPreExecute() {
        startTime = System.currentTimeMillis()
        progressTime = startTime
        handler?.onStart()
    }

    override fun doInBackground(vararg params: Void): CommandResult {
        if (!isCancelled)
        try {
            if (initBinary) {
                initBinary = false
                checkBinary()
            }

            if (!isCancelled)
                process = run(cmd, envp)
            if (process == null)
                return CommandResult.getDummyFailureResponse()

            Log.d(TAG, "Running publishing updates method")
            waitForProcess()
            return CommandResult.getOutputFromProcess(process!!)
        } catch (e: TimeoutException) {
            Log.e(TAG, "FFmpeg timed out", e)
            return CommandResult(false, e.message)
        } catch (e: Exception) {
            Log.e(TAG, "Error running FFmpeg", e)
        } finally {
            process?.destroy()
        }
        return CommandResult.getDummyFailureResponse()
    }

    override fun onProgressUpdate(vararg values: String?) {
        try {
            handler?.onProgress(values.joinToString("\n"))
        } catch (e: Throwable) {
            Log.e(TAG, "#onProgressUpdate", e)
        }
    }

    override fun onPostExecute(commandResult: CommandResult) {
        internalHandler.onEndTask(this)

        if (handler != null) {
            output += commandResult.output
            if (commandResult.success)
                handler.onSuccess(output)
            else
                handler.onFailure(output)
            handler.onFinish()
        }
    }

    override fun onCancelled(result: CommandResult?) {
        super.onCancelled(result)
        internalHandler.onEndTask(this)

        if (handler != null) {
            handler.onFailure(null)
            handler.onFinish()
        }
    }

    @Throws(TimeoutException::class, InterruptedException::class)
    private fun waitForProcess() {
        while (!isCancelled && !isProcessCompleted(process)) {
            // Handling timeout
            if (timeout != java.lang.Long.MAX_VALUE && System.currentTimeMillis() > startTime + timeout)
                throw TimeoutException("FFmpeg timed out")

            if (progressTimeout != java.lang.Long.MAX_VALUE && System.currentTimeMillis() > progressTime + progressTimeout)
                throw TimeoutException("FFmpeg progress timed out")
            progressTime = System.currentTimeMillis()

            try {
                val reader = BufferedReader(InputStreamReader(process!!.errorStream))
                var line = reader.readLine()
                while (line != null) {
                    if (isCancelled)
                        return

                    output += line + "\n"
                    publishProgress(line)
                    line = reader.readLine()
                }
            } catch (e: IOException) {
                Log.e(TAG, "#waitForProcess" + e.message, e)
            }

            if (!isCancelled && !isProcessCompleted(process))
                Thread.sleep(10)
        }
    }

    val isProcessCompleted: Boolean
        get() = isProcessCompleted(process)

    private fun isAssetFileSizeDiffer(file: File, assetFileName: String): Boolean {
        try {
            val assetsSize = context.assets.open(assetFileName).use {
                it.available()
            }
            return assetsSize > 0 && assetsSize < Int.MAX_VALUE/4 // InputStream.available() can fail
                    && assetsSize.toLong() != file.length()
        } catch (e: Throwable) {
            Log.e(TAG, e.message)
            return false
        }
    }

    private fun prepareFile(file: File): Boolean {
        val assetFileName = NativeCpuHelper.assetsDir(context) + File.separator + file.name
        Log.i(TAG, "assets = ${context.assets.list(Build.CPU_ABI.toLowerCase())?.joinToString()}")
        Log.i(TAG, "assetFileName = $assetFileName")

        if (file.exists() && isAssetFileSizeDiffer(file, assetFileName) && !file.delete()) {
            Log.e(TAG, "${file.name} is out of date and cannot be updated")
            return false
        }
        if (!file.exists()) {
            val isFileCopied = copyBinaryFromAssetsToData(context,
                    fileNameFromAssets = assetFileName,
                    outputFileName = file.name)

            if (isFileCopied) {
                if (file.canExecute())
                    return true

                Log.d(TAG, file.name + " is not executable, trying to make it executable ...")
                if (file.setExecutable(true))
                    return true
            }
        }
        return file.exists() && file.canExecute()
    }

    private fun checkBinary(): Boolean {
        val ffmpegFile = File(getFFmpeg(context))
        val h264File = File(getH264(context))
        return prepareFile(h264File) && prepareFile(ffmpegFile)
    }

    fun kill(): Boolean {
        val rc = !isCancelled && cancel(true)
        process?.destroy()
        return rc
    }

    companion object {
        private val TAG = "FFmpeg"

        fun run(commandString: List<String>, envp: List<String>?): Process? {
            var process: Process? = null
            try {
                Log.i(TAG, "exec '$commandString' with environment '$envp}'")
                process = Runtime.getRuntime().exec(commandString.toTypedArray(), envp?.toTypedArray())
            } catch (e: IOException) {
                Log.e(TAG, "Exception while trying to run: " + commandString, e)
            }
            return process
        }

        fun runWaitFor(s: List<String>, envp: List<String>?): CommandResult {
            val process = run(s, envp)

            var exitValue: Int? = null
            var output: String? = null
            try {
                if (process != null) {
                    exitValue = process.waitFor()

                    output = if (CommandResult.success(exitValue))
                        convertInputStreamToString(process.inputStream)
                    else
                        convertInputStreamToString(process.errorStream)
                }
            } catch (e: InterruptedException) {
                Log.e(TAG, "Interrupt exception", e)
            } finally {
                process?.destroy()
            }

            return CommandResult(CommandResult.success(exitValue), output)
        }
    }
}
