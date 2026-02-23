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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.syntax.TokenKind;

/** Base class for binary operations that delegates to EvalUtils.binaryOp for the general case. */
public final class BinaryOpNode extends StarlarkExpressionNode {

  @Child private StarlarkExpressionNode left;
  @Child private StarlarkExpressionNode right;
  @CompilationFinal private final TokenKind operator;

  public BinaryOpNode(
      StarlarkExpressionNode left, StarlarkExpressionNode right, TokenKind operator) {
    this.left = left;
    this.right = right;
    this.operator = operator;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    Object x = left.executeGeneric(frame);
    Object y = right.executeGeneric(frame);
    StarlarkThread thread = (StarlarkThread) frame.getArguments()[1];
    return doBinaryOp(operator, x, y, thread);
  }

  @TruffleBoundary
  private static Object doBinaryOp(TokenKind op, Object x, Object y, StarlarkThread thread) {
    try {
      return StarlarkTruffleAccessor.binaryOp(op, x, y, thread);
    } catch (EvalException e) {
      throw new RuntimeException(e);
    }
  }
}
