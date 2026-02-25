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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
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
import net.starlark.java.eval.truffle.runtime.StarlarkTruffleFunction;
import net.starlark.java.syntax.Location;

/**
 * A function call node. Evaluates the callee and arguments, then dispatches the call via the
 * standard Starlark calling machinery.
 */
public final class CallNode extends StarlarkExpressionNode {

  /** Shared empty array for zero-argument calls; avoids {@code new Object[0]} per call. */
  private static final Object[] NO_ARGS = new Object[0];

  @Child private StarlarkExpressionNode function;
  @Children private final StarlarkExpressionNode[] positionalArgs;
  @Children private final StarlarkExpressionNode[] namedArgValues;
  @CompilationFinal(dimensions = 1)
  private final String[] namedArgNames;
  @Child @Nullable private StarlarkExpressionNode starArg;
  @Child @Nullable private StarlarkExpressionNode starStarArg;
  @Child private DispatchNode dispatchNode = new DispatchNode();
  /** Location of the opening parenthesis '(' of this call expression. */
  @CompilationFinal @Nullable private final Location lparenLocation;

  public CallNode(
      StarlarkExpressionNode function,
      StarlarkExpressionNode[] positionalArgs,
      String[] namedArgNames,
      StarlarkExpressionNode[] namedArgValues,
      @Nullable StarlarkExpressionNode starArg,
      @Nullable StarlarkExpressionNode starStarArg,
      @Nullable Location lparenLocation) {
    this.function = function;
    this.positionalArgs = positionalArgs;
    this.namedArgNames = namedArgNames;
    this.namedArgValues = namedArgValues;
    this.starArg = starArg;
    this.starStarArg = starStarArg;
    this.lparenLocation = lparenLocation;
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    StarlarkThread thread = (StarlarkThread) frame.getArguments()[1];
    Object fn = function.executeGeneric(frame);

    // Fast path: positional-only, no star/starstar — uses inline-cached dispatch
    if (namedArgNames.length == 0 && starArg == null && starStarArg == null) {
      if (positionalArgs.length == 0) {
        // Update the calling frame's PC to this call's '(' so stack traces show the call site.
        StarlarkTruffleAccessor.setCurrentLocation(thread, lparenLocation);
        // Snapshot current frame slot values so Debug.getCallStack sees up-to-date locals.
        snapshotCurrentLocals(thread, frame);
        return dispatchNode.dispatch(thread, fn, NO_ARGS);
      }
      if (positionalArgs.length == 1) {
        // Single-arg fast path: pass the argument directly, avoiding an Object[1] allocation.
        Object arg0 = positionalArgs[0].executeGeneric(frame);
        StarlarkTruffleAccessor.setCurrentLocation(thread, lparenLocation);
        snapshotCurrentLocals(thread, frame);
        return dispatchNode.dispatchSingle(thread, fn, arg0);
      }
      Object[] positional = new Object[positionalArgs.length];
      for (int i = 0; i < positionalArgs.length; i++) {
        positional[i] = positionalArgs[i].executeGeneric(frame);
      }
      StarlarkTruffleAccessor.setCurrentLocation(thread, lparenLocation);
      snapshotCurrentLocals(thread, frame);
      return dispatchNode.dispatch(thread, fn, positional);
    }

    // General path (named args, *args, **kwargs)
    // Update calling frame location and snapshot locals before the call.
    StarlarkTruffleAccessor.setCurrentLocation(thread, lparenLocation);
    snapshotCurrentLocals(thread, frame);
    return doGeneralCall(frame, thread, fn);
  }

  /**
   * Reads the current values of all local variables from their Truffle frame slots and snapshots
   * them into the topmost {@link StarlarkThread.Frame} so that {@link Debug#getCallStack} sees
   * up-to-date values.
   *
   * <p>This method is intentionally <em>not</em> annotated with {@code @TruffleBoundary}: passing
   * a {@code VirtualFrame} to a {@code @TruffleBoundary} method either forces frame
   * materialization or prevents the entire calling {@code executeGeneric} from being compiled by
   * PE. The {@code frame.getObject(i)} reads are PE-native. The cold path (allocation) is guarded
   * by {@link CompilerDirectives#transferToInterpreterAndInvalidate()} so it is never reached in
   * compiled code.
   *
   * <p>Uses a lazily-allocated locals array stored in the frame. The first outgoing call for this
   * frame invocation triggers the cold path once (deoptimizing and recompiling); subsequent calls
   * use the already-allocated array in-place. Leaf functions (those that make no outgoing calls)
   * never trigger this method, so their frame's locals array is never allocated.
   */
  private static void snapshotCurrentLocals(StarlarkThread thread, VirtualFrame frame) {
    Object callee0 = frame.getArguments()[0];
    if (!(callee0 instanceof StarlarkTruffleFunction stf)) {
      return;
    }
    int numLocals = stf.getResolvedFunction().getLocals().size();
    if (numLocals == 0) {
      return;
    }
    // Hot path: fill the existing array in-place (no allocation).
    Object[] dest = StarlarkTruffleAccessor.getPreallocatedFrameLocals(thread);
    if (dest != null && dest.length == numLocals) {
      for (int i = 0; i < numLocals; i++) {
        dest[i] = frame.getObject(i);
      }
    } else {
      // Cold path: allocate and cache the locals array for future calls.
      // Deoptimize so this path is not inlined into compiled code.
      CompilerDirectives.transferToInterpreterAndInvalidate();
      Object[] locals = new Object[numLocals];
      for (int i = 0; i < numLocals; i++) {
        locals[i] = frame.getObject(i);
      }
      StarlarkTruffleAccessor.snapshotLocalsToFrame(thread, locals);
    }
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
