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
package net.starlark.java.eval.truffle.nodes.local;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import net.starlark.java.eval.StarlarkFunction;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.eval.truffle.nodes.StarlarkStatementNode;

/** Writes through a Cell indirection for variables shared with nested functions. */
public final class WriteCellNode extends StarlarkStatementNode {
    @CompilationFinal private final int slot;
    @Child private StarlarkExpressionNode value;

    public WriteCellNode(int slot, StarlarkExpressionNode value) {
        this.slot = slot;
        this.value = value;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        ((StarlarkFunction.Cell) frame.getObject(slot)).x = value.executeGeneric(frame);
    }
}
