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

import com.oracle.truffle.api.Assumption;
import com.oracle.truffle.api.utilities.CyclicAssumption;
import net.starlark.java.eval.Mutability;

/**
 * A Truffle Assumption wrapping a {@link Mutability} object.
 *
 * <p>When the mutability is not frozen, JIT-compiled code can assume values are mutable and skip
 * frozen checks. When {@link Mutability#freeze()} is called, the assumption is invalidated, causing
 * compiled code to deoptimize and re-enter the interpreter where the frozen state is now checked.
 */
public final class FreezeAssumption {

  private final CyclicAssumption notFrozen;

  public FreezeAssumption() {
    this.notFrozen = new CyclicAssumption("not frozen");
  }

  /**
   * Creates a FreezeAssumption and registers it as the freeze listener on the given Mutability. When
   * the Mutability is frozen, this assumption will be automatically invalidated.
   */
  public static FreezeAssumption forMutability(Mutability mutability) {
    FreezeAssumption assumption = new FreezeAssumption();
    mutability.setFreezeListener(assumption::freeze);
    return assumption;
  }

  /** Returns the current assumption that the value is not frozen. */
  public Assumption getAssumption() {
    return notFrozen.getAssumption();
  }

  /**
   * Invalidates the assumption, indicating that the associated Mutability has been frozen. This
   * causes any JIT-compiled code that depended on the value being mutable to deoptimize.
   */
  public void freeze() {
    notFrozen.invalidate("frozen");
  }
}
