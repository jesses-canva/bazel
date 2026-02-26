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
package net.starlark.java.eval.truffle;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.frame.FrameDescriptor;
import com.oracle.truffle.api.impl.DefaultTruffleRuntime;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkFunction;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.Tuple;
import net.starlark.java.eval.truffle.nodes.StarlarkModuleRootNode;
import net.starlark.java.eval.truffle.runtime.FreezeAssumption;
import net.starlark.java.eval.truffle.runtime.StarlarkTruffleFunction;
import net.starlark.java.syntax.Program;
import net.starlark.java.syntax.Resolver;

/**
 * Entry point for the Truffle-based Starlark interpreter.
 *
 * <p>This class is called from {@link Starlark#execFileProgram} via reflection. It translates the
 * resolved AST to Truffle nodes and executes them.
 */
public final class TruffleIntegration {

  static {
    // Verify that the Truffle JIT compiler is available. If the runtime is
    // DefaultTruffleRuntime, Truffle is running in interpreter-only mode — defeating the
    // purpose of the Truffle-based Starlark interpreter.
    if (Truffle.getRuntime() instanceof DefaultTruffleRuntime defaultRuntime) {
      throw new IllegalStateException(
          "Starlark Truffle interpreter requires JIT compilation support, but Truffle is "
              + "running in interpreter-only mode (DefaultTruffleRuntime). Reason: "
              + defaultRuntime.getFallbackReason()
              + ". Ensure Bazel is running on GraalVM with the Truffle compiler on the module path.");
    }
  }

  private TruffleIntegration() {} // uninstantiable

  /**
   * Executes a compiled Starlark program using the Truffle interpreter.
   *
   * @param prog the compiled program
   * @param module the module environment
   * @param thread the Starlark thread for execution context
   * @return the result of executing the program (None unless the file's final statement is an
   *     expression)
   */
  public static Object execFileProgram(Program prog, Module module, StarlarkThread thread)
      throws EvalException, InterruptedException {
    Resolver.Function rfn = prog.getResolvedFunction();

    int[] globalIndex =
        net.starlark.java.eval.StarlarkTruffleAccessor.getIndicesOfGlobals(
            module, rfn.getGlobals());

    if (module.getDocumentation() == null) {
      String documentation = rfn.getDocumentation();
      if (documentation != null) {
        module.setDocumentation(Starlark.trimDocString(documentation));
      }
    }

    // Register a FreezeAssumption on the thread's mutability so that when the thread is frozen,
    // any JIT-compiled Truffle code that assumed mutability will deoptimize.
    if (!thread.mutability().isFrozen()) {
      FreezeAssumption.forMutability(thread.mutability());
    }

    // Translate the resolved AST to a Truffle node tree.
    SyntaxToTruffleTranslator translator =
        new SyntaxToTruffleTranslator(module, globalIndex, thread);
    FrameDescriptor frameDescriptor = translator.buildFrameDescriptor(rfn);

    StarlarkModuleRootNode rootNode =
        translator.translateModule(rfn, frameDescriptor, module, globalIndex, thread);

    CallTarget callTarget = rootNode.getCallTarget();

    // Create a synthetic StarlarkTruffleFunction to serve as args[0] in the unified
    // calling convention: args[0]=callee, args[1]=thread.
    // Module-level nodes (ReadGlobalNode, WriteGlobalNode, ReadPredeclaredNode, etc.)
    // access the module and globalIndex through this callee object.
    StarlarkTruffleFunction syntheticCallee =
        new StarlarkTruffleFunction(
            rfn,
            module,
            globalIndex,
            Tuple.empty(),
            new StarlarkFunction.Cell[0],
            callTarget,
            thread.getNextIdentityToken());

    // Push the synthetic callee onto the call stack so that Bazel's framework can find
    // the enclosing module (via Module.ofInnermostEnclosingStarlarkFunction).
    net.starlark.java.eval.StarlarkTruffleAccessor.pushCallStack(thread, syntheticCallee);

    // Execute the Truffle AST.
    // CallTarget.call() wraps exceptions in RuntimeException, so we unwrap them here.
    try {
      return callTarget.call(syntheticCallee, thread);
    } catch (Starlark.UncheckedEvalException | Starlark.UncheckedEvalError e) {
      // Already properly wrapped by Starlark.positionalOnlyCall() etc. - re-throw directly.
      throw e;
    } catch (RuntimeException e) {
      Throwable cause = e.getCause();
      if (cause instanceof EvalException evalEx) {
        throw net.starlark.java.eval.StarlarkTruffleAccessor.ensureEvalExceptionStack(evalEx, thread);
      }
      if (cause instanceof InterruptedException) {
        throw (InterruptedException) cause;
      }
      // Wrap other RuntimeExceptions so the original is accessible via getCause(), matching
      // the Starlark.positionalOnlyCall() behavior of wrapping in UncheckedEvalException.
      throw new RuntimeException(e);
    } finally {
      net.starlark.java.eval.StarlarkTruffleAccessor.popCallStack(thread);
    }
  }
}
