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
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.Node;
import javax.annotation.Nullable;
import net.starlark.java.eval.BuiltinFunction;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.runtime.StarlarkTruffleFunction;
import net.starlark.java.syntax.Types;

/**
 * Inline-caching dispatch node for Starlark function calls.
 *
 * <p>Implements the standard Truffle inline caching pattern for two callee types:
 *
 * <ul>
 *   <li><b>StarlarkTruffleFunction monomorphic</b>: Same {@code StarlarkTruffleFunction} instance
 *       at every call. Argument preparation runs in a {@code @TruffleBoundary} helper; the actual
 *       {@code DirectCallNode.call()} executes in compiled Truffle code, enabling inlining on
 *       GraalVM.
 *   <li><b>BuiltinFunction monomorphic</b>: Same {@code BuiltinFunction} instance at every call
 *       (common for universal functions like {@code type}, {@code len}, {@code bool} accessed via
 *       {@link net.starlark.java.eval.truffle.nodes.local.ReadUniversalNode} which pre-caches the
 *       value). Dispatches directly via {@link Starlark#positionalOnlyCall} (one boundary
 *       crossing), bypassing {@link #dispatchGeneric}'s {@code getStarlarkCallable} cast.
 *   <li><b>Megamorphic / non-Truffle</b>: Different function seen, or a non-Truffle callable.
 *       Falls back to the fully-interpreted {@link #dispatchGeneric} path.
 * </ul>
 */
public final class DispatchNode extends Node {

  /** Cached function for the StarlarkTruffleFunction monomorphic case; null while uninitialized. */
  @CompilationFinal @Nullable private StarlarkTruffleFunction cachedFn;

  /**
   * {@code true} if {@link #cachedFn} satisfies {@link StarlarkTruffleFunction#isSimplePositional
   * Function()}: no {@code *args}, {@code **kwargs}, cells, or keyword-only parameters. When true,
   * {@link #prepareArgsBoundary} uses {@link StarlarkTruffleFunction#preparePositionalArgsDirect}
   * instead of the full {@link TruffleArgumentProcessor}, saving two intermediate allocations.
   */
  @CompilationFinal private boolean cachedFnIsSimple;

  /** Cached number of ordinary parameters for the monomorphic function; set with {@link #cachedFn}. */
  @CompilationFinal private int cachedNumOrdinaryParams;

  /** Cached total number of locals for the monomorphic function; set with {@link #cachedFn}. */
  @CompilationFinal private int cachedTotalLocals;

  /**
   * Cached {@link BuiltinFunction} for the builtin monomorphic case; null while uninitialized.
   * Used for call sites that always invoke the same builtin (e.g. {@code type(x)}, {@code
   * len(x)}).
   */
  @CompilationFinal @Nullable private BuiltinFunction cachedBuiltin;

  /**
   * Direct call node for the monomorphic case; null while uninitialized. Registered as a child so
   * Truffle's runtime can track it for inlining decisions.
   */
  @Child @Nullable private DirectCallNode directCallNode;

