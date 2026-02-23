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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.truffle.nodes.StarlarkStatementNode;
import net.starlark.java.eval.truffle.nodes.assign.AssignTargetNode;
import net.starlark.java.spelling.SpellChecker;

/** Implements a load statement: {@code load("module", "name1", name2="orig2")}. */
public final class LoadNode extends StarlarkStatementNode {
    @CompilationFinal private final String moduleName;
    @CompilationFinal(dimensions = 1) private final String[] originalNames;
    @Children private final AssignTargetNode[] targets;

    public LoadNode(String moduleName, String[] originalNames, AssignTargetNode[] targets) {
        this.moduleName = moduleName;
        this.originalNames = originalNames;
        this.targets = targets;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        Object[] args = frame.getArguments();
        StarlarkThread thread = (StarlarkThread) args[1];
        doLoad(frame, thread);
    }

    @TruffleBoundary
    private void doLoad(VirtualFrame frame, StarlarkThread thread) {
        StarlarkThread.Loader loader =
                net.starlark.java.eval.StarlarkTruffleAccessor.getLoader(thread);
        if (loader == null) {
            throw new RuntimeException(
                    new EvalException("load statements may not be executed in this thread"));
        }
        Module module = loader.load(moduleName);
        if (module == null) {
            throw new RuntimeException(
                    new EvalException(String.format("module '%s' not found", moduleName)));
        }
        for (int i = 0; i < originalNames.length; i++) {
            Object value = module.getGlobal(originalNames[i]);
            if (value == null) {
                throw new RuntimeException(new EvalException(String.format(
                        "file '%s' does not contain symbol '%s'%s",
                        moduleName, originalNames[i],
                        SpellChecker.didYouMean(originalNames[i], module.getGlobals().keySet()))));
            }
            targets[i].executeAssign(frame, value);
        }
    }
}
