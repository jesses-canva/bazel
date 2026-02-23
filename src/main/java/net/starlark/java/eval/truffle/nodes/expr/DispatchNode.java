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
import com.oracle.truffle.api.nodes.Node;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;

/**
 * Inline-caching dispatch node for Starlark function calls.
 *
 * <p>Implements the standard Truffle inline caching pattern:
 *
 * <ul>
 *   <li><b>Uninitialized</b>: First call, transitions to monomorphic
 *   <li><b>Monomorphic</b>: Same CallTarget seen, uses DirectCallNode (fastest, enables inlining)
 *   <li><b>Megamorphic</b>: Different CallTarget seen, falls back to IndirectCallNode
 * </ul>
 *
 * <p>For non-Truffle callables (BuiltinFunction, etc.), dispatch goes through the standard Starlark
 * calling machinery via reflection.
 */
public final class DispatchNode extends Node {

  /**
   * Dispatches a positional-only call to the given function.
   *
   * <p>All calls go through {@link Starlark#positionalOnlyCall} which handles argument processing
   * (defaults, *args, **kwargs, cell creation) correctly for both StarlarkTruffleFunction and other
   * callables. For StarlarkTruffleFunction, the argument processor packages bound locals and calls
   * the Truffle CallTarget directly.
   */
  public Object dispatch(
      StarlarkThread thread, Object fn, Object[] positional) {
    return dispatchGeneric(thread, fn, positional);
  }

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