  /**
   * Dispatches a positional-only call to {@code fn} with {@code positional} arguments.
   *
   * <p>If {@code fn} is the same {@link StarlarkTruffleFunction} seen on the previous call
   * (monomorphic), argument preparation happens in a {@code @TruffleBoundary} helper and the
   * actual call executes via {@link DirectCallNode}, which allows the Truffle compiler to inline
   * the callee.
   *
   * <p>If {@code fn} is the same {@link BuiltinFunction} seen on the previous call (monomorphic),
   * dispatches directly to {@link Starlark#positionalOnlyCall} via {@link #callBuiltinBoundary},
   * skipping the {@link #dispatchGeneric} wrapper and its {@code getStarlarkCallable} cast.
   *
   * <p>Otherwise falls back to the fully-interpreted {@link #dispatchGeneric} path.
   */
  public Object dispatch(StarlarkThread thread, Object fn, Object[] positional) {
    if (fn instanceof StarlarkTruffleFunction stf) {
      if (cachedFn == null) {
        // First call: initialize the monomorphic cache.
        CompilerDirectives.transferToInterpreterAndInvalidate();
        cachedFn = stf;
        cachedFnIsSimple = stf.isSimplePositionalFunction();
        cachedNumOrdinaryParams = stf.getResolvedFunction().getNumOrdinaryParameters();
        cachedTotalLocals = stf.getResolvedFunction().getLocals().size();
        directCallNode = insert(DirectCallNode.create(stf.getCallTarget()));
      }
      if (stf == cachedFn) {
        // Monomorphic fast path.
        return callDirect(thread, stf, positional);
      }
      // Cache miss: a different StarlarkTruffleFunction — fall through to generic dispatch.
    } else if (fn instanceof BuiltinFunction bf) {
      if (cachedBuiltin == null) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        cachedBuiltin = bf;
      }
      if (bf == cachedBuiltin) {
        // Monomorphic builtin fast path: same BuiltinFunction every call (e.g. type, len, bool).
        return callBuiltinBoundary(thread, bf, positional);
      }
      // Cache miss: a different BuiltinFunction — fall through to generic dispatch.
    }
    return dispatchGeneric(thread, fn, positional);
  }

  /**
   * Single-argument variant of {@link #dispatch}: dispatches a positional call with exactly one
   * argument, passing {@code arg0} directly to avoid allocating an intermediate {@code Object[1]}
   * positional array. The fast paths ({@link StarlarkTruffleFunction} and {@link BuiltinFunction})
   * share the same inline cache as {@link #dispatch}.
   *
   * <p>For the {@link StarlarkTruffleFunction} monomorphic path, {@link
   * #prepareArgsBoundarySingle} builds the Truffle args array with {@code arg0} placed directly at
   * index 2, saving one {@code Object[1]} allocation per call. For the {@link BuiltinFunction} and
   * megamorphic paths, an {@code Object[]{arg0}} is still created inside the boundary (unavoidable
   * for the reflection-based dispatch path).
   */
  public Object dispatchSingle(StarlarkThread thread, Object fn, Object arg0) {
    if (fn instanceof StarlarkTruffleFunction stf) {
      if (cachedFn == null) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        cachedFn = stf;
        cachedFnIsSimple = stf.isSimplePositionalFunction();
        cachedNumOrdinaryParams = stf.getResolvedFunction().getNumOrdinaryParameters();
        cachedTotalLocals = stf.getResolvedFunction().getLocals().size();
        directCallNode = insert(DirectCallNode.create(stf.getCallTarget()));
      }
      if (stf == cachedFn) {
        return callDirectSingle(thread, stf, arg0);
      }
    } else if (fn instanceof BuiltinFunction bf) {
      if (cachedBuiltin == null) {
        CompilerDirectives.transferToInterpreterAndInvalidate();
        cachedBuiltin = bf;
      }
      if (bf == cachedBuiltin) {
        return callBuiltinBoundarySingle(thread, bf, arg0);
      }
    }
    return dispatchGenericSingle(thread, fn, arg0);
  }

  /**
   * Executes the monomorphic call: prepares arguments in a boundary helper, pushes the function
   * onto the call stack, checks recursion, then invokes via {@link DirectCallNode}.
   */
  private Object callDirect(StarlarkThread thread, StarlarkTruffleFunction stf, Object[] positional) {
    // Prepare the args array in @TruffleBoundary (validates counts, applies defaults, spills cells).
    Object[] args = prepareArgsBoundary(thread, stf, positional);

    // Push the function onto the call stack, then check for recursion. If the recursion check
    // fails, pushAndCheckRecursion pops the frame before throwing — so no pop is needed here.
    pushAndCheckRecursionBoundary(thread, stf);

    // From here the function IS on the call stack; pop it in finally.
    try {
      Object result = directCallNode.call(args);
      // Check return type if dynamic type checking is enabled (done while callee is on stack
      // so that any EvalException stack capture sees the right call depth).
      checkReturnTypeBoundary(thread, stf, result);
      return result;
    } catch (RuntimeException e) {
      // Capture the call stack into any EvalException while the callee is still on the stack.
      captureExceptionStackBoundary(thread, e);
      throw e;
    } finally {
      StarlarkTruffleAccessor.popCallStack(thread);
    }
  }

  /**
   * Single-argument variant of {@link #callDirect}: avoids allocating an intermediate {@code
   * Object[1]} positional array.
   *
   * <p>For "simple" functions with exactly 1 ordinary parameter (the common {@code f(x)} case),
   * builds the args array directly in PE-visible code. This allows Truffle's partial evaluator to
   * see the allocation and, when {@link DirectCallNode} inlines the callee, scalar-replace the
   * array to zero heap bytes. Falls back to {@link #prepareArgsBoundarySingle} for functions with
   * defaults, complex parameter patterns, or dynamic type checking.
   */
  private Object callDirectSingle(StarlarkThread thread, StarlarkTruffleFunction stf, Object arg0) {
    if (cachedFnIsSimple && cachedNumOrdinaryParams == 1
        && !thread
            .getSemantics()
            .getBool(StarlarkSemantics.EXPERIMENTAL_STARLARK_DYNAMIC_TYPE_CHECKING)) {
      // Hot path: simple function with exactly 1 param, no type checking, exact arg count match.
      // Build the args array in PE-visible code so Truffle can scalar-replace it.
      Object[] args = new Object[cachedTotalLocals + 2];
      args[0] = stf;
      args[1] = thread;
      args[2] = arg0;
      pushAndCheckRecursionBoundary(thread, stf);
      try {
        Object result = directCallNode.call(args);
        checkReturnTypeBoundary(thread, stf, result);
        return result;
      } catch (RuntimeException e) {
        captureExceptionStackBoundary(thread, e);
        throw e;
      } finally {
        StarlarkTruffleAccessor.popCallStack(thread);
      }
    }
    // Non-simple, defaults needed, or dynamic type checking: use boundary path.
    Object[] args = prepareArgsBoundarySingle(thread, stf, arg0);
    pushAndCheckRecursionBoundary(thread, stf);
    try {
      Object result = directCallNode.call(args);
      checkReturnTypeBoundary(thread, stf, result);
      return result;
    } catch (RuntimeException e) {
      captureExceptionStackBoundary(thread, e);
      throw e;
    } finally {
      StarlarkTruffleAccessor.popCallStack(thread);
    }
  }

  /**
   * Single-argument variant of {@link #prepareArgsBoundary}: passes {@code arg0} directly to
   * {@link StarlarkTruffleFunction#preparePositionalArgsDirect1}, avoiding the intermediate
   * {@code Object[1]} that {@link #prepareArgsBoundary} would create when delegating to {@link
   * StarlarkTruffleFunction#preparePositionalArgsDirect}.
   */
  @TruffleBoundary
  private Object[] prepareArgsBoundarySingle(
      StarlarkThread thread, StarlarkTruffleFunction stf, Object arg0) {
    try {
      if (cachedFnIsSimple
          && !thread
              .getSemantics()
              .getBool(StarlarkSemantics.EXPERIMENTAL_STARLARK_DYNAMIC_TYPE_CHECKING)) {
        return stf.preparePositionalArgsDirect1(thread, arg0);
      }
      // Fallback: wrap in Object[1] and use the full processor.
      return stf.preparePositionalArgs(thread, new Object[] {arg0});
    } catch (EvalException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Single-argument variant of {@link #callBuiltinBoundary}. Creates {@code Object[]{arg0}}
   * inside the boundary so the allocation does not appear in the compiled Truffle code path.
   */
  @TruffleBoundary
  private static Object callBuiltinBoundarySingle(
      StarlarkThread thread, BuiltinFunction bf, Object arg0) {
    try {
      return Starlark.positionalOnlyCall(thread, bf, arg0);
    } catch (EvalException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Single-argument megamorphic fallback: creates {@code Object[]{arg0}} and delegates to
   * {@link #dispatchGeneric}.
   */
  @TruffleBoundary
  private static Object dispatchGenericSingle(StarlarkThread thread, Object fn, Object arg0) {
    try {
      StarlarkCallable callable = StarlarkTruffleAccessor.getStarlarkCallable(thread, fn);
      return Starlark.positionalOnlyCall(thread, callable, arg0);
    } catch (EvalException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Validates and binds {@code positional} arguments for {@code stf}. When {@link
   * #cachedFnIsSimple} is {@code true} and dynamic type checking is disabled (the common case),
   * uses {@link StarlarkTruffleFunction#preparePositionalArgsDirect} to build the args array
   * directly — avoiding the {@link
   * net.starlark.java.eval.truffle.runtime.TruffleArgumentProcessor} object and its intermediate
   * locals array. Falls back to the full processor for complex functions or when dynamic type
   * checking is enabled.
   *
   * <p>All work happens in a {@code @TruffleBoundary} to keep the Java logic out of compiled code.
   */
  @TruffleBoundary
  private Object[] prepareArgsBoundary(
      StarlarkThread thread, StarlarkTruffleFunction stf, Object[] positional) {
    try {
      // Fast path: "simple" function + dynamic type checking disabled (common in Bazel).
      if (cachedFnIsSimple
          && !thread
              .getSemantics()
              .getBool(StarlarkSemantics.EXPERIMENTAL_STARLARK_DYNAMIC_TYPE_CHECKING)) {
        return stf.preparePositionalArgsDirect(thread, positional);
      }
      return stf.preparePositionalArgs(thread, positional);
    } catch (EvalException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Pushes {@code stf} onto the call stack, then checks for recursive calls. If a recursive call
   * is detected, pops the frame before throwing so the stack remains consistent.
   */
  @TruffleBoundary
  private static void pushAndCheckRecursionBoundary(
      StarlarkThread thread, StarlarkTruffleFunction stf) {
    StarlarkTruffleAccessor.pushCallStack(thread, stf);
    if (!StarlarkTruffleAccessor.isRecursionAllowed(thread)
        && StarlarkTruffleAccessor.isRecursiveCallByCode(
            thread, stf.getResolvedFunction())) {
      StarlarkTruffleAccessor.popCallStack(thread); // undo push before throwing
      throw new RuntimeException(
          new EvalException(
              String.format("function '%s' called recursively", stf.getName())));
    }
  }

  /**
   * Checks the return type of a call when dynamic type checking is enabled. Throws a {@link
   * RuntimeException} wrapping an {@link EvalException} if the return type does not match. Called
   * while the callee is still on the call stack so that stack capture sees the right depth.
   */
  @TruffleBoundary
  private static void checkReturnTypeBoundary(
      StarlarkThread thread, StarlarkTruffleFunction stf, Object returnValue) {
    if (!thread.getSemantics().getBool(StarlarkSemantics.EXPERIMENTAL_STARLARK_DYNAMIC_TYPE_CHECKING)) {
      return;
    }
    if (!(stf.getStarlarkType() instanceof Types.CallableType functionType)) {
      return;
    }
    if (!StarlarkTruffleAccessor.isValueSubtypeOf(returnValue, functionType.getReturnType())) {
      throw new RuntimeException(
          Starlark.errorf(
              "%s(): returns value of type '%s', declares '%s'",
              stf.getName(),
              StarlarkTruffleAccessor.getStarlarkType(returnValue),
              functionType.getReturnType()));
    }
  }

  /**
   * If {@code e} wraps an {@link EvalException}, ensures the exception captures the current call
   * stack (first-set-wins semantics). Called while the callee is still on the call stack.
   */
  @TruffleBoundary
  private static void captureExceptionStackBoundary(StarlarkThread thread, RuntimeException e) {
    if (e.getCause() instanceof EvalException ex) {
      StarlarkTruffleAccessor.ensureEvalExceptionStack(ex, thread);
    }
  }

  /**
   * Builtin monomorphic fast path: calls the cached {@link BuiltinFunction} directly via {@link
   * Starlark#positionalOnlyCall}, which handles {@code thread.push/pop} and exception wrapping.
   * Avoids the {@link #dispatchGeneric} overhead for call sites that always invoke the same
   * builtin.
   */
  @TruffleBoundary
  private static Object callBuiltinBoundary(
      StarlarkThread thread, BuiltinFunction bf, Object[] positional) {
    try {
      return Starlark.positionalOnlyCall(thread, bf, positional);
    } catch (EvalException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  /** Fully-interpreted fallback for megamorphic sites and non-Truffle callables. */
  @TruffleBoundary
  private static Object dispatchGeneric(
      StarlarkThread thread, Object fn, Object[] positional) {
    try {
      StarlarkCallable callable = StarlarkTruffleAccessor.getStarlarkCallable(thread, fn);
      return Starlark.positionalOnlyCall(thread, callable, positional);
    } catch (EvalException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
