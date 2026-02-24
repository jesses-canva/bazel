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
import com.oracle.truffle.api.frame.VirtualFrame;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.eval.truffle.nodes.StarlarkStatementNode;
import net.starlark.java.eval.truffle.nodes.assign.AssignTargetNode;
import net.starlark.java.eval.truffle.runtime.StarlarkBreakException;
import net.starlark.java.eval.truffle.runtime.StarlarkContinueException;

/** Implements a for loop statement. */
public final class ForNode extends StarlarkStatementNode {
    @Child private StarlarkExpressionNode collection;
    @Child private AssignTargetNode variable;
    @Child private StarlarkStatementNode body;

    public ForNode(StarlarkExpressionNode collection, AssignTargetNode variable,
                   StarlarkStatementNode body) {
        this.collection = collection;
        this.variable = variable;
        this.body = body;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        StarlarkThread thread = (StarlarkThread) frame.getArguments()[1];
        Object seq = collection.executeGeneric(frame);
        Iterable<?> iterable = toIterable(seq);
        StarlarkTruffleAccessor.addIterator(seq);
        try {
            for (Object element : iterable) {
                // Skip the Thread.interrupted() native call when not interruptible (common case).
                if (StarlarkTruffleAccessor.isInterruptible(thread)) {
                    checkInterruptBoundary(thread);
                }
                variable.executeAssign(frame, element);
                try {
                    body.executeVoid(frame);
                } catch (StarlarkContinueException e) {
                    // continue to next iteration
                } catch (StarlarkBreakException e) {
                    break;
                }
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
    private static Iterable<?> toIterable(Object value) {
        try {
            return Starlark.toIterable(value);
        } catch (EvalException e) {
            throw new RuntimeException(e);
        }
    }
}
