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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
/** Destructuring assignment: {@code a, b = expr}. */
public final class AssignUnpackNode extends AssignTargetNode {
    @Children private final AssignTargetNode[] targets;

    public AssignUnpackNode(AssignTargetNode[] targets) {
        this.targets = targets;
    }

    /** Unpacks the value into the target variables. */
    @ExplodeLoop
    public void executeAssign(VirtualFrame frame, Object value) {
        int nlhs = targets.length;
        int nrhs = Starlark.len(value);
        if (nrhs < 0 || value instanceof String) {
            throwNotIterable(value, nlhs);
        }
        if (nrhs != nlhs) {
            throwWrongCount(nrhs, nlhs);
        }
        try {
            Iterable<?> rhs = Starlark.toIterable(value);
            int i = 0;
            for (Object item : rhs) {
                targets[i].executeAssign(frame, item);
                i++;
            }
        } catch (EvalException e) {
            throw new RuntimeException(e);
        }
    }

    @TruffleBoundary
    private static void throwNotIterable(Object value, int nlhs) {
        throw new RuntimeException(new EvalException(
                String.format("got '%s' in sequence assignment (want %d-element sequence)",
                        Starlark.type(value), nlhs)));
    }

    @TruffleBoundary
    private static void throwWrongCount(int nrhs, int nlhs) {
        throw new RuntimeException(new EvalException(
                String.format("too %s values to unpack (got %d, want %d)",
                        nrhs < nlhs ? "few" : "many", nrhs, nlhs)));
    }

}
