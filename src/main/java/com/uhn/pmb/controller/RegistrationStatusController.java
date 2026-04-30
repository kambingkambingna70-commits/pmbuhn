package com.uhn.pmb.controller;

import com.uhn.pmb.entity.RegistrationStatus;
import com.uhn.pmb.entity.RegistrationStatus.RegistrationStage;
import com.uhn.pmb.service.RegistrationStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/camaba/registration-status")
@CrossOrigin(origins = "*")
@RequiredArgsConstructor
@Slf4j
public class RegistrationStatusController {

    private final RegistrationStatusService registrationStatusService;

    @GetMapping("/all")
    public ResponseEntity<?> getAllStatuses(Authentication authentication) {
        try {
            String email = authentication.getName();
            List<RegistrationStatus> statuses = registrationStatusService.getUserStatusesByEmail(email);

            List<Map<String, Object>> enhancedStatuses = new java.util.ArrayList<>();
            for (RegistrationStatus status : statuses) {
                Map<String, Object> statusMap = new HashMap<>();
                statusMap.put("id", status.getId());
                statusMap.put("stage", status.getStage().toString());
                statusMap.put("status", status.getStatus().toString());
                statusMap.put("submissionDate", status.getSubmissionDate());
                statusMap.put("createdAt", status.getCreatedAt());
                statusMap.put("updatedAt", status.getUpdatedAt());
                statusMap.put("editDeadline", status.getEditDeadline());
                statusMap.put("canEdit", status.getCanEdit());
                statusMap.put("adminVerified", status.getAdminVerified());
                statusMap.put("verifiedBy", status.getVerifiedBy());
                statusMap.put("verificationDate", status.getVerificationDate());
                statusMap.put("adminNotes", status.getAdminNotes());
                statusMap.put("editCount", status.getEditCount());
                Long editTimeRemaining = registrationStatusService.getEditTimeRemainingByEmail(email, status.getStage());
                statusMap.put("editTimeRemainingHours", editTimeRemaining);
                enhancedStatuses.add(statusMap);
            }

            return ResponseEntity.ok(Map.of("success", true, "data", enhancedStatuses));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/{stage}")
    public ResponseEntity<?> getStatus(@PathVariable String stage, Authentication authentication) {
        try {
            String email = authentication.getName();
            RegistrationStage registrationStage = RegistrationStage.valueOf(stage.toUpperCase());
            Optional<RegistrationStatus> statusOpt = registrationStatusService.getStatusByEmail(email, registrationStage);

            if (statusOpt.isEmpty()) {
                return ResponseEntity.ok(Map.of("success", false, "message", "Status tidak ditemukan"));
            }

            RegistrationStatus regStatus = statusOpt.get();
            boolean canEdit = registrationStatusService.canUserEditByEmail(email, registrationStage);
            Long timeRemaining = registrationStatusService.getEditTimeRemainingByEmail(email, registrationStage);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", regStatus);
            response.put("canEdit", canEdit);
            response.put("editTimeRemainingHours", timeRemaining);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Stage tidak valid: " + stage));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/{stage}/complete")
    public ResponseEntity<?> completeStage(
            @PathVariable String stage,
            @RequestBody(required = false) Map<String, Object> request,
            Authentication authentication) {
        try {
            log.info("📝 [COMPLETE-STAGE] Received request for stage: {}", stage);
            String email = authentication.getName();
            RegistrationStage registrationStage = RegistrationStage.valueOf(stage.toUpperCase());

            String dataJson = "";
            if (request != null) {
                if (request.containsKey("data")) {
                    Object data = request.get("data");
                    dataJson = data != null ? data.toString() : "";
                } else {
                    try {
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        dataJson = mapper.writeValueAsString(request);
                    } catch (Exception e) {
                        log.warn("Could not convert request to JSON: {}", e.getMessage());
                    }
                }
            }

            RegistrationStatus status = registrationStatusService.markAsCompletedByEmail(email, registrationStage, dataJson);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Tahapan " + stage + " berhasil diselesaikan");
            response.put("data", status);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Stage tidak valid: " + stage));
        } catch (Exception e) {
            log.error("❌ Error completing stage {}: {}", stage, e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    @PutMapping("/{stage}/update")
    public ResponseEntity<?> updateStageData(
            @PathVariable String stage,
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            String email = authentication.getName();
            RegistrationStage registrationStage = RegistrationStage.valueOf(stage.toUpperCase());

            boolean canEdit = registrationStatusService.canUserEditByEmail(email, registrationStage);
            if (!canEdit) {
                Long timeRemaining = registrationStatusService.getEditTimeRemainingByEmail(email, registrationStage);
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Waktu edit sudah habis (24 jam telah lewat atau sudah diverifikasi admin)",
                        "editTimeRemainingHours", timeRemaining));
            }

            String dataJson = request.getOrDefault("data", "").toString();
            RegistrationStatus status = registrationStatusService.updateStatusDataByEmail(email, registrationStage, dataJson);
            return ResponseEntity.ok(Map.of("success", true, "message", "Data berhasil diupdate", "data", status));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Stage tidak valid: " + stage));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/{stage}/can-edit")
    public ResponseEntity<?> checkCanEdit(@PathVariable String stage, Authentication authentication) {
        try {
            String email = authentication.getName();
            RegistrationStage registrationStage = RegistrationStage.valueOf(stage.toUpperCase());
            boolean canEdit = registrationStatusService.canUserEditByEmail(email, registrationStage);
            Long timeRemaining = registrationStatusService.getEditTimeRemainingByEmail(email, registrationStage);
            return ResponseEntity.ok(Map.of("success", true, "canEdit", canEdit, "editTimeRemainingHours", timeRemaining));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Stage tidak valid: " + stage));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }
}