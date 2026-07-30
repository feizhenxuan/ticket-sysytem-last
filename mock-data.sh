#!/bin/bash
# ============================================================
#  Mock 数据填充脚本
#  用法: bash mock-data.sh [backend_url]
#  默认 backend_url=http://localhost:8888
# ============================================================
set -euo pipefail

BASE_URL="${1:-http://localhost:8888}"
STEP=0

banner() {
  STEP=$((STEP + 1))
  echo ""
  echo "========== STEP $STEP: $1 =========="
}

json_extract() {
  # 从 JSON 响应中提取字段（兼容 grep+sed，无需 jq）
  echo "$1" | grep -o "\"$2\"[[:space:]]*:[[:space:]]*[^,}]*" | head -1 | sed 's/.*:[[:space:]]*//' | sed 's/["\\ ]//g'
}

# ============================================================
# STEP 1: 注册管理员用户
# ============================================================
banner "注册管理员用户"

ADMIN_USER="admin"
ADMIN_PASS="admin123"

REGISTER_RESP=$(curl -s -X POST "${BASE_URL}/api/auth/register" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}" 2>/dev/null || true)

# 如果用户已存在，走登录
if echo "$REGISTER_RESP" | grep -q "已存在"; then
  echo "  用户已存在，走登录流程"
  LOGIN_RESP=$(curl -s -X POST "${BASE_URL}/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\"}")
  TOKEN=$(json_extract "$LOGIN_RESP" "access_token")
else
  TOKEN=$(json_extract "$REGISTER_RESP" "access_token")
fi

if [ -z "$TOKEN" ]; then
  echo "  [ERROR] 无法获取 token，请确认后端已启动"
  exit 1
fi
echo "  Token: ${TOKEN:0:30}..."

AUTH_HEADER="Authorization: Bearer ${TOKEN}"

# ============================================================
# STEP 2: 创建电影
# ============================================================
banner "创建电影"

create_movie() {
  local resp=$(curl -s -X POST "${BASE_URL}/api/admin/movies" \
    -H "Content-Type: application/json" \
    -H "$AUTH_HEADER" \
    -d "$1")
  local id=$(json_extract "$resp" "id")
  echo "$id"
}

MOVIE_1=$(create_movie '{"title":"沙丘3：救世主","rating":"9.2","duration":"166","genre":"科幻,冒险","director":"丹尼斯·维伦纽瓦","actors":"提莫西·查拉梅,赞达亚,丽贝卡·弗格森","releaseDate":"2026-03-20","posterUrl":"https://image.tmdb.org/t/p/w500/dune3.jpg","description":"保罗·厄崔迪在沙丘星球上建立了帝国，但他的权力正面临来自各方的威胁。","status":"showing","tmdbId":1011985}')
echo "  电影1: 沙丘3 (id=$MOVIE_1)"

MOVIE_2=$(create_movie '{"title":"流浪地球3","rating":"9.0","duration":"150","genre":"科幻,动作","director":"郭帆","actors":"吴京,刘德华,李雪健","releaseDate":"2026-02-01","posterUrl":"https://image.tmdb.org/t/p/w500/earth3.jpg","description":"人类带着地球继续流浪，面对木星引力的致命挑战。","status":"showing","tmdbId":1011986}')
echo "  电影2: 流浪地球3 (id=$MOVIE_2)"

MOVIE_3=$(create_movie '{"title":"哪吒之魔童闹海","rating":"8.8","duration":"144","genre":"动画,奇幻","director":"饺子","actors":"配音:吕艳婷,囧森瑟夫","releaseDate":"2025-01-29","posterUrl":"https://image.tmdb.org/t/p/w500/nezha2.jpg","description":"哪吒重生归来，与东海龙族展开终极对决。","status":"showing","tmdbId":1011987}')
echo "  电影3: 哪吒之魔童闹海 (id=$MOVIE_3)"

