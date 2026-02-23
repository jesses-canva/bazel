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
package net.starlark.java.eval.truffle.nodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;

/** A sequence of statements executed in order. */
public final class BlockNode extends StarlarkStatementNode {

  @Children private final StarlarkStatementNode[] statements;

  public BlockNode(StarlarkStatementNode[] statements) {
    this.statements = statements;
  }

  @Override
  @ExplodeLoop
  public void executeVoid(VirtualFrame frame) {
    // Step counting: only count in interpreter mode (not after JIT compilation).
    // In JIT-compiled code, safepoints handle interrupts instead.
    if (CompilerDirectives.inInterpreter()) {
      StarlarkThread thread = (StarlarkThread) frame.getArguments()[1];
      for (StarlarkStatementNode stmt : statements) {
        incrementAndCheck(thread);
        stmt.executeVoid(frame);
      }
    } else {
      for (StarlarkStatementNode stmt : statements) {
        stmt.executeVoid(frame);
      }
    }
  }

  @TruffleBoundary
  private static void incrementAndCheck(StarlarkThread thread) {
    try {
      StarlarkTruffleAccessor.incrementStepsAndCheck(thread);
    } catch (EvalException e) {
      throw new RuntimeException(e);
    }
  }
}
