package com.github.fourtalk.ffmpegandroid

import android.content.Context
import android.os.AsyncTask
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.concurrent.TimeoutException

internal class FFmpegExecuteAsyncTask(
        private val context: Context,
        private var initBinary: Boolean,
        private val cmd: List<String>,
        private val envp: List<String>,
        private val timeout: Long,
        private val handler: FFmpegExecuteResponseHandler?)
    : AsyncTask<Void, String, FFmpegExecuteAsyncTask.CommandResult>() {

    private var startTime: Long = 0
    private var process: Process? = null
    private var output = ""

    class CommandResult(val success: Boolean, val output: String?) {
        companion object {
            fun getDummyFailureResponse() = CommandResult(false, "")

            fun getOutputFromProcess(process: Process): CommandResult {
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
        handler?.onStart()
    }

    override fun doInBackground(vararg params: Void): CommandResult {
        try {
            if (initBinary) {
                initBinary = false
                checkBinary()
            }

            Log.d(TAG, "#doInBackground 1")
            process = run(cmd, envp)
            Log.d(TAG, "#doInBackground 2")
            if (process == null)
                return CommandResult.getDummyFailureResponse()
            Log.d(TAG, "#doInBackground 3")

            Log.d(TAG, "Running publishing updates method")
            checkAndUpdateProcess()
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
        if (handler != null) {
            output += commandResult.output
            if (commandResult.success)
                handler.onSuccess(output)
            else
                handler.onFailure(output)
            handler.onFinish()
        }
    }

    @Throws(TimeoutException::class, InterruptedException::class)
    private fun checkAndUpdateProcess() {
        while (!isProcessCompleted(process)) {
            Log.d(TAG, "#checkAndUpdateProcess 1")

            // Handling timeout
            if (timeout != java.lang.Long.MAX_VALUE && System.currentTimeMillis() > startTime + timeout)
                throw TimeoutException("FFmpeg timed out")

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
                Log.d(TAG, "#checkAndUpdateProcess $line")
            } catch (e: IOException) {
                Log.e(TAG, "#checkAndUpdateProcess" + e.message, e)
                //e.printStackTrace()
            }
        }
    }

    val isProcessCompleted: Boolean
        get() = isProcessCompleted(process)

    private fun isAssetFileSizeDiffer(file: File): Boolean {
        try {
            val assetsSize = context.assets.open(NativeCpuHelper.assetsDir + File.separator + file.name).use {
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
        if (file.exists() && isAssetFileSizeDiffer(file) && !file.delete()) {
            Log.e(TAG, "${file.name} is out of date and cannot be updated")
            return false
        }
        if (!file.exists()) {
            val isFileCopied = copyBinaryFromAssetsToData(context,
                    fileNameFromAssets = NativeCpuHelper.assetsDir + File.separator + file.name,
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

    fun kill(): Boolean = !isCancelled && cancel(true)

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
