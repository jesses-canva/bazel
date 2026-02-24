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
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.StarlarkTruffleLanguage;
import net.starlark.java.eval.truffle.runtime.StarlarkReturnException;

/**
 * RootNode for a Starlark def function. Wraps the function body, catches
 * StarlarkReturnException, and returns Starlark.NONE on fallthrough.
 *
 * <p>The calling convention is: args[0]=callee (StarlarkTruffleFunction), args[1]=thread
 * (StarlarkThread), args[2..]=bound locals (parameters with defaults applied, cells created).
 * The prologue copies these bound locals from the arguments array into frame slots so that
 * ReadLocalNode/ReadCellNode can access them.
 */
public final class StarlarkRootNode extends RootNode {

  @Child private StarlarkStatementNode body;
  @CompilationFinal private final String name;

  public StarlarkRootNode(
      StarlarkTruffleLanguage language,
      FrameDescriptor frameDescriptor,
      StarlarkStatementNode body,
      String name) {
    super(language, frameDescriptor);
    this.body = body;
    this.name = name;
  }

  @Override
  public Object execute(VirtualFrame frame) {
    // Copy bound locals from arguments array to frame slots.
    // Calling convention: args[0]=callee, args[1]=thread, args[2..]=locals
    Object[] args = frame.getArguments();
    int numLocals = args.length - 2;
    for (int i = 0; i < numLocals; i++) {
      if (args[i + 2] != null) {
        frame.setObject(i, args[i + 2]);
      }
    }

    try {
      body.executeVoid(frame);
    } catch (StarlarkReturnException e) {
      // The return value was stored on the thread by ReturnNode (no per-return allocation).
      return StarlarkTruffleAccessor.getTruffleReturnValue(
          (StarlarkThread) frame.getArguments()[1]);
    }
    return Starlark.NONE;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public String toString() {
    return name;
  }
}
