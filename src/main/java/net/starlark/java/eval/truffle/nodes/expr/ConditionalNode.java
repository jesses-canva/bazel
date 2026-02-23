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

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;

/** Ternary conditional expression node: evaluates {@code thenExpr if condition else elseExpr}. */
public final class ConditionalNode extends StarlarkExpressionNode {

  @Child private StarlarkExpressionNode condition;
  @Child private StarlarkExpressionNode thenExpr;
  @Child private StarlarkExpressionNode elseExpr;
  private final ConditionProfile profile = ConditionProfile.createCountingProfile();

  public ConditionalNode(
      StarlarkExpressionNode condition,
      StarlarkExpressionNode thenExpr,
      StarlarkExpressionNode elseExpr) {
    this.condition = condition;
    this.thenExpr = thenExpr;
    this.elseExpr = elseExpr;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    if (profile.profile(Starlark.truth(condition.executeGeneric(frame)))) {
      return thenExpr.executeGeneric(frame);
    } else {
      return elseExpr.executeGeneric(frame);
    }
  }
}
