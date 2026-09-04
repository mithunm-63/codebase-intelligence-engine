package com.codeintel.analysis;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CodeFieldRepository extends JpaRepository<CodeField, Long> {
    void deleteAllByCodeClass_Project_Id(String projectId);
    List<CodeField> findAllByCodeClass_IdOrderByLine(Long classId);
}
