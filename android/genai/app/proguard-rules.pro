# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile
-dontwarn java.beans.**

# Keep classes used by structured output for deserialization for release builds.
-keep class com.google.mlkit.genai.demo.kotlin.Plant { *; }
-keep class com.google.mlkit.genai.demo.kotlin.PlantList { *; }
-keep class com.google.mlkit.genai.demo.kotlin.ScheduleEvent { *; }

# Keep rules for kotlinx.coroutines SendChannel to prevent NoSuchMethodError during minification
-keep class kotlinx.coroutines.channels.SendChannel { *; }
-keep class kotlinx.coroutines.channels.SendChannel$DefaultImpls { *; }
