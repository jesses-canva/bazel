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

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import net.starlark.java.eval.StarlarkFunction;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.eval.truffle.nodes.StarlarkStatementNode;
import net.starlark.java.eval.truffle.nodes.assign.AssignTargetNode;
import net.starlark.java.eval.truffle.runtime.StarlarkTruffleFunction;
import net.starlark.java.syntax.Resolver;

/** Creates a StarlarkTruffleFunction at runtime and assigns it to a local scope. */
public final class DefNode extends StarlarkStatementNode {
    @Children private final StarlarkExpressionNode[] defaultValueExprs;
    @CompilationFinal private final CallTarget callTarget;
    @CompilationFinal private final Resolver.Function rfn;
    @CompilationFinal(dimensions = 1) private final int[] freeVarBindingScopes;
    @CompilationFinal(dimensions = 1) private final int[] freeVarBindingIndices;
    @Child private AssignTargetNode assignTarget;

    public DefNode(
            StarlarkExpressionNode[] defaultValueExprs,
            CallTarget callTarget,
            Resolver.Function rfn,
            int[] freeVarBindingScopes,
            int[] freeVarBindingIndices,
            AssignTargetNode assignTarget) {
        this.defaultValueExprs = defaultValueExprs;
        this.callTarget = callTarget;
        this.rfn = rfn;
        this.freeVarBindingScopes = freeVarBindingScopes;
        this.freeVarBindingIndices = freeVarBindingIndices;
        this.assignTarget = assignTarget;
    }

    @Override
    @ExplodeLoop
    public void executeVoid(VirtualFrame frame) {
        // Evaluate default parameter values
        Object[] defaults = new Object[defaultValueExprs.length];
        for (int i = 0; i < defaultValueExprs.length; i++) {
            if (defaultValueExprs[i] != null) {
                defaults[i] = defaultValueExprs[i].executeGeneric(frame);
            } else {
                defaults[i] = StarlarkFunction.MANDATORY;
            }
        }

        // Capture free variables
        Object[] args = frame.getArguments();
        StarlarkFunction.Cell[] freevars = new StarlarkFunction.Cell[freeVarBindingScopes.length];
        for (int i = 0; i < freevars.length; i++) {
            int scope = freeVarBindingScopes[i];
            int index = freeVarBindingIndices[i];
            if (scope == 0) { // FREE
                StarlarkTruffleFunction callee = (StarlarkTruffleFunction) args[0];
                freevars[i] = callee.getFreeVar(index);
            } else { // CELL
                freevars[i] = (StarlarkFunction.Cell) frame.getObject(index);
            }
        }

        StarlarkTruffleFunction callee = (StarlarkTruffleFunction) args[0];
        StarlarkThread thread = (StarlarkThread) args[1];

        StarlarkTruffleFunction fn = new StarlarkTruffleFunction(
                rfn, callee.getModule(), callee.getGlobalIndex(),
                StarlarkTruffleAccessor.wrapTuple(defaults), freevars, callTarget,
                thread.getNextIdentityToken());

        assignTarget.executeAssign(frame, fn);
    }
}
