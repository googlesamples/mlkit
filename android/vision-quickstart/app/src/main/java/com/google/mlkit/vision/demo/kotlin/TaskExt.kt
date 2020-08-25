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

package com.google.mlkit.vision.demo.kotlin

import com.google.android.gms.tasks.OnCanceledListener
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import java.util.concurrent.Executor

/**
 * Quality-of-life helper to allow using trailing lambda syntax for adding a success listener to a
 * [Task].
 */
fun <TResult> Task<TResult>.addOnSuccessListener(
  executor: Executor,
  listener: (TResult) -> Unit
): Task<TResult> {
  return addOnSuccessListener(executor, OnSuccessListener(listener))
}

/**
 * Quality-of-life helper to allow using trailing lambda syntax for adding a failure listener to a
 * [Task].
 */
fun <TResult> Task<TResult>.addOnFailureListener(
  executor: Executor,
  listener: (Exception) -> Unit
): Task<TResult> {
  return addOnFailureListener(executor, OnFailureListener(listener))
}

/**
 * Quality-of-life helper to allow using trailing lambda syntax for adding a completion listener to
 * a [Task].
 */
fun <TResult> Task<TResult>.addOnCompleteListener(
  executor: Executor,
  listener: (Task<TResult>) -> Unit
): Task<TResult> {
  return addOnCompleteListener(executor, OnCompleteListener(listener))
}

/**
 * Quality-of-life helper to allow using trailing lambda syntax for adding a cancellation listener
 * to a [Task].
 */
fun <TResult> Task<TResult>.addOnCanceledListener(
  executor: Executor,
  listener: () -> Unit
): Task<TResult> {
  return addOnCanceledListener(executor, OnCanceledListener(listener))
}
