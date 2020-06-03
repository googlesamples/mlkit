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

import com.android.tools.lint.client.api.UElementHandler
import com.android.tools.lint.detector.api.Category
import com.android.tools.lint.detector.api.Detector
import com.android.tools.lint.detector.api.Implementation
import com.android.tools.lint.detector.api.Issue
import com.android.tools.lint.detector.api.JavaContext
import com.android.tools.lint.detector.api.Scope
import com.android.tools.lint.detector.api.Severity
import org.jetbrains.uast.UField

val ISSUE_HUNGARIAN_NOTATION = Issue.create(
        "HungarianNotation",
        "Using mHungarianNotation in a Kotlin file!",
        "mFriends don’t let sFriends use Hungarian notation! -Jake Wharton",
        Category.MESSAGES,
        9,
        Severity.ERROR,
        Implementation(
                HungarianNotationDetector::class.java,
                Scope.JAVA_FILE_SCOPE))

class HungarianNotationDetector : Detector(), Detector.UastScanner {

    override fun getApplicableUastTypes() = listOf(UField::class.java)

    override fun createUastHandler(context: JavaContext) = HungarianNotationHandler(context)

    class HungarianNotationHandler(private val context: JavaContext) : UElementHandler() {

        override fun visitField(node: UField) {
            val varName = node.name
            val isKotlin = context.file.name.endsWith("kt")
            val isHungarian = varName.matches(RE_HUNGARIAN)

            if (isKotlin && isHungarian) {
                node.uastAnchor?.let {
                    context.report(ISSUE_HUNGARIAN_NOTATION, node, context.getLocation(it), SHORT_MESSAGE)
                }
            }
        }
    }

    companion object {
        const val SHORT_MESSAGE = "Invalid Field Name: hungarian notation in a Kotlin file."

        val RE_HUNGARIAN = Regex("^m[A-Z].*")
    }
}
