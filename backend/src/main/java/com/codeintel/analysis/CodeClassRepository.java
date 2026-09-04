package com.codeintel.analysis;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CodeClassRepository extends JpaRepository<CodeClass, Long> {
    List<CodeClass> findAllByProject_IdOrderByQualifiedName(String projectId);
    Page<CodeClass> findAllByProject_IdOrderByQualifiedName(String projectId, Pageable pageable);
    Optional<CodeClass> findByIdAndProject_Id(Long id, String projectId);
    void deleteAllByProject_Id(String projectId);
}
