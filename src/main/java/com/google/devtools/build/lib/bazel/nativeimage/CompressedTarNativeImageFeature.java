// Copyright 2026 The Bazel Authors. All rights reserved.
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

package com.google.devtools.build.lib.bazel.nativeimage;

import java.lang.reflect.Method;
import java.nio.charset.Charset;
import org.graalvm.nativeimage.hosted.Feature;

/**
 * GraalVM native image {@link Feature} that registers {@link
 * com.google.devtools.build.lib.bazel.repository.decompressor.CompressedTarFunction.MarkedIso88591Charset}
 * with the native image localization support.
 *
 * <p>GraalVM native image substitutes {@code Charset.forName} to look up charsets from a static
 * map built at image build time ({@code LocalizationSupport.charsets}), bypassing {@code
 * ServiceLoader} at runtime. {@code MarkedIso88591CharsetProvider} is intentionally hidden from
 * {@code Charset.availableCharsets()}, so it must be registered explicitly here via {@code
 * LocalizationFeature.addCharset}.
 */
public final class CompressedTarNativeImageFeature implements Feature {

  @Override
  public void beforeAnalysis(BeforeAnalysisAccess access) {
    // LocalizationSupport is populated during LocalizationFeature.afterRegistration, so it is
    // available by the time beforeAnalysis callbacks run.
    try {
      Class<?> compressedTarFunction =
          Class.forName(
              "com.google.devtools.build.lib.bazel.repository.decompressor.CompressedTarFunction");
      Charset charset =
          (Charset) compressedTarFunction.getMethod("getMarkedIso88591Charset").invoke(null);

      Class<?> localizationFeature =
          Class.forName("com.oracle.svm.hosted.jdk.localization.LocalizationFeature");
      Method addCharset = localizationFeature.getMethod("addCharset", Charset.class);
      addCharset.invoke(null, charset);
    } catch (ReflectiveOperationException e) {
      throw new RuntimeException(
          "Failed to register MarkedIso88591Charset with native image localization support", e);
    }
  }
}
