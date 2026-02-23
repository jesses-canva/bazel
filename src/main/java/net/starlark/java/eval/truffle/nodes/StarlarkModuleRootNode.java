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
package net.starlark.java.eval.truffle.nodes;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.RootNode;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkFunction;
import net.starlark.java.eval.truffle.StarlarkTruffleLanguage;
import net.starlark.java.eval.truffle.runtime.StarlarkReturnException;

/**
 * RootNode for file-level (module) execution. Uses the unified calling convention:
 * args[0]=StarlarkTruffleFunction (synthetic callee holding module/globalIndex),
 * args[1]=StarlarkThread.
 */
public final class StarlarkModuleRootNode extends RootNode {

  @Child private StarlarkStatementNode body;
  @CompilationFinal private String name;
  @CompilationFinal(dimensions = 1) private final int[] cellIndices;

  public StarlarkModuleRootNode(
      StarlarkTruffleLanguage language,
      FrameDescriptor frameDescriptor,
      StarlarkStatementNode body,
      String name,
      int[] cellIndices) {
    super(language, frameDescriptor);
    this.body = body;
    this.name = name;
    this.cellIndices = cellIndices;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    // Arguments: [StarlarkTruffleFunction (synthetic callee), StarlarkThread]
    // Same convention as function calls: args[0]=callee, args[1]=thread

    // Initialize cell slots for module-level variables that are captured by inner functions.
    // Without this, WriteCellNode would get null from the frame slot.
    initCells(frame);

    try {
      body.executeVoid(frame);
    } catch (StarlarkReturnException e) {
      return e.getResult();
    }
    return Starlark.NONE;
  }

  @ExplodeLoop
  private void initCells(VirtualFrame frame) {
    for (int index : cellIndices) {
      frame.setObject(index, new StarlarkFunction.Cell(null));
    }
  }

  @Override
  public String getName() {
    return name;
  }
}
