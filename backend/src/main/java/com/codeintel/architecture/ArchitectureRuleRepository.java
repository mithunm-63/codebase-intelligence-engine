package com.codeintel.architecture;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ArchitectureRuleRepository extends JpaRepository<ArchitectureRule, Long> {
    List<ArchitectureRule> findAllByProject_IdOrderBySourceLayerAscTargetLayerAsc(String projectId);
    void deleteAllByProject_Id(String projectId);
}
