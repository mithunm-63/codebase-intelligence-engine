package com.codeintel.analysis;

import com.codeintel.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "code_analysis_state", uniqueConstraints = @UniqueConstraint(
        name = "uk_code_analysis_state_project", columnNames = "project_id"
))
public class CodeAnalysisState {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "analyzed_commit_sha", length = 40)
    private String analyzedCommitSha;

    @Column(name = "analyzed_at")
    private Instant analyzedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (analyzedAt == null) analyzedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        if (analyzedAt == null) analyzedAt = Instant.now();
    }

    public String getId() { return id; }
    public Project getProject() { return project; }
    public String getAnalyzedCommitSha() { return analyzedCommitSha; }
    public Instant getAnalyzedAt() { return analyzedAt; }

    public void setProject(Project value) { project = value; }
    public void setAnalyzedCommitSha(String value) { analyzedCommitSha = value; }
    public void setAnalyzedAt(Instant value) { analyzedAt = value; }
}
