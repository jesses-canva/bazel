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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.eval.truffle.nodes.StarlarkStatementNode;
import net.starlark.java.eval.truffle.nodes.assign.AssignTargetNode;
import net.starlark.java.syntax.TokenKind;

/** Implements an augmented assignment statement: {@code x += y}, {@code x -= y}, etc. */
public final class AugmentedAssignmentNode extends StarlarkStatementNode {
    @Child private StarlarkExpressionNode lhs;
    @Child private StarlarkExpressionNode rhs;
    @Child private AssignTargetNode target;
    @CompilationFinal private final TokenKind operator;

    public AugmentedAssignmentNode(
            StarlarkExpressionNode lhs, StarlarkExpressionNode rhs,
            AssignTargetNode target, TokenKind operator) {
        this.lhs = lhs;
        this.rhs = rhs;
        this.target = target;
        this.operator = operator;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        Object x = lhs.executeGeneric(frame);
        Object y = rhs.executeGeneric(frame);
        Object[] args = frame.getArguments();
        StarlarkThread thread = getThread(args);
        Object result = doBinaryOp(operator, x, y, thread);
        target.executeAssign(frame, result);
    }

    private static StarlarkThread getThread(Object[] args) {
        // Thread is arg[1] in the Truffle calling convention
        return (StarlarkThread) args[1];
    }

    @TruffleBoundary
    private static Object doBinaryOp(TokenKind op, Object x, Object y, StarlarkThread thread) {
        try {
            return StarlarkTruffleAccessor.inplaceBinaryOp(op, x, y, thread);
        } catch (EvalException e) {
            throw new RuntimeException(e);
        }
    }
}
