package com.codeintel.history;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "git_history_file_changes")
public class GitFileChangeRecord {
    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "commit_id", nullable = false)
    private GitCommitRecord commit;

    @Column(nullable = false, length = 1000)
    private String path;

    @Column(length = 30)
    private String status;

    @Column(nullable = false)
    private int additions;

    @Column(nullable = false)
    private int deletions;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
    }

    public String getId() { return id; }
    public GitCommitRecord getCommit() { return commit; }
    public String getPath() { return path; }
    public String getStatus() { return status; }
    public int getAdditions() { return additions; }
    public int getDeletions() { return deletions; }

    public void setCommit(GitCommitRecord value) { commit = value; }
    public void setPath(String value) { path = value; }
    public void setStatus(String value) { status = value; }
    public void setAdditions(int value) { additions = value; }
    public void setDeletions(int value) { deletions = value; }
}