MOVIE_4=$(create_movie '{"title":"奥本海默2","rating":"8.5","duration":"180","genre":"传记,剧情","director":"克里斯托弗·诺兰","actors":"基里安·墨菲,艾米丽·布朗特","releaseDate":"2026-07-15","posterUrl":"https://image.tmdb.org/t/p/w500/opp2.jpg","description":"奥本海默晚年的政治挣扎与科学遗产。","status":"coming","tmdbId":1011988}')
echo "  电影4: 奥本海默2 (id=$MOVIE_4)"

MOVIE_5=$(create_movie '{"title":"长安三万里2","rating":"8.3","duration":"168","genre":"动画,历史","director":"谢德仁","actors":"配音:杨天翔,刘琮","releaseDate":"2026-04-30","posterUrl":"https://image.tmdb.org/t/p/w500/changan2.jpg","description":"李白与高适的重逢，盛世大唐的最后挽歌。","status":"coming","tmdbId":1011989}')
echo "  电影5: 长安三万里2 (id=$MOVIE_5)"

# ============================================================
# STEP 3: 创建影院
# ============================================================
banner "创建影院"

create_cinema() {
  local resp=$(curl -s -X POST "${BASE_URL}/api/admin/cinemas" \
    -H "Content-Type: application/json" \
    -H "$AUTH_HEADER" \
    -d "$1")
  local id=$(json_extract "$resp" "id")
  echo "$id"
}

CINEMA_1=$(create_cinema '{"name":"万达影城（西溪龙湖店）","address":"杭州市西湖区西溪龙湖天街5楼","longitude":"120.0658","latitude":"30.2986","phone":"0571-88888001","city":"杭州"}')
echo "  影院1: 万达影城西溪龙湖店 (id=$CINEMA_1)"

CINEMA_2=$(create_cinema '{"name":"CGV影城（滨江宝龙店）","address":"杭州市滨江区宝龙城市广场4楼","longitude":"120.2089","latitude":"30.1956","phone":"0571-88888002","city":"杭州"}')
echo "  影院2: CGV影城滨江宝龙店 (id=$CINEMA_2)"

CINEMA_3=$(create_cinema '{"name":"卢米埃影城（阿里中心店）","address":"杭州市余杭区阿里中心B座3楼","longitude":"119.9899","latitude":"30.2828","phone":"0571-88888003","city":"杭州"}')
echo "  影院3: 卢米埃影城阿里中心店 (id=$CINEMA_3)"

# ============================================================
# STEP 4: 创建影厅
# ============================================================
banner "创建影厅"

create_hall() {
  # $1=cinemaId $2=name $3=type $4=rows $5=cols
  local resp=$(curl -s -X POST "${BASE_URL}/api/admin/cinemas/$1/halls" \
    -H "Content-Type: application/json" \
    -H "$AUTH_HEADER" \
    -d "{\"name\":\"$2\",\"hallType\":\"$3\",\"totalRows\":$4,\"totalCols\":$5}")
  local id=$(json_extract "$resp" "id")
  echo "$id"
}

HALL_1=$(create_hall "$CINEMA_1" "1号厅" "normal" 8 12)
echo "  影厅1: 万达1号厅 (id=$HALL_1, 8x12=96座)"

HALL_2=$(create_hall "$CINEMA_1" "IMAX厅" "imax" 10 14)
echo "  影厅2: 万达IMAX厅 (id=$HALL_2, 10x14=140座)"

HALL_3=$(create_hall "$CINEMA_2" "1号厅" "normal" 8 12)
echo "  影厅3: CGV1号厅 (id=$HALL_3, 8x12=96座)"

HALL_4=$(create_hall "$CINEMA_2" "VIP厅" "vip" 6 10)
echo "  影厅4: CGV VIP厅 (id=$HALL_4, 6x10=60座)"

HALL_5=$(create_hall "$CINEMA_3" "杜比全景声厅" "normal" 8 14)
echo "  影厅5: 卢米埃杜比厅 (id=$HALL_5, 8x14=112座)"

# ============================================================
# STEP 5: 创建场次
# ============================================================
banner "创建场次"

