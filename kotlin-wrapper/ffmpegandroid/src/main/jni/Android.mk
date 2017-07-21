LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := cpucheck
LOCAL_SRC_FILES := cpuCheck.c
LOCAL_STATIC_LIBRARIES := cpufeatures
include $(BUILD_SHARED_LIBRARY)

$(call import-module,android/cpufeatures)