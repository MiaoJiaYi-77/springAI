package com.example.demo.dao;

import com.example.demo.model.HealthCheck;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 体检指标数据访问层
 */
@Repository
public class HealthCheckDAO {

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public HealthCheckDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 根据姓名查询体检指标
     */
    public HealthCheck findByPersonName(String personName) {
        String sql = "SELECT * FROM health_check WHERE person_name = ? ORDER BY check_date DESC LIMIT 1";
        List<HealthCheck> results = jdbcTemplate.query(sql, new HealthCheckRowMapper(), personName);
        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * 保存体检指标
     */
    public int save(HealthCheck healthCheck) {
        String sql = "INSERT INTO health_check (person_name, gender, age, check_date, wbc_count, " +
                "neutrophil_pct, lymphocyte_pct, monocyte_pct, neutrophil_count) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        return jdbcTemplate.update(sql,
                healthCheck.getPersonName(),
                healthCheck.getGender(),
                healthCheck.getAge(),
                healthCheck.getCheckDate(),
                healthCheck.getWbcCount(),
                healthCheck.getNeutrophilPct(),
                healthCheck.getLymphocytePct(),
                healthCheck.getMonocytePct(),
                healthCheck.getNeutrophilCount());
    }

    /**
     * 行映射器
     */
    private static class HealthCheckRowMapper implements RowMapper<HealthCheck> {
        @Override
        public HealthCheck mapRow(ResultSet rs, int rowNum) throws SQLException {
            HealthCheck healthCheck = new HealthCheck();
            healthCheck.setId(rs.getLong("id"));
            healthCheck.setPersonName(rs.getString("person_name"));
            healthCheck.setGender(rs.getString("gender"));
            healthCheck.setAge(rs.getInt("age"));
            healthCheck.setCheckDate(rs.getDate("check_date").toLocalDate());
            healthCheck.setWbcCount(rs.getDouble("wbc_count"));
            healthCheck.setNeutrophilPct(rs.getDouble("neutrophil_pct"));
            healthCheck.setLymphocytePct(rs.getDouble("lymphocyte_pct"));
            healthCheck.setMonocytePct(rs.getDouble("monocyte_pct"));
            healthCheck.setNeutrophilCount(rs.getDouble("neutrophil_count"));
            return healthCheck;
        }
    }
} 