create_session() {
  # $1=movieId $2=cinemaId $3=hallId $4=startTime $5=endTime $6=price
  local resp=$(curl -s -X POST "${BASE_URL}/api/admin/sessions" \
    -H "Content-Type: application/json" \
    -H "$AUTH_HEADER" \
    -d "{\"movieId\":$1,\"cinemaId\":$2,\"hallId\":$3,\"startTime\":\"$4\",\"endTime\":\"$5\",\"price\":\"$6\",\"status\":\"available\"}")
  local id=$(json_extract "$resp" "id")
  echo "$id"
}

SESSION_1=$(create_session "$MOVIE_1" "$CINEMA_1" "$HALL_1" "2026-07-31 19:00:00" "2026-07-31 21:46:00" "49.90")
echo "  场次1: 沙丘3 @ 万达1号厅 (id=$SESSION_1, 票价49.90)"

SESSION_2=$(create_session "$MOVIE_1" "$CINEMA_1" "$HALL_2" "2026-07-31 14:00:00" "2026-07-31 16:46:00" "79.90")
echo "  场次2: 沙丘3 @ 万达IMAX厅 (id=$SESSION_2, 票价79.90)"

SESSION_3=$(create_session "$MOVIE_2" "$CINEMA_2" "$HALL_3" "2026-07-31 20:00:00" "2026-07-31 22:30:00" "45.00")
echo "  场次3: 流浪地球3 @ CGV1号厅 (id=$SESSION_3, 票价45.00)"

SESSION_4=$(create_session "$MOVIE_3" "$CINEMA_3" "$HALL_5" "2026-07-31 18:30:00" "2026-07-31 20:54:00" "55.00")
echo "  场次4: 哪吒之魔童闹海 @ 卢米埃杜比厅 (id=$SESSION_4, 票价55.00)"

SESSION_5=$(create_session "$MOVIE_2" "$CINEMA_2" "$HALL_4" "2026-08-01 16:00:00" "2026-08-01 18:30:00" "88.00")
echo "  场次5: 流浪地球3 @ CGV VIP厅 (id=$SESSION_5, 票价88.00)"

# ============================================================
# STEP 6: 生成座位 + 场次座位 SQL
# ============================================================
banner "生成座位SQL（请在ODC上执行）"

SQL_FILE="/Users/herlan/Downloads/TicketProject/ticketbacked/mock-seats.sql"

cat > "$SQL_FILE" <<SQLEOF
-- ============================================================
--  座位 & 场次座位状态初始化 SQL
--  由 mock-data.sh 自动生成，请在 ODC 上执行
--  生成时间: $(date '+%Y-%m-%d %H:%M:%S')
-- ============================================================

-- ---------------------------------
-- 1. 清理旧座位数据（可选，首次执行可跳过）
-- ---------------------------------
-- DELETE FROM hx_session_seats;
-- DELETE FROM hx_seats;

-- ---------------------------------
-- 2. 插入影厅座位
--    影厅 ${HALL_1}: 8行x12列 = 96座 (normal)
--    影厅 ${HALL_2}: 10行x14列 = 140座 (imax)
--    影厅 ${HALL_3}: 8行x12列 = 96座 (normal)
--    影厅 ${HALL_4}: 6行x10列 = 60座 (vip)
--    影厅 ${HALL_5}: 8行x14列 = 112座 (normal)
-- ---------------------------------

SQLEOF

# 生成座位插入 SQL
generate_seats_sql() {
  local hall_id=$1
  local rows=$2
  local cols=$3
  local seat_type=$4

  for r in $(seq 1 $rows); do
    for c in $(seq 1 $cols); do
      # VIP 厅前两排为 couple 座位
      local stype="$seat_type"
      if [ "$seat_type" = "vip" ] && [ "$r" -le 2 ]; then
        stype="couple"
      fi
      echo "INSERT INTO hx_seats (hall_id, row_num, col_num, seat_type, gmt_create) VALUES (${hall_id}, ${r}, ${c}, '${stype}', NOW());" >> "$SQL_FILE"
    done
  done
  echo "" >> "$SQL_FILE"
}

echo "-- 影厅 ${HALL_1} (8x12, normal)" >> "$SQL_FILE"
generate_seats_sql "$HALL_1" 8 12 "normal"

echo "-- 影厅 ${HALL_2} (10x14, imax)" >> "$SQL_FILE"
generate_seats_sql "$HALL_2" 10 14 "normal"

