#!/usr/bin/env bash
# Example usage:
# ./build_app.sh --cmd=sdk && ./build_app.sh --cmd=app
# ./build_app.sh --cmd=sdk --android_home='~/Android/Sdk21'
# ./build_app.sh --cmd=app --granular_sdk=/tmp/x20/granular_sdk

source gbash.sh || exit

DEFINE_string cmd '' 'values in {sdk, app}'
DEFINE_string android_home "/usr/local/google/home/${USER}/Android/Sdk" 'Path to android home'
DEFINE_string granular_sdk "/usr/local/google/home/${USER}/granular_sdk" 'Path to store sdk output'
DEFINE_string mlkit_output '/tmp' 'Output for mlkit and testapp'
DEFINE_bool cleanup True 'delete temporary directory on exit'
DEFINE_bool use_generated_test_config_file False 'Use user generated test config file when this is True.' \
' See //javatests/com/google/android/libraries/mlkit/test_apps/vision:use_temp_config_file for details.'

gbash::init_google "$@"
GOOGLE3="$(gbash::get_google3_dir)"
cd "$GOOGLE3"
set -ex

if [[("${FLAGS_cmd}" != 'sdk') && ("${FLAGS_cmd}" != 'app')]]; then
  echo "Error: 'cmd' must be one of 'sdk' or 'app'"
  exit 1
fi

if [[ "${FLAGS_cmd}" == 'sdk' ]]; then
  java/com/google/android/libraries/mlkit/test_apps/build_app.sh --cmd=sdk --granular_sdk="${FLAGS_granular_sdk}"
fi

if [[ "${FLAGS_cmd}" == 'app' ]]; then
  rm -rf "${FLAGS_mlkit_output}/mlkit_vision_quickstart"
  rm -rf "${FLAGS_mlkit_output}/test_apps"
  mkdir "${FLAGS_mlkit_output}/mlkit_vision_quickstart"
  mkdir "${FLAGS_mlkit_output}/test_apps"

  #Collect all files to prepare for gradle build
  blaze_use_generated_test_config_file="false"
  if (( FLAGS_use_generated_test_config_file )); then
    blaze_use_generated_test_config_file="true"
  fi
  TEST_APP="third_party/java_src/android_apps/mlkit_samples/vision_quickstart"
  blaze build --define "use_temp_config_file=${blaze_use_generated_test_config_file}" "${TEST_APP}:mlkit_vision_quickstart_zip"
  blaze_bin="$(blaze info blaze-bin)"
  unzip "$blaze_bin/${TEST_APP}/mlkit_vision_quickstart.zip" -d "${FLAGS_mlkit_output}/mlkit_vision_quickstart" >> /dev/null

  #Gradle build the test app (including tests)
  export ANDROID_HOME="${FLAGS_android_home}"
  blaze run //gmscore/tools/gradle:build_project -- -e user -nou \
      --cleanup="${FLAGS_cleanup}" \
      --noupdate_android_sdk -a "${FLAGS_android_home}" \
      -sdk "${FLAGS_granular_sdk}/latest_maven_repo.zip" \
      --output "${FLAGS_mlkit_output}/test_apps/mlkit_vision" \
      "${FLAGS_mlkit_output}/mlkit_vision_quickstart" >> /dev/null

  echo "adb install -r -d ${FLAGS_mlkit_output}/test_apps/mlkit_vision/vision-quickstart-proguard.apk"
  #Run tests
  #blaze test --notest_loasd --test_output=streamed \
  #    --define test_app_base_path=/tmp/test_apps \
  #    --nocache_test_results javatests/com/google/android/gmscore/dev/test_apps/firebase_ml:firebase_ml_prerelease_generic_phone_google_23_x86_forge
fi
