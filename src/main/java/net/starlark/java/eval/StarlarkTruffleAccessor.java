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
package net.starlark.java.eval;

import javax.annotation.Nullable;
import net.starlark.java.syntax.StarlarkType;
import net.starlark.java.syntax.TokenKind;

/**
 * Public accessor for package-private EvalUtils methods, used by the Truffle-based interpreter
 * nodes in the {@code net.starlark.java.eval.truffle} package.
 */
public final class StarlarkTruffleAccessor {

  private StarlarkTruffleAccessor() {} // uninstantiable

  /** Delegates to {@link EvalUtils#binaryOp}. */
  public static Object binaryOp(TokenKind op, Object x, Object y, StarlarkThread thread)
      throws EvalException {
    return EvalUtils.binaryOp(op, x, y, thread);
  }

  /**
   * In-place binary operation for augmented assignments ({@code x op= y}).
   *
   * <p>For mutable types (list, dict, set), this mutates the left operand in place and returns it.
   * For other types, falls back to {@link EvalUtils#binaryOp}.
   */
  @SuppressWarnings("unchecked")
  public static Object inplaceBinaryOp(TokenKind op, Object x, Object y, StarlarkThread thread)
      throws EvalException {
    switch (op) {
      case PLUS:
        if (x instanceof StarlarkList<?> xList && y instanceof StarlarkList<?> yList) {
          xList.extend((StarlarkIterable) yList);
          return xList;
        }
        break;
      case PIPE:
        if (x instanceof Dict && y instanceof java.util.Map) {
          Dict<Object, Object> xDict = (Dict<Object, Object>) x;
          java.util.Map<Object, Object> yMap = (java.util.Map<Object, Object>) y;
          xDict.putEntries(yMap);
          return xDict;
        } else if (x instanceof StarlarkSet<?> xSet && y instanceof java.util.Set<?> ySet) {
          xSet.update(Tuple.of(ySet));
          return xSet;
        }
        break;
      case AMPERSAND:
        if (x instanceof StarlarkSet<?> xSet && y instanceof java.util.Set<?> ySet) {
          xSet.intersectionUpdate(Tuple.of(ySet));
          return xSet;
        }
        break;
      case CARET:
        if (x instanceof StarlarkSet<?> xSet && y instanceof java.util.Set<?> ySet) {
          xSet.symmetricDifferenceUpdate(ySet);
          return xSet;
        }
        break;
      case MINUS:
        if (x instanceof StarlarkSet<?> xSet && y instanceof java.util.Set<?> ySet) {
          xSet.differenceUpdate(Tuple.of(ySet));
          return xSet;
        }
        break;
      default:
        break;
    }
    return EvalUtils.binaryOp(op, x, y, thread);
  }

  /** Delegates to {@link EvalUtils#unaryOp}. */
  public static Object unaryOp(TokenKind op, Object x) throws EvalException {
    return EvalUtils.unaryOp(op, x);
  }

  /** Delegates to {@link EvalUtils#index}. */
  public static Object index(StarlarkThread thread, Object object, Object key)
      throws EvalException {
    return EvalUtils.index(thread, object, key);
  }

  /** Delegates to {@link EvalUtils#getSequenceIndex}. */
  public static int getSequenceIndex(int index, int length) throws EvalException {
    return EvalUtils.getSequenceIndex(index, length);
  }

  /** Delegates to {@link EvalUtils#addIterator}. */
  public static void addIterator(Object x) {
    EvalUtils.addIterator(x);
  }

  /** Delegates to {@link EvalUtils#removeIterator}. */
  public static void removeIterator(Object x) {
    EvalUtils.removeIterator(x);
  }

  /** Delegates to {@link Starlark#getStarlarkCallable}. */
  public static StarlarkCallable getStarlarkCallable(StarlarkThread thread, Object fn)
      throws EvalException {
    return Starlark.getStarlarkCallable(thread, fn);
  }

  /** Delegates to {@link StarlarkList#wrap}. */
  public static <T> StarlarkList<T> wrapList(@Nullable Mutability mutability, Object[] elems) {
    return StarlarkList.wrap(mutability, elems);
  }

