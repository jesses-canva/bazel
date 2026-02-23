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
import com.oracle.truffle.api.frame.VirtualFrame;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.eval.truffle.nodes.StarlarkStatementNode;

/**
 * Comprehension node (list or dict). The body and clauses are translated into a nested statement
 * tree that populates the result collection. The result is either a StarlarkList or a Dict
 * depending on the isDict flag.
 */
public final class ComprehensionNode extends StarlarkExpressionNode {

  @Child private StarlarkStatementNode clauseChain;
  @CompilationFinal private final boolean isDict;
  @CompilationFinal private final int resultSlot;

  public ComprehensionNode(StarlarkStatementNode clauseChain, boolean isDict, int resultSlot) {
    this.clauseChain = clauseChain;
    this.isDict = isDict;
    this.resultSlot = resultSlot;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    StarlarkThread thread = (StarlarkThread) frame.getArguments()[1];
    Object result;
    if (isDict) {
      result = Dict.of(thread.mutability());
    } else {
      result = StarlarkList.newList(thread.mutability());
    }
    frame.setObject(resultSlot, result);
    clauseChain.executeVoid(frame);
    return frame.getObject(resultSlot);
  }
}
