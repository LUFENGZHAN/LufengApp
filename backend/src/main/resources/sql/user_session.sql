CREATE TABLE user_session
(
    id BIGINT PRIMARY KEY AUTO_INCREMENT,


    user_id BIGINT NOT NULL,


    device_type VARCHAR(20)
        COMMENT 'pc/app',


    token VARCHAR(255) NOT NULL,


    device_id VARCHAR(128),


    login_time DATETIME,


    last_active_time DATETIME,


    UNIQUE KEY uk_user_device(user_id,device_type)

);