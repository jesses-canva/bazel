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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.frame.VirtualFrame;
import javax.annotation.Nullable;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkTruffleAccessor;
import net.starlark.java.eval.truffle.nodes.StarlarkExpressionNode;

/**
 * Attribute access expression node: evaluates {@code object.name}.
 *
 * <p>Uses a monomorphic inline cache keyed by the receiver's class. On the first call the cache is
 * populated. Subsequent calls with the same receiver class skip the {@link
 * net.starlark.java.eval.CallUtils.BuiltinManager} map lookups and use the cached {@link
 * net.starlark.java.eval.MethodDescriptor} directly. A different receiver class evicts the cache
 * and falls back to the full {@link Starlark#getattr} path.
 */
public final class DotNode extends StarlarkExpressionNode {

  @Child private StarlarkExpressionNode object;
  @CompilationFinal private final String name;

  /**
   * Cached receiver class. {@code null} while uninitialized. On a cache miss (different class),
   * set to a sentinel {@code Object.class} to signal "megamorphic" and stop caching.
   */
  @CompilationFinal @Nullable private Class<?> cachedClass;

  /**
   * Cached {@link net.starlark.java.eval.MethodDescriptor} (opaque {@code Object}) for {@code
   * name} on {@link #cachedClass}. {@code null} means the attribute is not a {@code
   * @StarlarkMethod} member (e.g. a {@link net.starlark.java.eval.Structure} field).
   */
  @CompilationFinal @Nullable private Object cachedDescriptor;

  public DotNode(StarlarkExpressionNode object, String name) {
    this.object = object;
    this.name = name;
  }

  @Override
  public Object executeGeneric(VirtualFrame frame) {
    Object obj = object.executeGeneric(frame);
    StarlarkThread thread = (StarlarkThread) frame.getArguments()[1];
    Class<?> receiverClass = obj.getClass();

    if (cachedClass == null) {
      // First call: populate the monomorphic cache.
      CompilerDirectives.transferToInterpreterAndInvalidate();
      cachedClass = receiverClass;
      cachedDescriptor = StarlarkTruffleAccessor.lookupAnnotatedMethod(thread, receiverClass, name);
      // Fall through to full getattr for this first invocation.
    } else if (receiverClass == cachedClass && cachedDescriptor != null) {
      // Monomorphic fast path: known class with a known @StarlarkMethod descriptor.
      return getattrCached(thread, obj, cachedDescriptor);
    }
    // Megamorphic (different class) or no @StarlarkMethod for this name: full lookup.
    return doGetattr(thread, obj, name);
  }

  /**
   * Fast path: evaluates the attribute using the pre-looked-up descriptor, avoiding the
   * BuiltinManager map lookups that the full {@link Starlark#getattr} would perform.
   */
  @TruffleBoundary
  private static Object getattrCached(StarlarkThread thread, Object obj, Object descriptor) {
    try {
      return StarlarkTruffleAccessor.getattrFromCachedDescriptor(
          thread.mutability(), thread.getSemantics(), obj, descriptor);
    } catch (EvalException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }

  @TruffleBoundary
  private static Object doGetattr(StarlarkThread thread, Object object, String name) {
    try {
      return Starlark.getattr(
          thread.mutability(), thread.getSemantics(), object, name, null);
    } catch (EvalException | InterruptedException e) {
      throw new RuntimeException(e);
    }
  }
}
