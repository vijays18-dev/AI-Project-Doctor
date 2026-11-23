
package AI.Project.Doctor;

import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@CrossOrigin(origins = "http://localhost:5173")
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of(
            "message", "AI Project Doctor Backend is running",
            "status", "ok"
        );
    }
}
