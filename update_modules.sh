#!/bin/bash

echo "============================================"
echo "cleanup modules"
rm -rf ffmpeg
rm -rf openh264

echo "============================================"
echo "Updating submodules"
git submodule update --init || exit 1

## or use openh264 v1.7.0 (tested on FFmpeg 3.3.2)
# rm -rf openh264
# wget -O- https://github.com/cisco/openh264/archive/v1.7.0.tar.gz | tar xz
# mv openh264-1.7.0 openh264

echo "Done!"
echo "============================================"
