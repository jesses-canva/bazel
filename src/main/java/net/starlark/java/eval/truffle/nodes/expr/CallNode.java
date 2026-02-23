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
import com.oracle.truffle.api.nodes.ExplodeLoop;
import java.util.Map;
import javax.annotation.Nullable;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkIterable;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;

/**
 * A function call node. Evaluates the callee and arguments, then dispatches the call via the
 * standard Starlark calling machinery.
 */
public final class CallNode extends StarlarkExpressionNode {

  @Child private StarlarkExpressionNode function;
  @Children private final StarlarkExpressionNode[] positionalArgs;
  @Children private final StarlarkExpressionNode[] namedArgValues;
  @com.oracle.truffle.api.CompilerDirectives.CompilationFinal(dimensions = 1)
  private final String[] namedArgNames;
  @Child @Nullable private StarlarkExpressionNode starArg;
  @Child @Nullable private StarlarkExpressionNode starStarArg;
  @Child private DispatchNode dispatchNode = new DispatchNode();

  public CallNode(
      StarlarkExpressionNode function,
      StarlarkExpressionNode[] positionalArgs,
      String[] namedArgNames,
      StarlarkExpressionNode[] namedArgValues,
      @Nullable StarlarkExpressionNode starArg,
      @Nullable StarlarkExpressionNode starStarArg) {
    this.function = function;
    this.positionalArgs = positionalArgs;
    this.namedArgNames = namedArgNames;
    this.namedArgValues = namedArgValues;
    this.starArg = starArg;
    this.starStarArg = starStarArg;
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    StarlarkThread thread = (StarlarkThread) frame.getArguments()[1];
    Object fn = function.executeGeneric(frame);

    // Fast path: positional-only, no star/starstar — uses inline-cached dispatch
    if (namedArgNames.length == 0 && starArg == null && starStarArg == null) {
      Object[] positional = new Object[positionalArgs.length];
      for (int i = 0; i < positionalArgs.length; i++) {
        positional[i] = positionalArgs[i].executeGeneric(frame);
      }
      return dispatchNode.dispatch(thread, fn, positional);
    }

    // General path (named args, *args, **kwargs)
    return doGeneralCall(frame, thread, fn);
  }

  @TruffleBoundary
  private Object doGeneralCall(VirtualFrame frame, StarlarkThread thread, Object fn) {
    try {
      StarlarkCallable callable = StarlarkTruffleAccessor.getStarlarkCallable(thread, fn);
      StarlarkCallable.ArgumentProcessor proc =
          Starlark.requestArgumentProcessor(thread, callable);

      for (StarlarkExpressionNode posArg : positionalArgs) {
        proc.addPositionalArg(posArg.executeGeneric(frame));
      }

      for (int i = 0; i < namedArgNames.length; i++) {
        proc.addNamedArg(namedArgNames[i], namedArgValues[i].executeGeneric(frame));
      }

      if (starArg != null) {
        Object value = starArg.executeGeneric(frame);
        if (!(value instanceof StarlarkIterable<?>)) {
          throw Starlark.errorf(
              "argument after * must be an iterable, not %s", Starlark.type(value));
        }
        for (Object o : (StarlarkIterable<?>) value) {
          proc.addPositionalArg(o);
        }
      }

      if (starStarArg != null) {
        Object value = starStarArg.executeGeneric(frame);
        if (!(value instanceof Map<?, ?>)) {
          throw Starlark.errorf(
              "argument after ** must be a dict, not %s", Starlark.type(value));
        }
        for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
          if (!(e.getKey() instanceof String)) {
            throw Starlark.errorf(
                "keywords must be strings, not %s", Starlark.type(e.getKey()));
          }
          proc.addNamedArg((String) e.getKey(), e.getValue());
        }
      }

      return Starlark.callViaArgumentProcessor(thread, callable, proc);
    } catch (EvalException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

}
