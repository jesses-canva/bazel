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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import javax.annotation.Nullable;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkFunction;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.Tuple;
import net.starlark.java.spelling.SpellChecker;
import net.starlark.java.syntax.Resolver;

/**
 * Argument processor for Truffle-based Starlark function calls.
 *
 * <p>This is analogous to StarlarkFunction.ArgumentProcessor but dispatches the actual execution
 * through the Truffle CallTarget instead of the tree-walking interpreter.
 */
final class TruffleArgumentProcessor extends StarlarkCallable.ArgumentProcessor {

  private final StarlarkTruffleFunction owner;
  private int numNonSurplusPositionalArgs;
  private final Object[] locals;
  @Nullable private List<String> unexpectedNamedArgs;
  @Nullable private ArrayList<Object> varArgs;
  @Nullable private LinkedHashMap<String, Object> kwargs;

  TruffleArgumentProcessor(StarlarkTruffleFunction owner, StarlarkThread thread) {
    super(thread);
    this.owner = owner;
    Resolver.Function rfn = owner.getResolvedFunction();
    this.locals = new Object[rfn.getLocals().size()];
    this.numNonSurplusPositionalArgs = 0;
  }

  @Override
  public StarlarkCallable getCallable() {
    return owner;
  }

  private int getNumOrdinaryParameters() {
    return owner.getResolvedFunction().getNumOrdinaryParameters();
  }

  private int getNumNonResidualParameters() {
    return owner.getResolvedFunction().getNumNonResidualParameters();
  }

  private int getKwargsIndex() {
    Resolver.Function rfn = owner.getResolvedFunction();
    return rfn.hasKwargs() ? rfn.getParameters().size() - 1 : -1;
  }

  private int getVarArgsIndex() {
    Resolver.Function rfn = owner.getResolvedFunction();
    if (rfn.hasVarargs()) {
      int index = rfn.getParameters().size();
      return rfn.hasKwargs() ? index - 2 : index - 1;
    }
    return -1;
  }

  @Override
  public void addPositionalArg(Object value) throws EvalException {
    if (numNonSurplusPositionalArgs < getNumOrdinaryParameters()) {
      locals[numNonSurplusPositionalArgs++] = value;
    } else if (owner.getResolvedFunction().hasVarargs()) {
      if (varArgs == null) {
        varArgs = new ArrayList<>();
      }
      varArgs.add(value);
    } else {
      numNonSurplusPositionalArgs++;
    }
  }

  @Override
  public void addNamedArg(String name, Object value) throws EvalException {
    ImmutableList<String> paramNames = owner.getResolvedFunction().getParameterNames();
    int formalIndex = paramNames.indexOf(name);
    if (0 <= formalIndex && formalIndex < getNumNonResidualParameters()) {
      if (locals[formalIndex] != null) {
        pushCallableAndThrow(
            Starlark.errorf(
                "%s() got multiple values for parameter '%s'", owner.getName(), name));
      }
      locals[formalIndex] = value;
    } else {
      if (owner.getResolvedFunction().hasKwargs()) {
        if (kwargs == null) {
          kwargs = Maps.newLinkedHashMapWithExpectedSize(1);
        }
        Object oldValue = kwargs.put(name, value);
        if (oldValue != null) {
          pushCallableAndThrow(
              Starlark.errorf(
                  "%s() got multiple values for parameter '%s'", owner.getName(), name));
        }
      } else {
        if (unexpectedNamedArgs == null) {
          unexpectedNamedArgs = new ArrayList<>();
        }
        unexpectedNamedArgs.add(name);
      }
    }
  }

