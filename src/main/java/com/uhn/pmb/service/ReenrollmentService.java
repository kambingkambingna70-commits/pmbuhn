package com.uhn.pmb.service;

import com.uhn.pmb.dto.DocumentValidationRequest;
import com.uhn.pmb.dto.ReenrollmentFinalizeRequest;
import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReenrollmentService {

    private final ReEnrollmentRepository reenrollmentRepository;
    private final ReEnrollmentValidationRepository reEnrollmentValidationRepository;
    private final ReEnrollmentDocumentRepository reenrollmentDocumentRepository;
    private final RegistrationStatusService registrationStatusService;

    public List<ReEnrollment> findPending() {
        return reenrollmentRepository.findAll().stream()
                .filter(r -> r.getStatus() == ReEnrollment.ReEnrollmentStatus.SUBMITTED)
                .toList();
    }

    public Optional<ReEnrollment> findById(Long id) {
        return reenrollmentRepository.findById(id);
    }

    public ReEnrollment approve(Long reEnrollmentId, User admin) {
        ReEnrollment reenrollment = reenrollmentRepository.findById(reEnrollmentId)
                .orElseThrow(() -> new RuntimeException("Daftar ulang tidak ditemukan"));

        ReEnrollmentValidation validation = reEnrollmentValidationRepository
                .findByReEnrollmentId(reEnrollmentId)
                .orElseGet(() -> {
                    ReEnrollmentValidation v = new ReEnrollmentValidation();
                    v.setReEnrollment(reenrollment);
                    v.setStudent(reenrollment.getStudent());
                    v.setCreatedAt(LocalDateTime.now());
                    v.setValidationStatus(ReEnrollmentValidation.ValidationStatus.PENDING);
                    return v;
                });

        validation.setValidationStatus(ReEnrollmentValidation.ValidationStatus.APPROVED);
        validation.setValidatedBy(admin);
        validation.setValidatedAt(LocalDateTime.now());
        reEnrollmentValidationRepository.save(validation);

        reenrollment.setStatus(ReEnrollment.ReEnrollmentStatus.VALIDATED);
        reenrollment.setValidatedAt(LocalDateTime.now());
        reenrollmentRepository.save(reenrollment);

        try {
            User studentUser = reenrollment.getStudent().getUser();
            if (studentUser != null) {
                registrationStatusService.approveByAdmin(
                        studentUser,
                        RegistrationStatus.RegistrationStage.DAFTAR_ULANG,
                        admin.getEmail(),
                        "Daftar ulang disetujui"
                );
            }
        } catch (Exception e) {
            log.warn("⚠️ Could not update RegistrationStatus: {}", e.getMessage());
        }

        log.info("✅ Reenrollment {} approved by {}", reEnrollmentId, admin.getEmail());
        return reenrollment;
    }

    public ReEnrollment reject(Long reEnrollmentId, String reason, String topic, User admin) {
        ReEnrollment reenrollment = reenrollmentRepository.findById(reEnrollmentId)
                .orElseThrow(() -> new RuntimeException("Daftar ulang tidak ditemukan"));

        ReEnrollmentValidation validation = reEnrollmentValidationRepository
                .findByReEnrollmentId(reEnrollmentId)
                .orElseGet(() -> {
                    ReEnrollmentValidation v = new ReEnrollmentValidation();
                    v.setReEnrollment(reenrollment);
                    v.setStudent(reenrollment.getStudent());
                    v.setCreatedAt(LocalDateTime.now());
                    v.setValidationStatus(ReEnrollmentValidation.ValidationStatus.PENDING);
                    return v;
                });

        validation.setValidationStatus(ReEnrollmentValidation.ValidationStatus.REJECTED);
        validation.setRejectionTopic(topic != null ? topic : "Lainnya");
        validation.setRejectionReason(reason != null ? reason : "");
        validation.setValidatedBy(admin);
        validation.setRejectedAt(LocalDateTime.now());
        reEnrollmentValidationRepository.save(validation);

        reenrollment.setStatus(ReEnrollment.ReEnrollmentStatus.REJECTED);
        reenrollmentRepository.save(reenrollment);

        try {
            User studentUser = reenrollment.getStudent().getUser();
            if (studentUser != null) {
                registrationStatusService.rejectByAdmin(
                        studentUser,
                        RegistrationStatus.RegistrationStage.DAFTAR_ULANG,
                        admin.getEmail(),
                        reason != null ? reason : "Daftar ulang ditolak"
                );
            }
        } catch (Exception e) {
            log.warn("⚠️ Could not update RegistrationStatus: {}", e.getMessage());
        }

        log.info("❌ Reenrollment {} rejected by {}", reEnrollmentId, admin.getEmail());
        return reenrollment;
    }

    public ReEnrollmentDocument validateDocument(Long docId, DocumentValidationRequest request, User admin) {
        ReEnrollmentDocument document = reenrollmentDocumentRepository.findById(docId)
                .orElseThrow(() -> new RuntimeException("Dokumen tidak ditemukan"));

        switch (request.getAction()) {
            case "APPROVE" -> document.setValidationStatus(ReEnrollmentDocument.ValidationStatus.APPROVED);
            case "REJECT" -> document.setValidationStatus(ReEnrollmentDocument.ValidationStatus.REJECTED);
            case "REVISION_NEEDED" -> document.setValidationStatus(ReEnrollmentDocument.ValidationStatus.REVISION_NEEDED);
            default -> throw new RuntimeException("Action tidak valid");
        }

        document.setAdminNotes(request.getAdminNotes());
        document.setValidatedAt(LocalDateTime.now());
        document.setValidatedByAdmin(admin);
        reenrollmentDocumentRepository.save(document);
        log.info("✅ Document {} validated ({}) by {}", docId, request.getAction(), admin.getEmail());
        return document;
    }

    public ReEnrollment finalize(Long id, ReenrollmentFinalizeRequest request) {
        ReEnrollment reenrollment = reenrollmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Re-enrollment tidak ditemukan"));

        if ("APPROVE".equals(request.getAction())) {
            boolean allApproved = reenrollment.getDocuments().stream()
                    .allMatch(d -> d.getValidationStatus() == ReEnrollmentDocument.ValidationStatus.APPROVED);
            if (!allApproved) {
                throw new RuntimeException("Semua dokumen harus disetujui terlebih dahulu");
            }
            reenrollment.setStatus(ReEnrollment.ReEnrollmentStatus.VALIDATED);
        } else {
            reenrollment.setStatus(ReEnrollment.ReEnrollmentStatus.REJECTED);
        }

        reenrollment.setValidationNotes(request.getValidationNotes());
        reenrollment.setValidatedAt(LocalDateTime.now());
        reenrollmentRepository.save(reenrollment);
        return reenrollment;
    }
}