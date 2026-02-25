load("@rules_java//java:defs.bzl", "java_library", "java_plugin")

licenses(["notice"])  # Apache 2.0

java_library(
    name = "truffle",
    srcs = glob(["**/*.java"]),
    visibility = ["//src/main/java/net/starlark/java:clients"],
    runtime_deps = [
        # truffle-runtime provides the HotSpotTruffleRuntime that bridges the
        # Truffle API to GraalVM's JIT compiler for partial evaluation.
        # Without it, Truffle runs in interpreter-only (DefaultTruffleRuntime) mode.
        "@maven//:org_graalvm_truffle_truffle_runtime",
        "@maven//:org_graalvm_truffle_truffle_compiler",
        "@maven//:org_graalvm_sdk_jniutils",
        "@maven//:org_graalvm_polyglot_polyglot",
    ],
    deps = [
        "//src/main/java/net/starlark/java/annot",
        "//src/main/java/net/starlark/java/eval",
        "//src/main/java/net/starlark/java/spelling",
        "//src/main/java/net/starlark/java/syntax",
        "//third_party:guava",
        "//third_party:jsr305",
        "@maven//:org_graalvm_sdk_graal_sdk",
        "@maven//:org_graalvm_sdk_collections",
        "@maven//:org_graalvm_sdk_nativeimage",
        "@maven//:org_graalvm_sdk_word",
        "@maven//:org_graalvm_truffle_truffle_api",
    ],
    plugins = [":truffle_dsl_processor"],
)

java_plugin(
    name = "truffle_dsl_processor",
    generates_api = True,
    processor_class = "com.oracle.truffle.dsl.processor.TruffleProcessor",
    deps = [
        "@maven//:org_graalvm_truffle_truffle_dsl_processor",
        "@maven//:org_graalvm_truffle_truffle_api",
        "@maven//:org_graalvm_sdk_graal_sdk",
    ],
)
