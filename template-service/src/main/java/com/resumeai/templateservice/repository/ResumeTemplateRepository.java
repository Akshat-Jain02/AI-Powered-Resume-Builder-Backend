package com.resumeai.templateservice.repository;

import com.resumeai.templateservice.entity.ResumeTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ResumeTemplateRepository extends JpaRepository<ResumeTemplate, Long> {

    List<ResumeTemplate> findByIsActiveTrue();

    List<ResumeTemplate> findByCategoryIgnoreCase(String category);

    List<ResumeTemplate> findByIsActiveTrueOrderByUsageCountDesc();
}
