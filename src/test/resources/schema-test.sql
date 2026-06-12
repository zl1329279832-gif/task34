-- H2兼容MySQL语法的测试建表脚本

CREATE TABLE IF NOT EXISTS score_rank (
    id INT AUTO_INCREMENT PRIMARY KEY,
    score VARCHAR(10),
    num INT,
    `rank` INT,
    rank_range VARCHAR(100),
    batch_name VARCHAR(50),
    control_score INT
);

CREATE TABLE IF NOT EXISTS school_info (
    school_id INT PRIMARY KEY,
    school_name VARCHAR(200),
    central INT,
    belongs VARCHAR(100),
    city_name VARCHAR(50),
    county_name VARCHAR(50),
    province_name VARCHAR(50),
    doublehigh VARCHAR(20),
    is211 INT,
    is985 INT,
    degree VARCHAR(50),
    owner VARCHAR(100),
    school_level VARCHAR(50),
    school_type VARCHAR(50),
    month_view INT,
    total_view VARCHAR(20)
);

CREATE TABLE IF NOT EXISTS school_info_detail (
    school_id INT PRIMARY KEY,
    school_name VARCHAR(200),
    num_subject INT,
    num_master INT,
    num_doctor INT,
    num_academician INT,
    create_date INT,
    ruanke_rank INT,
    xyh_rank INT,
    us_rank INT,
    email VARCHAR(100),
    phone VARCHAR(50),
    school_site VARCHAR(200),
    content TEXT,
    job VARCHAR(50),
    postgraduate VARCHAR(50),
    abroad VARCHAR(50),
    men_rate VARCHAR(50),
    female_rate VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS sc_li_score (
    id INT AUTO_INCREMENT PRIMARY KEY,
    school_id INT,
    school_name VARCHAR(200),
    score2022 INT,
    rank2022 INT,
    score2021 INT,
    rank2021 INT,
    score2020 INT,
    rank2020 INT
);

CREATE TABLE IF NOT EXISTS major_info (
    major_id INT PRIMARY KEY,
    type VARCHAR(50),
    major_name VARCHAR(200),
    major_code VARCHAR(50),
    level VARCHAR(20),
    years VARCHAR(10),
    degree VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS major_score (
    id INT AUTO_INCREMENT PRIMARY KEY,
    school_id INT,
    major_id INT,
    major_name VARCHAR(200),
    max INT,
    min INT,
    average INT,
    min_section INT,
    province_id INT,
    level VARCHAR(50),
    level1 VARCHAR(50),
    level2 VARCHAR(50),
    level3 VARCHAR(50),
    batch VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS `user` (
    id INT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50),
    password VARCHAR(50)
);

CREATE TABLE IF NOT EXISTS user_voluntary (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_name VARCHAR(50),
    school_name VARCHAR(200),
    major_a VARCHAR(200),
    major_b VARCHAR(200),
    major_c VARCHAR(200),
    major_d VARCHAR(200),
    major_e VARCHAR(200),
    major_f VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS voluntary_plan (
    id INT AUTO_INCREMENT PRIMARY KEY,
    plan_name VARCHAR(100) NOT NULL,
    user_name VARCHAR(50) NOT NULL,
    score INT NOT NULL,
    user_rank INT NOT NULL,
    subject_type VARCHAR(20) NOT NULL,
    region_pref VARCHAR(500),
    school_tier VARCHAR(100),
    major_pref VARCHAR(500),
    batch_name VARCHAR(50),
    version INT NOT NULL DEFAULT 1,
    parent_id INT,
    status TINYINT NOT NULL DEFAULT 0,
    total_risk_score DECIMAL(5,2),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS plan_school (
    id INT AUTO_INCREMENT PRIMARY KEY,
    plan_id INT NOT NULL,
    school_id INT NOT NULL,
    school_name VARCHAR(100) NOT NULL,
    category VARCHAR(10) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    admission_prob DECIMAL(5,2) NOT NULL,
    major_adjust_risk DECIMAL(5,2) NOT NULL,
    popularity_score DECIMAL(5,2),
    popularity_trend VARCHAR(10),
    rank_fluctuation VARCHAR(500),
    avg_rank_3yr DECIMAL(10,2),
    rank_std_dev DECIMAL(10,2),
    rank_trend VARCHAR(10),
    selected_majors VARCHAR(500),
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_plan_school_plan FOREIGN KEY (plan_id) REFERENCES voluntary_plan(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS plan_version_log (
    id INT AUTO_INCREMENT PRIMARY KEY,
    plan_id INT NOT NULL,
    version INT NOT NULL,
    snapshot_data TEXT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_version_log_plan FOREIGN KEY (plan_id) REFERENCES voluntary_plan(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS simulation_batch (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    user_name        VARCHAR(50)  NOT NULL,
    source_plan_id   INT          NOT NULL,
    batch_name       VARCHAR(100) NOT NULL,
    sequence_number  INT          NOT NULL DEFAULT 1,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    total_tasks      INT          NOT NULL DEFAULT 0,
    completed_tasks  INT          NOT NULL DEFAULT 0,
    failed_tasks     INT          NOT NULL DEFAULT 0,
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sim_batch_plan FOREIGN KEY (source_plan_id) REFERENCES voluntary_plan(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS simulation_task (
    id               INT AUTO_INCREMENT PRIMARY KEY,
    batch_id         INT          NOT NULL,
    task_label       VARCHAR(100),
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    sequence_number  INT          NOT NULL DEFAULT 0,
    param_score      INT,
    param_user_rank  INT,
    param_batch_name VARCHAR(50),
    param_region_pref VARCHAR(500),
    param_school_tier VARCHAR(100),
    param_major_pref  VARCHAR(500),
    risk_snapshot    TEXT,
    result_data      TEXT,
    total_risk_score DECIMAL(5,2),
    reach_count      INT,
    match_count      INT,
    safety_count     INT,
    error_message    VARCHAR(500),
    compute_start    DATETIME,
    compute_end      DATETIME,
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sim_task_batch FOREIGN KEY (batch_id) REFERENCES simulation_batch(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS simulation_task_school (
    id                    INT AUTO_INCREMENT PRIMARY KEY,
    task_id               INT          NOT NULL,
    school_id             INT          NOT NULL,
    school_name           VARCHAR(100) NOT NULL,
    category              VARCHAR(10)  NOT NULL,
    sort_order            INT          NOT NULL DEFAULT 0,
    admission_prob        DECIMAL(5,2) NOT NULL,
    admission_prob_level  VARCHAR(10)  NOT NULL,
    major_adjust_risk     DECIMAL(5,2) NOT NULL,
    major_adjust_risk_level VARCHAR(10) NOT NULL,
    popularity_score      DECIMAL(5,2),
    popularity_trend      VARCHAR(10),
    rank_fluctuation      VARCHAR(500),
    avg_rank_3yr          DECIMAL(10,2),
    rank_std_dev          DECIMAL(10,2),
    rank_trend            VARCHAR(10),
    selected_majors       VARCHAR(500),
    prob_rank_ratio       DECIMAL(8,4),
    prob_stability_factor DECIMAL(5,4),
    prob_category_adj     VARCHAR(100),
    prob_base_value       DECIMAL(5,2),
    prob_explanation       VARCHAR(1000),
    source_admission_prob DECIMAL(5,2),
    source_category       VARCHAR(10),
    prob_delta            DECIMAL(5,2),
    category_changed      TINYINT      NOT NULL DEFAULT 0,
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sts_task FOREIGN KEY (task_id) REFERENCES simulation_task(id) ON DELETE CASCADE
);
