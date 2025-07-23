-- 创建体检指标表
DROP TABLE IF EXISTS health_check;

CREATE TABLE health_check (
    id               SERIAL PRIMARY KEY,
    person_name      VARCHAR(100) NOT NULL,
    gender           VARCHAR(10),
    age              INTEGER,
    check_date       DATE DEFAULT CURRENT_DATE,
    wbc_count        DOUBLE PRECISION,
    neutrophil_pct   DOUBLE PRECISION,
    lymphocyte_pct   DOUBLE PRECISION,
    monocyte_pct     DOUBLE PRECISION,
    neutrophil_count DOUBLE PRECISION
); 