#!/bin/bash
set -e

# ============================================================
# one-api-java 一键部署脚本
# 用法: bash deploy.sh [--skip-tests]
# ============================================================

SKIP_TESTS=false
if [ "$1" = "--skip-tests" ]; then
    SKIP_TESTS=true
fi

# 脚本所在目录 = 项目根目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

log()  { echo -e "${GREEN}[DEPLOY]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
err()  { echo -e "${RED}[ERROR]${NC} $1"; }

# ---- Step 1: 检测/配置 Java 17+ ----
log "Step 1/5: 检测 Java 环境..."

JAVA_EXE=""
# 优先查找系统已安装的 JDK 17
JAVA17_DIRS=(
    "/c/Program Files/Eclipse Adoptium"
    "/c/Program Files/Java"
    "/c/Program Files/OpenJDK"
    "/c/Program Files/Microsoft"
    "/c/Program Files/Zulu"
    "/c/Program Files/semeru"
)
for dir in "${JAVA17_DIRS[@]}"; do
    if [ -d "$dir" ]; then
        JAVA_EXE=$(find "$dir" -maxdepth 4 -name "java.exe" -path "*/jdk-17*/bin/*" 2>/dev/null | head -1)
        [ -n "$JAVA_EXE" ] && break
    fi
done

if [ -z "$JAVA_EXE" ]; then
    # fallback: 检查系统 java 版本是否 >= 17
    if command -v java &>/dev/null; then
        JAVA_VER=$(java -version 2>&1 | grep -oP '"(\d+)' | tr -d '"')
        if [ "$JAVA_VER" -ge 17 ] 2>/dev/null; then
            JAVA_EXE=$(which java)
        fi
    fi
fi

if [ -z "$JAVA_EXE" ]; then
    # ---- 自动下载 JDK 17 到 ~/.one-api/jdk17 ----
    JDK_HOME="$HOME/.one-api/jdk17"
    JDK_ZIP="$JDK_HOME/jdk17.zip"

    # 多源下载 URL（按速度排序，华北优先生效）
    JDK_URLS=(
        "https://repo.huaweicloud.com/openjdk/17.0.2/openjdk-17.0.2_windows-x64_bin.zip||华为云 (约178MB)"
        "https://mirrors.ustc.edu.cn/adoptium/temurin17/jdk-17.0.12%2B7/windows/x64/OpenJDK17U-jdk_x64_windows_hotspot_17.0.12_7.zip||中科大 (约178MB)"
        "https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse||Adoptium官方 (约180MB)"
    )

    if [ -f "$JDK_HOME/bin/java.exe" ]; then
        # 已下载过，直接使用
        JAVA_EXE="$JDK_HOME/bin/java.exe"
        JAVA_HOME="$JDK_HOME"
    else
        warn "未找到 JDK 17，正在自动下载到 $JDK_HOME ..."
        mkdir -p "$JDK_HOME"

        if ! command -v curl &>/dev/null && ! command -v wget &>/dev/null; then
            err "未找到 curl 或 wget，无法下载。请手动安装 JDK 17。"
            exit 1
        fi

        DOWNLOAD_OK=false
        for entry in "${JDK_URLS[@]}"; do
            URL="${entry%%||*}"
            LABEL="${entry##*||}"
            log "尝试: $LABEL"
            rm -f "$JDK_ZIP"

            if command -v curl &>/dev/null; then
                curl -L --progress-bar --max-time 300 -o "$JDK_ZIP" "$URL" 2>&1
            else
                wget --timeout=300 -O "$JDK_ZIP" "$URL" 2>&1
            fi

            if [ -f "$JDK_ZIP" ] && [ -s "$JDK_ZIP" ]; then
                # 验证是有效的 zip 文件
                if file "$JDK_ZIP" 2>/dev/null | grep -qi "zip"; then
                    DOWNLOAD_OK=true
                    break
                fi
            fi
            warn "此源下载失败，尝试下一个..."
            rm -f "$JDK_ZIP"
        done

        if ! $DOWNLOAD_OK; then
            err "所有下载源均失败。请手动安装 JDK 17: https://adoptium.net/download/"
            exit 1
        fi

        log "解压 JDK 17..."
        # 用 PowerShell Expand-Archive（Windows 原生，不需要额外工具）
        JDK_ZIP_WIN=$(cygpath -w "$JDK_ZIP" 2>/dev/null || echo "$JDK_ZIP")
        JDK_HOME_WIN=$(cygpath -w "$JDK_HOME" 2>/dev/null || echo "$JDK_HOME")
        powershell -Command "Expand-Archive -Path '$JDK_ZIP_WIN' -DestinationPath '$JDK_HOME_WIN' -Force" 2>&1

        # 扁平化：解压后可能有一层目录包装
        EXTRACTED_DIR=$(ls -d "$JDK_HOME"/jdk-17* "$JDK_HOME"/openjdk-17* 2>/dev/null | head -1)
        if [ -n "$EXTRACTED_DIR" ] && [ "$EXTRACTED_DIR" != "$JDK_HOME" ]; then
            # 把内容移到上层
            find "$EXTRACTED_DIR" -maxdepth 1 -mindepth 1 -exec mv {} "$JDK_HOME/" \; 2>/dev/null
            rmdir "$EXTRACTED_DIR" 2>/dev/null
        fi

        rm -f "$JDK_ZIP"

        if [ -f "$JDK_HOME/bin/java.exe" ]; then
            JAVA_EXE="$JDK_HOME/bin/java.exe"
            JAVA_HOME="$JDK_HOME"
            "$JAVA_EXE" -version 2>&1 | head -1
            log "JDK 17 安装完成: $JDK_HOME"
        else
            err "JDK 解压后未找到 java.exe，请检查。"
            ls "$JDK_HOME/" 2>/dev/null | head -5
            exit 1
        fi
    fi
fi

# 从 java.exe 路径推导 JAVA_HOME（如果尚未设置）
if [ -z "$JAVA_HOME" ]; then
    JAVA_HOME=$(dirname "$(dirname "$JAVA_EXE")")
fi
JAVA_HOME_WIN=$(cygpath -w "$JAVA_HOME" 2>/dev/null || echo "$JAVA_HOME")
JAVA_VER=$("$JAVA_EXE" -version 2>&1 | head -1)
log "Java 检测: $JAVA_VER"
log "JAVA_HOME: $JAVA_HOME_WIN"

# ---- Step 2: 检测/配置 Maven ----
log "Step 2/5: 检测 Maven..."

MAVEN_HOME=""
for candidate in \
    "/c/BASIC_ENV/apache-maven-3.9.5" \
    "/c/BASIC_ENV/apache-maven-3.9.2" \
    "/c/BASIC_ENV/apache-maven-"* \
    "/c/Program Files/apache-maven-"* \
    "/c/tools/apache-maven-"*; do
    for d in $candidate; do
        if [ -d "$d" ] && [ -f "$d/bin/mvn" ]; then
            MAVEN_HOME="$d"
            break 2
        fi
    done
done

if [ -z "$MAVEN_HOME" ]; then
    if command -v mvn &>/dev/null; then
        MAVEN_HOME=$(dirname "$(dirname "$(which mvn)")")
    fi
fi

if [ -z "$MAVEN_HOME" ]; then
    err "未找到 Maven。"
    echo ""
    echo "请安装 Apache Maven 3.9+:"
    echo "  https://maven.apache.org/download.cgi"
    echo "  (解压到 /c/BASIC_ENV/apache-maven-3.9.x 即可)"
    exit 1
fi

MAVEN_HOME_WIN=$(cygpath -w "$MAVEN_HOME" 2>/dev/null || echo "$MAVEN_HOME")
CLASSWORLDS_JAR=$(find "$MAVEN_HOME/boot" -name "plexus-classworlds-*.jar" 2>/dev/null | head -1)
CLASSWORLDS_JAR_WIN=$(cygpath -w "$CLASSWORLDS_JAR" 2>/dev/null || echo "$CLASSWORLDS_JAR")
log "MAVEN_HOME: $MAVEN_HOME_WIN"

# ---- Step 3: 编译 + 测试 ----
log "Step 3/5: 编译项目..."

PROJECT_DIR_WIN=$(cygpath -w "$SCRIPT_DIR" 2>/dev/null || echo "$SCRIPT_DIR")

TEST_OPTS="test"
if $SKIP_TESTS; then
    TEST_OPTS="-DskipTests"
    warn "跳过测试"
fi

"$JAVA_EXE" -classpath "$CLASSWORLDS_JAR_WIN" \
    -Dclassworlds.conf="$MAVEN_HOME_WIN/bin/m2.conf" \
    -Dmaven.home="$MAVEN_HOME_WIN" \
    -Dmaven.multiModuleProjectDirectory="$PROJECT_DIR_WIN" \
    -Dlibrary.jansi.path="$MAVEN_HOME_WIN/lib/jansi-native" \
    org.codehaus.plexus.classworlds.launcher.Launcher \
    -f "$PROJECT_DIR_WIN/pom.xml" \
    -Dmaven.compiler.source=17 \
    -Dmaven.compiler.target=17 \
    -Dmaven.compiler.fork=false \
    compile $TEST_OPTS -q

log "编译通过"

# ---- Step 4: 打包 shaded JAR ----
log "Step 4/5: 打包 shaded JAR..."

"$JAVA_EXE" -classpath "$CLASSWORLDS_JAR_WIN" \
    -Dclassworlds.conf="$MAVEN_HOME_WIN/bin/m2.conf" \
    -Dmaven.home="$MAVEN_HOME_WIN" \
    -Dmaven.multiModuleProjectDirectory="$PROJECT_DIR_WIN" \
    -Dlibrary.jansi.path="$MAVEN_HOME_WIN/lib/jansi-native" \
    org.codehaus.plexus.classworlds.launcher.Launcher \
    -f "$PROJECT_DIR_WIN/pom.xml" \
    -Dmaven.compiler.source=17 \
    -Dmaven.compiler.target=17 \
    -Dmaven.compiler.fork=false \
    package shade:shade $TEST_OPTS -q

JAR_SIZE=$(ls -lh target/one-api-java-1.0.0-shaded.jar | awk '{print $5}')
log "打包完成 ($JAR_SIZE)"

# ---- Step 5: 部署到 ~/.one-api/ ----
log "Step 5/5: 部署到 ~/.one-api/..."

DEPLOY_DIR="$HOME/.one-api"
mkdir -p "$DEPLOY_DIR"

# 备份旧 JAR
if [ -f "$DEPLOY_DIR/one-api-java.jar" ]; then
    BACKUP_NAME="one-api-java.jar.bak.$(date +%Y%m%d%H%M%S)"
    cp "$DEPLOY_DIR/one-api-java.jar" "$DEPLOY_DIR/$BACKUP_NAME"
    log "已备份旧 JAR: $BACKUP_NAME"
fi

cp target/one-api-java-1.0.0-shaded.jar "$DEPLOY_DIR/one-api-java.jar"

# 如果 config.yaml 不存在，生成模板
if [ ! -f "$DEPLOY_DIR/config.yaml" ]; then
    warn "未找到 config.yaml，生成默认模板..."
    cat > "$DEPLOY_DIR/config.yaml" << 'YAML'
server:
  port: 13000
database:
  host: bj.xiaoceng.space
  port: 5432
  database: oneapi
  user: oneapi
  password: "CHANGE_ME"
YAML
    warn "请编辑 $DEPLOY_DIR/config.yaml 填入正确的数据库密码"
fi

# 停止旧服务（通过端口找进程）
OLD_PID=$(netstat -ano 2>/dev/null | grep ":13000 " | grep LISTENING | awk '{print $NF}' | head -1)
if [ -z "$OLD_PID" ]; then
    # fallback: 用 ss
    OLD_PID=$(ss -tlnp 2>/dev/null | grep ":13000" | grep -oP 'pid=\K\d+' | head -1)
fi
if [ -n "$OLD_PID" ] && [ "$OLD_PID" != "0" ]; then
    log "停止旧服务 (端口 13000, PID: $OLD_PID)..."
    taskkill /PID "$OLD_PID" /F 2>/dev/null || true
    sleep 2
fi

# 启动新服务
log "启动 one-api-java..."
cd "$DEPLOY_DIR"
"$JAVA_EXE" -Dfile.encoding=UTF-8 -cp one-api-java.jar com.oneapi.Main > server.log 2>&1 &
NEW_PID=$!
sleep 3

# 验证
if kill -0 "$NEW_PID" 2>/dev/null; then
    log "服务已启动 (PID: $NEW_PID)"
    log "日志: $DEPLOY_DIR/server.log"

    # 快速健康检查
    if command -v curl &>/dev/null; then
        sleep 2
        HTTP_CODE=$(curl -s --max-time 5 -w "%{http_code}" -o /dev/null http://localhost:13000/v1/models 2>/dev/null || true)
        HTTP_CODE=$(echo "$HTTP_CODE" | tr -d '\r\n ' | grep -oE '[0-9]{3}' | head -1)
        if [ "$HTTP_CODE" = "200" ]; then
            log "健康检查通过 (HTTP $HTTP_CODE)"
        else
            warn "健康检查返回 $HTTP_CODE，请检查日志"
        fi
    fi
else
    err "服务启动失败，查看日志: $DEPLOY_DIR/server.log"
    tail -20 "$DEPLOY_DIR/server.log"
    exit 1
fi

echo ""
echo -e "${GREEN}============================================${NC}"
echo -e "${GREEN}  one-api-java 部署完成${NC}"
echo -e "${GREEN}  地址: http://localhost:13000${NC}"
echo -e "${GREEN}  日志: $DEPLOY_DIR/server.log${NC}"
echo -e "${GREEN}============================================${NC}"
