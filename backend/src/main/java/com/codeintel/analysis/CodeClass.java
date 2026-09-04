package com.codeintel.analysis;

import com.codeintel.project.Project;
import jakarta.persistence.*;

@Entity
@Table(name = "code_classes", uniqueConstraints = @UniqueConstraint(name = "uk_code_class_project_fqn", columnNames = {"project_id", "qualified_name"}))
public class CodeClass {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "name", nullable = false, length = 300)
    private String name;
    @Column(name = "qualified_name", nullable = false, length = 500)
    private String qualifiedName;
    @Column(name = "kind", nullable = false, length = 20)
    private String kind;
    @Column(name = "source_path", nullable = false, length = 800)
    private String sourcePath;
    @Column(name = "modifiers", length = 500)
    private String modifiers;
    @Column(name = "annotations", length = 1000)
    private String annotations;
    private int startLine;
    private int endLine;
    private int lineCount;
    private int methodCount;
    private int constructorCount;
    private int fieldCount;

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public String getName() { return name; }
    public String getQualifiedName() { return qualifiedName; }
    public String getKind() { return kind; }
    public String getSourcePath() { return sourcePath; }
    public String getModifiers() { return modifiers; }
    public String getAnnotations() { return annotations; }
    public int getStartLine() { return startLine; }
    public int getEndLine() { return endLine; }
    public int getLineCount() { return lineCount; }
    public int getMethodCount() { return methodCount; }
    public int getConstructorCount() { return constructorCount; }
    public int getFieldCount() { return fieldCount; }

    public void setProject(Project v) { project = v; }
    public void setName(String v) { name = v; }
    public void setQualifiedName(String v) { qualifiedName = v; }
    public void setKind(String v) { kind = v; }
    public void setSourcePath(String v) { sourcePath = v; }
    public void setModifiers(String v) { modifiers = v; }
    public void setAnnotations(String v) { annotations = v; }
    public void setStartLine(int v) { startLine = v; }
    public void setEndLine(int v) { endLine = v; }
    public void setLineCount(int v) { lineCount = v; }
    public void setMethodCount(int v) { methodCount = v; }
    public void setConstructorCount(int v) { constructorCount = v; }
    public void setFieldCount(int v) { fieldCount = v; }
}
