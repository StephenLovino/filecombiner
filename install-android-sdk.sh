#!/bin/bash

# Ensure Android SDK is properly installed
yes | ${ANDROID_HOME}/cmdline-tools/tools/bin/sdkmanager --licenses
${ANDROID_HOME}/cmdline-tools/tools/bin/sdkmanager "platform-tools" "platforms;android-30" "build-tools;30.0.3"