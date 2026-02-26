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
package net.starlark.java.eval.truffle.runtime;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.Printer;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkFunction;
import net.starlark.java.eval.StarlarkSemantics;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.SymbolGenerator;
import net.starlark.java.eval.Tuple;
import net.starlark.java.syntax.Location;
import net.starlark.java.syntax.Resolver;
import net.starlark.java.syntax.StarlarkType;
import net.starlark.java.syntax.Types;

/**
 * A Truffle-native Starlark function value created by a {@code def} statement.
 *
 * <p>This implements {@link StarlarkCallable} so it can be used interchangeably with the
 * tree-walking interpreter's {@link StarlarkFunction}.
 */
@StarlarkBuiltin(
    name = "function",
    category = "core",
    doc = "The type of functions declared in Starlark.")
public final class StarlarkTruffleFunction implements StarlarkCallable {

  @CompilationFinal private final Resolver.Function rfn;
  @CompilationFinal private final Module module;
  @CompilationFinal(dimensions = 1) private final int[] globalIndex;
  @CompilationFinal private final Tuple defaultValues;
  @CompilationFinal(dimensions = 1) private final StarlarkFunction.Cell[] freevars;
  @CompilationFinal private final CallTarget callTarget;
  @CompilationFinal private SymbolGenerator.Symbol<?> token;
  private volatile StarlarkFunction cachedStarlarkFunction;

  public StarlarkTruffleFunction(
      Resolver.Function rfn,
      Module module,
      int[] globalIndex,
      Tuple defaultValues,
      StarlarkFunction.Cell[] freevars,
      CallTarget callTarget,
      SymbolGenerator.Symbol<?> token) {
    this.rfn = rfn;
    this.module = module;
    this.globalIndex = globalIndex;
    this.defaultValues = defaultValues;
    this.freevars = freevars;
    this.callTarget = callTarget;
    this.token = token;
  }

  @Override
  public String getName() {
    return rfn.getName();
  }

  @Override
  public Location getLocation() {
    return rfn.getLocation();
  }

  @Override
  public Resolver.Function getResolvedFunction() {
    return rfn;
  }

  @Override
  public Module getModule() {
    return module;
  }

  public int[] getGlobalIndex() {
    return globalIndex;
  }

  public Tuple getDefaultValues() {
    return defaultValues;
  }

  public StarlarkFunction.Cell getFreeVar(int index) {
    return freevars[index];
  }

  public StarlarkFunction.Cell[] getFreeVars() {
    return freevars;
  }

  public CallTarget getCallTarget() {
    return callTarget;
  }

  @Override
  public StarlarkType getStarlarkType() {
    Types.CallableType type = rfn.getFunctionType();
    return type != null ? type : Types.ANY;
  }

  @Override
  public StarlarkCallable.ArgumentProcessor requestArgumentProcessor(StarlarkThread thread) {
    return new TruffleArgumentProcessor(this, thread);
  }

  /**
   * Returns {@code true} if this function is "simple" for the positional fast path: no {@code
   * *args}, no {@code **kwargs}, no free-variable cells, and no keyword-only parameters. Callers
   * can cache this value as {@code @CompilationFinal} to make the check free in compiled code.
   */
  public boolean isSimplePositionalFunction() {
    return !rfn.hasVarargs()
        && !rfn.hasKwargs()
        && rfn.getCellIndices().length == 0
        // No keyword-only parameters (all non-residual params are ordinary positional).
        && rfn.getNumNonResidualParameters() == rfn.getNumOrdinaryParameters();
  }

