load("@rules_java//java:defs.bzl", "java_binary", "java_library", "java_test")

package(default_testonly = 1)

filegroup(
    name = "srcs",
    testonly = 0,
    srcs = glob(["**"]),
    visibility = ["//src/test/java/net/starlark/java:__pkg__"],
)

# EvalTests test the Starlark interpreter.
# These JUnit tests exercise internal APIs (StarlarkFunction casts, debug hooks, stack traces)
# that are specific to the tree-walking interpreter. Without Truffle on the classpath, the
# isTruffleAvailable() check in Starlark.java causes graceful fallback to tree-walking.
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

# Script-based tests using the Truffle-based interpreter (the default).
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

# Script-based tests explicitly using the Truffle-based interpreter.
# Kept for backward compatibility; equivalent to ScriptTest with the new default.
java_test(
    name = "ScriptTest_Truffle",
    jvm_flags = ["-Dnet.starlark.java.eval.ScriptTest.useTruffleInterpreter=true"],
    main_class = "net.starlark.java.eval.ScriptTest",
    use_testrunner = False,
    runtime_deps = [
        ":ScriptTest_lib",
        "//src/main/java/net/starlark/java/eval/truffle",
    ],
)

# Script-based tests explicitly using the tree-walking interpreter.
java_test(
    name = "ScriptTest_TreeWalking",
    jvm_flags = ["-Dnet.starlark.java.eval.ScriptTest.useTruffleInterpreter=false"],
    main_class = "net.starlark.java.eval.ScriptTest",
    use_testrunner = False,
    runtime_deps = [":ScriptTest_lib"],
)

# Script-based benchmarks of the Starlark interpreter.
# Uses Truffle by default (with Truffle on the classpath).
java_binary(
    name = "Benchmarks",
    data = glob(["testdata/bench_*.star"]),
    jvm_flags = ["-Dfile.encoding=UTF8"],
    main_class = "net.starlark.java.eval.Benchmarks",
    runtime_deps = [
        ":Benchmarks_lib",
        "//src/main/java/net/starlark/java/eval/truffle",
    ],
)

# Script-based benchmarks explicitly using the Truffle-based interpreter.
# Kept for backward compatibility; equivalent to Benchmarks with the new default.
java_binary(
    name = "Benchmarks_Truffle",
    data = glob(["testdata/bench_*.star"]),
    jvm_flags = [
        "-Dfile.encoding=UTF8",
        "-Dnet.starlark.java.eval.Benchmarks.useTruffleInterpreter=true",
    ],
    main_class = "net.starlark.java.eval.Benchmarks",
    runtime_deps = [
        ":Benchmarks_lib",
        "//src/main/java/net/starlark/java/eval/truffle",
    ],
)

# Script-based benchmarks explicitly using the tree-walking interpreter.
java_binary(
    name = "Benchmarks_TreeWalking",
    data = glob(["testdata/bench_*.star"]),
    jvm_flags = [
        "-Dfile.encoding=UTF8",
        "-Dnet.starlark.java.eval.Benchmarks.useTruffleInterpreter=false",
    ],
    main_class = "net.starlark.java.eval.Benchmarks",
    runtime_deps = [":Benchmarks_lib"],
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
