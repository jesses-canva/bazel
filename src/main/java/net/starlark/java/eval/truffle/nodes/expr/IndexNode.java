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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;

/** Index expression node: evaluates {@code object[key]}. */
public final class IndexNode extends StarlarkExpressionNode {

  @Child private StarlarkExpressionNode object;
  @Child private StarlarkExpressionNode key;

  public IndexNode(StarlarkExpressionNode object, StarlarkExpressionNode key) {
    this.object = object;
    this.key = key;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    Object obj = object.executeGeneric(frame);
    Object k = key.executeGeneric(frame);
    StarlarkThread thread = (StarlarkThread) frame.getArguments()[1];
    return doIndex(thread, obj, k);
  }

  @TruffleBoundary
  private static Object doIndex(StarlarkThread thread, Object object, Object key) {
    try {
      return StarlarkTruffleAccessor.index(thread, object, key);
    } catch (EvalException e) {
      throw new RuntimeException(e);
    }
  }
}
