# Android FFmpeg-openh264 converter

* FFmpeg for Android compiled (encoders: [aac & openh264](build_module_ffmpeg.sh#L60))
* Supports Android N
* kotlin wrapper for FFmpeg (task queue)

Supported Architecture
----
* armeabi-v7a
* x86 (with [has text relocations](https://trac.ffmpeg.org/ticket/4928) kludge - disabled asm)

Supported Host Environments
----
* MacOS
* Linux
* Windows10 Linux Subsystem (**NOTE:** you have to set ANDROID_NDK_ROOT to LINUX version of Android SDK / NDK / build tools)

FFmpeg, openh2664 build instructions
----
* Set environment variable
  [default](set_environment.sh#L54): `~/Android/Sdk/ndk-bundle`  
  or set it manually: `export ANDROID_NDK_ROOT={Android NDK Base Path}`  
* Run following commands to compile ffmpeg
  1. `sudo apt-get --quiet --yes install build-essential git autoconf libtool pkg-config gperf gettext yasm python-lxml nasm`
  2. `./update_modules.sh` - update submodules and libraries
  3. `./build_android.sh`  - build ffmpeg & libopenh264
* Find the executable binary in build/{arch}/bin directory.

Kotlin wrapper module instructions
----
1. import [ffmpegandroid module](kotlin-wrapper/ffmpegandroid)  
2. put your ffmpeg & libopenh264.so into [kotlin-wrapper/ffmpegandroid/ffmpeg](kotlin-wrapper/ffmpegandroid/ffmpeg)
3. replace
```
  compile project(path: ':ffmpegandroid')
```
with
```
  debugCompile project(path: ':ffmpegandroid', configuration: "debug") // force compile debug module
  releaseCompile project(path: ':ffmpegandroid', configuration: "release")
```
in your `app` build.gradle file (`dependencies` section)  

4. [usage example](kotlin-wrapper/app/src/main/java/com/github/fourtalk/kotlin_wrapper/Converter.kt)

Licenses
----
- FFmpeg: LGPL 3.0  
- openh264: BSD  
- kotlin wrapper: MIT  
- build scripts: MIT  

Info
----
Inspired by [WritingMinds](https://github.com/WritingMinds) java [forks](https://github.com/WritingMinds/ffmpeg-android/network)
