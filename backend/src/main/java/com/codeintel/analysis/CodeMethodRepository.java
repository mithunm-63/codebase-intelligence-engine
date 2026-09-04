package com.codeintel.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CodeMethodRepository extends JpaRepository<CodeMethod, Long> {
    void deleteAllByCodeClass_Project_Id(String projectId);
    List<CodeMethod> findAllByCodeClass_IdOrderByStartLine(Long classId);
}
