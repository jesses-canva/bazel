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
import javax.annotation.Nullable;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.eval.truffle.nodes.StarlarkStatementNode;
import net.starlark.java.eval.truffle.runtime.StarlarkReturnException;

/** A node representing a return statement in Starlark. */
public final class ReturnNode extends StarlarkStatementNode {

  @Child @Nullable private StarlarkExpressionNode expression;

  public ReturnNode(@Nullable StarlarkExpressionNode expression) {
    this.expression = expression;
  }

  @Override
  public void executeVoid(VirtualFrame frame) {
    Object result;
    if (expression != null) {
      result = expression.executeGeneric(frame);
    } else {
      result = Starlark.NONE;
    }
    throw new StarlarkReturnException(result);
  }
}
