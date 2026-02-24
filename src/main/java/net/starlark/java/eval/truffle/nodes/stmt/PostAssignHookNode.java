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
import net.starlark.java.eval.StarlarkFunction;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.nodes.StarlarkStatementNode;
import net.starlark.java.eval.truffle.runtime.StarlarkTruffleFunction;
import net.starlark.java.syntax.Location;

/**
 * Wraps a top-level assignment or def statement to implement the postAssignHook ("export") hack.
 *
 * <p>After executing the wrapped statement, if {@code thread.postAssignHook != null}, this node
 * calls {@code export()} on function values or {@code postAssignHook.assign()} on other values for
 * each bound identifier.
 */
public final class PostAssignHookNode extends StarlarkStatementNode {
    @Child private StarlarkStatementNode inner;
    @CompilationFinal(dimensions = 1) private final String[] boundNames;
    @CompilationFinal(dimensions = 1) private final int[] globalIndices;
    @CompilationFinal(dimensions = 1) private final Location[] locations;

    public PostAssignHookNode(
            StarlarkStatementNode inner,
            String[] boundNames,
            int[] globalIndices,
            Location[] locations) {
        this.inner = inner;
        this.boundNames = boundNames;
        this.globalIndices = globalIndices;
        this.locations = locations;
    }

    @Override
    public void executeVoid(VirtualFrame frame) {
        inner.executeVoid(frame);

        StarlarkThread thread = (StarlarkThread) frame.getArguments()[1];
        StarlarkThread.PostAssignHook hook = StarlarkTruffleAccessor.getPostAssignHook(thread);
        if (hook != null) {
            StarlarkTruffleFunction callee = (StarlarkTruffleFunction) frame.getArguments()[0];
            fireHook(thread, hook, callee, boundNames, globalIndices, locations);
        }
    }

    @TruffleBoundary
    private static void fireHook(
            StarlarkThread thread,
            StarlarkThread.PostAssignHook hook,
            StarlarkTruffleFunction callee,
            String[] names,
            int[] indices,
            Location[] locs) {
        for (int i = 0; i < names.length; i++) {
            Object value =
                    StarlarkTruffleAccessor.getGlobalByIndex(callee.getModule(), indices[i]);
            if (value instanceof StarlarkFunction func) {
                func.export(thread, names[i]);
            } else if (value instanceof StarlarkTruffleFunction func) {
                func.export(thread, names[i]);
            } else {
                hook.assign(names[i], locs[i], value);
            }
        }
    }
}
