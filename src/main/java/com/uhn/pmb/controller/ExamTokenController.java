package com.uhn.pmb.controller;

import com.uhn.pmb.dto.ExamTokenDTO;
import com.uhn.pmb.entity.ExamSubmission;
import com.uhn.pmb.entity.ExamToken;
import com.uhn.pmb.service.ExamTokenService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin(origins = "*")
public class ExamTokenController {

    @Autowired
    private ExamTokenService tokenService;

    @Value("${exam.gform.link:}")
    private String gformLink;

    @PostMapping("/admin/api/exam/generate-token")
    public ResponseEntity<?> generateToken(
            @RequestBody ExamTokenDTO.GenerateTokenRequest request,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Silakan login sebagai admin"));
            }
            ExamToken token = tokenService.generateToken(
                    request.getStudentId(),
                    request.getApprovedFormId(),
                    request.getExpirationMinutes()
            );
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Token berhasil di-generate",
                    "tokenId", token.getId(),
                    "token", token.getTokenValue(),
                    "expiresAt", token.getExpiresAt(),
                    "studentInfo", Map.of(
                            "id", token.getStudent().getId(),
                            "name", token.getStudent().getFullName(),
                            "email", token.getStudent().getUser().getEmail()
                    )
            ));
        } catch (Exception e) {
            log.error("Error generate token", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", "Gagal generate token: " + e.getMessage()));
        }
    }

    @GetMapping("/admin/api/exam/get-validated-students")
    public ResponseEntity<?> getValidatedStudents(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Silakan login"));
            }
            List<ExamTokenDTO.TokenInfoResponse> students = tokenService.getValidatedStudentsWithTokens();
            return ResponseEntity.ok(Map.of("success", true, "data", students, "total", students.size()));
        } catch (Exception e) {
            log.error("Error get validated students", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/admin/api/exam/statistics")
    public ResponseEntity<?> getStatistics(Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Silakan login"));
            }
            return ResponseEntity.ok(Map.of("success", true, "data", tokenService.getExamStatistics()));
        } catch (Exception e) {
            log.error("Error get statistics", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/admin/api/exam/revoke-token")
    public ResponseEntity<?> revokeToken(
            @RequestBody ExamTokenDTO.RevokeTokenRequest request,
            Authentication authentication) {
        try {
            if (authentication == null || !authentication.isAuthenticated()) {
                return ResponseEntity.status(401).body(Map.of("success", false, "message", "Silakan login"));
            }
            tokenService.revokeToken(request.getToken(), request.getReason());
            return ResponseEntity.ok(Map.of("success", true, "message", "Token berhasil di-revoke"));
        } catch (Exception e) {
            log.error("Error revoke token", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/api/exam/validate-token")
    public ResponseEntity<?> validateToken(@RequestBody ExamTokenDTO.ValidateTokenRequest request) {
        try {
            ExamTokenDTO.ValidateTokenResponse response = tokenService.validateToken(
                    request.getToken(), request.getStudentId());
            return ResponseEntity.ok(Map.of("success", true, "data", response));
        } catch (Exception e) {
            log.error("Error validate token", e);
            return ResponseEntity.status(400).body(Map.of("success", false, "valid", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/api/exam/get-gform-link")
    public ResponseEntity<?> getGFormLink() {
        try {
            if (gformLink == null || gformLink.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("success", false, "message", "Admin belum setup link Google Form"));
            }
            return ResponseEntity.ok(Map.of("success", true, "gformLink", gformLink));
        } catch (Exception e) {
            log.error("Error get gform link", e);
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/api/exam/submit-results")
    public ResponseEntity<?> submitResults(@RequestBody ExamTokenDTO.SubmitResultRequest request) {
        try {
            ExamSubmission submission = tokenService.submitExamResult(request);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Hasil ujian berhasil di-submit",
                    "submissionId", submission.getId(),
                    "submittedAt", submission.getSubmittedAt(),
                    "score", submission.getScore()
            ));
        } catch (Exception e) {
            log.error("Error submit results", e);
            return ResponseEntity.status(400).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/api/exam/submission-status/{studentId}")
    public ResponseEntity<?> getSubmissionStatus(@PathVariable Long studentId) {
        return ResponseEntity.ok(Map.of("success", true, "message", "Status endpoint"));
    }
}