load("@rules_java//java:defs.bzl", "java_library", "java_plugin")

licenses(["notice"])  # Apache 2.0

java_library(
    name = "truffle",
    srcs = glob(["**/*.java"]),
    visibility = ["//src/main/java/net/starlark/java:clients"],
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
