load("@rules_java//java:java_library.bzl", "java_library")

package(default_applicable_licenses = ["@io_bazel//:license"])

java_library(
    name = "graalvm_resources",
    resources = glob(["META-INF/**"]),
    resource_strip_prefix = package_name(),
    visibility = ["//src/main/java/com/google/devtools/build/lib/bazel:__pkg__"],
)
