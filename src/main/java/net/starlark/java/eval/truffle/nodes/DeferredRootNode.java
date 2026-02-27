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
package net.starlark.java.eval.truffle.nodes;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.RootNode;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.truffle.SyntaxToTruffleTranslator;
import net.starlark.java.eval.truffle.StarlarkTruffleLanguage;
import net.starlark.java.syntax.Resolver;

/**
 * A root node that defers translation of a Starlark function body until first call.
 *
 * <p>This avoids the overhead of eagerly translating function bodies at module load time. For
 * functions that are never called during loading (common in .bzl files that define many rules but
 * only some are used), the translation cost is completely eliminated. For functions that are called,
 * the translation happens on first invocation and is then cached.
 *
 * <p>After materialization, the {@code @CompilationFinal} annotation on {@link #realTarget} allows
 * the Truffle JIT compiler to treat the real CallTarget as a constant, enabling full inlining.
 */
public final class DeferredRootNode extends RootNode {

  private final Resolver.Function rfn;
  private final Module module;
  private final int[] globalIndex;
  @CompilationFinal private CallTarget realTarget;

  public DeferredRootNode(
      StarlarkTruffleLanguage language,
      Resolver.Function rfn,
      Module module,
      int[] globalIndex) {
    super(language, FrameDescriptor.newBuilder().build());
    this.rfn = rfn;
    this.module = module;
    this.globalIndex = globalIndex;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    CallTarget target = realTarget;
    if (target == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      target = materialize();
    }
    return target.call(frame.getArguments());
  }

  @TruffleBoundary
  private synchronized CallTarget materialize() {
    if (realTarget != null) {
      return realTarget;
    }
    SyntaxToTruffleTranslator translator =
        new SyntaxToTruffleTranslator(module, globalIndex, null);
    StarlarkRootNode root = translator.translateFunction(rfn);
    realTarget = root.getCallTarget();
    return realTarget;
  }

  @Override
  public String getName() {
    return rfn.getName();
  }
}
