#!/bin/bash
set -e

# hev-socks5-tunnel source (relative to this script)
SOURCE_DIR="./third-party/hev-socks5-tunnel"
OUTPUT_DIR="./app/src/main/jniLibs"

if [[ ! -d $NDK_HOME ]]; then
  echo "Error: NDK_HOME not found. Please set it, e.g.: export NDK_HOME=~/Library/Android/sdk/ndk/..."
  exit 1
fi

echo "Building hev-socks5-tunnel..."

cd $SOURCE_DIR

$NDK_HOME/ndk-build \
    NDK_PROJECT_PATH=. \
    APP_BUILD_SCRIPT=Android.mk \
    APP_ABI="arm64-v8a" \
    APP_PLATFORM=android-30 \
    NDK_LIBS_OUT="../../app/src/main/jniLibs" \
    "APP_CFLAGS=-O3 -DPKGNAME=iwakura/lain/wheelvpn/service" \
    "APP_LDFLAGS=-Wl,--build-id=none -Wl,--hash-style=gnu -shared"

echo "ok! libhev-socks5-tunnel.so should be in $OUTPUT_DIR"
