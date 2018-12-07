#!/bin/bash

# $1 = architecture
# $2 = 1 - default compiler environment variables, 0 - none

. set_module_environment.sh $1 $2

oot="${BASEDIR}/build/${MODULE_ARCH}/oot/ffmpeg"
mkdir -p "$oot"
pushd "$oot"

case ${MODULE_ARCH} in
  armeabi-v7a | armeabi-v7a-neon)
    CPU='armv7-a'
	ASM='--enable-asm'
  ;;
  x86)
    CPU='i686'
	ASM='--disable-asm --enable-pic'
  ;;
esac

make distclean

"${BASEDIR}/ffmpeg/configure" \
--target-os="$TARGET_OS" \
--cross-prefix="$CROSS_PREFIX" \
--prefix="${BASEDIR}/build/${MODULE_ARCH}" \
--arch="$NDK_ABI" \
--cpu="$CPU" \
--enable-runtime-cpudetect \
--sysroot="$NDK_SYSROOT" \
$ASM \
--disable-debug \
--disable-symver \
--disable-ffserver \
--disable-ffplay \
--disable-ffprobe \
--disable-gpl \
--disable-doc \
--enable-version3 \
--enable-pthreads \
--enable-hardcoded-tables \
--disable-postproc \
--disable-bsfs \
--disable-indevs \
--disable-outdevs \
--disable-devices \
--disable-network \
--disable-filters \
 \
--enable-filter=rotate \
--enable-filter=scale \
--enable-filter=anull \
--enable-filter=anullsrc \
--enable-filter=copy \
--enable-filter=crop \
--enable-filter=cropdetect \
--enable-filter=aresample \
 \
--disable-muxers \
--enable-muxer=mp4 \
--enable-muxer=h264 \
 \
--disable-encoders \
--enable-encoder=aac \
--enable-libopenh264 \
--enable-encoder=libopenh264 \
--enable-decoder=libopenh264 \
 \
--disable-protocols \
--enable-protocol=file,pipe \
--enable-small \
--pkg-config="${BASEDIR}/pkg-config-ffmpeg" \
--extra-cflags="-I${TOOLCHAIN_PREFIX}/include $CFLAGS" \
--extra-ldflags="-L${TOOLCHAIN_PREFIX}/lib $LDFLAGS" \
--extra-cxxflags="$CXX_FLAGS" || exit 1

make -j${NUMBER_OF_CORES} && make install || exit 1

popd
