package com.uhn.pmb.controller;

import com.uhn.pmb.dto.AdmissionFormSubmitRequest;
import com.uhn.pmb.entity.AdmissionForm;
import com.uhn.pmb.repository.SelectionTypeRepository;
import com.uhn.pmb.repository.UserRepository;
import com.uhn.pmb.service.AdmissionFormService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.*;

/**
 * Handles student admission form CRUD, submission, update, and revision endpoints.
 * Delegates all business logic to AdmissionFormService.
 */
@RestController
@RequestMapping("/api/camaba")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
@PreAuthorize("hasRole('CAMABA')")
public class CamabaFormController {

    private final AdmissionFormService admissionFormService;
    private final SelectionTypeRepository selectionTypeRepository;
    private final UserRepository userRepository;

    private String currentUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @GetMapping("/submission-status")
    public ResponseEntity<?> checkSubmissionStatus() {
        try {
            return ResponseEntity.ok(admissionFormService.checkSubmissionStatus(currentUserEmail()));
        } catch (Exception e) {
            log.error("Error checking submission status: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/admission-form")
    public ResponseEntity<?> getAdmissionFormData() {
        try {
            return ResponseEntity.ok(admissionFormService.getAdmissionFormData(currentUserEmail()));
        } catch (Exception e) {
            log.error("Error fetching admission form: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/admission-forms/current")
    public ResponseEntity<?> getCurrentAdmissionForm() {
        try {
            return ResponseEntity.ok(admissionFormService.getCurrentAdmissionFormData(currentUserEmail()));
        } catch (Exception e) {
            log.error("Error fetching current admission form: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/admission-forms/me")
    public ResponseEntity<?> getMyAdmissionForm() {
        return getCurrentAdmissionForm();
    }

    @PutMapping(value = "/admission-form", consumes = "multipart/form-data")
    public ResponseEntity<?> updateAdmissionFormData(HttpServletRequest request) {
        try {
            return ResponseEntity.ok(admissionFormService.updateAdmissionFormData(currentUserEmail(), request));
        } catch (Exception e) {
            log.error("Error updating admission form data: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error: " + e.getMessage()));
        }
    }

    @PostMapping("/test-selection-type")
    public ResponseEntity<?> testSelectionType(
            @RequestParam(required = false) String selectionTypeId,
            @RequestParam(required = false) String fullName,
            @RequestParam(required = false) Map<String, String> allParams) {
        try {
            log.info("===== TEST ENDPOINT CALLED =====");
            log.info("selectionTypeId param: '{}'", selectionTypeId);
            if (selectionTypeId != null && !selectionTypeId.isEmpty()) {
                Long stId = Long.parseLong(selectionTypeId.trim());
                var st = selectionTypeRepository.findById(stId).orElse(null);
                Map<String, Object> resp = new HashMap<>();
                resp.put("success", true);
                resp.put("message", "SelectionTypeId received and parsed successfully");
                resp.put("received_selectionTypeId", selectionTypeId);
                resp.put("parsed_as_long", stId);
                resp.put("found_in_db", st != null);
                resp.put("selection_type_name", st != null ? st.getName() : null);
                return ResponseEntity.ok(resp);
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "selectionTypeId is null or empty",
                        "received_value", selectionTypeId));
            }
        } catch (Exception e) {
            log.error("Test endpoint error: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/submit-admission-form")
    public ResponseEntity<?> submitAdmissionForm(
            @RequestHeader("Authorization") String token,
            AdmissionFormSubmitRequest request) {
        try {
            return ResponseEntity.ok(admissionFormService.submitAdmissionForm(currentUserEmail(), request));
        } catch (Exception e) {
            log.error("Error submitting admission form: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/admission-forms/update-selection")
    public ResponseEntity<?> updateAdmissionFormSelection(
            @RequestHeader("Authorization") String token,
            @RequestBody Map<String, Object> request) {
        try {
            return ResponseEntity.ok(admissionFormService.updateAdmissionFormSelection(currentUserEmail(), request));
        } catch (Exception e) {
            log.error("Error updating admission form selection: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Error updating form: " + e.getMessage()));
        }
    }

    @PostMapping("/register-admission")
    public ResponseEntity<?> registerForAdmission(@RequestBody RegistrationRequest request) {
        try {
            AdmissionForm form = admissionFormService.registerForAdmission(
                    currentUserEmail(), request.getPeriodId(), request.getSelectionTypeId(), request.getProgramStudi());
            return ResponseEntity.status(HttpStatus.CREATED).body(form);
        } catch (Exception e) {
            log.error("Error registering for admission: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/admission-status")
    public ResponseEntity<?> getAdmissionStatus() {
        try {
            return ResponseEntity.ok(admissionFormService.getAdmissionStatus(currentUserEmail()));
        } catch (Exception e) {
            log.error("Error fetching admission status: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/submit-revision/{formId}")
    public ResponseEntity<?> submitRevision(
            @PathVariable Long formId,
            @RequestParam String fullName,
            @RequestParam String nik,
            @RequestParam String birthDate,
            @RequestParam String birthPlace,
            @RequestParam String gender,
            @RequestParam String phoneNumber,
            @RequestParam String email,
            @RequestParam(required = false) String addressMedan,
            @RequestParam(required = false) String residenceInfo,
            @RequestParam String subdistrict,
            @RequestParam String district,
            @RequestParam String city,
            @RequestParam String province,
            @RequestParam String religion,
            @RequestParam(required = false) String informationSource,
            @RequestParam(required = false) String fatherNik,
            @RequestParam String fatherName,
            @RequestParam(required = false) String fatherBirthDate,
            @RequestParam(required = false) String fatherEducation,
            @RequestParam(required = false) String fatherOccupation,
            @RequestParam(required = false) String fatherIncome,
            @RequestParam(required = false) String fatherPhone,
            @RequestParam String fatherStatus,
            @RequestParam(required = false) String motherNik,
            @RequestParam String motherName,
            @RequestParam(required = false) String motherBirthDate,
            @RequestParam(required = false) String motherEducation,
            @RequestParam(required = false) String motherOccupation,
            @RequestParam(required = false) String motherIncome,
            @RequestParam(required = false) String motherPhone,
            @RequestParam String motherStatus,
            @RequestParam String parentSubdistrict,
            @RequestParam String parentCity,
            @RequestParam String parentProvince,
            @RequestParam(required = false) String parentPhone,
            @RequestParam String schoolOrigin,
            @RequestParam String schoolMajor,
            @RequestParam String schoolYear,
            @RequestParam(required = false) String nisn,
            @RequestParam(required = false) String schoolCity,
            @RequestParam(required = false) String schoolProvince,
            @RequestParam(required = false) MultipartFile photoId,
            @RequestParam(required = false) MultipartFile certificate,
            @RequestParam(required = false) MultipartFile transcript) {
        try {
            return ResponseEntity.ok(admissionFormService.submitRevision(
                    currentUserEmail(), formId, fullName, nik, birthDate, birthPlace, gender,
                    phoneNumber, email, addressMedan, residenceInfo, subdistrict, district,
                    city, province, religion, informationSource, fatherNik, fatherName,
                    fatherBirthDate, fatherEducation, fatherOccupation, fatherIncome,
                    fatherPhone, fatherStatus, motherNik, motherName, motherBirthDate,
                    motherEducation, motherOccupation, motherIncome, motherPhone, motherStatus,
                    parentSubdistrict, parentCity, parentProvince, parentPhone,
                    schoolOrigin, schoolMajor, schoolYear, nisn, schoolCity, schoolProvince,
                    photoId, certificate, transcript));
        } catch (Exception e) {
            log.error("Error submitting revision: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Gagal submit revisi: " + e.getMessage()));
        }
    }

    @lombok.Data
    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    static class RegistrationRequest {
        private Long periodId;
        private Long selectionTypeId;
        private String programStudi;
    }
}