package com.codeintel.analysis;

import com.codeintel.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "code_imports")
public class CodeImport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "source_path", nullable = false, length = 800)
    private String sourcePath;

    @Column(name = "import_name", nullable = false, length = 1000)
    private String importName;

    @Column(name = "static_import", nullable = false)
    private boolean staticImport;

    @Column(name = "wildcard", nullable = false)
    private boolean wildcard;

    @Column(nullable = false)
    private int line;

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public String getSourcePath() { return sourcePath; }
    public String getImportName() { return importName; }
    public boolean isStaticImport() { return staticImport; }
    public boolean isWildcard() { return wildcard; }
    public int getLine() { return line; }

    public void setProject(Project v) { project = v; }
    public void setSourcePath(String v) { sourcePath = v; }
    public void setImportName(String v) { importName = v; }
    public void setStaticImport(boolean v) { staticImport = v; }
    public void setWildcard(boolean v) { wildcard = v; }
    public void setLine(int v) { line = v; }
}
