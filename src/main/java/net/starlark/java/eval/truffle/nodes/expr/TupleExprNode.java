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
package net.starlark.java.eval.truffle.nodes.expr;

import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;

/** Tuple literal expression node: evaluates {@code (e1, e2, ...)}. */
public final class TupleExprNode extends StarlarkExpressionNode {

  @Children private final StarlarkExpressionNode[] elements;

  public TupleExprNode(StarlarkExpressionNode[] elements) {
    this.elements = elements;
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    Object[] values = new Object[elements.length];
    for (int i = 0; i < elements.length; i++) {
      values[i] = elements[i].executeGeneric(frame);
    }
    return StarlarkTruffleAccessor.wrapTuple(values);
  }
}
