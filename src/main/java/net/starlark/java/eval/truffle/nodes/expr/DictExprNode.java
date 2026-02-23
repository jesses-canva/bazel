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

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;

/** Dict literal expression node: evaluates {@code {k1: v1, k2: v2, ...}}. */
public final class DictExprNode extends StarlarkExpressionNode {

  @Children private final StarlarkExpressionNode[] keys;
  @Children private final StarlarkExpressionNode[] values;

  public DictExprNode(StarlarkExpressionNode[] keys, StarlarkExpressionNode[] values) {
    assert keys.length == values.length;
    this.keys = keys;
    this.values = values;
  }

  @Override
  @ExplodeLoop
  public Object executeGeneric(VirtualFrame frame) {
    StarlarkThread thread = (StarlarkThread) frame.getArguments()[1];
    Dict<Object, Object> dict = Dict.of(thread.mutability());
    for (int i = 0; i < keys.length; i++) {
      Object k = keys[i].executeGeneric(frame);
      Object v = values[i].executeGeneric(frame);
      putEntry(dict, k, v, thread);
    }
    return dict;
  }

  @TruffleBoundary
  private static void putEntry(
      Dict<Object, Object> dict, Object k, Object v, StarlarkThread thread) {
    try {
      int before = dict.size();
      dict.putEntry(k, v);
      if (dict.size() == before) {
        throw Starlark.errorf(
            "dictionary expression has duplicate key: %s",
            Starlark.repr(k, thread.getSemantics()));
      }
    } catch (EvalException e) {
      throw new RuntimeException(e);
    }
  }
}
