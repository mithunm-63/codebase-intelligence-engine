package com.codeintel.analysis;

import jakarta.persistence.*;

@Entity
@Table(name = "code_methods")
public class CodeMethod {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "class_id", nullable = false)
    private CodeClass codeClass;
    @Column(nullable = false, length = 300) private String name;
    @Column(nullable = false, length = 20) private String kind;
    @Column(name = "return_type", length = 500) private String returnType;
    @Column(length = 1000) private String signature;
    @Column(length = 500) private String modifiers;
    @Column(length = 1000) private String annotations;
    @Column(length = 2000) private String parameters;
    @Column(name = "thrown_types", length = 1000) private String thrownTypes;
    private int startLine;
    private int endLine;
    private int lineCount;

    public Long getId() { return id; }
    public CodeClass getCodeClass() { return codeClass; }
    public String getName() { return name; }
    public String getKind() { return kind; }
    public String getReturnType() { return returnType; }
    public String getSignature() { return signature; }
    public String getModifiers() { return modifiers; }
    public String getAnnotations() { return annotations; }
    public String getParameters() { return parameters; }
    public String getThrownTypes() { return thrownTypes; }
    public int getStartLine() { return startLine; }
    public int getEndLine() { return endLine; }
    public int getLineCount() { return lineCount; }

    public void setCodeClass(CodeClass v) { codeClass = v; }
    public void setName(String v) { name = v; }
    public void setKind(String v) { kind = v; }
    public void setReturnType(String v) { returnType = v; }
    public void setSignature(String v) { signature = v; }
    public void setModifiers(String v) { modifiers = v; }
    public void setAnnotations(String v) { annotations = v; }
    public void setParameters(String v) { parameters = v; }
    public void setThrownTypes(String v) { thrownTypes = v; }
    public void setStartLine(int v) { startLine = v; }
    public void setEndLine(int v) { endLine = v; }
    public void setLineCount(int v) { lineCount = v; }
}
