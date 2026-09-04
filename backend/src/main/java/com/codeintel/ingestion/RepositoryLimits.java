package com.codeintel.ingestion;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class RepositoryLimits {
    private final boolean demoMode;
    private final long maxRepositoryBytes;
    private final int maxJavaFiles;
    private final int maxFiles;

    public RepositoryLimits(
            @Value("${app.demo.mode:false}") boolean demoMode,
            @Value("${app.demo.max-repository-size-mb:50}") int maxRepositorySizeMb,
            @Value("${app.demo.max-java-files:2000}") int maxJavaFiles,
            @Value("${app.demo.max-files:10000}") int maxFiles) {
        this.demoMode = demoMode;
        this.maxRepositoryBytes = Math.multiplyExact((long) maxRepositorySizeMb, 1024L * 1024L);
        this.maxJavaFiles = maxJavaFiles;
        this.maxFiles = maxFiles;
    }

    public boolean isDemoMode() { return demoMode; }
    public long getMaxRepositoryBytes() { return maxRepositoryBytes; }
    public int getMaxJavaFiles() { return maxJavaFiles; }
    public int getMaxFiles() { return maxFiles; }
}
