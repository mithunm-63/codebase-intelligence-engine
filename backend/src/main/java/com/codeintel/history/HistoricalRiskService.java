package com.codeintel.history;

import com.codeintel.analysis.CodeClass;
import com.codeintel.analysis.CodeClassRepository;
import com.codeintel.project.ProjectRepository;
import com.codeintel.risk.RiskAnalysisService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
public class HistoricalRiskService {
    private final ProjectRepository projectRepository;
    private final CodeClassRepository classRepository;
    private final RiskAnalysisService riskAnalysisService;
    private final GitCommitRecordRepository commitRepository;
    private final GitFileChangeRecordRepository fileChangeRepository;

    public HistoricalRiskService(ProjectRepository projectRepository,
                                 CodeClassRepository classRepository,
                                 RiskAnalysisService riskAnalysisService,
                                 GitCommitRecordRepository commitRepository,
                                 GitFileChangeRecordRepository fileChangeRepository) {
        this.projectRepository = projectRepository;
        this.classRepository = classRepository;
        this.riskAnalysisService = riskAnalysisService;
        this.commitRepository = commitRepository;
        this.fileChangeRepository = fileChangeRepository;
    }

    public HistoricalRiskReport analyze(String projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found.");
        }

        List<GitCommitRecord> commits = commitRepository.findTop40ByProject_IdOrderByCommittedAtDesc(projectId);
        if (commits.isEmpty()) {
            return empty(projectId);
        }

        Set<String> commitIds = new HashSet<>();
        for (GitCommitRecord commit : commits) commitIds.add(commit.getId());
        List<GitFileChangeRecord> changes = fileChangeRepository.findByCommit_IdIn(commitIds);

        Map<String, FileStats> byPath = new HashMap<>();
        Map<String, FileStats> recentByPath = new HashMap<>();
        Map<String, FileStats> olderByPath = new HashMap<>();
        Set<String> recentIds = new HashSet<>();
        int split = Math.max(1, (commits.size() + 1) / 2);
        for (int i = 0; i < commits.size(); i++) {
            if (i < split) recentIds.add(commits.get(i).getId());
        }

        for (GitFileChangeRecord change : changes) {
            String path = normalize(change.getPath());
            FileStats stats = byPath.computeIfAbsent(path, FileStats::new);
            stats.record(change);
            Map<String, FileStats> bucket = recentIds.contains(change.getCommit().getId()) ? recentByPath : olderByPath;
            bucket.computeIfAbsent(path, FileStats::new).record(change);
        }

        Map<String, RiskAnalysisService.Hotspot> riskByPath = new HashMap<>();
        RiskAnalysisService.ProjectRiskReport risk = riskAnalysisService.analyzeProject(projectId);
        for (RiskAnalysisService.Hotspot hotspot : risk.hotspots()) {
            classRepository.findByIdAndProject_Id(Long.valueOf(hotspot.classId()), projectId)
                    .ifPresent(c -> riskByPath.put(normalize(c.getSourcePath()), hotspot));
        }

        List<ClassHistoryRisk> classes = new ArrayList<>();
        for (CodeClass codeClass : classRepository.findAllByProject_IdOrderByQualifiedName(projectId)) {
            FileStats all = byPath.get(normalize(codeClass.getSourcePath()));
            if (all == null) continue;
            FileStats recent = recentByPath.getOrDefault(normalize(codeClass.getSourcePath()), new FileStats(codeClass.getSourcePath()));
            FileStats older = olderByPath.getOrDefault(normalize(codeClass.getSourcePath()), new FileStats(codeClass.getSourcePath()));
            RiskAnalysisService.Hotspot hotspot = riskByPath.get(normalize(codeClass.getSourcePath()));
            int currentScore = hotspot == null ? 0 : hotspot.riskScore();
            int changePressure = pressure(all.commits, all.churn);
            int recentPressure = pressure(recent.commits, recent.churn);
            int olderPressure = pressure(older.commits, older.churn);
            int combined = Math.min(100, Math.round(currentScore * 0.65f + changePressure * 0.35f));
            String trend = trend(recentPressure, olderPressure);
            String priority = combined >= 75 ? "CRITICAL" : combined >= 55 ? "HIGH" : combined >= 35 ? "MEDIUM" : "LOW";
            List<String> factors = new ArrayList<>();
            if (currentScore >= 70) factors.add("current structural risk is " + currentScore + "/100");
            if (all.commits >= 5) factors.add(all.commits + " commits touched this file");
            if (all.churn >= 100) factors.add(all.churn + " changed lines in sampled history");
            if ("RISING".equals(trend)) factors.add("recent change pressure is rising");
            if (factors.isEmpty()) factors.add("combined score is driven by current risk and historical change pressure");
            classes.add(new ClassHistoryRisk(String.valueOf(codeClass.getId()), codeClass.getName(), codeClass.getQualifiedName(),
                    codeClass.getSourcePath(), currentScore, changePressure, combined, priority,
                    all.commits, all.additions, all.deletions, all.churn, recentPressure, olderPressure, trend, factors));
        }
        classes.sort(Comparator.comparingInt(ClassHistoryRisk::historicalRiskScore).reversed()
                .thenComparing(ClassHistoryRisk::qualifiedName));

        int critical = (int) classes.stream().filter(c -> "CRITICAL".equals(c.priority())).count();
        int high = (int) classes.stream().filter(c -> "HIGH".equals(c.priority())).count();
        int rising = (int) classes.stream().filter(c -> "RISING".equals(c.trend())).count();
        int avg = (int) Math.round(classes.stream().mapToInt(ClassHistoryRisk::historicalRiskScore).average().orElse(0));
        return new HistoricalRiskReport(projectId, commits.size(), byPath.size(), classes.size(), critical, high, rising, avg,
                classes.stream().limit(20).toList());
    }

    private HistoricalRiskReport empty(String projectId) {
        return new HistoricalRiskReport(projectId, 0, 0, 0, 0, 0, 0, 0, List.of());
    }

    private static int pressure(int commits, int churn) {
        int commitScore = Math.min(55, commits * 11);
        int churnScore = Math.min(45, churn / 4);
        return Math.min(100, commitScore + churnScore);
    }

    private static String trend(int recent, int older) {
        if (older == 0 && recent > 0) return "RISING";
        if (recent > older * 1.2) return "RISING";
        if (older > recent * 1.2) return "COOLING";
        return "STABLE";
    }

    private static String normalize(String path) {
        if (path == null) return "";
        return path.replace('\\', '/').replaceFirst("^\\./", "");
    }

    public record HistoricalRiskReport(String projectId, int commitsAnalyzed, int filesTouched,
                                       int classesMatched, int criticalClasses, int highPriorityClasses,
                                       int risingClasses, int averageHistoricalRiskScore,
                                       List<ClassHistoryRisk> hotspots) {}

    public record ClassHistoryRisk(String classId, String name, String qualifiedName, String sourcePath,
                                   int currentRiskScore, int changePressure, int historicalRiskScore,
                                   String priority, int commits, int additions, int deletions, int churn,
                                   int recentPressure, int olderPressure, String trend, List<String> factors) {}

    private static final class FileStats {
        private final String path;
        private int commits;
        private int additions;
        private int deletions;
        private int churn;
        private FileStats(String path) { this.path = path; }
        private void record(GitFileChangeRecord change) {
            commits++;
            additions += change.getAdditions();
            deletions += change.getDeletions();
            churn += change.getAdditions() + change.getDeletions();
        }
    }
}