  @Override
  public Object call(StarlarkThread thread) throws EvalException, InterruptedException {
    int numOrdinaryParams = getNumOrdinaryParameters();
    if (numNonSurplusPositionalArgs > numOrdinaryParams) {
      if (numOrdinaryParams > 0) {
        throw Starlark.errorf(
            "%s() accepts no more than %d positional argument%s but got %d",
            owner.getName(),
            numOrdinaryParams,
            numOrdinaryParams == 1 ? "" : "s",
            numNonSurplusPositionalArgs);
      } else {
        throw Starlark.errorf(
            "%s() does not accept positional arguments, but got %d",
            owner.getName(), numNonSurplusPositionalArgs);
      }
    }

    // Check unexpected named args
    if (unexpectedNamedArgs != null) {
      throw Starlark.errorf(
          "%s() got unexpected keyword argument%s: %s%s",
          owner.getName(),
          unexpectedNamedArgs.size() == 1 ? "" : "s",
          Joiner.on(", ").join(unexpectedNamedArgs),
          unexpectedNamedArgs.size() == 1
              ? SpellChecker.didYouMean(
                  unexpectedNamedArgs.get(0),
                  owner.getResolvedFunction().getParameterNames()
                      .subList(0, getNumNonResidualParameters()))
              : "");
    }

    Resolver.Function rfn = owner.getResolvedFunction();

    // Bind *args
    if (rfn.hasVarargs()) {
      locals[getVarArgsIndex()] =
          varArgs == null
              ? Tuple.empty()
              : varArgs.size() == 1
                  ? Tuple.of(varArgs.getFirst())
                  : StarlarkTruffleAccessor.wrapTuple(varArgs.toArray());
    }

    // Bind **kwargs
    if (rfn.hasKwargs()) {
      locals[getKwargsIndex()] =
          kwargs == null
              ? Dict.of(thread.mutability())
              : StarlarkTruffleAccessor.wrapDict(thread.mutability(), kwargs);
    }

    // Apply defaults
    applyDefaults();

    // Spill to cells
    for (int index : rfn.getCellIndices()) {
      locals[index] = new StarlarkFunction.Cell(locals[index]);
    }

    // Check for recursion (unless explicitly allowed, e.g. in non-Bazel contexts).
    if (!StarlarkTruffleAccessor.isRecursionAllowed(thread)
        && StarlarkTruffleAccessor.isRecursiveCallByCode(thread, rfn)) {
      throw Starlark.errorf("function '%s' called recursively", owner.getName());
    }

    // Execute via Truffle CallTarget
    // Calling convention: [callee, thread, params...]
    Object[] args = new Object[locals.length + 2];
    args[0] = owner;
    args[1] = thread;
    System.arraycopy(locals, 0, args, 2, locals.length);

    try {
      return owner.getCallTarget().call(args);
    } catch (RuntimeException e) {
      if (e.getCause() instanceof EvalException) {
        throw (EvalException) e.getCause();
      }
      throw e;
    }
  }

  private void applyDefaults() throws EvalException {
    int numParams = getNumNonResidualParameters();
    Tuple defaultValues = owner.getDefaultValues();
    int firstDefault = numParams - defaultValues.size();
    List<String> missingPositional = null;
    List<String> missingKwonly = null;

    for (int i = numNonSurplusPositionalArgs; i < numParams; i++) {
      if (locals[i] != null) {
        continue;
      }
      if (i >= firstDefault) {
        Object dflt = defaultValues.get(i - firstDefault);
        if (dflt != StarlarkFunction.MANDATORY) {
          locals[i] = dflt;
          continue;
        }
      }
      if (i < getNumOrdinaryParameters()) {
        if (missingPositional == null) {
          missingPositional = new ArrayList<>();
        }
        missingPositional.add(owner.getResolvedFunction().getParameterNames().get(i));
      } else {
        if (missingKwonly == null) {
          missingKwonly = new ArrayList<>();
        }
        missingKwonly.add(owner.getResolvedFunction().getParameterNames().get(i));
      }
    }

    if (missingPositional != null) {
      throw Starlark.errorf(
          "%s() missing %d required positional argument%s: %s",
          owner.getName(),
          missingPositional.size(),
          missingPositional.size() == 1 ? "" : "s",
          Joiner.on(", ").join(missingPositional));
    }
    if (missingKwonly != null) {
      throw Starlark.errorf(
          "%s() missing %d required keyword-only argument%s: %s",
          owner.getName(),
          missingKwonly.size(),
          missingKwonly.size() == 1 ? "" : "s",
          Joiner.on(", ").join(missingKwonly));
    }
  }
}
