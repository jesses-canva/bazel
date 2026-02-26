load("@rules_java//java:defs.bzl", "java_binary", "java_library", "java_test")
load("@rules_shell//shell:sh_binary.bzl", "sh_binary")

package(default_testonly = 1)

filegroup(
    name = "srcs",
    testonly = 0,
    srcs = glob(["**"]),
    visibility = ["//src/test/java/net/starlark/java:__pkg__"],
)

# EvalTests test the Starlark interpreter.
java_test(
    name = "EvalTests",
    srcs = [
        "DynamicTypeCheckTest.java",
        "EvalTests.java",  # (suite)
        "EvalUtilsTest.java",
        "EvaluationTest.java",
        "EvaluationTestCase.java",
        "Examples.java",
        "FunctionTest.java",
        "ImmutableKeyTrackingDictTest.java",
        "MethodLibraryTest.java",
        "MutabilityTest.java",
        "PrinterTest.java",
        "StarlarkAnnotationsTest.java",
        "StarlarkClassTest.java",
        "StarlarkEvaluationTest.java",
        "StarlarkFlagGuardingTest.java",
        "StarlarkListTest.java",
        "StarlarkMutableTest.java",
        "StarlarkThreadDebuggingTest.java",
        "StarlarkThreadTest.java",
        "StaticTypeCheckTest.java",
        "SymbolGeneratorTest.java",
    ],
    jvm_flags = [
        "-Dfile.encoding=UTF8",
        # StarlarkListTest.concat_failsCleanlyOnOverflow() needs at least 4GB of max heap
        "-Xmx4096m",
    ],
    runtime_deps = [
        "//src/main/java/net/starlark/java/eval/truffle",
    ],
    deps = [
        "//src/main/java/net/starlark/java/annot",
        "//src/main/java/net/starlark/java/eval",
        "//src/main/java/net/starlark/java/syntax",
        "//src/test/java/net/starlark/java/syntax:TestUtils",
        "//third_party:guava",
        "//third_party:guava-testlib",
        "//third_party:jsr305",
        "//third_party:junit4",
        "//third_party:truth",
    ],
)

# Script-based tests of the Starlark interpreter.
java_library(
    name = "ScriptTest_lib",
    srcs = ["ScriptTest.java"],
    data = glob(["testdata/*.star"]),
    deps = [
        "//src/main/java/net/starlark/java/annot",
        "//src/main/java/net/starlark/java/eval",
        "//src/main/java/net/starlark/java/lib/json",
        "//src/main/java/net/starlark/java/syntax",
        "//third_party:error_prone_annotations",
        "//third_party:guava",
    ],
)

java_test(
    name = "ScriptTest",
    main_class = "net.starlark.java.eval.ScriptTest",
    use_testrunner = False,
    runtime_deps = [
        ":ScriptTest_lib",
        "//src/main/java/net/starlark/java/eval/truffle",
    ],
)

java_test(
    name = "ScriptTest_Latin1",
    jvm_flags = ["-Dnet.starlark.java.eval.ScriptTest.utf8ByteStrings=true"],
    main_class = "net.starlark.java.eval.ScriptTest",
    use_testrunner = False,
    runtime_deps = [
        ":ScriptTest_lib",
        "//src/main/java/net/starlark/java/eval/truffle",
    ],
)

# Script-based benchmarks of the Starlark interpreter.
# To enable Truffle JIT compilation, run with:
#   bazel run :Benchmarks -- --jvm_flag=--module-path=<truffle-jars> \
#     --jvm_flag=--add-modules=org.graalvm.truffle.runtime,org.graalvm.truffle.compiler \
#     --jvm_flag=-XX:+UnlockExperimentalVMOptions --jvm_flag=-XX:+EnableJVMCI
# Or use the :BenchmarksJIT target which configures module-path automatically.
java_binary(
    name = "Benchmarks",
    data = glob(["testdata/bench_*.star"]),
    jvm_flags = [
        "-Dfile.encoding=UTF8",
        "--enable-native-access=ALL-UNNAMED",
    ],
    main_class = "net.starlark.java.eval.Benchmarks",
    runtime_deps = [
        ":Benchmarks_lib",
        "//src/main/java/net/starlark/java/eval/truffle",
    ],
)

# Benchmark binary with Truffle JIT compilation enabled.
# The Truffle runtime must be loaded as a named module for JVMCI access;
# this target configures --module-path and --add-modules accordingly.
sh_binary(
    name = "BenchmarksJIT",
    srcs = ["benchmarks_jit.sh"],
    data = [
        ":Benchmarks",
        "@maven//:org_graalvm_compiler_compiler",
        "@maven//:org_graalvm_polyglot_polyglot",
        "@maven//:org_graalvm_sdk_collections",
        "@maven//:org_graalvm_sdk_graal_sdk",
        "@maven//:org_graalvm_sdk_jniutils",
        "@maven//:org_graalvm_sdk_nativeimage",
        "@maven//:org_graalvm_sdk_word",
        "@maven//:org_graalvm_truffle_truffle_api",
        "@maven//:org_graalvm_truffle_truffle_compiler",
        "@maven//:org_graalvm_truffle_truffle_runtime",
    ],
)

java_library(
    name = "Benchmarks_lib",
    srcs = ["Benchmarks.java"],
    data = glob(["testdata/bench_*.star"]),
    deps = [
        "//src/main/java/net/starlark/java/annot",
        "//src/main/java/net/starlark/java/eval",
        "//src/main/java/net/starlark/java/lib/json",
        "//src/main/java/net/starlark/java/syntax",
        "//third_party:guava",
    ],
)
