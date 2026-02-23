// Copyright 2024 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package net.starlark.java.eval.truffle;

import com.oracle.truffle.api.TruffleLanguage;

/**
 * Per-context state for the Starlark Truffle language.
 *
 * <p>This bridges Truffle's context model to Starlark's existing {@code StarlarkThread} and {@code
 * Module} threading model. A StarlarkContext holds a reference to the language instance and the
 * Truffle environment.
 */
public final class StarlarkContext {

  private final StarlarkTruffleLanguage language;
  private final TruffleLanguage.Env env;

  StarlarkContext(StarlarkTruffleLanguage language, TruffleLanguage.Env env) {
    this.language = language;
    this.env = env;
  }

  /** Returns the language instance associated with this context. */
  public StarlarkTruffleLanguage getLanguage() {
    return language;
  }

  /** Returns the Truffle environment associated with this context. */
  public TruffleLanguage.Env getEnv() {
    return env;
  }
}
