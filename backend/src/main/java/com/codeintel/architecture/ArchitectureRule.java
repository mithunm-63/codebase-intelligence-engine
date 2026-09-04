package com.codeintel.architecture;

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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "architecture_rules", uniqueConstraints = @UniqueConstraint(
        name = "uk_arch_rule_project_layers",
        columnNames = {"project_id", "source_layer", "target_layer"}
))
public class ArchitectureRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "source_layer", nullable = false, length = 50)
    private String sourceLayer;

    @Column(name = "target_layer", nullable = false, length = 50)
    private String targetLayer;

    @Column(nullable = false)
    private boolean allowed;

    @Column(nullable = false, length = 20)
    private String severity = "HIGH";

    @Column(length = 500)
    private String description;

    public Long getId() { return id; }
    public Project getProject() { return project; }
    public String getSourceLayer() { return sourceLayer; }
    public String getTargetLayer() { return targetLayer; }
    public boolean isAllowed() { return allowed; }
    public String getSeverity() { return severity; }
    public String getDescription() { return description; }

    public void setProject(Project value) { project = value; }
    public void setSourceLayer(String value) { sourceLayer = value; }
    public void setTargetLayer(String value) { targetLayer = value; }
    public void setAllowed(boolean value) { allowed = value; }
    public void setSeverity(String value) { severity = value; }
    public void setDescription(String value) { description = value; }
}
