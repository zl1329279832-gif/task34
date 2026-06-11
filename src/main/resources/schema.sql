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
