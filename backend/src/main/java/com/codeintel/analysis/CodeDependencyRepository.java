package com.codeintel.analysis;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CodeDependencyRepository extends JpaRepository<CodeDependency, Long> {

    @EntityGraph(attributePaths = {"sourceClass", "targetClass"})
    List<CodeDependency> findAllBySourceClass_Project_Id(String projectId);

    @EntityGraph(attributePaths = {"sourceClass", "targetClass"})
    List<CodeDependency> findAllBySourceClass_Id(Long classId);

    @EntityGraph(attributePaths = {"sourceClass", "targetClass"})
    List<CodeDependency> findAllByTargetClass_Id(Long classId);

    void deleteAllBySourceClass_Project_Id(String projectId);
}
