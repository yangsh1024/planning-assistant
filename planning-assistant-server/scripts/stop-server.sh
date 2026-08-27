#!/usr/bin/env bash
# 停止由 scripts/start-server.sh 启动的小猫月度预算账本后端。

set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
DEFAULT_APP_HOME=$(cd "$SCRIPT_DIR/.." && pwd)
ENV_FILE=${PLANNING_ENV_FILE:-"$DEFAULT_APP_HOME/conf/server.env"}

if [[ ! -r "$ENV_FILE" ]]; then
  echo "未找到部署环境文件：$ENV_FILE" >&2
  exit 1
fi

# shellcheck source=/dev/null
source "$ENV_FILE"

if [[ -z ${APP_HOME:-} || -z ${JAR_FILE:-} ]]; then
  echo "环境变量 APP_HOME 或 JAR_FILE 未配置。" >&2
  exit 1
fi

RUN_DIR=${RUN_DIR:-"$APP_HOME/run"}
PID_FILE="$RUN_DIR/planning-assistant.pid"

if [[ ! -s "$PID_FILE" ]]; then
  echo "未发现 PID 文件，服务未由本脚本启动或已经停止。"
  exit 0
fi

PID=$(<"$PID_FILE")
if ! [[ $PID =~ ^[0-9]+$ ]]; then
  echo "PID 文件内容无效：$PID_FILE" >&2
  exit 1
fi

if ! kill -0 "$PID" 2>/dev/null; then
  rm -f "$PID_FILE"
  echo "服务已经停止，已清理失效 PID 文件。"
  exit 0
fi

PROCESS_COMMAND=$(ps -p "$PID" -o command=)
JAR_NAME=$(basename "${JAR_FILE:-planning-assistant-app}")
if [[ $PROCESS_COMMAND != *"$JAR_NAME"* ]]; then
  echo "PID=$PID 不属于配置的应用 JAR，拒绝停止。" >&2
  exit 1
fi

kill -TERM "$PID"
for _ in {1..30}; do
  if ! kill -0 "$PID" 2>/dev/null; then
    rm -f "$PID_FILE"
    echo "服务已停止。"
    exit 0
  fi
  sleep 1
done

if [[ ${1:-} == "--force" ]]; then
  kill -KILL "$PID"
  rm -f "$PID_FILE"
  echo "服务已强制停止。"
  exit 0
fi

echo "服务在 30 秒内未停止。确认后可执行：$0 --force" >&2
exit 1
