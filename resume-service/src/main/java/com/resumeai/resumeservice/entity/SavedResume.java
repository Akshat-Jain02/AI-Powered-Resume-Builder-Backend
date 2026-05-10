package com.resumeai.resumeservice.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(
    name = "saved_resumes",
    indexes = {
        @Index(name = "idx_saved_resumes_username", columnList = "username")
    }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavedResume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Owner username — injected by API Gateway (X-Username header) after JWT validation.
     */
    @Column(name = "username", nullable = true)
    private String username;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "template_name")
    private String templateName;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "resume_data", columnDefinition = "LONGTEXT", nullable = false)
    private String resumeData;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (updatedAt == null) updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
