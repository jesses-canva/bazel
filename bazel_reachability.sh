#!/usr/bin/env bash

set -eu -o pipefail

METADATA_DIR=src/main/java/com/google/devtools/build/lib/bazel/graalvm_resources/META-INF/native-image/bazel/bazel
AGENT_LIB=bazel-out/../../../external/+bazel_build_deps+graalvm_ce/lib/libnative-image-agent.dylib
AGENT_ARG="-agentpath:${AGENT_LIB}=config-merge-dir=${METADATA_DIR}"
BAZEL_ARGS=(--host_jvm_args="${AGENT_ARG}")

bazel build //src:bazel-dev
./bazel-bin/src/bazel-dev "${BAZEL_ARGS[@]}" "$@"
./bazel-bin/src/bazel-dev "${BAZEL_ARGS[@]}" shutdown

