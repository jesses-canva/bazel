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
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import javax.annotation.Nullable;
import net.starlark.java.syntax.Location;

/** Base class for all Starlark statement nodes in the Truffle AST. */
public abstract class StarlarkStatementNode extends Node {

    /**
     * The source location of the syntax node this Truffle node was translated from. Used for error
     * reporting and stack traces.
     */
    @CompilationFinal @Nullable private Location sourceLocation;

    /** Executes this statement for its side effects. */
    public abstract void executeVoid(VirtualFrame frame);

    /** Sets the source location from the corresponding syntax AST node. */
    public void setSourceLocation(Location location) {
        this.sourceLocation = location;
    }

    /** Returns the source location, or null if not set. */
    @Nullable
    public Location getSourceLocation() {
        return sourceLocation;
    }
}
