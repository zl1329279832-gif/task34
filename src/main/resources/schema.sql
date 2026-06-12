-- 志愿方案主表
CREATE TABLE IF NOT EXISTS voluntary_plan (
    id              INT AUTO_INCREMENT PRIMARY KEY COMMENT '方案ID',
    plan_name       VARCHAR(100) NOT NULL COMMENT '方案名称',
    user_name       VARCHAR(50)  NOT NULL COMMENT '用户名',
    score           INT          NOT NULL COMMENT '高考分数',
    user_rank       INT          NOT NULL COMMENT '位次(排名)',
    subject_type    VARCHAR(20)  NOT NULL COMMENT '科类(理科/文科/综合)',
    region_pref     VARCHAR(500)          COMMENT '地区偏好,逗号分隔省份名(空=不限)',
    school_tier     VARCHAR(100)          COMMENT '院校层次(985/211/双一流/普通本科,逗号分隔)',
    major_pref      VARCHAR(500)          COMMENT '专业偏好,逗号分隔专业类名(空=不限)',
    batch_name      VARCHAR(50)           COMMENT '批次(一本/二本/专科)',
    version         INT          NOT NULL DEFAULT 1 COMMENT '版本号(从1递增)',
    parent_id       INT                   COMMENT '父方案ID(复制/版本链追溯)',
    status          TINYINT      NOT NULL DEFAULT 0 COMMENT '0=草稿,1=已定稿',
    total_risk_score DECIMAL(5,2)         COMMENT '综合风险评分(0-100,越低越安全)',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_name (user_name),
    INDEX idx_parent_id (parent_id),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='志愿方案主表';

-- 方案-院校明细表
CREATE TABLE IF NOT EXISTS plan_school (
    id                   INT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    plan_id              INT          NOT NULL COMMENT '所属方案ID',
    school_id            INT          NOT NULL COMMENT '院校ID',
    school_name          VARCHAR(100) NOT NULL COMMENT '院校名称',
    category             VARCHAR(10)  NOT NULL COMMENT '冲/稳/保',
    sort_order           INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    admission_prob       DECIMAL(5,2) NOT NULL COMMENT '录取概率(0-100)',
    major_adjust_risk    DECIMAL(5,2) NOT NULL COMMENT '专业调剂风险(0-100)',
    popularity_score     DECIMAL(5,2)          COMMENT '院校热度评分(0-100)',
    popularity_trend     VARCHAR(10)           COMMENT '热度趋势: rising/stable/declining',
    rank_fluctuation     VARCHAR(500)          COMMENT '位次波动解释',
    avg_rank_3yr         DECIMAL(10,2)         COMMENT '三年平均位次',
    rank_std_dev         DECIMAL(10,2)         COMMENT '位次标准差',
    rank_trend           VARCHAR(10)           COMMENT '位次趋势: rising/stable/declining',
    selected_majors      VARCHAR(500)          COMMENT '已选专业名,逗号分隔',
    create_time          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_plan_id (plan_id),
    INDEX idx_category (category),
    CONSTRAINT fk_plan_school_plan FOREIGN KEY (plan_id) REFERENCES voluntary_plan(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方案-院校明细表';

-- 方案版本快照日志
CREATE TABLE IF NOT EXISTS plan_version_log (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    plan_id         INT      NOT NULL COMMENT '方案ID',
    version         INT      NOT NULL COMMENT '版本号',
    snapshot_data   JSON              COMMENT '该版本完整快照',
    create_time     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_plan_version (plan_id, version),
    CONSTRAINT fk_version_log_plan FOREIGN KEY (plan_id) REFERENCES voluntary_plan(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='方案版本快照日志';

-- 模拟批次表
CREATE TABLE IF NOT EXISTS simulation_batch (
    id               INT AUTO_INCREMENT PRIMARY KEY COMMENT '批次ID',
    user_name        VARCHAR(50)  NOT NULL COMMENT '用户名',
    source_plan_id   INT          NOT NULL COMMENT '源方案ID',
    batch_name       VARCHAR(100) NOT NULL COMMENT '批次名称',
    sequence_number  INT          NOT NULL DEFAULT 1 COMMENT '单调递增序列号(防旧覆新)',
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/COMPUTING/COMPLETED/FAILED/CANCELLED',
    total_tasks      INT          NOT NULL DEFAULT 0 COMMENT '总任务数',
    completed_tasks  INT          NOT NULL DEFAULT 0 COMMENT '已完成数',
    failed_tasks     INT          NOT NULL DEFAULT 0 COMMENT '失败数',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sim_batch_user (user_name),
    INDEX idx_sim_batch_source (source_plan_id),
    CONSTRAINT fk_sim_batch_plan FOREIGN KEY (source_plan_id) REFERENCES voluntary_plan(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟批次表';

-- 模拟任务表
CREATE TABLE IF NOT EXISTS simulation_task (
    id               INT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
    batch_id         INT          NOT NULL COMMENT '所属批次ID',
    task_label       VARCHAR(100)          COMMENT '任务标签(如: 分数+10)',
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/COMPUTING/COMPLETED/FAILED/CANCELLED',
    sequence_number  INT          NOT NULL DEFAULT 0 COMMENT 'CAS乐观锁序列号',
    param_score      INT                   COMMENT '覆盖分数(null=继承源方案)',
    param_user_rank  INT                   COMMENT '覆盖位次',
    param_batch_name VARCHAR(50)           COMMENT '覆盖批次',
    param_region_pref VARCHAR(500)         COMMENT '覆盖地区偏好CSV',
    param_school_tier VARCHAR(100)         COMMENT '覆盖院校层次CSV',
    param_major_pref  VARCHAR(500)         COMMENT '覆盖专业偏好CSV',
    risk_snapshot    JSON                  COMMENT '冻结的风险计算输入快照',
    result_data      JSON                  COMMENT '完整计算结果',
    total_risk_score DECIMAL(5,2)          COMMENT '综合风险评分',
    reach_count      INT                   COMMENT '冲的数量',
    match_count      INT                   COMMENT '稳的数量',
    safety_count     INT                   COMMENT '保的数量',
    error_message    VARCHAR(500)          COMMENT '失败原因',
    compute_start    DATETIME              COMMENT '计算开始时间',
    compute_end      DATETIME              COMMENT '计算结束时间',
    create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sim_task_batch (batch_id),
    INDEX idx_sim_task_status (status),
    CONSTRAINT fk_sim_task_batch FOREIGN KEY (batch_id) REFERENCES simulation_batch(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟任务表';

-- 模拟任务院校明细表
CREATE TABLE IF NOT EXISTS simulation_task_school (
    id                    INT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    task_id               INT          NOT NULL COMMENT '所属任务ID',
    school_id             INT          NOT NULL COMMENT '院校ID',
    school_name           VARCHAR(100) NOT NULL COMMENT '院校名称',
    category              VARCHAR(10)  NOT NULL COMMENT '冲/稳/保',
    sort_order            INT          NOT NULL DEFAULT 0,
    admission_prob        DECIMAL(5,2) NOT NULL COMMENT '录取概率',
    admission_prob_level  VARCHAR(10)  NOT NULL COMMENT '高/中/低',
    major_adjust_risk     DECIMAL(5,2) NOT NULL COMMENT '专业调剂风险',
    major_adjust_risk_level VARCHAR(10) NOT NULL,
    popularity_score      DECIMAL(5,2),
    popularity_trend      VARCHAR(10),
    rank_fluctuation      VARCHAR(500),
    avg_rank_3yr          DECIMAL(10,2),
    rank_std_dev          DECIMAL(10,2),
    rank_trend            VARCHAR(10),
    selected_majors       VARCHAR(500),
    prob_rank_ratio       DECIMAL(8,4) COMMENT 'avgRank/userRank比值',
    prob_stability_factor DECIMAL(5,4) COMMENT '稳定性因子',
    prob_category_adj     VARCHAR(100) COMMENT '类别调整说明',
    prob_base_value       DECIMAL(5,2) COMMENT '调整前概率',
    prob_explanation       VARCHAR(1000) COMMENT '完整概率解释',
    source_admission_prob DECIMAL(5,2) COMMENT '源方案录取概率(用于对比)',
    source_category       VARCHAR(10)  COMMENT '源方案类别',
    prob_delta            DECIMAL(5,2) COMMENT '概率变化量',
    category_changed      TINYINT      NOT NULL DEFAULT 0 COMMENT '类别是否变化',
    create_time           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sts_task (task_id),
    CONSTRAINT fk_sts_task FOREIGN KEY (task_id) REFERENCES simulation_task(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模拟任务院校明细表';
