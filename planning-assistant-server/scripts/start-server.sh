#!/usr/bin/env bash
# 启动已部署的小猫月度预算账本后端。需要 Bash 4+ 和 JDK 21。

set -Eeuo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
DEFAULT_APP_HOME=$(cd "$SCRIPT_DIR/.." && pwd)
ENV_FILE=${PLANNING_ENV_FILE:-"$DEFAULT_APP_HOME/conf/server.env"}

if [[ ! -r "$ENV_FILE" ]]; then
  echo "未找到部署环境文件：$ENV_FILE" >&2
  echo "请复制 scripts/server.env.example 为 $DEFAULT_APP_HOME/conf/server.env 并填写配置。" >&2
  exit 1
fi

# shellcheck source=/dev/null
source "$ENV_FILE"

require_env() {
  local name=$1
  if [[ -z ${!name:-} ]]; then
    echo "环境变量 $name 未配置。" >&2
    exit 1
  fi
}

for name in APP_HOME JAR_FILE RUN_DIR LOG_DIR DB_URL DB_USERNAME DB_PASSWORD WECHAT_APPID WECHAT_SECRET JWT_SECRET DEEPSEEK_API_KEY APP_PUBLIC_BASE_URL APP_FIXED_QR_URL; do
  require_env "$name"
done

SERVER_PORT=${SERVER_PORT:-8080}
if ! [[ $SERVER_PORT =~ ^[1-9][0-9]{0,4}$ ]] || (( SERVER_PORT > 65535 )); then
  echo "SERVER_PORT 必须是 1 到 65535 的整数，当前值：$SERVER_PORT" >&2
  exit 1
fi
export SERVER_PORT

JAVA_CMD=${JAVA_HOME:+"$JAVA_HOME/bin/java"}
JAVA_CMD=${JAVA_CMD:-$(command -v java || true)}
if [[ -z "$JAVA_CMD" || ! -x "$JAVA_CMD" ]]; then
  echo "未找到可执行 Java；请设置 JAVA_HOME 或将 JDK 21 加入 PATH。" >&2
  exit 1
fi

JAVA_VERSION=$("$JAVA_CMD" -version 2>&1 | head -n 1)
if [[ $JAVA_VERSION != *'"21.'* ]]; then
  echo "需要 JDK 21，当前为：$JAVA_VERSION" >&2
  exit 1
fi

if [[ ! -f "$JAR_FILE" ]]; then
  echo "未找到应用 JAR：$JAR_FILE" >&2
  exit 1
fi

if [[ ${1:-} == "--check" ]]; then
  echo "环境检查通过："
  echo "  环境文件：$ENV_FILE"
  echo "  端口：$SERVER_PORT"
  echo "  固定小程序码：已配置"
  echo "  JDK：$JAVA_CMD"
  echo "  JAR：$JAR_FILE"
  exit 0
fi

mkdir -p "$RUN_DIR" "$LOG_DIR"
PID_FILE="$RUN_DIR/planning-assistant.pid"
LOG_FILE="$LOG_DIR/planning-assistant.out"

if [[ -s "$PID_FILE" ]]; then
  RUNNING_PID=$(<"$PID_FILE")
  if kill -0 "$RUNNING_PID" 2>/dev/null; then
    echo "服务已运行，PID=$RUNNING_PID。" >&2
    exit 1
  fi
  rm -f "$PID_FILE"
fi

JAVA_OPTS=${JAVA_OPTS:-'-Xms256m -Xmx512m -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai'}
read -r -a JAVA_ARGS <<< "$JAVA_OPTS"

nohup "$JAVA_CMD" "${JAVA_ARGS[@]}" -jar "$JAR_FILE" >> "$LOG_FILE" 2>&1 &
NEW_PID=$!
echo "$NEW_PID" > "$PID_FILE"

sleep 2
if ! kill -0 "$NEW_PID" 2>/dev/null; then
  rm -f "$PID_FILE"
  echo "服务启动失败，请查看日志：$LOG_FILE" >&2
  exit 1
fi

echo "服务已启动，PID=$NEW_PID"
echo "日志文件：$LOG_FILE"
