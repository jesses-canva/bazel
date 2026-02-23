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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.TruffleLanguage;

/**
 * The Truffle language registration for Starlark.
 *
 * <p>This class serves as the entry point for the Truffle framework's language infrastructure. It
 * bridges Truffle's context model to Starlark's existing threading model via {@link
 * StarlarkContext}.
 */
@TruffleLanguage.Registration(
    id = StarlarkTruffleLanguage.ID,
    name = "Starlark",
    defaultMimeType = StarlarkTruffleLanguage.MIME_TYPE,
    characterMimeTypes = StarlarkTruffleLanguage.MIME_TYPE)
public final class StarlarkTruffleLanguage extends TruffleLanguage<StarlarkContext> {

  public static final String ID = "starlark";
  public static final String MIME_TYPE = "application/x-starlark";

  @Override
  protected StarlarkContext createContext(Env env) {
    return new StarlarkContext(this, env);
  }

  @Override
  protected CallTarget parse(ParsingRequest request) {
    // Parsing is handled externally via SyntaxToTruffleTranslator.
    // This method is required by the Truffle API but not used in our integration path.
    throw new UnsupportedOperationException(
        "Starlark files are parsed externally via SyntaxToTruffleTranslator");
  }

  /** Returns the current language instance from the given context. */
  public static StarlarkTruffleLanguage get(StarlarkContext context) {
    return context.getLanguage();
  }
}
