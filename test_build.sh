#!/bin/bash

set -e  # Exit immediately if any command fails
set -o pipefail

# ----------------------------
# Environment setup
# ----------------------------

export ANDROID_HOME="/Users/spacial/Library/Android/sdk"
export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$ANDROID_HOME/platform-tools"

echo "Starting Android build pipeline..."
echo "ANDROID_HOME=$ANDROID_HOME"
echo "PATH=$PATH"

if [ ! -d "$ANDROID_HOME" ]; then
    echo "❌ ERROR: ANDROID_HOME path does not exist: $ANDROID_HOME"
    echo "Please fix your Android SDK path before running the build."
    exit 1
fi

if ! command -v adb >/dev/null 2>&1; then
    echo "❌ ERROR: adb not found in PATH."
    echo "Please ensure Android platform-tools are installed and in your PATH."
    exit 1
fi

if [ ! -f "./gradlew" ]; then
    echo "❌ ERROR: ./gradlew not found in current directory."
    echo "Please run this script from the Android project root."
    exit 1
fi

# ----------------------------
# Stage: Clean
# ----------------------------
echo "==> Cleaning build environment..."
adb kill-server
sleep 2
adb start-server
./gradlew clean --no-daemon

# ----------------------------
# Stage: Unit Test
# ----------------------------
echo "==> Running unit tests..."
./gradlew test --no-daemon

# ----------------------------
# Stage: Android Test
# ----------------------------
echo "==> Running connected Android tests..."
adb uninstall 'com.tritondigital.player.test' || true
adb uninstall 'com.tritondigital.util.test' || true
./gradlew connectedAndroidTest --no-daemon

# ----------------------------
# Stage: Build Modules
# ----------------------------
echo "==> Building modules..."
./gradlew assemblerelease --no-daemon

# ----------------------------
# Stage: Build SDK
# ----------------------------
echo "==> Building SDK..."
./gradlew sdk --no-daemon

# ----------------------------
# Stage: Generate Documentation
# ----------------------------
echo "==> Generating documentation..."
./gradlew doc --no-daemon

# ----------------------------
# Stage: Generate Sample App
# ----------------------------
echo "==> Generating sample app..."
./gradlew sample --no-daemon

# ----------------------------
# Stage: Generate Build Sample App
# ----------------------------
echo "==> Preparing and building sample app..."
chmod +x generated/sample/gradlew
sed -i '' "s/namespace 'com.tritondigital.sample'/namespace 'com.tritondigital.sample'\\
    namespace 'com.tritondigital.sdksample'/g" /Volumes/SpacialHD/Player/android-sdk/generated/sample/sample/build.gradle
./gradlew buildSample --no-daemon

# ----------------------------
# Stage: Package Sample App
# ----------------------------
echo "==> Packaging sample app..."
./gradlew pack --no-daemon

echo "✅ Build pipeline completed successfully!"
