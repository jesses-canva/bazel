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
import javax.annotation.Nullable;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.syntax.Location;
import net.starlark.java.syntax.TokenKind;

/**
 * Binary operation node with inline fast paths for common type combinations.
 *
 * <p>Hot paths (int op int, string + string, equality) are handled without crossing a {@link
 * TruffleBoundary}, allowing the Truffle compiler to partially evaluate them on GraalVM. All other
 * cases fall through to the generic {@link #doBinaryOp} boundary helper.
 */
public final class BinaryOpNode extends StarlarkExpressionNode {

  @Child private StarlarkExpressionNode left;
  @Child private StarlarkExpressionNode right;
  @CompilationFinal private final TokenKind operator;
  /** Location of the operator token, for accurate error reporting in stack traces. */
  @CompilationFinal @Nullable private final Location operatorLocation;

  public BinaryOpNode(
      StarlarkExpressionNode left, StarlarkExpressionNode right, TokenKind operator,
      @Nullable Location operatorLocation) {
    this.left = left;
    this.right = right;
    this.operator = operator;
    this.operatorLocation = operatorLocation;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    Object x = left.executeGeneric(frame);
    Object y = right.executeGeneric(frame);

    // Equality/inequality work for any types without any @TruffleBoundary.
    if (operator == TokenKind.EQUALS_EQUALS) {
      return x.equals(y);
    }
    if (operator == TokenKind.NOT_EQUALS) {
      return !x.equals(y);
    }

    // Fast paths for integer arithmetic and comparisons.
    if (x instanceof StarlarkInt xi && y instanceof StarlarkInt yi) {
      switch (operator) {
        case PLUS:          return StarlarkInt.add(xi, yi);
        case MINUS:         return StarlarkInt.subtract(xi, yi);
        case STAR:          return StarlarkInt.multiply(xi, yi);
        case LESS:          return StarlarkInt.compare(xi, yi) < 0;
        case LESS_EQUALS:   return StarlarkInt.compare(xi, yi) <= 0;
        case GREATER:       return StarlarkInt.compare(xi, yi) > 0;
        case GREATER_EQUALS: return StarlarkInt.compare(xi, yi) >= 0;
        default:            break; // SLASH, SLASH_SLASH, PERCENT, PIPE, AMPERSAND, CARET, shifts
      }
    }

    // Fast path for string concatenation.
    if (operator == TokenKind.PLUS && x instanceof String xs && y instanceof String ys) {
      return xs + ys;
    }

    StarlarkThread thread = (StarlarkThread) frame.getArguments()[1];
    return doBinaryOp(operator, operatorLocation, x, y, thread);
  }

  @TruffleBoundary
  private static Object doBinaryOp(
      TokenKind op, Location operatorLoc, Object x, Object y, StarlarkThread thread) {
    try {
      return StarlarkTruffleAccessor.binaryOp(op, x, y, thread);
    } catch (EvalException e) {
      StarlarkTruffleAccessor.setErrorLocation(thread, operatorLoc);
      throw new RuntimeException(e);
    }
  }
}
