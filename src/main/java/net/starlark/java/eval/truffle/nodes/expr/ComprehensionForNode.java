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
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.eval.truffle.nodes.StarlarkStatementNode;
import net.starlark.java.eval.truffle.nodes.assign.AssignTargetNode;
import net.starlark.java.syntax.Location;

/** For clause in a comprehension: iterates over a sequence and executes the body for each item. */
public final class ComprehensionForNode extends StarlarkStatementNode {

  @Child private StarlarkExpressionNode iterable;
  @Child private AssignTargetNode variable;
  @Child private StarlarkStatementNode body;

  public ComprehensionForNode(
      StarlarkExpressionNode iterable, AssignTargetNode variable, StarlarkStatementNode body) {
    this.iterable = iterable;
    this.variable = variable;
    this.body = body;
  }

  @Override
  public void executeVoid(VirtualFrame frame) {
    StarlarkThread thread = (StarlarkThread) frame.getArguments()[1];
    Object seq = iterable.executeGeneric(frame);
    Location iterLoc = iterable.getSourceLocation();
    Iterable<?> iter = toIterable(seq, thread, iterLoc);
    StarlarkTruffleAccessor.addIterator(seq);
    try {
      for (Object element : iter) {
        // Inline step check: direct field access, no @TruffleBoundary needed.
        try {
          StarlarkTruffleAccessor.incrementStepsAndCheck(thread);
        } catch (EvalException e) {
          throw new RuntimeException(e);
        }
        // Interrupt check: skip the native Thread.interrupted() call unless interruptible.
        if (StarlarkTruffleAccessor.isInterruptible(thread)) {
          checkInterruptBoundary(thread);
        }
        variable.executeAssign(frame, element);
        body.executeVoid(frame);
      }
    } finally {
      StarlarkTruffleAccessor.removeIterator(seq);
    }
  }

  @TruffleBoundary
  private static void checkInterruptBoundary(StarlarkThread thread) {
    try {
      StarlarkTruffleAccessor.checkInterrupt(thread);
    } catch (InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @TruffleBoundary
  private static Iterable<?> toIterable(Object value, StarlarkThread thread, Location loc) {
    try {
      return Starlark.toIterable(value);
    } catch (EvalException e) {
      StarlarkTruffleAccessor.setErrorLocation(thread, loc);
      throw new RuntimeException(e);
    }
  }
}
