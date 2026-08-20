INSERT INTO hx_movies (id, title, rating, duration, genre, director, actors, release_date, poster_url, description, status, tmdb_id)
VALUES
    (1, '流浪地球2', 8.3, 173, '科幻,冒险', '郭帆', '吴京,刘德华,李雪健', DATE '2023-01-22', 'https://image.tmdb.org/t/p/w500/pR858ihc6Ls9xohpdRJVjV787ml.jpg', '太阳危机来临，地球人类开启漫长求生计划。', 'showing', 842675),
    (2, '哪吒之魔童闹海', 8.5, 144, '动画,奇幻', '饺子', '吕艳婷,囧森瑟夫', DATE '2025-01-29', 'https://image.tmdb.org/t/p/w500/5lUmWTGkEcYnXujixXn31o9q2T0.jpg', '哪吒与敖丙在新的命运中并肩破局。', 'showing', 980477),
    (3, '沙丘2', 8.1, 166, '科幻,剧情', '丹尼斯·维伦纽瓦', '提莫西·查拉梅,赞达亚', DATE '2024-03-08', 'https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg', '保罗踏上复仇与觉醒之路。', 'showing', 693134);

INSERT INTO hx_cinemas (id, name, address, longitude, latitude, phone, city, poi_id)
VALUES
    (1, '本地影城人民广场店', '上海市黄浦区人民大道100号', 121.47519000, 31.22883000, '021-10000001', '上海', 'local-poi-1'),
    (2, '本地 IMAX 静安店', '上海市静安区南京西路1600号', 121.44939000, 31.22703000, '021-10000002', '上海', 'local-poi-2');

INSERT INTO hx_halls (id, cinema_id, name, hall_type, total_rows, total_cols)
VALUES
    (1, 1, '1号厅', 'normal', 4, 5),
    (2, 2, 'IMAX厅', 'imax', 4, 5);

INSERT INTO hx_seats (hall_id, row_num, col_num, seat_type)
SELECT 1, r.x, c.x,
       CASE WHEN r.x = 1 THEN 'vip' WHEN r.x = 4 THEN 'couple' ELSE 'normal' END
FROM SYSTEM_RANGE(1, 4) r
CROSS JOIN SYSTEM_RANGE(1, 5) c;

INSERT INTO hx_seats (hall_id, row_num, col_num, seat_type)
SELECT 2, r.x, c.x,
       CASE WHEN r.x = 1 THEN 'vip' WHEN r.x = 4 THEN 'couple' ELSE 'normal' END
FROM SYSTEM_RANGE(1, 4) r
CROSS JOIN SYSTEM_RANGE(1, 5) c;

INSERT INTO hx_sessions (id, movie_id, cinema_id, hall_id, start_time, end_time, price, status)
VALUES
    (1, 1, 1, 1, DATEADD('HOUR', 3, CURRENT_TIMESTAMP), DATEADD('HOUR', 6, CURRENT_TIMESTAMP), 48.00, 'available'),
    (2, 2, 1, 1, DATEADD('HOUR', 7, CURRENT_TIMESTAMP), DATEADD('HOUR', 9, CURRENT_TIMESTAMP), 58.00, 'available'),
    (3, 3, 2, 2, DATEADD('HOUR', 5, CURRENT_TIMESTAMP), DATEADD('HOUR', 8, CURRENT_TIMESTAMP), 68.00, 'available');

INSERT INTO hx_session_seats (session_id, seat_id, status)
SELECT 1, id, 'available' FROM hx_seats WHERE hall_id = 1;

INSERT INTO hx_session_seats (session_id, seat_id, status)
SELECT 2, id, 'available' FROM hx_seats WHERE hall_id = 1;

INSERT INTO hx_session_seats (session_id, seat_id, status)
SELECT 3, id, 'available' FROM hx_seats WHERE hall_id = 2;
