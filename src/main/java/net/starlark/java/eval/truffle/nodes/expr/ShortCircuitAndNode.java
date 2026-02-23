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

/** Short-circuit logical AND: returns the left operand if falsy, otherwise the right operand. */
public final class ShortCircuitAndNode extends StarlarkExpressionNode {

  @Child private StarlarkExpressionNode left;
  @Child private StarlarkExpressionNode right;
  private final ConditionProfile profile = ConditionProfile.createCountingProfile();

  public ShortCircuitAndNode(StarlarkExpressionNode left, StarlarkExpressionNode right) {
    this.left = left;
    this.right = right;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    Object x = left.executeGeneric(frame);
    if (profile.profile(Starlark.truth(x))) {
      return right.executeGeneric(frame);
    }
    return x;
  }
}
