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
import net.starlark.java.eval.truffle.nodes.assign.AssignTargetNode;

/** Implements a simple assignment statement: {@code target = rhs}. */
public final class AssignmentNode extends StarlarkStatementNode {
    @Child private StarlarkExpressionNode rhs;
    @Child private AssignTargetNode target;

    public AssignmentNode(AssignTargetNode target, StarlarkExpressionNode rhs) {
        this.target = target;
        this.rhs = rhs;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        Object value = rhs.executeGeneric(frame);
        target.executeAssign(frame, value);
    }
}
