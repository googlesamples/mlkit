/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mlkit.lint

import com.android.tools.lint.checks.infrastructure.LintDetectorTest.java
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import com.mlkit.lint.InvalidImportDetector.Companion.SHORT_MESSAGE
import org.junit.Test

class InvalidImportDetectorTest {

    private val javaPackage = java("""
      package com.google.mlkit.java;

      public final class Hello {
        public static final class drawable {
        }
      }""").indented()

    @Test
    fun normalRImport() {
        lint()
                .files(javaPackage, java("""
          package com.google.mlkit.kotlin;

          import com.google.mlkit.Hello;

          class Example {
          }""").indented())
                .issues(ISSUE_INVALID_IMPORT)
                .run()
                .expectClean()
    }

    @Test
    fun wrongImport() {
        lint()
                .files(javaPackage, java("""
          package com.google.mlkit.kotlin;

          import com.google.mlkit.java.Hello;

          class Example {
          }""").indented())
                .issues(ISSUE_INVALID_IMPORT)
                .run()
                .expect("""
          |src/com/google/mlkit/kotlin/Example.java:3: Error: $SHORT_MESSAGE [SuspiciousImport]
          |import com.google.mlkit.java.Hello;
          |       ~~~~~~~~~~~~~~~~~~~~~~~~~~~
          |1 errors, 0 warnings""".trimMargin())
    }
}
