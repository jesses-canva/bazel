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
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.eval.truffle.nodes.StarlarkStatementNode;
import net.starlark.java.eval.truffle.runtime.StarlarkTruffleFunction;

/** Writes a value to a global variable via the callee function's module and global index mapping. */
public final class WriteGlobalNode extends StarlarkStatementNode {
    @CompilationFinal private final int progIndex;
    @Child private StarlarkExpressionNode value;

    public WriteGlobalNode(int progIndex, StarlarkExpressionNode value) {
        this.progIndex = progIndex;
        this.value = value;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        StarlarkTruffleFunction callee = (StarlarkTruffleFunction) args[0];
        Object val = value.executeGeneric(frame);
        net.starlark.java.eval.StarlarkTruffleAccessor.setGlobalByIndex(
                callee.getModule(), callee.getGlobalIndex()[progIndex], val);
    }
}
