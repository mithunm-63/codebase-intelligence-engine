package com.codeintel.history;

import com.codeintel.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "git_history_commits", uniqueConstraints = @UniqueConstraint(
        name = "uk_git_history_project_sha", columnNames = {"project_id", "sha"}
))
public class GitCommitRecord {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false, length = 40)
    private String sha;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(nullable = false, length = 160)
    private String author;

    @Column(name = "committed_at")
    private Instant committedAt;

    @Column(nullable = false)
    private int additions;

    @Column(nullable = false)
    private int deletions;

    @Column(name = "changed_files", nullable = false)
    private int changedFiles;

    @Column(length = 500)
    private String url;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public Project getProject() { return project; }
    public String getSha() { return sha; }
    public String getMessage() { return message; }
    public String getAuthor() { return author; }
    public Instant getCommittedAt() { return committedAt; }
    public int getAdditions() { return additions; }
    public int getDeletions() { return deletions; }
    public int getChangedFiles() { return changedFiles; }
    public String getUrl() { return url; }

    public void setProject(Project value) { project = value; }
    public void setSha(String value) { sha = value; }
    public void setMessage(String value) { message = value; }
    public void setAuthor(String value) { author = value; }
    public void setCommittedAt(Instant value) { committedAt = value; }
    public void setAdditions(int value) { additions = value; }
    public void setDeletions(int value) { deletions = value; }
    public void setChangedFiles(int value) { changedFiles = value; }
    public void setUrl(String value) { url = value; }
}
