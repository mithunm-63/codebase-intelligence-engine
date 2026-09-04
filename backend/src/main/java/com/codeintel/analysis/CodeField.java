package com.codeintel.analysis;

import jakarta.persistence.*;

@Entity
@Table(name = "code_fields")
public class CodeField {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private CodeClass codeClass;
    @Column(nullable = false, length = 300) private String name;
    @Column(nullable = false, length = 500) private String type;
    @Column(length = 500) private String modifiers;
    @Column(length = 1000) private String annotations;
    private int line;

    public Long getId() { return id; }
    public CodeClass getCodeClass() { return codeClass; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getModifiers() { return modifiers; }
    public String getAnnotations() { return annotations; }
    public int getLine() { return line; }

    public void setCodeClass(CodeClass v) { codeClass = v; }
    public void setName(String v) { name = v; }
    public void setType(String v) { type = v; }
    public void setModifiers(String v) { modifiers = v; }
    public void setAnnotations(String v) { annotations = v; }
    public void setLine(int v) { line = v; }
}
