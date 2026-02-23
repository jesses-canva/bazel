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
import com.oracle.truffle.api.frame.VirtualFrame;
import net.starlark.java.eval.truffle.nodes.StarlarkStatementNode;
import net.starlark.java.eval.truffle.runtime.StarlarkBreakException;
import net.starlark.java.eval.truffle.runtime.StarlarkContinueException;
import net.starlark.java.syntax.TokenKind;

/** A node representing a break or continue statement in Starlark. */
public final class FlowNode extends StarlarkStatementNode {

  @CompilationFinal private final TokenKind kind;

  public FlowNode(TokenKind kind) {
    assert kind == TokenKind.BREAK || kind == TokenKind.CONTINUE || kind == TokenKind.PASS;
    this.kind = kind;
  }

  @Override
  public void executeVoid(VirtualFrame frame) {
    if (kind == TokenKind.BREAK) {
      throw StarlarkBreakException.INSTANCE;
    } else if (kind == TokenKind.CONTINUE) {
      throw StarlarkContinueException.INSTANCE;
    }
    // PASS is a no-op
  }
}
