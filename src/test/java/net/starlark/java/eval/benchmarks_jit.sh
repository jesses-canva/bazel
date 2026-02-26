#!/bin/bash
# Wrapper script that runs Starlark benchmarks with Truffle JIT compilation enabled.
#
# The Truffle runtime's HotSpotTruffleRuntimeAccess requires the Truffle jars
# to be loaded as named modules (on --module-path) rather than from the
# classpath, so that JVMCI packages can be exported to the Truffle module.
#
# Bazel's java_binary puts ALL deps on the classpath, which means Truffle
# classes end up in the unnamed module and cannot access JVMCI.  This wrapper
# bypasses the Bazel launcher: it constructs the classpath from runfiles
# (EXCLUDING Truffle/GraalVM jars), places those jars on --module-path instead,
# and invokes the JDK directly.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# Use this binary's own runfiles (contains all data deps including the Graal compiler jar).
# When invoked as BenchmarksJIT, its runfiles are at $SCRIPT_DIR/BenchmarksJIT.runfiles/.
BINARY_NAME="$(basename "$0")"
RUNFILES="$SCRIPT_DIR/${BINARY_NAME}.runfiles"
JAVABIN="$RUNFILES/rules_java++toolchains+remotejdk25_linux/bin/java"
MAVEN="$RUNFILES/rules_jvm_external++maven+maven/org/graalvm"

if [[ ! -x "$JAVABIN" ]]; then
  echo "ERROR: Cannot find java at $JAVABIN" >&2
  exit 1
fi

# The Graal compiler jar must go on --upgrade-module-path because Zulu JDK 25 ships
# a built-in (empty) jdk.graal.compiler module.  --module-path is ignored for modules
# already in the JDK; --upgrade-module-path replaces them.
COMPILER="$RUNFILES/rules_jvm_external++maven+maven/org/graalvm/compiler/compiler/25.0.2/processed_compiler-25.0.2.jar"

# Build the module path: all Truffle/GraalVM SDK jars as named modules.
MP="$MAVEN/truffle/truffle-compiler/25.0.2/processed_truffle-compiler-25.0.2.jar"
MP="$MP:$MAVEN/truffle/truffle-runtime/25.0.2/processed_truffle-runtime-25.0.2.jar"
MP="$MP:$MAVEN/truffle/truffle-api/25.0.2/processed_truffle-api-25.0.2.jar"
MP="$MP:$MAVEN/sdk/graal-sdk/25.0.2/processed_graal-sdk-25.0.2.jar"
MP="$MP:$MAVEN/sdk/collections/25.0.2/processed_collections-25.0.2.jar"
MP="$MP:$MAVEN/sdk/word/25.0.2/processed_word-25.0.2.jar"
MP="$MP:$MAVEN/sdk/nativeimage/25.0.2/processed_nativeimage-25.0.2.jar"
MP="$MP:$MAVEN/sdk/jniutils/25.0.2/processed_jniutils-25.0.2.jar"
MP="$MP:$MAVEN/polyglot/polyglot/25.0.2/processed_polyglot-25.0.2.jar"

# Build the classpath from runfiles, EXCLUDING Truffle/GraalVM jars.
# These stay on classpath: our code, guava, jsr305, json, etc.
CP=""
MAIN_DIR="$RUNFILES/_main"
add_cp() {
  if [[ -z "$CP" ]]; then CP="$1"; else CP="$CP:$1"; fi
}
for jar in "$MAIN_DIR"/src/test/java/net/starlark/java/eval/Benchmarks.jar \
           "$MAIN_DIR"/src/test/java/net/starlark/java/eval/libBenchmarks_lib.jar \
           "$MAIN_DIR"/src/main/java/net/starlark/java/eval/truffle/libtruffle.jar \
           "$MAIN_DIR"/src/main/java/net/starlark/java/eval/libeval.jar \
           "$MAIN_DIR"/src/main/java/net/starlark/java/annot/libannot_sans_processor.jar \
           "$MAIN_DIR"/src/main/java/net/starlark/java/syntax/libsyntax.jar \
           "$MAIN_DIR"/src/main/java/net/starlark/java/spelling/libspelling.jar \
           "$MAIN_DIR"/src/main/java/net/starlark/java/lib/json/libjson.jar \
           "$MAIN_DIR"/src/main/java/net/starlark/java/lib/liblib.jar \
           "$MAIN_DIR"/src/main/java/net/starlark/java/eval/libcpu_profiler_native_support.jar; do
  if [[ -f "$jar" ]]; then
    add_cp "$jar"
  fi