  /**
   * Fast path for "simple" positional-only calls (no {@code *args}, {@code **kwargs}, cells, or
   * keyword-only parameters). Builds the args array directly without allocating a {@link
   * TruffleArgumentProcessor} or its intermediate locals array, saving two allocations per call.
   *
   * <p>Handles the common case where the caller passes exactly {@code getNumOrdinaryParameters()}
   * arguments (no defaults needed) and the less-common case where trailing defaults fill in
   * omitted arguments. Dynamic type checking must be disabled; callers are responsible for
   * checking {@code StarlarkSemantics.EXPERIMENTAL_STARLARK_DYNAMIC_TYPE_CHECKING} and falling
   * back to {@link #preparePositionalArgs} when it is enabled.
   *
   * @return args array ready for {@code CallTarget.call()}: {@code [callee, thread, locals...]}
   * @throws EvalException if too many positional args or a required argument is missing
   */
  public Object[] preparePositionalArgsDirect(StarlarkThread thread, Object[] positional)
      throws EvalException {
    int numOrdinaryParams = rfn.getNumOrdinaryParameters();

    // Validate count: too many positional arguments?
    if (positional.length > numOrdinaryParams) {
      throw Starlark.errorf(
          "%s() accepts no more than %d positional argument%s but got %d",
          getName(),
          numOrdinaryParams,
          numOrdinaryParams == 1 ? "" : "s",
          positional.length);
    }

    // Validate defaults for missing trailing parameters.
    if (positional.length < numOrdinaryParams) {
      int numDefaults = defaultValues.size();
      int firstDefault = numOrdinaryParams - numDefaults;
      for (int i = positional.length; i < numOrdinaryParams; i++) {
        int dfltIndex = i - firstDefault;
        if (dfltIndex < 0 || defaultValues.get(dfltIndex) == StarlarkFunction.MANDATORY) {
          throw Starlark.errorf(
              "%s() missing %d required positional argument%s: %s",
              getName(),
              1,
              "",
              rfn.getParameterNames().get(i));
        }
      }
    }

    // Build the args array directly: [callee, thread, param0, ..., paramN-1, null (body locals)]
    int totalLocals = rfn.getLocals().size();
    Object[] args = new Object[totalLocals + 2];
    args[0] = this;
    args[1] = thread;
    System.arraycopy(positional, 0, args, 2, positional.length);

    // Apply defaults for missing trailing params.
    if (positional.length < numOrdinaryParams) {
      int numDefaults = defaultValues.size();
      int firstDefault = numOrdinaryParams - numDefaults;
      for (int i = positional.length; i < numOrdinaryParams; i++) {
        args[i + 2] = defaultValues.get(i - firstDefault);
      }
    }

    return args;
  }

  /**
   * Fast path for "simple" single-argument positional calls. Equivalent to {@link
   * #preparePositionalArgsDirect} but specialized for exactly one caller-supplied argument,
   * eliminating the need for the caller to allocate an intermediate {@code Object[1]} array.
   *
   * <p>Only valid for "simple" functions ({@link #isSimplePositionalFunction()} returns {@code
   * true}); dynamic type checking must be disabled; callers are responsible for checking these
   * preconditions and falling back to {@link #preparePositionalArgs} when they do not hold.
   *
   * @return args array ready for {@code CallTarget.call()}: {@code [callee, thread, locals...]}
   * @throws EvalException if too many positional args or a required argument is missing
   */
  public Object[] preparePositionalArgsDirect1(StarlarkThread thread, Object arg0)
      throws EvalException {
    int numOrdinaryParams = rfn.getNumOrdinaryParameters();

    // Validate count: too many positional arguments?
    if (1 > numOrdinaryParams) {
      throw Starlark.errorf(
          "%s() accepts no more than %d positional argument%s but got %d",
          getName(),
          numOrdinaryParams,
          numOrdinaryParams == 1 ? "" : "s",
          1);
    }

    // Validate defaults for missing trailing parameters (params 1..numOrdinaryParams-1).
    if (1 < numOrdinaryParams) {
      int numDefaults = defaultValues.size();
      int firstDefault = numOrdinaryParams - numDefaults;
      for (int i = 1; i < numOrdinaryParams; i++) {
        int dfltIndex = i - firstDefault;
        if (dfltIndex < 0 || defaultValues.get(dfltIndex) == StarlarkFunction.MANDATORY) {
          throw Starlark.errorf(
              "%s() missing 1 required positional argument: %s",
              getName(), rfn.getParameterNames().get(i));
        }
      }
    }

    // Build the args array directly: [callee, thread, arg0, param1_default, ..., null (body locals)]
    int totalLocals = rfn.getLocals().size();
    Object[] args = new Object[totalLocals + 2];
    args[0] = this;
    args[1] = thread;
    args[2] = arg0;

    // Apply defaults for missing trailing params (params 1..numOrdinaryParams-1).
    if (1 < numOrdinaryParams) {
      int numDefaults = defaultValues.size();
      int firstDefault = numOrdinaryParams - numDefaults;
      for (int i = 1; i < numOrdinaryParams; i++) {
        args[i + 2] = defaultValues.get(i - firstDefault);
      }
    }

    return args;
  }

