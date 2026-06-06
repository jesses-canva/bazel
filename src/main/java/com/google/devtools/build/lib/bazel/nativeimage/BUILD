load("@rules_java//java:defs.bzl", "java_library")

package(
    default_applicable_licenses = ["@io_bazel//:license"],
    default_visibility = ["//src/main/java/com/google/devtools/build/lib/bazel:__pkg__"],
)

java_library(
    name = "nativeimage",
    srcs = ["CompressedTarNativeImageFeature.java"],
    resources = glob(["META-INF/**"]),
    resource_strip_prefix = package_name(),
    deps = [
        "//src/main/java/com/google/devtools/build/lib/bazel/repository/decompressor",
        "@maven//:org_graalvm_sdk_nativeimage",
    ],
)