  /** Delegates to {@link Tuple#wrap}. */
  public static Tuple wrapTuple(Object[] array) {
    return Tuple.wrap(array);
  }

  /** Delegates to {@link EvalUtils#setIndex}. */
  public static void setIndex(Object object, Object key, Object value) throws EvalException {
    EvalUtils.setIndex(object, key, value);
  }

  /** Delegates to {@link EvalUtils#setField}. */
  public static void setField(Object object, String field, Object value) throws EvalException {
    EvalUtils.setField(object, field, value);
  }

  /** Delegates to {@link Module#getGlobalByIndex}. */
  public static Object getGlobalByIndex(Module module, int index) {
    return module.getGlobalByIndex(index);
  }

  /** Delegates to {@link Module#setGlobalByIndex}. */
  public static void setGlobalByIndex(Module module, int index, Object value) {
    module.setGlobalByIndex(index, value);
  }

  /** Delegates to {@link Module#getIndicesOfGlobals}. */
  public static int[] getIndicesOfGlobals(Module module, java.util.List<String> globals) {
    return module.getIndicesOfGlobals(globals);
  }

  /** Delegates to {@link StarlarkThread#getLoader}. */
  public static StarlarkThread.Loader getLoader(StarlarkThread thread) {
    return thread.getLoader();
  }

  /** Delegates to {@link Dict#wrap}. */
  public static <K, V> Dict<K, V> wrapDict(
      @Nullable Mutability mu, java.util.LinkedHashMap<K, V> contents) {
    return Dict.wrap(mu, contents);
  }

  /** Delegates to {@link StarlarkThread#getOwner}. */
  public static Object getOwner(StarlarkThread thread) {
    return thread.getOwner();
  }

  /**
   * Increments the thread's step counter and throws if the step limit is exceeded.
   *
   * @return true if execution should continue (used as a hint for Truffle compilation)
   */
  public static boolean incrementStepsAndCheck(StarlarkThread thread) throws EvalException {
    if (++thread.steps >= thread.stepLimit) {
      throw new EvalException("Starlark computation cancelled: too many steps");
    }
    return true;
  }

  /** Delegates to {@link StarlarkThread#checkInterrupt}. */
  public static void checkInterrupt(StarlarkThread thread) throws InterruptedException {
    thread.checkInterrupt();
  }

  /**
   * Returns whether the thread can be interrupted via {@link Thread#interrupt}.
   *
   * <p>Used by hot-path loop nodes to guard the {@link #checkInterrupt} call: when this returns
   * {@code false} (the common case in Bazel), the expensive {@link Thread#interrupted()} native
   * call is skipped entirely.
   */
  public static boolean isInterruptible(StarlarkThread thread) {
    return thread.isInterruptible();
  }

  /** Delegates to {@link StarlarkThread#isRecursionAllowed}. */
  public static boolean isRecursionAllowed(StarlarkThread thread) {
    return thread.isRecursionAllowed();
  }

  /** Delegates to {@link StarlarkThread#isRecursiveCallByCode}. */
  public static boolean isRecursiveCallByCode(
      StarlarkThread thread, net.starlark.java.syntax.Resolver.Function code) {
    return thread.isRecursiveCallByCode(code);
  }

  /** Returns the thread's {@link StarlarkThread.PostAssignHook}, or null if none is set. */
  @Nullable
  public static StarlarkThread.PostAssignHook getPostAssignHook(StarlarkThread thread) {
    return thread.postAssignHook;
  }

  /** Pushes a function onto the thread's call stack. */
  public static void pushCallStack(StarlarkThread thread, StarlarkCallable fn) {
    thread.push(fn);
    // Pre-allocate the frame's locals array so that inplaceSnapshotLocals can fill it without
    // a per-call allocation. The pre-allocation happens once per function invocation; subsequent
    // outgoing calls from this function reuse the same array.
    net.starlark.java.syntax.Resolver.Function rfn = fn.getResolvedFunction();
    if (rfn != null && thread.getCallStackSize() > 0) {
      int n = rfn.getLocals().size();
      if (n > 0) {
        thread.frame(0).locals = new Object[n];
      }
    }
  }

