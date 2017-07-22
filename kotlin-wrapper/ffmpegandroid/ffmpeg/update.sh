#!/bin/bash

rm -f ./armeabi-v7a/ffmpeg
rm -f ./armeabi-v7a/libopenh264.so
rm -f ./x86/ffmpeg
rm -f ./x86/libopenh264.so

cp -a -p ../../../build/armeabi-v7a/bin/ffmpeg ./armeabi-v7a/ffmpeg
cp -a -p ../../../build/armeabi-v7a/bin/libopenh264.so ./armeabi-v7a/libopenh264.so
cp -a -p ../../../build/x86/bin/ffmpeg ./x86/ffmpeg
cp -a -p ../../../build/x86/bin/libopenh264.so ./x86/libopenh264.so
