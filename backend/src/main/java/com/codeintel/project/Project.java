package com.codeintel.project;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private SourceType sourceType;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ProjectStatus status = ProjectStatus.CREATED;

    @Column(name = "repository_size_bytes")
    private Long repositorySizeBytes;

    @Column(name = "total_files")
    private Integer totalFiles;

    @Column(name = "java_files")
    private Integer javaFiles;

    @Column(name = "main_java_files")
    private Integer mainJavaFiles;

    @Column(name = "test_java_files")
    private Integer testJavaFiles;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @PrePersist
    void onCreate() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public SourceType getSourceType() { return sourceType; }
    public String getSourceUrl() { return sourceUrl; }
    public ProjectStatus getStatus() { return status; }
    public Long getRepositorySizeBytes() { return repositorySizeBytes; }
    public Integer getTotalFiles() { return totalFiles; }
    public Integer getJavaFiles() { return javaFiles; }
    public Integer getMainJavaFiles() { return mainJavaFiles; }
    public Integer getTestJavaFiles() { return testJavaFiles; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getErrorMessage() { return errorMessage; }

    public void setName(String name) { this.name = name; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public void setRepositorySizeBytes(Long repositorySizeBytes) { this.repositorySizeBytes = repositorySizeBytes; }
    public void setTotalFiles(Integer totalFiles) { this.totalFiles = totalFiles; }
    public void setJavaFiles(Integer javaFiles) { this.javaFiles = javaFiles; }
    public void setMainJavaFiles(Integer mainJavaFiles) { this.mainJavaFiles = mainJavaFiles; }
    public void setTestJavaFiles(Integer testJavaFiles) { this.testJavaFiles = testJavaFiles; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
