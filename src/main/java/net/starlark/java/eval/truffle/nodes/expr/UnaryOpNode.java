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
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.syntax.TokenKind;

/**
 * Unary operation node with inline fast paths for common types.
 *
 * <p>The {@code not} operator and integer negation/complement are handled without crossing a {@link
 * TruffleBoundary}. Other cases fall through to the generic helper.
 */
public final class UnaryOpNode extends StarlarkExpressionNode {

  @Child private StarlarkExpressionNode operand;
  @CompilationFinal private final TokenKind operator;

  public UnaryOpNode(StarlarkExpressionNode operand, TokenKind operator) {
    this.operand = operand;
    this.operator = operator;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    Object x = operand.executeGeneric(frame);

    // Fast path for boolean NOT — works for any type via Starlark.truth().
    if (operator == TokenKind.NOT) {
      return !Starlark.truth(x);
    }

    // Fast paths for integer unary operations.
    if (x instanceof StarlarkInt xi) {
      switch (operator) {
        case MINUS: return StarlarkInt.uminus(xi);
        case PLUS:  return xi;   // unary + is identity for int
        case TILDE: return StarlarkInt.bitnot(xi);
        default: break;
      }
    }

    return doUnaryOp(operator, x);
  }

  @TruffleBoundary
  private static Object doUnaryOp(TokenKind op, Object x) {
    try {
      return StarlarkTruffleAccessor.unaryOp(op, x);
    } catch (EvalException e) {
      throw new RuntimeException(e);
    }
  }
}
