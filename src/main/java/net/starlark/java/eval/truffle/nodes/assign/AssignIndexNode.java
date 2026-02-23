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
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;

/** Assignment target: {@code obj[key] = val}. */
public final class AssignIndexNode extends AssignTargetNode {
    @Child private StarlarkExpressionNode object;
    @Child private StarlarkExpressionNode key;

    public AssignIndexNode(StarlarkExpressionNode object, StarlarkExpressionNode key) {
        this.object = object;
        this.key = key;
    }

    @Override
    public void executeAssign(VirtualFrame frame, Object value) {
        Object obj = object.executeGeneric(frame);
        Object k = key.executeGeneric(frame);
        doSetIndex(obj, k, value);
    }

    @TruffleBoundary
    private static void doSetIndex(Object obj, Object key, Object value) {
        try {
            StarlarkTruffleAccessor.setIndex(obj, key, value);
        } catch (EvalException e) {
            throw new RuntimeException(e);
        }
    }
}
