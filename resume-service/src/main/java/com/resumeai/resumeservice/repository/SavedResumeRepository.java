package com.resumeai.resumeservice.repository;

import com.resumeai.resumeservice.entity.SavedResume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedResumeRepository extends JpaRepository<SavedResume, Long> {

    /** All resumes belonging to a specific user, newest first */
    List<SavedResume> findByUsernameOrderByCreatedAtDesc(String username);

    /** All resumes (admin use) */
    List<SavedResume> findAllByOrderByCreatedAtDesc();

    /** Find by id and username — prevents users from accessing other users' resumes */
    Optional<SavedResume> findByIdAndUsername(Long id, String username);

    List<SavedResume> findByTemplateIdOrderByCreatedAtDesc(Long templateId);
}
