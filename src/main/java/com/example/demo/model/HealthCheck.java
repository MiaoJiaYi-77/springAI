package com.example.demo.model;

import java.time.LocalDate;

/**
 * 体检指标实体类
 */
public class HealthCheck {
    private Long id;
    private String personName;
    private String gender;
    private Integer age;
    private LocalDate checkDate;
    private Double wbcCount;
    private Double neutrophilPct;
    private Double lymphocytePct;
    private Double monocytePct;
    private Double neutrophilCount;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPersonName() {
        return personName;
    }

    public void setPersonName(String personName) {
        this.personName = personName;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public LocalDate getCheckDate() {
        return checkDate;
    }

    public void setCheckDate(LocalDate checkDate) {
        this.checkDate = checkDate;
    }

    public Double getWbcCount() {
        return wbcCount;
    }

    public void setWbcCount(Double wbcCount) {
        this.wbcCount = wbcCount;
    }

    public Double getNeutrophilPct() {
        return neutrophilPct;
    }

    public void setNeutrophilPct(Double neutrophilPct) {
        this.neutrophilPct = neutrophilPct;
    }

    public Double getLymphocytePct() {
        return lymphocytePct;
    }

    public void setLymphocytePct(Double lymphocytePct) {
        this.lymphocytePct = lymphocytePct;
    }

    public Double getMonocytePct() {
        return monocytePct;
    }

    public void setMonocytePct(Double monocytePct) {
        this.monocytePct = monocytePct;
    }

    public Double getNeutrophilCount() {
        return neutrophilCount;
    }

    public void setNeutrophilCount(Double neutrophilCount) {
        this.neutrophilCount = neutrophilCount;
    }
} 