echo "-- 影厅 ${HALL_3} (8x12, normal)" >> "$SQL_FILE"
generate_seats_sql "$HALL_3" 8 12 "normal"

echo "-- 影厅 ${HALL_4} (6x10, vip, 前2排couple)" >> "$SQL_FILE"
generate_seats_sql "$HALL_4" 6 10 "vip"

echo "-- 影厅 ${HALL_5} (8x14, normal)" >> "$SQL_FILE"
generate_seats_sql "$HALL_5" 8 14 "normal"

# 生成场次座位 SQL
cat >> "$SQL_FILE" <<'SQLEOF'

-- ---------------------------------
-- 3. 插入场次座位状态
--    为每个场次的影厅所有座位初始化为 available
--    依赖：hx_seats 表中已有对应 hall_id 的座位记录
--    OceanBase 不支持 INSERT...SELECT...JOIN 组合，
--    所以用子查询方式逐条插入
-- ---------------------------------

SQLEOF

generate_session_seats_sql() {
  local session_id=$1
  local hall_id=$2

  cat >> "$SQL_FILE" <<SQLEOF
-- 场次 ${session_id} (影厅 ${hall_id} 的所有座位)
INSERT INTO hx_session_seats (session_id, seat_id, status, gmt_modify)
SELECT ${session_id}, id, 'available', NOW() FROM hx_seats WHERE hall_id = ${hall_id};

SQLEOF
}

generate_session_seats_sql "$SESSION_1" "$HALL_1"
generate_session_seats_sql "$SESSION_2" "$HALL_2"
generate_session_seats_sql "$SESSION_3" "$HALL_3"
generate_session_seats_sql "$SESSION_4" "$HALL_5"
generate_session_seats_sql "$SESSION_5" "$HALL_4"

cat >> "$SQL_FILE" <<'SQLEOF'

-- ---------------------------------
-- 4. 验证数据
-- ---------------------------------
SELECT '座位总数' AS metric, COUNT(*) AS count FROM hx_seats
UNION ALL
SELECT '场次座位总数', COUNT(*) FROM hx_session_seats
UNION ALL
SELECT '电影数量', COUNT(*) FROM hx_movies
UNION ALL
SELECT '影院数量', COUNT(*) FROM hx_cinemas
UNION ALL
SELECT '影厅数量', COUNT(*) FROM hx_halls
UNION ALL
SELECT '场次数量', COUNT(*) FROM hx_sessions;

SQLEOF

echo "  SQL 文件已生成: $SQL_FILE"
echo "  包含: $(grep -c 'INSERT INTO hx_seats' "$SQL_FILE") 条座位 + $(grep -c 'INSERT INTO hx_session_seats' "$SQL_FILE") 条场次座位批量插入"

# ============================================================
# STEP 7: 注册测试用户
# ============================================================
banner "注册测试用户"

for i in 2 3 4 5; do
  curl -s -X POST "${BASE_URL}/api/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"user${i}\",\"password\":\"pass${i}123\"}" > /dev/null 2>&1 && echo "  测试用户 user${i} 注册成功" || echo "  测试用户 user${i} 已存在"
done

# ============================================================
# 完成
# ============================================================
banner "完成！

  API 数据已填充:
    - 用户: admin (管理员) + user2~user5 (测试用户)
    - 电影: 5 部 (3部热映 + 2部即将上映)
    - 影院: 3 家
    - 影厅: 5 个
    - 场次: 5 场

  下一步:
    请在 ODC 上执行 SQL 文件填充座位数据:
    $SQL_FILE

  ID 汇总:
    Movies:  $MOVIE_1(沙丘3) $MOVIE_2(流浪地球3) $MOVIE_3(哪吒2) $MOVIE_4(奥本海默2) $MOVIE_5(长安2)
    Cinemas: $CINEMA_1(万达) $CINEMA_2(CGV) $CINEMA_3(卢米埃)
    Halls:   $HALL_1 $HALL_2 $HALL_3 $HALL_4 $HALL_5
    Sessions: $SESSION_1 $SESSION_2 $SESSION_3 $SESSION_4 $SESSION_5
"