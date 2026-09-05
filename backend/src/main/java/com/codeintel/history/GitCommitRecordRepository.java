package com.codeintel.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GitCommitRecordRepository extends JpaRepository<GitCommitRecord, String> {
    Optional<GitCommitRecord> findByProject_IdAndSha(String projectId, String sha);
    List<GitCommitRecord> findTop40ByProject_IdOrderByCommittedAtDesc(String projectId);
    long countByProject_Id(String projectId);
}