  /** Pops a function off the thread's call stack. */
  public static void popCallStack(StarlarkThread thread) {
    thread.pop();
  }

  /** Delegates to {@link TypeChecker#isValueSubtypeOf}. */
  public static boolean isValueSubtypeOf(Object value, StarlarkType type) {
    return TypeChecker.isValueSubtypeOf(value, type);
  }

  /** Delegates to {@link Starlark#getStarlarkType}. */
  public static StarlarkType getStarlarkType(Object value) {
    return Starlark.getStarlarkType(value);
  }

  /**
   * Ensures that the EvalException has a call stack populated from the given thread.
   *
   * @return the same exception (for chaining)
   */
  public static EvalException ensureEvalExceptionStack(EvalException e, StarlarkThread thread) {
    return e.ensureStack(thread);
  }

  /** Updates the current (innermost) frame's PC location unconditionally. */
  public static void setCurrentLocation(StarlarkThread thread, net.starlark.java.syntax.Location loc) {
    if (loc != null && thread.getCallStackSize() > 0) {
      thread.frame(0).setLocation(loc);
    }
  }

  /**
   * Updates the current frame's error location (first-set-wins: only the innermost expression
   * that fails gets to record its location).
   */
  public static void setErrorLocation(StarlarkThread thread, net.starlark.java.syntax.Location loc) {
    if (loc != null && thread.getCallStackSize() > 0) {
      thread.frame(0).setErrorLocation(loc);
    }
  }

  /**
   * Snapshots the given locals array into the topmost {@code StarlarkThread.Frame.locals}, making
   * them visible to {@link Debug#getCallStack}.
   *
   * <p>The caller is responsible for building {@code locals} from the current frame slot values
   * (not from the immutable {@code frame.getArguments()} array).
   */
  public static void snapshotLocalsToFrame(StarlarkThread thread, Object[] locals) {
    if (locals.length > 0 && thread.getCallStackSize() > 0) {
      thread.frame(0).locals = locals;
    }
  }

  /**
   * Returns the pre-allocated locals array of the topmost {@link StarlarkThread.Frame}, or
   * {@code null} if the stack is empty or the frame has no pre-allocated locals.
   *
   * <p>Used by {@link net.starlark.java.eval.truffle.nodes.expr.CallNode} to fill locals
   * in-place without allocating a new array on every outgoing call (the hot path). The array is
   * pre-allocated by {@link #pushCallStack} exactly once per function invocation.
   */
  @Nullable
  public static Object[] getPreallocatedFrameLocals(StarlarkThread thread) {
    if (thread.getCallStackSize() == 0) return null;
    return thread.frame(0).locals;
  }

  /**
   * Returns the {@link MethodDescriptor} (as an opaque {@link Object}) for the attribute {@code
   * name} on the given receiver's class, or {@code null} if no such {@link
   * net.starlark.java.annot.StarlarkMethod} attribute exists. Used by {@link
   * net.starlark.java.eval.truffle.nodes.expr.DotNode} for monomorphic inline caching.
   */
  @Nullable
  public static Object lookupAnnotatedMethod(
      StarlarkThread thread, Class<?> receiverClass, String name) {
    return thread.getBuiltinManager().getAnnotatedMethods(receiverClass).get(name);
  }

  /**
   * Evaluates {@code receiver.name} using a cached {@link MethodDescriptor} previously obtained
   * from {@link #lookupAnnotatedMethod}. The caller must ensure the descriptor was obtained for
   * {@code receiver.getClass()} (same class).
   */
  public static Object getattrFromCachedDescriptor(
      Mutability mu, StarlarkSemantics semantics, Object receiver, Object descriptor)
      throws EvalException, InterruptedException {
    MethodDescriptor desc = (MethodDescriptor) descriptor;
    if (desc.isStructField()) {
      return desc.callField(receiver, semantics, mu);
    }
    return BuiltinFunction.of(receiver, desc, semantics);
  }

