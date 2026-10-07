# ML Kit GenAI Speech APIs Quickstart

* [Read more about ML Kit GenAI APIs](https://developers.google.com/ml-kit/genai)

## Introduction

This sample app demonstrates how to use the following GenAI Speech APIs. Select
an API on the landing screen to open its demo.

- [Speech Recognition](https://developers.google.com/ml-kit/genai/speech-recognition/android) - Transcribes speech from the microphone or an audio file into text.

- Speech Translation - Translates speech from the microphone into text in another language.

- Text-to-Speech - Converts text to speech with on-device voices.

## Speech Recognition

- Type or select the **Locale**, then select the **Mode** (**Advanced** or
  **Basic**) and the **AudioSource**:
  - **Microphone** records with `AudioSource.fromMic()`, which requires API
    level 31 or higher.
  - **File Descriptor** records with `AudioRecord` and streams the audio to the
    recognizer through a `ParcelFileDescriptor`.
  - **Audio File** transcribes an audio file that is already on the device.
    Enter the path to the file.
- Turn on **Word Timing** to show when each word was spoken. Word timing isn't
  available for **Audio File**.
- Tap **Download** to check feature availability and download the model if
  needed.
- Optionally, tap **Warmup** to load the model in advance and reduce the latency
  of the first recognition request.
- Tap **Start Recording** to start real-time speech recognition, or
  **Start Playing** to transcribe the selected audio file.

## Speech Translation

- Type or select the **Source Locale** and the **Target Locale**. The two
  locales must be different.
- Tap **Download** to check feature availability and download the model if
  needed.
- Tap **Start Recording** and speak. The translated text appears as you speak.
  Tap **Stop Recording** to finish.

## Text-to-Speech

- Type or select the **Locale**, then tap **Query Voices** to list the
  supported voices for that locale.
- Select a voice in the list. The app downloads the voice if needed and shows
  the download progress.
- Enter text and tap **Speak**.
- Select an **Earcon** and tap **Play Earcon** to play a short sound that the
  app registers with the synthesizer. Earcons require API level 31 or higher.

## Requirements

- Android SDK API level 26 (Android 8.0) or higher.
- An up-to-date version of Speech Services by Google (`com.google.android.tts`),
  which runs the on-device speech models (available on Android 11 / API level
  30 or higher):
  - Microphone input with `AudioSource.fromMic()` and Text-to-Speech Earcons
    require Android 12 (API level 31) or higher.
  - Speech Recognition using `FileDescriptor` or an audio file works on
    Android 11 (API level 30) or higher.
  - Speech Recognition Advanced mode is available on select devices. See
    [Supported devices](https://developers.google.com/ml-kit/genai/speech-recognition/android#supported-devices).

Feature availability also depends on the device and the locale. Each demo checks
the feature status first and shows an error message if the feature is
unavailable.

## Support

* [Documentation](https://developers.google.com/ml-kit/genai)
* [Stack Overflow](https://stackoverflow.com/questions/tagged/mlkit)

## License

Copyright 2025 Google, Inc.

Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements. See the NOTICE file distributed with this work for
additional information regarding copyright ownership. The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.
