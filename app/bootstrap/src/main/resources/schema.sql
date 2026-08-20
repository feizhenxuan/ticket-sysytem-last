DROP TABLE IF EXISTS hx_admin_logs;
DROP TABLE IF EXISTS hx_chat_sessions;
DROP TABLE IF EXISTS hx_session_seats;
DROP TABLE IF EXISTS hx_orders;
DROP TABLE IF EXISTS hx_sessions;
DROP TABLE IF EXISTS hx_seats;
DROP TABLE IF EXISTS hx_halls;
DROP TABLE IF EXISTS hx_cinemas;
DROP TABLE IF EXISTS hx_movies;
DROP TABLE IF EXISTS hx_admins;
DROP TABLE IF EXISTS hx_users;

CREATE TABLE hx_users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modify TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE hx_admins (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(64) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL DEFAULT 'admin',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modify TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE hx_movies (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    rating DECIMAL(3,1),
    duration INT,
    genre VARCHAR(128),
    director VARCHAR(128),
    actors VARCHAR(512),
    release_date DATE,
    poster_url VARCHAR(1024),
    description TEXT,
    status VARCHAR(32) NOT NULL,
    tmdb_id INT,
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE hx_cinemas (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    address VARCHAR(512),
    longitude DECIMAL(12,8),
    latitude DECIMAL(12,8),
    phone VARCHAR(64),
    city VARCHAR(64),
    poi_id VARCHAR(128),
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE hx_halls (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    cinema_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    hall_type VARCHAR(32) NOT NULL,
    total_rows INT NOT NULL,
    total_cols INT NOT NULL,
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE hx_seats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    hall_id BIGINT NOT NULL,
    row_num INT NOT NULL,
    col_num INT NOT NULL,
    seat_type VARCHAR(32) NOT NULL,
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (hall_id, row_num, col_num)
);

CREATE TABLE hx_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    movie_id BIGINT NOT NULL,
    cinema_id BIGINT NOT NULL,
    hall_id BIGINT NOT NULL,
    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE hx_orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    seat_ids TEXT NOT NULL,
    ticket_count INT NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(32) NOT NULL,
    trade_no VARCHAR(128),
    pickup_code VARCHAR(32),
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    paid_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    refunded_at TIMESTAMP
);

CREATE TABLE hx_session_seats (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'available',
    locked_by_order_id BIGINT,
    locked_at TIMESTAMP,
    gmt_modify TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (session_id, seat_id)
);

CREATE TABLE hx_chat_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(128) NOT NULL UNIQUE,
    user_id INT,
    slots TEXT,
    last_intent VARCHAR(64),
    context TEXT,
    messages TEXT,
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_modify TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    gmt_expire TIMESTAMP
);

CREATE TABLE hx_admin_logs (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    admin_id BIGINT,
    admin_username VARCHAR(64),
    module VARCHAR(64),
    action VARCHAR(64),
    target_id BIGINT,
    target_name VARCHAR(128),
    request_path VARCHAR(512),
    request_method VARCHAR(16),
    status VARCHAR(32),
    detail TEXT,
    gmt_create TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
