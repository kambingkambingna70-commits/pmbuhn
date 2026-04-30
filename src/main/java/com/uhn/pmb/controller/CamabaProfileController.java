package com.uhn.pmb.controller;

import com.uhn.pmb.dto.*;
import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.*;
import com.uhn.pmb.service.FormValidationService;
import com.uhn.pmb.service.ValidationStatusTrackerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Handles CAMABA student profile management: get profile, update profile, change password.
 * Extracted from CamabaController for Single Responsibility Principle.
 */
@RestController
@RequestMapping("/api/camaba")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@PreAuthorize("hasRole('CAMABA')")
public class CamabaProfileController {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final ValidationStatusTrackerService validationStatusTrackerService;
    private final FormValidationService formValidationService;

    /**
     * Get student profile with user email included
     * GET /api/camaba/profile
     */
    @GetMapping("/profile")
    public ResponseEntity<?> getProfile() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Student student = studentRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new RuntimeException("Student profile not found"));

            Map<String, Object> response = new HashMap<>();
            response.put("id", student.getId());
            response.put("fullName", student.getFullName());
            response.put("nik", student.getNik());
            response.put("birthDate", student.getBirthDate());
            response.put("birthPlace", student.getBirthPlace());
            response.put("gender", student.getGender());
            response.put("address", student.getAddress());
            response.put("phoneNumber", student.getPhoneNumber());
            response.put("email", student.getUser() != null ? student.getUser().getEmail() : "");
            response.put("parentName", student.getParentName());
            response.put("parentPhone", student.getParentPhone());
            response.put("schoolOrigin", student.getSchoolOrigin());
            response.put("schoolYear", student.getSchoolYear());
            response.put("createdAt", student.getCreatedAt());
            response.put("updatedAt", student.getUpdatedAt());

            Map<String, Object> userData = new HashMap<>();
            userData.put("id", user.getId());
            userData.put("email", user.getEmail());
            userData.put("role", user.getRole());
            userData.put("createdAt", user.getCreatedAt());
            response.put("user", userData);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error getting profile: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Update student profile
     * PUT /api/camaba/profile
     */
    @PutMapping("/profile")
    public ResponseEntity<?> updateProfile(@Valid @RequestBody StudentProfileRequest request) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Student student = studentRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new RuntimeException("Student profile not found"));

            student.setFullName(request.getFullName());
            student.setNik(request.getNik());
            student.setBirthDate(request.getBirthDate());
            student.setBirthPlace(request.getBirthPlace());
            student.setGender(request.getGender());
            student.setAddress(request.getAddress());
            student.setPhoneNumber(request.getPhoneNumber());
            student.setParentName(request.getParentName());
            student.setParentPhone(request.getParentPhone());
            student.setSchoolOrigin(request.getSchoolOrigin());
            student.setSchoolYear(request.getSchoolYear());

            studentRepository.save(student);
            return ResponseEntity.ok(new ApiResponse(true, "Profile updated successfully"));
        } catch (Exception e) {
            log.error("Error updating profile: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Change student password
     * POST /api/camaba/change-password
     */
    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
            if (!encoder.matches(request.getOldPassword(), user.getPassword())) {
                return ResponseEntity.badRequest().body(new ApiResponse(false, "Current password is incorrect"));
            }

            user.setPassword(encoder.encode(request.getNewPassword()));
            userRepository.save(user);
            return ResponseEntity.ok(new ApiResponse(true, "Password changed successfully"));
        } catch (Exception e) {
            log.error("Error changing password: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    /**
     * Get validation status for the current CAMABA student
     * GET /api/camaba/validation-status
     */
    @GetMapping("/validation-status")
    public ResponseEntity<?> getMyValidationStatus() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Student student = studentRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new RuntimeException("Student profile not found"));

            Optional<ValidationStatusTracker> trackerOpt = validationStatusTrackerService.getTrackerByStudentId(student.getId());
            if (trackerOpt.isEmpty()) {
                return ResponseEntity.ok(Map.of("success", true, "found", false, "status", "NOT_STARTED"));
            }

            ValidationStatusTracker tracker = trackerOpt.get();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("found", true);
            response.put("status", tracker.getStatus() != null ? tracker.getStatus().toString() : "NOT_STARTED");
            response.put("lastReason", tracker.getLastReason());
            response.put("lastAction", tracker.getLastAction());
            response.put("updatedAt", tracker.getUpdatedAt());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching validation status: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Mark revision as complete (SUDAH_PERBAIKAN) for the current CAMABA student
     * PUT /api/camaba/repair-status
     */
    @PutMapping("/repair-status")
    public ResponseEntity<?> markRepairComplete() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            Student student = studentRepository.findByUser_Id(user.getId())
                    .orElseThrow(() -> new RuntimeException("Student profile not found"));

            Map<String, Object> result = formValidationService.updateRepairStatus(student.getId(), "SUDAH_PERBAIKAN");
            log.info("✅ CAMABA {} marked repair as SUDAH_PERBAIKAN", email);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error marking repair complete: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Debug authentication info
     * GET /api/camaba/debug-auth
     */
    @GetMapping("/debug-auth")
    @PreAuthorize("permitAll()")
    public ResponseEntity<?> debugAuth(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            Map<String, Object> debug = new HashMap<>();
            debug.put("auth_header_received", authHeader != null && !authHeader.isEmpty());
            debug.put("auth_header_value", authHeader != null ? (authHeader.length() > 10 ? authHeader.substring(0, 10) + "..." : authHeader) : "NONE");
            debug.put("security_context_principal", auth != null ? auth.getPrincipal() : "null");
            debug.put("security_context_authenticated", auth != null ? auth.isAuthenticated() : false);
            debug.put("security_context_authorities", auth != null ? auth.getAuthorities().toString() : "none");
            return ResponseEntity.ok(debug);
        } catch (Exception e) {
            log.error("Error in debug endpoint: {}", e.getMessage());
            return ResponseEntity.ok(Map.of("error", e.getMessage(), "timestamp", java.time.LocalDateTime.now()));
        }
    }
}
