package com.codeintel.project;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private SourceType sourceType;

    @Column(name = "source_url", length = 500)
    private String sourceUrl;

    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private ProjectStatus status = ProjectStatus.CREATED;

    @Column(name = "repository_size_bytes")
    private Long repositorySizeBytes;

    @Column(name = "total_files")
    private Integer totalFiles;

    @Column(name = "java_files")
    private Integer javaFiles;

    @Column(name = "main_java_files")
    private Integer mainJavaFiles;

    @Column(name = "test_java_files")
    private Integer testJavaFiles;

    @Column(name = "class_count")
    private Integer classCount;

    @Column(name = "interface_count")
    private Integer interfaceCount;

    @Column(name = "enum_count")
    private Integer enumCount;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "annotation_count")
    private Integer annotationCount;

    @Column(name = "method_count")
    private Integer methodCount;

    @Column(name = "constructor_count")
    private Integer constructorCount;

    @Column(name = "field_count")
    private Integer fieldCount;

    @Column(name = "import_count")
    private Integer importCount;

    @Column(name = "dependency_count")
    private Integer dependencyCount;

    @Column(name = "dependency_occurrence_count")
    private Integer dependencyOccurrenceCount;

    @Column(name = "unresolved_reference_count")
    private Integer unresolvedReferenceCount;

    @Lob
    @Column(name = "unresolved_references")
    private String unresolvedReferences;

    @Column(name = "parse_error_count")
    private Integer parseErrorCount;

    @Lob
    @Column(name = "parse_errors")
    private String parseErrors;

    @Column(name = "ast_analyzed_at")
    private Instant astAnalyzedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    public String getId() { return id; }
    public String getName() { return name; }
    public SourceType getSourceType() { return sourceType; }
    public String getSourceUrl() { return sourceUrl; }
    public ProjectStatus getStatus() { return status; }
    public Long getRepositorySizeBytes() { return repositorySizeBytes; }
    public Integer getTotalFiles() { return totalFiles; }
    public Integer getJavaFiles() { return javaFiles; }
    public Integer getMainJavaFiles() { return mainJavaFiles; }
    public Integer getTestJavaFiles() { return testJavaFiles; }
    public Integer getClassCount() { return classCount; }
    public Integer getInterfaceCount() { return interfaceCount; }
    public Integer getEnumCount() { return enumCount; }
    public Integer getRecordCount() { return recordCount; }
    public Integer getAnnotationCount() { return annotationCount; }
    public Integer getMethodCount() { return methodCount; }
    public Integer getConstructorCount() { return constructorCount; }
    public Integer getFieldCount() { return fieldCount; }
    public Integer getImportCount() { return importCount; }
    public Integer getDependencyCount() { return dependencyCount; }
    public Integer getDependencyOccurrenceCount() { return dependencyOccurrenceCount; }
    public Integer getUnresolvedReferenceCount() { return unresolvedReferenceCount; }
    public String getUnresolvedReferences() { return unresolvedReferences; }
    public Integer getParseErrorCount() { return parseErrorCount; }
    public String getParseErrors() { return parseErrors; }
    public Instant getAstAnalyzedAt() { return astAnalyzedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getErrorMessage() { return errorMessage; }

    public void setName(String name) { this.name = name; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public void setRepositorySizeBytes(Long v) { repositorySizeBytes = v; }
    public void setTotalFiles(Integer v) { totalFiles = v; }
    public void setJavaFiles(Integer v) { javaFiles = v; }
    public void setMainJavaFiles(Integer v) { mainJavaFiles = v; }
    public void setTestJavaFiles(Integer v) { testJavaFiles = v; }
    public void setClassCount(Integer v) { classCount = v; }
    public void setInterfaceCount(Integer v) { interfaceCount = v; }
    public void setEnumCount(Integer v) { enumCount = v; }
    public void setRecordCount(Integer v) { recordCount = v; }
    public void setAnnotationCount(Integer v) { annotationCount = v; }
    public void setMethodCount(Integer v) { methodCount = v; }
    public void setConstructorCount(Integer v) { constructorCount = v; }
    public void setFieldCount(Integer v) { fieldCount = v; }
    public void setImportCount(Integer v) { importCount = v; }
    public void setDependencyCount(Integer v) { dependencyCount = v; }
    public void setDependencyOccurrenceCount(Integer v) { dependencyOccurrenceCount = v; }
    public void setUnresolvedReferenceCount(Integer v) { unresolvedReferenceCount = v; }
    public void setUnresolvedReferences(String v) { unresolvedReferences = v; }
    public void setParseErrorCount(Integer v) { parseErrorCount = v; }
    public void setParseErrors(String v) { parseErrors = v; }
    public void setAstAnalyzedAt(Instant v) { astAnalyzedAt = v; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
