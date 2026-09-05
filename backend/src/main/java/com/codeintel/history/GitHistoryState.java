package com.codeintel.history;

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
@Table(name = "git_history_state", uniqueConstraints = @UniqueConstraint(
        name = "uk_git_history_state_project", columnNames = "project_id"
))
public class GitHistoryState {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "latest_commit_sha", length = 40)
    private String latestCommitSha;

    @Column(name = "branch_name", length = 120)
    private String branchName;

    @Column(name = "synced_at")
    private Instant syncedAt;

    @Column(name = "commits_stored", nullable = false)
    private long commitsStored;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (syncedAt == null) syncedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        if (syncedAt == null) syncedAt = Instant.now();
    }

    public String getId() { return id; }
    public Project getProject() { return project; }
    public String getLatestCommitSha() { return latestCommitSha; }
    public String getBranchName() { return branchName; }
    public Instant getSyncedAt() { return syncedAt; }
    public long getCommitsStored() { return commitsStored; }

    public void setProject(Project value) { project = value; }
    public void setLatestCommitSha(String value) { latestCommitSha = value; }
    public void setBranchName(String value) { branchName = value; }
    public void setSyncedAt(Instant value) { syncedAt = value; }
    public void setCommitsStored(long value) { commitsStored = value; }
}
