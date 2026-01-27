#!/bin/bash
set -e

#PATH="$PATH:~/go/bin/"
# config
PROJECT_ROOT="$(pwd)"
CATRAY_DIR="$PROJECT_ROOT/third-party/catray"
#FIXME: change to your value
NDK_HOME="/Applications/AndroidNDK13750724.app/Contents/NDK"

echo "Building Catray library via gomobile..."

if [[ ! -d $NDK_HOME ]]; then
  echo "Error: NDK not found at $NDK_HOME"
  exit 1
fi
# Export NDK for gomobile
export ANDROID_NDK_HOME="$NDK_HOME"
export ANDROID_HOME="/Users/lain/.android-sdk" # based on your error message

cd "$CATRAY_DIR"

# Ensure dependencies are tidy
go mod tidy

# Build AAR using gomobile with correct flags from v2rayNG
# We use the full path to gomobile and ensure it sees the NDK
mkdir -p "$PROJECT_ROOT/app/libs"
~/go/bin/gomobile bind \
    -v \
    -target=android/arm64 \
    -androidapi 30 \
    -trimpath \
    -ldflags='-s -w -buildid=' \
    -o "$PROJECT_ROOT/app/libs/catray.aar" \
    .

echo "Success! catray.aar created in app/libs"
