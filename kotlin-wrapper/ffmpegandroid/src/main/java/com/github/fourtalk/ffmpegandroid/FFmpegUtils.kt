package com.github.fourtalk.ffmpegandroid

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.*

internal class NativeCpuHelper {
    external fun isCpuSupported(): Boolean

    companion object {
        init {
            System.loadLibrary("cpucheck")
        }

        val supportsFFmpeg: Boolean =
                when (Build.CPU_ABI.toLowerCase()) {
                    "x86", "x86_64", "arm64-v8a" -> true
                    "armeabi-v7a" -> NativeCpuHelper().isCpuSupported()
                    else -> false
                }

        val assetsDir: String =
                if (Build.CPU_ABI.toLowerCase().startsWith("arm"))
                    "armeabi-v7a"
                else if (Build.CPU_ABI.toLowerCase().startsWith("x86"))
                    "x86"
                else
                    "armeabi-v7a" // fallback
    }
}

internal object Utils {
    private const val TAG = "FFmpeg"
    private const val ffmpegFileName = "ffmpeg"
    private const val h264FileName = "libopenh264.so"
    private const val DEFAULT_BUFFER_SIZE = 1024 * 4
    private const val EOF = -1

    fun copyBinaryFromAssetsToData(context: Context, fileNameFromAssets: String, outputFileName: String): Boolean {
        var rc = false
        var src: InputStream? = null
        try {
            src = context.assets.open(fileNameFromAssets)

            val dst = FileOutputStream(File(getFilesDirectory(context), outputFileName))
            try {
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var n: Int = src.read(buffer)
                while (EOF != n) {
                    dst.write(buffer, 0, n)
                    n = src.read(buffer)
                }
                dst.flush()
                rc = true
            } finally {
                dst.close()
            }
        } catch (e: IOException) {
            Log.e(TAG, "issue in coping binary from assets to data. ", e)
        } finally {
            src?.close()
        }
        return rc
    }

    private fun getFilesDirectory(context: Context): File {
        return context.filesDir
    }

    private fun getExecuteDir(context: Context): String {
        return getFilesDirectory(context).absolutePath
    }

    fun getFFmpeg(context: Context): String {
        return getExecuteDir(context) + File.separator + ffmpegFileName
    }

    fun getH264(context: Context): String {
        return getExecuteDir(context) + File.separator + h264FileName
    }

    fun getFFmpegDefaultEnvironment(context: Context, envp: Map<String, String>?): List<String> {
        val rc = mutableListOf(
                "LD_LIBRARY_PATH=\$LD_LIBRARY_PATH:" + getExecuteDir(context),
                "ANDROID_DATA=/data",
                "ANDROID_ROOT=/system")
        if (envp != null)
            rc.addAll(envp.map { "${it.key}=${it.value}" })
        return rc
    }

    fun convertInputStreamToString(inputStream: InputStream): String? {
        try {
            val r = BufferedReader(InputStreamReader(inputStream))
            val sb = StringBuilder()
            var str = r.readLine()
            while (str != null) {
                sb.append(str)
                str = r.readLine()
            }
            return sb.toString()
        } catch (e: IOException) {
            Log.e(TAG, "error converting input stream to string", e)
        }
        return null
    }

    fun isProcessCompleted(process: Process?): Boolean =
        try {
            process?.exitValue()
            true
        } catch (e: IllegalThreadStateException) {
            false
        }
}
