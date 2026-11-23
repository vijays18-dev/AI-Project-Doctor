package AI.Project.Doctor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class GitHubRepositoryService {
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GitHubRepositoryService() {
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        objectMapper = new ObjectMapper();
    }

    public Map<String, Object> inspectRepository(String repoUrl) throws IOException, InterruptedException {
        String[] details = extractRepositoryDetails(repoUrl);
        String owner = details[0];
        String repository = details[1];
        String repositoryApi = "https://api.github.com/repos/" + owner + "/" + repository;
        JsonNode repoData = getGitHubResponse(repositoryApi);

        String defaultBranch = repoData.path("default_branch").asText();
        if (defaultBranch.isBlank()) {
            throw new IllegalStateException("Could not identify the repository default branch.");
        }

        String encodedBranch = URLEncoder.encode(defaultBranch, StandardCharsets.UTF_8).replace("+", "%20");
        JsonNode treeData = getGitHubResponse(repositoryApi + "/git/trees/" + encodedBranch + "?recursive=1");

        List<String> filePaths = new ArrayList<>();
        List<Map<String, Object>> files = new ArrayList<>();
        for (JsonNode item : treeData.path("tree")) {
            if (!"blob".equals(item.path("type").asText()) || files.size() >= 500) {
                continue;
            }
            String path = item.path("path").asText();
            filePaths.add(path);
            Map<String, Object> file = new LinkedHashMap<>();
            file.put("path", path);
            file.put("size", item.path("size").asLong(0));
            files.add(file);
        }

        List<Map<String, String>> findings = buildFindings(filePaths, treeData);
        Map<String, Object> health = buildHealth(filePaths, treeData);
        List<String> recommendations = findings.stream()
                .map(finding -> finding.get("recommendation"))
                .distinct()
                .toList();
        if (recommendations.isEmpty()) {
            recommendations = List.of("Keep dependency updates, tests, and documentation current.");
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "success");
        result.put("message", "Repository analysis completed.");
        result.put("repository", repoData.path("name").asText());
        result.put("owner", repoData.path("owner").path("login").asText());
        result.put("description", repoData.path("description").asText(""));
        result.put("defaultBranch", defaultBranch);
        result.put("language", repoData.path("language").asText("Unknown"));
        result.put("stars", repoData.path("stargazers_count").asInt());
        result.put("forks", repoData.path("forks_count").asInt());
        result.put("repositoryUrl", repoData.path("html_url").asText());
        result.put("fileCount", treeData.path("tree").size());
        result.put("treeTruncated", treeData.path("truncated").asBoolean());
        result.put("files", files);
        result.put("health", health);
        result.put("findings", findings);
        result.put("recommendations", recommendations);
        return result;
    }

    private Map<String, Object> buildHealth(List<String> filePaths, JsonNode treeData) {
        Set<String> files = normalized(filePaths);
        int security = 100;
        if (containsAny(files, ".env", "credentials", "secret", "private_key")) security -= 30;
        if (!containsAny(files, "dependabot.yml", ".github/workflows")) security -= 8;

        int quality = 55;
        if (containsAny(files, "src/", "lib/", "app/")) quality += 15;
        if (containsAny(files, "eslint", "checkstyle", "spotbugs", "prettier")) quality += 15;
        if (containsAny(files, "readme.md")) quality += 10;

        int testing = 35;
        if (containsAny(files, "test/", "tests/", "__tests__", ".spec.", ".test.")) testing += 45;
        if (containsAny(files, "jacoco", "coverage", "vitest", "jest", "pytest")) testing += 15;

        int dependencies = 50;
        if (containsAny(files, "pom.xml", "build.gradle", "package.json", "requirements.txt", "go.mod")) dependencies += 20;
        if (containsAny(files, "dependabot.yml", "renovate.json")) dependencies += 20;

        int documentation = 25;
        if (containsAny(files, "readme.md")) documentation += 45;
        if (containsAny(files, "docs/", "changelog.md", "contributing.md", "openapi.yaml")) documentation += 25;

        Map<String, Object> health = new LinkedHashMap<>();
        health.put("security", clamp(security));
        health.put("codeQuality", clamp(quality));
        health.put("testing", clamp(testing));
        health.put("dependencies", clamp(dependencies));
        health.put("documentation", clamp(documentation));
        health.put("overall", Math.round((clamp(security) + clamp(quality) + clamp(testing)
                + clamp(dependencies) + clamp(documentation)) / 5.0f));
        health.put("filesAnalyzed", filePaths.size());
        health.put("treeTruncated", treeData.path("truncated").asBoolean());
        return health;
    }

    private List<Map<String, String>> buildFindings(List<String> filePaths, JsonNode treeData) {
        Set<String> files = normalized(filePaths);
        List<Map<String, String>> findings = new ArrayList<>();
        if (!containsAny(files, "readme.md")) {
            findings.add(finding("Documentation", "high", "Add a README with setup and usage instructions."));
        }
        if (!containsAny(files, "test/", "tests/", "__tests__", ".spec.", ".test.")) {
            findings.add(finding("Testing", "high", "Add automated tests to protect core project behavior."));
        }
        if (!containsAny(files, ".github/workflows", "jenkinsfile", ".circleci")) {
            findings.add(finding("Delivery", "medium", "Add continuous integration checks for every change."));
        }
        if (containsAny(files, ".env", "credentials", "secret", "private_key")) {
            findings.add(finding("Security", "critical", "Review tracked files for secrets and rotate exposed credentials."));
        }
        if (treeData.path("truncated").asBoolean()) {
            findings.add(finding("Coverage", "medium", "The GitHub tree was truncated; large repositories need paginated analysis."));
        }
        return findings;
    }

    private Map<String, String> finding(String area, String severity, String recommendation) {
        Map<String, String> finding = new LinkedHashMap<>();
        finding.put("area", area);
        finding.put("severity", severity);
        finding.put("recommendation", recommendation);
        return finding;
    }

    private Set<String> normalized(List<String> filePaths) {
        Set<String> files = new HashSet<>();
        filePaths.forEach(path -> files.add(path.toLowerCase()));
        return files;
    }

    private boolean containsAny(Set<String> files, String... patterns) {
        for (String file : files) {
            for (String pattern : patterns) {
                if (file.equals(pattern) || file.contains(pattern)) return true;
            }
        }
        return false;
    }

    private int clamp(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private String[] extractRepositoryDetails(String repoUrl) {
        try {
            URI uri = URI.create(repoUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"github.com".equalsIgnoreCase(uri.getHost())
                    || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException("Please enter a valid public GitHub repository URL.");
            }
            String[] parts = uri.getPath().replaceAll("^/+|/+$", "").split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Use this format: https://github.com/owner/repository");
            }
            String owner = parts[0];
            String repository = parts[1];
            if (repository.endsWith(".git")) repository = repository.substring(0, repository.length() - 4);
            if (!owner.matches("[A-Za-z0-9_.-]+") || !repository.matches("[A-Za-z0-9_.-]+")) {
                throw new IllegalArgumentException("Invalid GitHub owner or repository name.");
            }
            return new String[]{owner, repository};
        } catch (IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalArgumentException("Invalid GitHub repository URL.");
        }
    }

    private JsonNode getGitHubResponse(String apiUrl) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "AI-Project-Doctor")
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 403) {
            throw new IllegalStateException("GitHub API rate limit reached. Please try again later.");
        }
        if (response.statusCode() == 404) {
            throw new IllegalArgumentException("Repository not found or it is not publicly accessible.");
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("GitHub API request failed. HTTP status: " + response.statusCode());
        }
        return objectMapper.readTree(response.body());
    }
}
