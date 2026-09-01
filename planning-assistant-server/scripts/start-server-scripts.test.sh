#!/usr/bin/env bash

set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
SERVER_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
FIXTURE="$SCRIPT_DIR/test-fixtures/server.env"

if [[ -x /usr/libexec/java_home ]]; then
  TEST_JAVA_HOME=$(/usr/libexec/java_home -v 21)
else
  TEST_JAVA_HOME=${JAVA_HOME:?Set JAVA_HOME to JDK 21 before running this test.}
fi
export TEST_JAVA_HOME
export TEST_JAR_FILE="$SERVER_DIR/pom.xml"

assert_check_succeeds() {
  local script=$1
  local output
  if ! output=$(PLANNING_ENV_FILE="$FIXTURE" "$script" --check 2>&1); then
    echo "$output" >&2
    echo "Expected $script --check to succeed." >&2
    return 1
  fi
  if [[ $output != *'固定小程序码：已配置'* ]]; then
    echo "$output" >&2
    echo "Expected $script --check to report configured fixed miniapp QR code." >&2
    return 1
  fi
}

assert_check_succeeds "$SERVER_DIR/start-server.zsh"
assert_check_succeeds "$SCRIPT_DIR/start-server.sh"
