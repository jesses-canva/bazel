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
package net.starlark.java.eval.truffle.runtime;

import com.oracle.truffle.api.nodes.ControlFlowException;

/**
 * Thrown by a {@code return} statement to unwind to the enclosing function's {@code RootNode}.
 *
 * <p>Uses a singleton instance (no payload) to eliminate per-return allocation. The actual return
 * value is stored on the {@link net.starlark.java.eval.StarlarkThread} via {@link
 * net.starlark.java.eval.StarlarkTruffleAccessor#setTruffleReturnValue} immediately before this
 * exception is thrown, and retrieved via {@link
 * net.starlark.java.eval.StarlarkTruffleAccessor#getTruffleReturnValue} immediately after it is
 * caught. This is safe because (a) {@code ControlFlowException} disables stack-trace capture,
 * and (b) the singleton is always caught at the nearest {@code StarlarkRootNode} boundary before
 * any other Starlark {@code return} can execute on the same thread.
 */
public final class StarlarkReturnException extends ControlFlowException {

  private static final long serialVersionUID = 1L;

  /** Singleton instance. Throw this; read the return value from the thread. */
  public static final StarlarkReturnException INSTANCE = new StarlarkReturnException();

  private StarlarkReturnException() {}
}
