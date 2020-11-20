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

package com.google.mlkit.vision.automl.demo;

import androidx.annotation.NonNull;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps an existing executor to provide a {@link #shutdown} method that allows subsequent
 * cancellation of submitted runnables.
 */
public class ScopedExecutor implements Executor {

  private final Executor executor;
  private final AtomicBoolean shutdown = new AtomicBoolean();

  public ScopedExecutor(@NonNull Executor executor) {
    this.executor = executor;
  }

  @Override
  public void execute(@NonNull Runnable command) {
    // Return early if this object has been shut down.
    if (shutdown.get()) {
      return;
    }
    executor.execute(
        () -> {
          // Check again in case it has been shut down in the mean time.
          if (shutdown.get()) {
            return;
          }
          command.run();
        });
  }

  /**
   * After this method is called, no runnables that have been submitted or are subsequently
   * submitted will start to execute, turning this executor into a no-op.
   *
   * <p>Runnables that have already started to execute will continue.
   */
  public void shutdown() {
    shutdown.set(true);
  }
}
