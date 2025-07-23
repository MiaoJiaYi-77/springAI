package com.example.demo.service;

import com.example.demo.model.HealthCheck;
import com.example.demo.dao.HealthCheckDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 体检指标服务层
 */
@Service
public class HealthCheckService {

    private final HealthCheckDAO healthCheckDAO;

    @Autowired
    public HealthCheckService(HealthCheckDAO healthCheckDAO) {
        this.healthCheckDAO = healthCheckDAO;
    }

    /**
     * 根据姓名获取体检指标
     */
    public HealthCheck getHealthCheckByPersonName(String personName) {
        return healthCheckDAO.findByPersonName(personName);
    }

    /**
     * 保存体检指标
     */
    public boolean saveHealthCheck(HealthCheck healthCheck) {
        return healthCheckDAO.save(healthCheck) > 0;
    }
} 