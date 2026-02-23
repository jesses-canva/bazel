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
package net.starlark.java.eval.truffle.nodes.expr;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;

/** Attribute access expression node: evaluates {@code object.name}. */
public final class DotNode extends StarlarkExpressionNode {

  @Child private StarlarkExpressionNode object;
  @CompilationFinal private final String name;

  public DotNode(StarlarkExpressionNode object, String name) {
    this.object = object;
    this.name = name;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    Object obj = object.executeGeneric(frame);
    StarlarkThread thread = (StarlarkThread) frame.getArguments()[1];
    return doGetattr(thread, obj, name);
  }

  @TruffleBoundary
  private static Object doGetattr(StarlarkThread thread, Object object, String name) {
    try {
      return Starlark.getattr(
          thread.mutability(), thread.getSemantics(), object, name, null);
    } catch (EvalException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
