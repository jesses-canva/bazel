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
import javax.annotation.Nullable;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;

/** Slice expression node: evaluates {@code object[start:stop:step]}. */
public final class SliceNode extends StarlarkExpressionNode {

  @Child private StarlarkExpressionNode object;
  @Child @Nullable private StarlarkExpressionNode start;
  @Child @Nullable private StarlarkExpressionNode stop;
  @Child @Nullable private StarlarkExpressionNode step;

  public SliceNode(
      StarlarkExpressionNode object,
      @Nullable StarlarkExpressionNode start,
      @Nullable StarlarkExpressionNode stop,
      @Nullable StarlarkExpressionNode step) {
    this.object = object;
    this.start = start;
    this.stop = stop;
    this.step = step;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    Object obj = object.executeGeneric(frame);
    Object startVal = start != null ? start.executeGeneric(frame) : Starlark.NONE;
    Object stopVal = stop != null ? stop.executeGeneric(frame) : Starlark.NONE;
    Object stepVal = step != null ? step.executeGeneric(frame) : Starlark.NONE;
    StarlarkThread thread = (StarlarkThread) frame.getArguments()[1];
    return doSlice(thread, obj, startVal, stopVal, stepVal);
  }

  @TruffleBoundary
  private static Object doSlice(
      StarlarkThread thread, Object obj, Object start, Object stop, Object step) {
    try {
      return Starlark.slice(thread.mutability(), obj, start, stop, step);
    } catch (EvalException e) {
      throw new RuntimeException(e);
    }
  }
}
