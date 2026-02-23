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

import com.oracle.truffle.api.frame.VirtualFrame;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.eval.truffle.nodes.StarlarkStatementNode;

/** A node representing a statement that evaluates an expression and discards the result. */
public final class ExpressionStatementNode extends StarlarkStatementNode {

  @Child private StarlarkExpressionNode expression;

  public ExpressionStatementNode(StarlarkExpressionNode expression) {
    this.expression = expression;
  }

  @Override
  public void executeVoid(VirtualFrame frame) {
    expression.executeGeneric(frame);
  }
}
