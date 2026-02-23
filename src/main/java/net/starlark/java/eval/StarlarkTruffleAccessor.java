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
  }

  /** Pops a function off the thread's call stack. */
  public static void popCallStack(StarlarkThread thread) {
    thread.pop();
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