  /**
   * Prepares the Truffle call arguments for a positional-only call without executing the function.
   *
   * <p>Validates argument counts, applies defaults, and spills cells. Used by {@link
   * net.starlark.java.eval.truffle.nodes.expr.DispatchNode} for the inline-cached fast path, where
   * argument preparation happens in a {@code @TruffleBoundary} helper but the actual
   * {@code CallTarget.call()} happens in compiled Truffle code.
   *
   * @return args array ready for {@code CallTarget.call()}: {@code [callee, thread, locals...]}
   */
  public Object[] preparePositionalArgs(StarlarkThread thread, Object[] positional)
      throws EvalException, InterruptedException {
    TruffleArgumentProcessor proc = new TruffleArgumentProcessor(this, thread);
    for (Object arg : positional) {
      proc.addPositionalArg(arg);
    }
    return proc.prepareCallArgs(thread);
  }

  public void export(StarlarkThread thread, String name) {
    if (!token.getOwner().equals(
            net.starlark.java.eval.StarlarkTruffleAccessor.getOwner(thread))) {
      return;
    }
    if (token.isGlobal()) {
      return;
    }
    token = token.exportAs(name);
  }

  @Override
  public void repr(Printer printer, StarlarkSemantics semantics) {
    Object clientData = module.getClientData();
    printer.append("<function " + getName());
    if (clientData != null) {
      printer.append(" from " + clientData);
    }
    printer.append(">");
  }

  @Override
  public String toString() {
    StringBuilder out = new StringBuilder();
    out.append(getName()).append('(');
    String sep = "";
    for (String param : rfn.getParameterNames()) {
      out.append(sep).append(param);
      sep = ", ";
    }
    out.append(')');
    return out.toString();
  }

  @Override
  public boolean isImmutable() {
    return true;
  }

  @Override
  public int hashCode() {
    return token.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (!(obj instanceof StarlarkTruffleFunction other)) {
      return false;
    }
    return token.equals(other.token);
  }

  public SymbolGenerator.Symbol<?> getToken() {
    return token;
  }

  /**
   * Returns a {@link StarlarkFunction} with the same data as this Truffle function. The result is
   * cached so that repeated conversions return the same object. This is used to bridge with Java API
   * methods that expect {@code StarlarkFunction} parameters (e.g., rule(implementation=...)).
   */
  @Override
  public StarlarkFunction toStarlarkFunction() {
    StarlarkFunction cached = cachedStarlarkFunction;
    if (cached == null) {
      // Convert Cell[] to Tuple for the StarlarkFunction constructor.
      Object[] freevarArray = new Object[freevars.length];
      System.arraycopy(freevars, 0, freevarArray, 0, freevars.length);
      Tuple freevarTuple =
          net.starlark.java.eval.StarlarkTruffleAccessor.wrapTuple(freevarArray);
      cached =
          net.starlark.java.eval.StarlarkTruffleAccessor.createStarlarkFunction(
              rfn, module, globalIndex, defaultValues, freevarTuple, token, this);
      cachedStarlarkFunction = cached;
    }
    return cached;
  }
}
