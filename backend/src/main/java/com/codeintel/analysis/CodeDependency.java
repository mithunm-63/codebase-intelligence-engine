package com.codeintel.analysis;

import com.codeintel.dependency.DependencyType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "code_dependencies",
        indexes = {
                @Index(name = "idx_dependency_source", columnList = "source_class_id"),
                @Index(name = "idx_dependency_target", columnList = "target_class_id"),
                @Index(name = "idx_dependency_type", columnList = "dependency_type")
        },
        uniqueConstraints = @UniqueConstraint(
                name = "uk_dependency_edge",
                columnNames = {"source_class_id", "target_class_id", "dependency_type"}
        )
)
public class CodeDependency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_class_id", nullable = false)
    private CodeClass sourceClass;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_class_id", nullable = false)
    private CodeClass targetClass;

    @Enumerated(EnumType.STRING)
    @Column(name = "dependency_type", nullable = false, length = 40)
    private DependencyType type;

    @Column(name = "source_line", nullable = false)
    private int sourceLine;

    @Column(name = "source_member", length = 500)
    private String sourceMember;

    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount;

    @Column(name = "evidence", length = 1200)
    private String evidence;

    public Long getId() { return id; }
    public CodeClass getSourceClass() { return sourceClass; }
    public CodeClass getTargetClass() { return targetClass; }
    public DependencyType getType() { return type; }
    public int getSourceLine() { return sourceLine; }
    public String getSourceMember() { return sourceMember; }
    public int getOccurrenceCount() { return occurrenceCount; }
    public String getEvidence() { return evidence; }

    public void setSourceClass(CodeClass value) { sourceClass = value; }
    public void setTargetClass(CodeClass value) { targetClass = value; }
    public void setType(DependencyType value) { type = value; }
    public void setSourceLine(int value) { sourceLine = value; }
    public void setSourceMember(String value) { sourceMember = value; }
    public void setOccurrenceCount(int value) { occurrenceCount = value; }
    public void setEvidence(String value) { evidence = value; }
}
