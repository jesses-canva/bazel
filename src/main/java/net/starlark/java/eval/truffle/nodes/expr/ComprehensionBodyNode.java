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

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import javax.annotation.Nullable;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.eval.truffle.nodes.StarlarkStatementNode;

/**
 * Evaluates the body expression of a comprehension and adds the result to the collection stored at
 * the result slot.
 */
public final class ComprehensionBodyNode extends StarlarkStatementNode {

  @Child @Nullable private StarlarkExpressionNode bodyKey;
  @Child private StarlarkExpressionNode bodyValue;
  @CompilationFinal private final boolean isDict;
  @CompilationFinal private final int resultSlot;

  /** Constructor for list comprehension. */
  public ComprehensionBodyNode(StarlarkExpressionNode bodyValue, int resultSlot) {
    this.bodyKey = null;
    this.bodyValue = bodyValue;
    this.isDict = false;
    this.resultSlot = resultSlot;
  }

  /** Constructor for dict comprehension. */
  public ComprehensionBodyNode(
      StarlarkExpressionNode bodyKey, StarlarkExpressionNode bodyValue, int resultSlot) {
    this.bodyKey = bodyKey;
    this.bodyValue = bodyValue;
    this.isDict = true;
    this.resultSlot = resultSlot;
  }

  @Override
  @SuppressWarnings("unchecked")
  public void executeVoid(VirtualFrame frame) {
    Object result = frame.getObject(resultSlot);
    if (isDict) {
      Object k = bodyKey.executeGeneric(frame);
      Object v = bodyValue.executeGeneric(frame);
      addDictEntry((Dict<Object, Object>) result, k, v);
    } else {
      Object v = bodyValue.executeGeneric(frame);
      addListElement((StarlarkList<Object>) result, v);
    }
  }

  @TruffleBoundary
  private static void addDictEntry(Dict<Object, Object> dict, Object k, Object v) {
    try {
      Starlark.checkHashable(k);
      dict.putEntry(k, v);
    } catch (EvalException e) {
      throw new RuntimeException(e);
    }
  }

  @TruffleBoundary
  private static void addListElement(StarlarkList<Object> list, Object v) {
    try {
      list.addElement(v);
    } catch (EvalException e) {
      throw new RuntimeException(e);
    }
  }
}
