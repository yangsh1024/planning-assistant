#!/usr/bin/env zsh
# 启动小猫月度预算账本后端（开发环境）。

set -euo pipefail

SERVER_DIR=${0:A:h}
PROJECT_DIR=${SERVER_DIR:h}
ENV_FILE=${PLANNING_ENV_FILE:-"$PROJECT_DIR/.my-source/server.env"}

if [[ ! -r "$ENV_FILE" ]]; then
  print -u2 "未找到环境变量文件：$ENV_FILE"
  print -u2 "请复制 scripts/server.env.example 为项目根目录的 .my-source/server.env，或通过 PLANNING_ENV_FILE 指定文件路径。"
  exit 1
fi

# 非交互式脚本不会自动读取 ~/.zshrc，因此只加载项目所需的环境变量文件。
source "$ENV_FILE"

if [[ -z ${WECHAT_APPID:-} || -z ${WECHAT_SECRET:-} || -z ${JWT_SECRET:-} || -z ${APP_FIXED_QR_URL:-} ]]; then
  print -u2 "环境变量文件缺少 WECHAT_APPID、WECHAT_SECRET、JWT_SECRET 或 APP_FIXED_QR_URL。"
  exit 1
fi

SERVER_PORT=${SERVER_PORT:-8080}
if [[ ! "$SERVER_PORT" =~ '^[1-9][0-9]{0,4}$' ]] || (( SERVER_PORT > 65535 )); then
  print -u2 "SERVER_PORT 必须是 1 到 65535 的整数，当前值：$SERVER_PORT"
  exit 1
fi
export SERVER_PORT

if [[ -z ${JAVA_HOME:-} || ! -x "$JAVA_HOME/bin/java" ]]; then
  if [[ -x /usr/libexec/java_home ]]; then
    JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || true)
  fi
fi

if [[ -z ${JAVA_HOME:-} || ! -x "$JAVA_HOME/bin/java" ]]; then
  print -u2 "未找到 JDK 21。请安装 JDK 21，或设置有效的 JAVA_HOME。"
  exit 1
fi

JAVA_MAJOR=$($JAVA_HOME/bin/java -version 2>&1 | sed -nE 's/.*version "([0-9]+).*/\1/p' | head -n 1)
if [[ "$JAVA_MAJOR" != "21" ]]; then
  print -u2 "当前 JAVA_HOME 不是 JDK 21：$JAVA_HOME"
  exit 1
fi

export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"

if ! command -v mvn >/dev/null 2>&1; then
  print -u2 "未找到 Maven（mvn）。请安装 Maven 并加入 PATH。"
  exit 1
fi

if [[ ${1:-} == "--check" ]]; then
  print "环境检查通过："
  print "  环境文件：$ENV_FILE"
  print "  端口：$SERVER_PORT"
  print "  固定小程序码：已配置"
  print "  JDK：$JAVA_HOME"
  print "  Maven：$(command -v mvn)"
  exit 0
fi

LISTENING_PID=$(lsof -tiTCP:"$SERVER_PORT" -sTCP:LISTEN 2>/dev/null || true)
if [[ -n "$LISTENING_PID" ]]; then
  print -u2 "$SERVER_PORT 端口已被进程 $LISTENING_PID 占用。请先停止现有后端，再重新执行本脚本。"
  print -u2 "这可避免 Maven 打包覆盖运行中的 JAR，导致类加载异常。"
  exit 1
fi

cd "$SERVER_DIR"
print "正在启动后端：http://localhost:$SERVER_PORT"
mvn -pl planning-assistant-app -am package -DskipTests

JARS=("$SERVER_DIR"/planning-assistant-app/target/*.jar(N))
if (( ${#JARS[@]} != 1 )); then
  print -u2 "未找到唯一的可执行 JAR，请检查 Maven 打包输出。"
  exit 1
fi

exec "$JAVA_HOME/bin/java" -jar "$JARS[1]" "$@"