done
# Add non-GraalVM third-party jars
for jar in "$RUNFILES"/rules_jvm_external++maven+maven/com/google/guava/guava/*/processed_guava-*.jar \
           "$RUNFILES"/rules_jvm_external++maven+maven/com/google/guava/failureaccess/*/processed_failureaccess-*.jar \
           "$RUNFILES"/rules_jvm_external++maven+maven/com/google/guava/listenablefuture/*/processed_listenablefuture-*.jar \
           "$RUNFILES"/rules_jvm_external++maven+maven/com/google/errorprone/error_prone_annotations/*/processed_error_prone_annotations-*.jar \
           "$RUNFILES"/rules_jvm_external++maven+maven/com/google/code/findbugs/jsr305/*/processed_jsr305-*.jar \
           "$RUNFILES"/rules_jvm_external++maven+maven/com/google/j2objc/j2objc-annotations/*/processed_j2objc-annotations-*.jar \
           "$RUNFILES"/rules_jvm_external++maven+maven/org/jspecify/jspecify/*/processed_jspecify-*.jar \
           "$RUNFILES"/rules_jvm_external++maven+maven/com/github/stephenc/jcip/jcip-annotations/*/processed_jcip-annotations-*.jar \
           "$RUNFILES"/rules_jvm_external++maven+maven/com/google/auto/value/auto-value-annotations/*/processed_auto-value-annotations-*.jar \
           "$RUNFILES"/rules_jvm_external++maven+maven/com/ryanharter/auto/value/auto-value-gson-runtime/*/processed_auto-value-gson-runtime-*.jar \
           "$RUNFILES"/rules_jvm_external++maven+maven/com/google/code/gson/gson/*/processed_gson-*.jar \
           "$RUNFILES"/rules_jvm_external++maven+maven/io/sweers/autotransient/autotransient/*/processed_autotransient-*.jar \
           "$RUNFILES"/rules_jvm_external++maven+maven/com/google/flogger/flogger/*/processed_flogger-*.jar \
           "$RUNFILES"/rules_jvm_external++maven+maven/com/google/flogger/flogger-system-backend/*/processed_flogger-system-backend-*.jar \
           "$RUNFILES"/rules_jvm_external++maven+maven/com/google/flogger/google-extensions/*/processed_google-extensions-*.jar \
           "$RUNFILES"/rules_jvm_external++maven+maven/org/checkerframework/checker-compat-qual/*/processed_checker-compat-qual-*.jar; do
  if [[ -f "$jar" ]]; then
    add_cp "$jar"
  fi
done

exec "$JAVABIN" \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+EnableJVMCI \
  --upgrade-module-path="$COMPILER" \
  --module-path="$MP" \
  --add-modules=jdk.graal.compiler,org.graalvm.truffle,org.graalvm.truffle.runtime,org.graalvm.truffle.compiler,org.graalvm.sdk,org.graalvm.polyglot,org.graalvm.collections,org.graalvm.word,org.graalvm.nativeimage,org.graalvm.jniutils \
  --enable-native-access=org.graalvm.truffle,org.graalvm.truffle.runtime,ALL-UNNAMED \
  -Dfile.encoding=UTF8 \
  -Dpolyglot.engine.TraceCompilation=true \
  -Dpolyglot.engine.CompileImmediately=true \
  -classpath "$CP" \
  net.starlark.java.eval.Benchmarks \
  "$@"
