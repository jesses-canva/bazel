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
package net.starlark.java.eval.truffle.nodes.assign;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import net.starlark.java.eval.StarlarkFunction;
import net.starlark.java.eval.truffle.runtime.StarlarkTruffleFunction;
import net.starlark.java.syntax.Resolver;

/** Assignment target for an identifier, writing to the appropriate scope. */
public final class AssignIdentifierTargetNode extends AssignTargetNode {
    @CompilationFinal private final Resolver.Scope scope;
    @CompilationFinal private final int index;

    public AssignIdentifierTargetNode(Resolver.Scope scope, int index) {
        this.scope = scope;
        this.index = index;
    }

    @Override
    public void executeAssign(VirtualFrame frame, Object value) {
        switch (scope) {
            case LOCAL:
                frame.setObject(index, value);
                break;
            case CELL:
                ((StarlarkFunction.Cell) frame.getObject(index)).x = value;
                break;
            case GLOBAL:
                Object[] args = frame.getArguments();
                StarlarkTruffleFunction callee = (StarlarkTruffleFunction) args[0];
                net.starlark.java.eval.StarlarkTruffleAccessor.setGlobalByIndex(
                        callee.getModule(), callee.getGlobalIndex()[index], value);
                break;
            default:
                throw new IllegalStateException("cannot assign to " + scope);
        }
    }
}
