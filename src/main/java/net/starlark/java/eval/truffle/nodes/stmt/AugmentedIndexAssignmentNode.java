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
import javax.annotation.Nullable;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.eval.truffle.nodes.StarlarkStatementNode;
import net.starlark.java.syntax.Location;
import net.starlark.java.syntax.TokenKind;

/**
 * Augmented assignment where the LHS is an index expression: {@code obj[key] op= rhs}.
 *
 * <p>Evaluates {@code obj} and {@code key} exactly once, reads the old value, applies the
 * operator, and writes back. This avoids the double-evaluation problem of using a separate
 * {@link AugmentedAssignmentNode} + {@link net.starlark.java.eval.truffle.nodes.assign.AssignIndexNode}.
 */
public final class AugmentedIndexAssignmentNode extends StarlarkStatementNode {
    @Child private StarlarkExpressionNode containerExpr;
    @Child private StarlarkExpressionNode keyExpr;
    @Child private StarlarkExpressionNode rhsExpr;
    @CompilationFinal private final TokenKind operator;
    /** Location of the operator token (e.g. '+='), for accurate error reporting. */
    @CompilationFinal @Nullable private final Location operatorLocation;

    public AugmentedIndexAssignmentNode(
            StarlarkExpressionNode containerExpr,
            StarlarkExpressionNode keyExpr,
            StarlarkExpressionNode rhsExpr,
            TokenKind operator,
            @Nullable Location operatorLocation) {
        this.containerExpr = containerExpr;
        this.keyExpr = keyExpr;
        this.rhsExpr = rhsExpr;
        this.operator = operator;
        this.operatorLocation = operatorLocation;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        StarlarkThread thread = (StarlarkThread) frame.getArguments()[1];
        Object container = containerExpr.executeGeneric(frame);
        Object key = keyExpr.executeGeneric(frame);
        Object rhs = rhsExpr.executeGeneric(frame);
        StarlarkTruffleAccessor.setErrorLocation(thread, operatorLocation);
        doAugmentedIndex(operator, container, key, rhs, thread);
    }

    @TruffleBoundary
    private static void doAugmentedIndex(
            TokenKind op, Object container, Object key, Object rhs, StarlarkThread thread) {
        try {
            Object oldValue = StarlarkTruffleAccessor.index(thread, container, key);
            Object newValue = StarlarkTruffleAccessor.inplaceBinaryOp(op, oldValue, rhs, thread);
            StarlarkTruffleAccessor.setIndex(container, key, newValue);
        } catch (EvalException e) {
            throw new RuntimeException(e);
        }
    }
}
