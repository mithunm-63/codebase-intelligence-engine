package com.codeintel.history;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GitHistoryStateRepository extends JpaRepository<GitHistoryState, String> {
    Optional<GitHistoryState> findByProject_Id(String projectId);
}
