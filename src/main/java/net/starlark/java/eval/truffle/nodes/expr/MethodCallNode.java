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
 * Fused method call node for {@code receiver.method(args)} patterns.
 *
 * <p>Combines what would otherwise be a separate {@link DotNode} + {@link CallNode} into a single
 * node, reducing the number of {@link TruffleBoundary} crossings per call from two to one. Uses a
 * monomorphic inline cache keyed by receiver class:
 *
 * <ul>
 *   <li><b>Monomorphic builtin fast path</b>: Receiver class matches the cached class and the
 *       attribute is a non-struct-field {@code @StarlarkMethod} method. Evaluates arguments and
 *       calls {@link StarlarkTruffleAccessor#callBuiltinPositionally} directly (one boundary
 *       crossing).
 *   <li><b>Fallback path</b>: Receiver class changed, attribute is not a {@code @StarlarkMethod}
 *       method, or the cache is in megamorphic state. Falls back to {@link Starlark#getattr} to
 *       obtain the callable, then dispatches via {@link DispatchNode}.
 * </ul>
 *
 * <p>Evaluation order invariant: the attribute lookup (or class-match verification) always
 * completes before any arguments are evaluated, matching the semantics of the separate
 * {@code DotNode} + {@code CallNode} combination.
 */
public final class MethodCallNode extends StarlarkExpressionNode {

  /** Shared empty array for zero-argument method calls; avoids {@code new Object[0]} per call. */
  private static final Object[] NO_ARGS = new Object[0];

  @Child private StarlarkExpressionNode receiver;

  /** Attribute name (the method name after the dot). */
  @CompilationFinal private final String methodName;

  @Children private final StarlarkExpressionNode[] positionalArgs;
  @Children private final StarlarkExpressionNode[] namedArgValues;

  @CompilationFinal(dimensions = 1)
  private final String[] namedArgNames;

  @Child @Nullable private StarlarkExpressionNode starArg;
  @Child @Nullable private StarlarkExpressionNode starStarArg;

  /** Location of the opening parenthesis '(' of this call expression. */
  @CompilationFinal @Nullable private final Location lparenLocation;

  /** Cached receiver class; null while uninitialized. */
  @CompilationFinal @Nullable private Class<?> cachedClass;

  /**
   * Cached {@code MethodDescriptor} (opaque {@code Object}) for {@link #methodName} on {@link
   * #cachedClass}. {@code null} if the attribute is not a {@code @StarlarkMethod} member.
   */
  @CompilationFinal @Nullable private Object cachedDescriptor;

  /**
   * {@code true} when {@link #cachedDescriptor} is non-null AND is a proper method (not a struct
   * field). Only valid when {@link #cachedDescriptor} is non-null. Cached as a boolean to avoid
   * a virtual call to {@code isStructField()} in the compiled hot path.
   */
  @CompilationFinal private boolean cachedIsMethod;

  /** Fallback dispatch node for attribute callables and cache misses. */
  @Child private DispatchNode dispatchNode = new DispatchNode();

  public MethodCallNode(
      StarlarkExpressionNode receiver,
      String methodName,
      StarlarkExpressionNode[] positionalArgs,
      String[] namedArgNames,
      StarlarkExpressionNode[] namedArgValues,
      @Nullable StarlarkExpressionNode starArg,
      @Nullable StarlarkExpressionNode starStarArg,
      @Nullable Location lparenLocation) {
    this.receiver = receiver;
    this.methodName = methodName;
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
    Object recv = receiver.executeGeneric(frame);

    // Initialize the monomorphic inline cache on the first call.
    // This is done BEFORE any argument evaluation to preserve the "function/method must be
    // resolved before arguments" evaluation order invariant.
    if (cachedClass == null) {
      CompilerDirectives.transferToInterpreterAndInvalidate();
      cachedClass = recv.getClass();
      cachedDescriptor =
          StarlarkTruffleAccessor.lookupAnnotatedMethod(thread, cachedClass, methodName);
      cachedIsMethod =
          cachedDescriptor != null
              && StarlarkTruffleAccessor.isDescriptorMethod(cachedDescriptor);
    }

    // Positional-only fast path (no named args, no *args/**kwargs)
    if (namedArgNames.length == 0 && starArg == null && starStarArg == null) {
      if (recv.getClass() == cachedClass && cachedIsMethod) {
        // Monomorphic builtin fast path: method is known to exist; evaluate args, then call.
        // The class-match check serves as the "method lookup" for evaluation-order purposes.
        Object[] positional;
        if (positionalArgs.length == 0) {
          positional = NO_ARGS;
        } else {
          positional = new Object[positionalArgs.length];
          for (int i = 0; i < positionalArgs.length; i++) {
            positional[i] = positionalArgs[i].executeGeneric(frame);
          }
        }
        StarlarkTruffleAccessor.setCurrentLocation(thread, lparenLocation);
        snapshotCurrentLocals(thread, frame);
        return callBuiltinBoundary(thread, recv, cachedDescriptor, positional);
      }
      // Fallback: look up the attribute FIRST (may throw "no field or method 'X'"), then eval args.
      Object fn = getAttrBoundary(thread, recv);
      if (positionalArgs.length == 0) {
        StarlarkTruffleAccessor.setCurrentLocation(thread, lparenLocation);
        snapshotCurrentLocals(thread, frame);
        return dispatchNode.dispatch(thread, fn, NO_ARGS);
      }
      if (positionalArgs.length == 1) {
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

    // General path (named args, *args, **kwargs): look up attribute FIRST, then process args.
    Object fn = getAttrBoundary(thread, recv);
    StarlarkTruffleAccessor.setCurrentLocation(thread, lparenLocation);
    snapshotCurrentLocals(thread, frame);
    return doGeneralCall(frame, thread, fn);
  }

  /**
   * Calls the builtin method via its cached descriptor. Delegates to {@link
   * StarlarkTruffleAccessor#callBuiltinPositionally} which handles call-stack push/pop and
   * exception wrapping (matching the semantics of {@link Starlark#positionalOnlyCall}).
   */
  @TruffleBoundary
  private static Object callBuiltinBoundary(
      StarlarkThread thread, Object recv, Object descriptor, Object[] positional) {
    try {
      return StarlarkTruffleAccessor.callBuiltinPositionally(thread, recv, descriptor, positional);
    } catch (EvalException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Obtains the method/attribute value via {@link Starlark#getattr}. Used for cache misses,
   * struct-field attributes, and the general call path (named args / *args / **kwargs).
   */
  @TruffleBoundary
  private Object getAttrBoundary(StarlarkThread thread, Object recv) {
    try {
      return Starlark.getattr(
          thread.mutability(), thread.getSemantics(), recv, methodName, null);
    } catch (EvalException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /** General call path for named args / *args / **kwargs. */
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

  /**
   * Reads local variables from the current frame and snapshots them into the topmost {@link
   * StarlarkThread.Frame} for {@link net.starlark.java.eval.Debug#getCallStack} visibility.
   * Fills the pre-allocated array in-place to avoid a per-call allocation.
   */
  @TruffleBoundary
  private static void snapshotCurrentLocals(StarlarkThread thread, VirtualFrame frame) {
    Object callee0 = frame.getArguments()[0];
    if (!(callee0 instanceof StarlarkTruffleFunction stf)) {
      return;
    }
    int numLocals = stf.getResolvedFunction().getLocals().size();
    if (numLocals == 0) {
      return;
    }
    Object[] dest = StarlarkTruffleAccessor.getPreallocatedFrameLocals(thread);
    if (dest != null && dest.length == numLocals) {
      for (int i = 0; i < numLocals; i++) {
        dest[i] = frame.getObject(i);
      }
    } else {
      Object[] locals = new Object[numLocals];
      for (int i = 0; i < numLocals; i++) {
        locals[i] = frame.getObject(i);
      }
      StarlarkTruffleAccessor.snapshotLocalsToFrame(thread, locals);
    }
  }
}
