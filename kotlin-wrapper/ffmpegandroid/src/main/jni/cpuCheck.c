#include <jni.h>
#include <cpu-features.h>

jboolean
Java_com_github_fourtalk_ffmpegandroid_NativeCpuHelper_isCpuSupported(JNIEnv* env, jobject obj)
{
    AndroidCpuFamily family = android_getCpuFamily();
    if (family == ANDROID_CPU_FAMILY_ARM) {
        uint64_t features = android_getCpuFeatures();
        if ((features & ANDROID_CPU_ARM_FEATURE_ARMv7) != 0)
            return JNI_TRUE;
        else
            return JNI_FALSE;
    } else if (family == ANDROID_CPU_FAMILY_ARM64
               || family == ANDROID_CPU_FAMILY_X86
               || family == ANDROID_CPU_FAMILY_X86_64) {
        return JNI_TRUE;
    }
    return JNI_FALSE;
}
