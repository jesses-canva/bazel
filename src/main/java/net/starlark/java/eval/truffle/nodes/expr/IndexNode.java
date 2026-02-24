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
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;
import net.starlark.java.syntax.Location;

/**
 * Index expression node: evaluates {@code object[key]}.
 *
 * <p>Includes an inline fast path for the most common case: indexing a {@link Sequence} (list,
 * tuple) with a {@link StarlarkInt} key. This avoids the {@link TruffleBoundary} and the
 * indirection through {@link EvalUtils#index} for this hot pattern.
 */
public final class IndexNode extends StarlarkExpressionNode {

  @Child private StarlarkExpressionNode object;
  @Child private StarlarkExpressionNode key;
  /** Location of the '[' token, for accurate error reporting in stack traces. */
  @CompilationFinal @Nullable private final Location lbracketLocation;

  public IndexNode(StarlarkExpressionNode object, StarlarkExpressionNode key,
      @Nullable Location lbracketLocation) {
    this.object = object;
    this.key = key;
    this.lbracketLocation = lbracketLocation;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    Object obj = object.executeGeneric(frame);
    Object k = key.executeGeneric(frame);
    StarlarkThread thread = (StarlarkThread) frame.getArguments()[1];

    // Fast path: sequence (list / tuple) indexed by an integer.
    if (obj instanceof Sequence<?> seq && k instanceof StarlarkInt ki) {
      return seqIndex(seq, ki, thread, lbracketLocation);
    }

    return doIndex(thread, obj, k, lbracketLocation);
  }

  /**
   * Indexes a {@link Sequence} by an integer key. The hot path (in-bounds access) requires no
   * {@link TruffleBoundary}. On error, the bracket location is set before rethrowing so that stack
   * traces point to the {@code [} token, matching the generic path's behaviour.
   */
  private static Object seqIndex(
      Sequence<?> seq, StarlarkInt ki, StarlarkThread thread, Location lbracketLoc) {
    try {
      int rawIndex = ki.toInt("sequence index");
      int index = StarlarkTruffleAccessor.getSequenceIndex(rawIndex, seq.size());
      return seq.get(index);
    } catch (EvalException e) {
      StarlarkTruffleAccessor.setErrorLocation(thread, lbracketLoc);
      throw new RuntimeException(e);
    }
  }

  @TruffleBoundary
  private static Object doIndex(
      StarlarkThread thread, Object object, Object key, Location lbracketLoc) {
    try {
      return StarlarkTruffleAccessor.index(thread, object, key);
    } catch (EvalException e) {
      StarlarkTruffleAccessor.setErrorLocation(thread, lbracketLoc);
      throw new RuntimeException(e);
    }
  }
}
