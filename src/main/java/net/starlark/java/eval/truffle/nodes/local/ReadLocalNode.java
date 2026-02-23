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
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;

/** Reads a local variable from the current frame by slot index. */
public final class ReadLocalNode extends StarlarkExpressionNode {
    @CompilationFinal private final int slot;
    @CompilationFinal private final String name;

    public ReadLocalNode(int slot, String name) {
        this.slot = slot;
        this.name = name;
    }

    @Override
    public Object executeGeneric(VirtualFrame frame) {
        Object value = frame.getObject(slot);
        if (value == null) {
            throw referencedBeforeAssignment();
        }
        return value;
    }

    @TruffleBoundary
    private RuntimeException referencedBeforeAssignment() {
        return new RuntimeException(
            new EvalException("local variable '" + name + "' is referenced before assignment."));
    }
}
