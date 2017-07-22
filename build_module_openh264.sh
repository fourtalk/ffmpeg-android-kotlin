#!/bin/bash

# $1 = architecture
# $2 = 1 - default compiler environment variables, 0 - none

. set_module_environment.sh $1 $2

pushd openh264

case ${MODULE_ARCH} in
  armeabi-v7a | armeabi-v7a-neon)
    ARCH=arm
	ASM=''
  ;;
  x86)
    ARCH=x86
	ASM='ENABLEPIC=Yes'
  ;;
esac

make \
  OS=${TARGET_OS} \
  NDKROOT=$ANDROID_NDK_ROOT \
  TARGET=android-${ANDROID_TARGET_API_VERSION} \
  SDK_MIN=${ANDROID_API_VERSION} \
  ARCH="${ARCH}" \
  ENABLEPIC=Yes \
  PREFIX="${TOOLCHAIN_PREFIX}" \
  NDKLEVEL=${ANDROID_API_VERSION} \
  clean

make \
  OS=${TARGET_OS} \
  NDKROOT=$ANDROID_NDK_ROOT \
  TARGET=android-${ANDROID_TARGET_API_VERSION} \
  SDK_MIN=${ANDROID_API_VERSION} \
  ARCH="${ARCH}" \
  ${ASM} \
  PREFIX="${TOOLCHAIN_PREFIX}" \
  NDKLEVEL=${ANDROID_API_VERSION} \
  -j${NUMBER_OF_CORES} install || exit 1

popd
