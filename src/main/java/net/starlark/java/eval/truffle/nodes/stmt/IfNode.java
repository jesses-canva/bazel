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
import com.oracle.truffle.api.profiles.ConditionProfile;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.eval.truffle.nodes.StarlarkStatementNode;
import javax.annotation.Nullable;

/** Implements an if/elif/else statement. */
public final class IfNode extends StarlarkStatementNode {
    @Child private StarlarkExpressionNode condition;
    @Child private StarlarkStatementNode thenBlock;
    @Child @Nullable private StarlarkStatementNode elseBlock;
    private final ConditionProfile conditionProfile = ConditionProfile.createCountingProfile();

    public IfNode(StarlarkExpressionNode condition, StarlarkStatementNode thenBlock,
                  @Nullable StarlarkStatementNode elseBlock) {
        this.condition = condition;
        this.thenBlock = thenBlock;
        this.elseBlock = elseBlock;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        if (conditionProfile.profile(Starlark.truth(condition.executeGeneric(frame)))) {
            thenBlock.executeVoid(frame);
        } else if (elseBlock != null) {
            elseBlock.executeVoid(frame);
        }
    }
}
