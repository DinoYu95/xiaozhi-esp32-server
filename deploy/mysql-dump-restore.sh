#!/bin/bash
# 从源 Docker MySQL 导出 xiaozhi_esp32_server，在目标 Docker MySQL 里先清空再导入
# 用法:
#   导出: ./mysql-dump-restore.sh dump   [源容器名默认 xiaozhi-mysql]
#   导入: ./mysql-dump-restore.sh restore [目标容器名] [dump文件路径默认 ./xiaozhi_esp32_server.sql]
#
# 环境变量可覆盖: MYSQL_ROOT_PASSWORD, MYSQL_DATABASE

set -e
DB_NAME="${MYSQL_DATABASE:-xiaozhi_esp32_server}"
ROOT_PW="${MYSQL_ROOT_PASSWORD:-123456}"
DUMP_FILE="${DUMP_FILE:-./xiaozhi_esp32_server.sql}"

subcommand="${1:-}"
case "$subcommand" in
  dump)
    SOURCE_CONTAINER="${2:-xiaozhi-mysql}"
    echo "从容器 $SOURCE_CONTAINER 导出库 $DB_NAME -> $DUMP_FILE"
    docker exec "$SOURCE_CONTAINER" mysqldump -uroot -p"$ROOT_PW" \
      --single-transaction --routines --triggers --set-gtid-purged=OFF \
      "$DB_NAME" > "$DUMP_FILE"
    echo "导出完成: $DUMP_FILE"
    ;;
  restore)
    TARGET_CONTAINER="${2:?请指定目标 MySQL 容器名}"
    RESTORE_FILE="${3:-$DUMP_FILE}"
    if [ ! -f "$RESTORE_FILE" ]; then
      echo "错误: 文件不存在 $RESTORE_FILE"
      exit 1
    fi
    echo "目标容器 $TARGET_CONTAINER: 先清空库 $DB_NAME，再导入 $RESTORE_FILE"
    docker exec -i "$TARGET_CONTAINER" mysql -uroot -p"$ROOT_PW" -e "
      DROP DATABASE IF EXISTS \`$DB_NAME\`;
      CREATE DATABASE \`$DB_NAME\` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
    "
    docker exec -i "$TARGET_CONTAINER" mysql -uroot -p"$ROOT_PW" "$DB_NAME" < "$RESTORE_FILE"
    echo "导入完成."
    ;;
  *)
    echo "用法: $0 dump [源容器名]"
    echo "      $0 restore <目标容器名> [dump文件路径]"
    echo "示例: $0 dump"
    echo "      $0 restore other-mysql ./xiaozhi_esp32_server.sql"
    exit 1
    ;;
esac