  /**
   * Returns {@code true} if the given descriptor (obtained from {@link #lookupAnnotatedMethod})
   * represents a callable method (i.e., {@link MethodDescriptor#isStructField()} is false).
   * Returns {@code false} if it is a struct field, meaning the attribute's value must be obtained
   * first and then called as a separate step.
   */
  public static boolean isDescriptorMethod(Object descriptor) {
    return !((MethodDescriptor) descriptor).isStructField();
  }

  /**
   * Calls a {@link MethodDescriptor}-backed builtin method with positional arguments.
   *
   * <p>Uses a cached name-only {@link StarlarkCallable} (one per descriptor) for the call-stack
   * push instead of allocating a new {@link BuiltinFunction} object per call. Argument binding and
   * method invocation proceed directly through the descriptor.
   *
   * <p>{@code descriptor} must be a non-{@code structField} {@link MethodDescriptor} obtained from
   * {@link #lookupAnnotatedMethod}.
   */
  public static Object callBuiltinPositionally(
      StarlarkThread thread, Object receiver, Object descriptor, Object[] positional)
      throws EvalException, InterruptedException {
    MethodDescriptor desc = (MethodDescriptor) descriptor;
    // Push a cached, receiver-independent callable for call-stack tracking (name only).
    thread.push(desc.getOrCreateStackCallable());
    try {
      desc.checkEnabled(thread);
      Object[] vector;
      if (desc.isPositionalsReusableAsJavaArgsVectorIfArgumentCountValid()
          && positional.length == desc.getParameters().length) {
        vector = positional;
      } else {
        vector = BuiltinFunction.buildPositionalVector(desc.getName(), receiver, desc, thread, positional);
      }
      return desc.call(
          receiver instanceof String ? StringModule.INSTANCE : receiver,
          vector,
          thread.mutability());
    } catch (Starlark.UncheckedEvalException | Starlark.UncheckedEvalError ex) {
      throw ex; // already wrapped with call stack
    } catch (RuntimeException ex) {
      // RuntimeExceptions from Java builtins (e.g. InvalidStarlarkValueException): wrap so callers
      // see UncheckedEvalException rather than a raw RuntimeException, matching positionalOnlyCall.
      Throwable cause = ex.getCause();
      if (cause instanceof InterruptedException ie) throw ie;
      if (cause instanceof EvalException ee) throw ee.ensureStack(thread);
      throw Starlark.newUncheckedEvalException(ex, thread);
    } catch (Error ex) {
      throw Starlark.newUncheckedEvalError(ex, thread);
    } catch (EvalException ex) {
      throw ex.ensureStack(thread);
    } finally {
      thread.pop();
    }
  }

  /**
   * Stores {@code value} as the pending return value for the current Truffle {@code return}
   * statement. Must be called immediately before throwing {@link
   * net.starlark.java.eval.truffle.runtime.StarlarkReturnException#INSTANCE}.
   */
  public static void setTruffleReturnValue(StarlarkThread thread, Object value) {
    thread.truffleReturnValue = value;
  }

  /**
   * Returns the return value stored by the most recent call to {@link
   * #setTruffleReturnValue}. Must be called immediately after catching {@link
   * net.starlark.java.eval.truffle.runtime.StarlarkReturnException#INSTANCE}.
   */
  public static Object getTruffleReturnValue(StarlarkThread thread) {
    return thread.truffleReturnValue;
  }

  /**
   * Creates a {@link StarlarkFunction} from the given components. Used by {@code
   * StarlarkTruffleFunction.toStarlarkFunction()} to create a compatible wrapper for Java API
   * methods that expect {@code StarlarkFunction} parameters.
   */
  public static StarlarkFunction createStarlarkFunction(
      net.starlark.java.syntax.Resolver.Function rfn,
      Module module,
      int[] globalIndex,
      Tuple defaultValues,
      Tuple freevars,
      SymbolGenerator.Symbol<?> token) {
    return new StarlarkFunction(rfn, module, globalIndex, defaultValues, freevars, token);
  }
}
