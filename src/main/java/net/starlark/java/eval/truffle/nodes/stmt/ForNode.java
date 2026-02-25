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
package net.starlark.java.eval.truffle.nodes.stmt;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.LoopNode;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RepeatingNode;
import java.util.Iterator;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.eval.truffle.nodes.StarlarkStatementNode;
import net.starlark.java.eval.truffle.nodes.assign.AssignTargetNode;
import net.starlark.java.eval.truffle.runtime.StarlarkBreakException;
import net.starlark.java.eval.truffle.runtime.StarlarkContinueException;

/**
 * Implements a for loop statement using Truffle's {@link LoopNode} for OSR (on-stack replacement)
 * support. The iterator is stored in a dedicated frame slot so it persists across {@link
 * RepeatingNode#executeRepeating} calls.
 *
 * <p>When the loop's back-edge count exceeds Truffle's compilation threshold, the runtime compiles
 * the loop body via partial evaluation, enabling type specialization and inlining of the inner
 * call. Without {@link LoopNode}, plain Java for-each loops are invisible to Truffle's compiler
 * and never trigger OSR.
 */
public final class ForNode extends StarlarkStatementNode {
  @Child private StarlarkExpressionNode collection;
  @Child private LoopNode loopNode;
  private final int iteratorSlot;

  public ForNode(
      StarlarkExpressionNode collection,
      AssignTargetNode variable,
      StarlarkStatementNode body,
      int iteratorSlot) {
    this.collection = collection;
    this.iteratorSlot = iteratorSlot;
    this.loopNode = Truffle.getRuntime().createLoopNode(new ForRepeatingNode(variable, body, iteratorSlot));
  }

  @Override
  public void executeVoid(VirtualFrame frame) {
    Object seq = collection.executeGeneric(frame);
    Iterator<?> iterator = toIterator(seq);
    StarlarkTruffleAccessor.addIterator(seq);
    frame.setObject(iteratorSlot, iterator);
    try {
      loopNode.execute(frame);
    } catch (StarlarkBreakException e) {
      // break exits the loop
    } finally {
      StarlarkTruffleAccessor.removeIterator(seq);
    }
  }

  @TruffleBoundary
  private static Iterator<?> toIterator(Object value) {
    try {
      return Starlark.toIterable(value).iterator();
    } catch (EvalException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * The repeating node that executes one iteration of the for loop. Truffle's {@link LoopNode}
   * calls {@link #executeRepeating} in a tight loop, counting back-edges for OSR.
   *
   * <ul>
   *   <li>Returns {@code true} to continue iterating (including after {@code continue}).
   *   <li>Returns {@code false} when the iterator is exhausted.
   *   <li>{@link StarlarkBreakException} propagates out through {@link LoopNode#execute} to {@link
   *       ForNode#executeVoid}.
   *   <li>{@link net.starlark.java.eval.truffle.runtime.StarlarkReturnException} propagates up to
   *       {@link net.starlark.java.eval.truffle.nodes.StarlarkRootNode#execute}.
   * </ul>
   */
  private static final class ForRepeatingNode extends Node implements RepeatingNode {
    @Child private AssignTargetNode variable;
    @Child private StarlarkStatementNode body;
    private final int iteratorSlot;

    ForRepeatingNode(AssignTargetNode variable, StarlarkStatementNode body, int iteratorSlot) {
      this.variable = variable;
      this.body = body;
      this.iteratorSlot = iteratorSlot;
    }

    @Override
    public boolean executeRepeating(VirtualFrame frame) {
      Iterator<?> iter = (Iterator<?>) frame.getObject(iteratorSlot);

      // Advance iterator in @TruffleBoundary (hasNext + next combined to minimize crossings).
      // Returns null when exhausted — safe because Starlark values are never null.
      Object element = nextOrNull(iter);
      if (element == null) {
        return false;
      }

      // Interrupt check: skip the native Thread.interrupted() call unless interruptible.
      StarlarkThread thread = (StarlarkThread) frame.getArguments()[1];
      if (StarlarkTruffleAccessor.isInterruptible(thread)) {
        checkInterruptBoundary(thread);
      }

      variable.executeAssign(frame, element);
      try {
        body.executeVoid(frame);
      } catch (StarlarkContinueException e) {
        // continue to next iteration
      }
      // StarlarkBreakException and StarlarkReturnException propagate up
      return true;
    }

    /** Returns the next element, or {@code null} if the iterator is exhausted. */
    @TruffleBoundary
    private static Object nextOrNull(Iterator<?> iter) {
      return iter.hasNext() ? iter.next() : null;
    }

    @TruffleBoundary
    private static void checkInterruptBoundary(StarlarkThread thread) {
      try {
        StarlarkTruffleAccessor.checkInterrupt(thread);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
