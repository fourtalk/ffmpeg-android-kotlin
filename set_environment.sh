#!/bin/bash

BASEDIR=$(pwd)
printf "BASEDIR=$BASEDIR\n\n"

RED='\033[1;31m'
GREEN='\033[1;32m'
NC='\033[0m' # No Color

get_path_true_case()
{
    local C_PATH="/"
    local C=""
    local OLD_IFS="$IFS"
    IFS=/
    for C in $1; do
	if [ "$C_PATH" = "/" ]; then
	    C_PATH=`find "$C_PATH" -maxdepth 1 -type d -ipath "$C_PATH$C"`;
	else
	    C_PATH=`find "$C_PATH" -maxdepth 1 -type d -ipath "$C_PATH/$C"`;
	fi
	if [ "$C_PATH" = "" ]; then
	    C_PATH=""
	    break;
	fi
    done;
    IFS="$OLD_IFS"
    echo "$C_PATH"
}

set_ndk_path()
{
    TEST_PATH=${1}
    if test "x$TEST_PATH" = "x" || test ! -e "$TEST_PATH/ndk-build"; then
		return 1
    else
		export ANDROID_NDK_ROOT="$TEST_PATH"
		return 0
    fi

    TEST_PATH=`get_path_true_case $1`
	printf "TEST_PATH: '$1' >> '$TEST_PATH'\n"

    if [ "$TEST_PATH" = "" ]; then
	return 1
    fi
    if test "x$TEST_PATH" = "x" || test ! -e "$TEST_PATH/ndk-build"; then
	return 1
    else
	export ANDROID_NDK_ROOT="$TEST_PATH"
	return 0
    fi
}

if ! set_ndk_path "$ANDROID_NDK_ROOT"; then
    if ! set_ndk_path "$HOME/library/android/sdk/ndk-bundle"; then
	if ! set_ndk_path "$HOME/android/sdk/ndk-bundle"; then
	if ! set_ndk_path "/r/Android/sdk/ndk-bundle-linux"; then
	if ! set_ndk_path "/r/Android/sdk/ndk-bundle"; then
	    printf "${RED}ANDROID_NDK_ROOT is not set or invalid, and can't be found automatically${NC}\nPlease notice, that it should point to ${GREEN}MacOS/Linux version${NC} of NDK\n\n"
	    exit 1
	fi
	fi
	fi
    fi
fi

printf "${GREEN}ndk = $ANDROID_NDK_ROOT ${NC}\n\n"

#===========================================================

ANDROID_TARGET_API_VERSION=25
ANDROID_API_VERSION=16
NDK_TOOLCHAIN_ABI_VERSION=4.9
TOOLCHAIN_PREFIX=${BASEDIR}/toolchain-android

NUMBER_OF_CORES=$(nproc)
HOST_UNAME=$(uname -m)
TARGET_OS=android

CFLAGS='-U_FORTIFY_SOURCE -D_FORTIFY_SOURCE=2 -fno-strict-overflow -fstack-protector-all -fPIE -pie'
LDFLAGS='-Wl,-z,relro -Wl,-z,now -pie -rdynamic'

