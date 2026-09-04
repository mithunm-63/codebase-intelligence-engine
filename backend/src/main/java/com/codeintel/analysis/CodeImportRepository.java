package com.codeintel.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeImportRepository extends JpaRepository<CodeImport, Long> {
    void deleteAllByProject_Id(String projectId);
}
