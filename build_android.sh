#!/bin/bash

. set_environment.sh
. set_build_arch.sh

rm -rf build

for i in "${SUPPORTED_ARCHITECTURES[@]}"
do
  rm -rf ${TOOLCHAIN_PREFIX}
  # $1 = architecture
  # $2 = 1 - default compiler environment variables, 0 - none
  ./build_module_openh264.sh $i 0 || exit 1
  ./build_module_ffmpeg.sh $i 0 || exit 1
  cp -a ./openh264/libopenh264.so ./build/$i/bin/libopenh264.so
done

rm -rf ${TOOLCHAIN_PREFIX}

$SHELL
