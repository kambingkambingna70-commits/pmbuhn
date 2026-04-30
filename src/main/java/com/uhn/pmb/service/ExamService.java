package com.uhn.pmb.service;

import com.uhn.pmb.dto.CreateExamLinkRequest;
import com.uhn.pmb.dto.ExamValidationRequest;
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
public class ExamService {

    private final ExamLinkRepository examLinkRepository;
    private final ExamResultRepository examResultRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final SelectionTypeRepository selectionTypeRepository;
    private final FormValidationRepository formValidationRepository;
    private final FormRepairStatusRepository formRepairStatusRepository;
    private final AdmissionFormRepository admissionFormRepository;

    public ExamLink createLink(CreateExamLinkRequest request) {
        RegistrationPeriod period = registrationPeriodRepository.findById(request.getPeriodId())
                .orElseThrow(() -> new RuntimeException("Period tidak ditemukan"));

        if (!request.getLinkUrl().contains("forms.google.com") &&
                !request.getLinkUrl().contains("forms.gle")) {
            throw new RuntimeException("Link harus menggunakan Google Form");
        }

        SelectionType selectionType = null;
        if (request.getSelectionTypeId() != null) {
            selectionType = selectionTypeRepository.findById(request.getSelectionTypeId()).orElse(null);
        }

        ExamLink link = ExamLink.builder()
                .period(period)
                .selectionType(selectionType)
                .linkTitle(request.getLinkTitle())
                .linkUrl(request.getLinkUrl())
                .description(request.getDescription())
                .isActive(true)
                .build();

        examLinkRepository.save(link);
        log.info("✅ ExamLink created: {}", link.getLinkTitle());
        return link;
    }

    public List<ExamLink> findLinksByPeriod(Long periodId) {
        return examLinkRepository.findByPeriodId(periodId);
    }

    public void deleteLink(Long id) {
        ExamLink link = examLinkRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Link ujian tidak ditemukan"));
        examLinkRepository.delete(link);
        log.info("✅ ExamLink deleted: {}", link.getLinkTitle());
    }

    public Optional<ExamResult> findResultByStudentId(Long studentId) {
        return examResultRepository.findByStudent_Id(studentId);
    }

    public ExamResult validateSubmission(Long id, ExamValidationRequest request, User admin) {
        ExamResult result = examResultRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Exam result not found"));

        if ("APPROVE".equals(request.getAction()) && !result.getTokenValidated()) {
            throw new RuntimeException("❌ Tidak bisa setujui: Token tidak cocok (indikasi fraud)");
        }

        switch (request.getAction()) {
            case "APPROVE" -> {
                result.setExamValidationStatus(ExamResult.ExamValidationStatus.APPROVED);
                result.setAdminNotes("✅ Disetujui oleh " + admin.getEmail() + " - " +
                        (request.getAdminNotes() != null ? request.getAdminNotes() : "Lolos validasi"));
            }
            case "REJECT" -> {
                result.setExamValidationStatus(ExamResult.ExamValidationStatus.REJECTED);
                result.setAdminNotes("❌ Ditolak oleh " + admin.getEmail() + " - " +
                        (request.getAdminNotes() != null ? request.getAdminNotes() : "Nilai tidak valid"));
            }
            case "REVISI" -> {
                result.setExamValidationStatus(ExamResult.ExamValidationStatus.REVISI);
                result.setAdminNotes("🔄 Revisi oleh " + admin.getEmail() + " - " +
                        (request.getAdminNotes() != null ? request.getAdminNotes() : "Perbaiki dan upload ulang"));
            }
            default -> throw new RuntimeException("Action harus APPROVE, REJECT, atau REVISI");
        }

        result.setExamValidatedAt(LocalDateTime.now());
        result.setValidatedByAdmin(admin);
        examResultRepository.save(result);
        log.info("✅ ExamResult {} validated ({}) by {}", id, request.getAction(), admin.getEmail());
        return result;
    }

    public String generateToken(Long formId) {
        AdmissionForm form = admissionFormRepository.findById(formId)
                .orElseThrow(() -> new RuntimeException("Form tidak ditemukan"));

        List<FormValidation> validations = formValidationRepository.findAll().stream()
                .filter(v -> v.getAdmissionForm().getId().equals(formId))
                .limit(1).toList();

        FormValidation validation;
        if (validations.isEmpty()) {
            validation = new FormValidation();
            validation.setAdmissionForm(form);
            validation.setStudent(form.getStudent());
            validation.setCreatedAt(LocalDateTime.now());
            validation = formValidationRepository.save(validation);

            FormRepairStatus rs = FormRepairStatus.builder()
                    .formValidation(validation)
                    .status(FormRepairStatus.RepairStatus.BELUM_PERBAIKAN)
                    .createdAt(LocalDateTime.now())
                    .updatedAt(LocalDateTime.now())
                    .build();
            formRepairStatusRepository.save(rs);
        } else {
            validation = validations.get(0);
        }

        String token = formId + "-" + System.currentTimeMillis();
        validation.setExamToken(token);
        validation.setUpdatedAt(LocalDateTime.now());
        formValidationRepository.save(validation);

        log.info("✅ Token generated for form {}: {}", formId, token);
        return token;
    }
}