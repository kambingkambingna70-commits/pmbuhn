package com.uhn.pmb.service;

import com.uhn.pmb.dto.FormValidationRejectRequest;
import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReEnrollmentValidationService {

    private final ReEnrollmentRepository reenrollmentRepository;
    private final ReEnrollmentValidationRepository reEnrollmentValidationRepository;
    private final ReEnrollmentDocumentRepository reenrollmentDocumentRepository;
    private final RegistrationStatusService registrationStatusService;

    public List<ReEnrollment> getPendingReEnrollments() {
        log.info("📋 Fetching pending re-enrollments");
        return reenrollmentRepository.findByStatus(ReEnrollment.ReEnrollmentStatus.SUBMITTED);
    }

    public List<ReEnrollment> getInProgressReEnrollments() {
        log.info("📋 Fetching in-progress re-enrollments");
        return reenrollmentRepository.findByStatus(ReEnrollment.ReEnrollmentStatus.SUBMITTED);
    }

    public ReEnrollment getReEnrollmentById(Long id) {
        return reenrollmentRepository.findById(id)
                .orElseThrow(() -> {
                    log.error("❌ Re-enrollment not found: {}", id);
                    return new RuntimeException("Daftar ulang tidak ditemukan");
                });
    }

    @Transactional
    public void approveReEnrollment(Long reEnrollmentId, User admin) {
        log.info("📋 Approving re-enrollment: {}", reEnrollmentId);

        ReEnrollment reEnrollment = getReEnrollmentById(reEnrollmentId);
        
        reEnrollment.setStatus(ReEnrollment.ReEnrollmentStatus.VALIDATED);
        reEnrollment.setValidatedAt(LocalDateTime.now());
        reEnrollment.setValidationNotes("Approved by admin: " + admin.getUsername());
        reenrollmentRepository.save(reEnrollment);

        // Update registration status
        registrationStatusService.approveByAdmin(
                reEnrollment.getStudent().getUser(),
                RegistrationStatus.RegistrationStage.DAFTAR_ULANG,
                admin.getUsername(),
                "Approved by admin: " + admin.getUsername()
        );

        log.info("✅ Re-enrollment approved: {}", reEnrollmentId);
    }

    @Transactional
    public void rejectReEnrollment(Long reEnrollmentId, FormValidationRejectRequest request, User admin) {
        log.info("📋 Rejecting re-enrollment: {}", reEnrollmentId);

        ReEnrollment reEnrollment = getReEnrollmentById(reEnrollmentId);

        reEnrollment.setStatus(ReEnrollment.ReEnrollmentStatus.REJECTED);
        reEnrollment.setValidationNotes(request.getReason() != null ? request.getReason() : "-");
        reEnrollment.setValidatedAt(LocalDateTime.now());
        reenrollmentRepository.save(reEnrollment);

        // Update registration status
        registrationStatusService.rejectByAdmin(
                reEnrollment.getStudent().getUser(),
                RegistrationStatus.RegistrationStage.DAFTAR_ULANG,
                admin.getUsername(),
                "Rejected by admin: " + request.getReason()
        );

        log.info("✅ Re-enrollment rejected: {} (Reason: {})", reEnrollmentId, request.getReason());
    }

    @Transactional
    public void markAsInProgress(Long reEnrollmentId) {
        log.info("📋 Marking re-enrollment as in-progress: {}", reEnrollmentId);

        ReEnrollment reEnrollment = getReEnrollmentById(reEnrollmentId);
        reEnrollment.setStatus(ReEnrollment.ReEnrollmentStatus.SUBMITTED);
        reenrollmentRepository.save(reEnrollment);

        log.info("✅ Re-enrollment marked as in-progress: {}", reEnrollmentId);
    }

    public List<ReEnrollmentDocument> getReEnrollmentDocuments(Long reEnrollmentId) {
        log.info("📋 Fetching documents for re-enrollment: {}", reEnrollmentId);
        return reenrollmentDocumentRepository.findByReenrollmentId(reEnrollmentId);
    }

    public ReEnrollmentDocument getReEnrollmentDocument(Long documentId) {
        return reenrollmentDocumentRepository.findById(documentId)
                .orElseThrow(() -> {
                    log.error("❌ Document not found: {}", documentId);
                    return new RuntimeException("Dokumen tidak ditemukan");
                });
    }

    @Transactional
    public void validateReEnrollmentDocument(Long documentId, Boolean isValid, String notes, User validator) {
        log.info("📋 Validating document: {} (isValid: {})", documentId, isValid);

        ReEnrollmentDocument document = getReEnrollmentDocument(documentId);

        ReEnrollmentValidation validation = ReEnrollmentValidation.builder()
                .reEnrollment(document.getReenrollment())
                .student(document.getReenrollment().getStudent())
                .validatedBy(validator)
                .validationStatus(isValid ? ReEnrollmentValidation.ValidationStatus.APPROVED : ReEnrollmentValidation.ValidationStatus.REJECTED)
                .rejectionReason(notes)
                .validatedAt(LocalDateTime.now())
                .build();

        reEnrollmentValidationRepository.save(validation);
        log.info("✅ Document validation saved: {}", documentId);
    }
}
