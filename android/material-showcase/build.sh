# Copyright 2020 Google LLC. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#!/bin/bash

# Exit on error
set -e

# Work off travis
if [[ ! -z TRAVIS_PULL_REQUEST ]]; then
  echo "TRAVIS_PULL_REQUEST: $TRAVIS_PULL_REQUEST"
else
  echo "TRAVIS_PULL_REQUEST: unset, setting to false"
  TRAVIS_PULL_REQUEST=false
fi

# Copy mock google-services file
echo "Using mock google-services.json"
cp mock-google-services.json app/google-services.json

# Install preview deps
${ANDROID_HOME}/tools/bin/sdkmanager --channel=3 \
  "tools" "platform-tools" "build-tools;26.0.0-rc2" "platforms;android-26"

# Build
if [ $TRAVIS_PULL_REQUEST = false ] ; then
  echo "Building full project"
  # For a merged commit, build all configurations.
  ./gradlew clean ktlint build
else
  # On a pull request, just build debug which is much faster and catches
  # obvious errors.
  ./gradlew clean ktlint assembleDebug check
fi
