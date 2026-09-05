package com.codeintel.analysis;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CodeAnalysisStateRepository extends JpaRepository<CodeAnalysisState, String> {
    Optional<CodeAnalysisState> findByProject_Id(String projectId);
}
