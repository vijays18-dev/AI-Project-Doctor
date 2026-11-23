
package AI.Project.Doctor;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class AnalysisController {
    private final GitHubRepositoryService repositoryService;

    public AnalysisController(GitHubRepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<Map<String, Object>> analyze(@RequestBody Map<String, String> request) {
        String repoUrl = request.get("repoUrl");
        if (repoUrl == null || repoUrl.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "GitHub repository URL is required."));
        }
        try {
            return ResponseEntity.ok(repositoryService.inspectRepository(repoUrl));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", error.getMessage()));
        } catch (Exception error) {
            String message = error.getMessage() == null ? "Repository analysis failed." : error.getMessage();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "status", "error",
                    "message", message));
        }
    }
}
