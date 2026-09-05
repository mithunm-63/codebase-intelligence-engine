package com.codeintel.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface GitFileChangeRecordRepository extends JpaRepository<GitFileChangeRecord, String> {
    List<GitFileChangeRecord> findByCommit_Project_Id(String projectId);
    List<GitFileChangeRecord> findByCommit_IdIn(Collection<String> commitIds);
}
