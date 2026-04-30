package com.uhn.pmb.service;

import com.uhn.pmb.dto.FormValidationRejectRequest;
import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class FormValidationService {

    // ===== REPOSITORIES =====
    private final FormValidationRepository formValidationRepository;
    private final FormRepairStatusRepository formRepairStatusRepository;
    private final AdmissionFormRepository admissionFormRepository;
    private final RegistrationStatusRepository registrationStatusRepository;
    private final CicilanRequestRepository cicilanRequestRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final JenisSeleksiRepository jenisSeleksiRepository;
    private final ReEnrollmentRepository reEnrollmentRepository;

    // ===== SERVICES =====
    private final RegistrationStatusService registrationStatusService;
    private final ValidationStatusTrackerService validationStatusTrackerService;
    private final HasilAkhirService hasilAkhirService;
    private final EmailService emailService;

    public List<FormValidation> findAll() {
        return formValidationRepository.findAll();
    }

    public Optional<FormValidation> findById(Long id) {
        return formValidationRepository.findById(id);
    }

    @Transactional
    public void approve(Long validationId, User admin) {
        FormValidation validation = formValidationRepository.findById(validationId)
                .orElseThrow(() -> new RuntimeException("Validasi tidak ditemukan"));

        validation.setValidationStatus(FormValidation.ValidationStatus.APPROVED);
        validation.setValidatedBy(admin);
        validation.setValidatedAt(LocalDateTime.now());
        formValidationRepository.save(validation);

        AdmissionForm form = validation.getAdmissionForm();
        if (form != null) {
            form.setStatus(AdmissionForm.FormStatus.VERIFIED);
            admissionFormRepository.save(form);

            try {
                validationStatusTrackerService.updateStatusToDivalidasi(form.getId(), admin);
            } catch (Exception e) {
                log.warn("⚠️ Failed to update ValidationStatusTracker: {}", e.getMessage());
            }

            if (form.getStudent() != null && form.getStudent().getUser() != null) {
                User studentUser = form.getStudent().getUser();

                RegistrationStatus reenrollStatus = registrationStatusService.getOrCreateStatus(
                        studentUser, RegistrationStatus.RegistrationStage.DAFTAR_ULANG);
                if (reenrollStatus.getStatus() != RegistrationStatus.RegistrationStatus_Enum.SELESAI &&
                    reenrollStatus.getStatus() != RegistrationStatus.RegistrationStatus_Enum.REJECTED) {
                    reenrollStatus.setStatus(RegistrationStatus.RegistrationStatus_Enum.MENUNGGU_VERIFIKASI);
                }
                reenrollStatus.setUpdatedAt(LocalDateTime.now());
                registrationStatusRepository.save(reenrollStatus);

                try {
                    hasilAkhirService.autoPopulateHasilAkhir(form.getStudent().getId());
                } catch (Exception e) {
                    log.warn("⚠️ Failed to auto-populate hasil akhir: {}", e.getMessage());
                }

                sendApprovalEmail(studentUser.getEmail(), form.getStudent().getFullName());
            }
        }
        log.info("✅ FormValidation {} approved by {}", validationId, admin.getEmail());
    }

    @Transactional
    public void reject(Long validationId, FormValidationRejectRequest request, User admin) {
        FormValidation validation = formValidationRepository.findById(validationId)
                .orElseThrow(() -> new RuntimeException("Validasi tidak ditemukan"));

        validation.setValidationStatus(FormValidation.ValidationStatus.REJECTED);
        validation.setRejectionTopic(request != null && request.getTopic() != null ? request.getTopic() : "Lainnya");
        validation.setRejectionReason(request != null && request.getReason() != null ? request.getReason() : "");
        validation.setValidatedBy(admin);
        validation.setRejectedAt(LocalDateTime.now());
        formValidationRepository.save(validation);

        AdmissionForm form = validation.getAdmissionForm();
        if (form != null) {
            form.setStatus(AdmissionForm.FormStatus.REJECTED);
            admissionFormRepository.save(form);

            String reason = validation.getRejectionReason();
            try {
                validationStatusTrackerService.updateStatusToDitolak(form.getId(), reason, admin);
            } catch (Exception e) {
                log.warn("⚠️ Failed to update ValidationStatusTracker: {}", e.getMessage());
            }

            if (form.getStudent() != null && form.getStudent().getUser() != null) {
                sendRejectionEmail(
                    form.getStudent().getUser().getEmail(),
                    form.getStudent().getFullName(),
                    reason.isEmpty() ? "Formulir Anda tidak memenuhi persyaratan" : reason
                );
            }
        }
        log.info("❌ FormValidation {} rejected by {}", validationId, admin.getEmail());
    }

    @Transactional
    public void markRevisionNeeded(Long validationId, FormValidationRejectRequest request, User admin) {
        FormValidation validation = formValidationRepository.findById(validationId)
                .orElseThrow(() -> new RuntimeException("Validasi tidak ditemukan"));
        int revisionNumber = request != null && request.getRevisionNumber() != null
                ? request.getRevisionNumber() : 1;

        validation.setValidationStatus(FormValidation.ValidationStatus.REVISION_NEEDED);
        validation.setRejectionTopic(request != null && request.getTopic() != null ? request.getTopic() : "Perbaiki Data");
        validation.setRejectionReason(request != null && request.getReason() != null ? request.getReason() : "");
        validation.setRevisionNumber(revisionNumber);
        validation.setValidatedBy(admin);
        validation.setValidatedAt(LocalDateTime.now());
        formValidationRepository.save(validation);

        // Reset repair status
        formRepairStatusRepository.findByFormValidationId(validationId).ifPresent(rs -> {
            rs.setStatus(FormRepairStatus.RepairStatus.BELUM_PERBAIKAN);
            rs.setUpdatedAt(LocalDateTime.now());
            formRepairStatusRepository.save(rs);
        });

        AdmissionForm form = validation.getAdmissionForm();
        if (form != null) {
            form.setStatus(AdmissionForm.FormStatus.SUBMITTED);
            admissionFormRepository.save(form);

            try {
                validationStatusTrackerService.updateStatusToRevisi(
                        form.getId(), validation.getRejectionReason(), admin);
            } catch (Exception e) {
                log.warn("⚠️ Failed to update ValidationStatusTracker: {}", e.getMessage());
            }

            if (form.getStudent() != null && form.getStudent().getUser() != null) {
                sendRevisionNeededEmail(
                    form.getStudent().getUser().getEmail(),
                    form.getStudent().getFullName(),
                    validation.getRejectionReason(),
                    revisionNumber
                );
            }
        }
        log.info("✏️ FormValidation {} marked revision needed by {}", validationId, admin.getEmail());
    }

    // ===== DASHBOARD QUERY =====

    public List<Map<String, Object>> getFormsForValidationDashboard() {
        List<FormValidation> validations = new ArrayList<>(formValidationRepository.findAll());
        validations.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));

        return validations.stream()
            .filter(fv -> {
                AdmissionForm form = fv.getAdmissionForm();
                Student student = fv.getStudent();
                if (student == null || student.getUser() == null || form == null) return false;
                User user = student.getUser();
                Optional<RegistrationStatus> formRegStatus = registrationStatusRepository
                        .findByUserAndStage(user, RegistrationStatus.RegistrationStage.FORM_SUBMISSION);
                return formRegStatus.isPresent() &&
                       formRegStatus.get().getStatus() == RegistrationStatus.RegistrationStatus_Enum.SELESAI;
            })
            .map(fv -> {
                Map<String, Object> data = new HashMap<>();
                AdmissionForm form = fv.getAdmissionForm();
                Student student = fv.getStudent();
                User user = student.getUser();

                data.put("id", fv.getId());
                data.put("formValidationId", fv.getId());
                data.put("formId", form != null ? form.getId() : null);
                data.put("admissionFormId", form != null ? form.getId() : null);
                data.put("studentId", student.getId());
                data.put("studentName", student.getFullName());
                data.put("studentEmail", user.getEmail());
                data.put("studentPhone", student.getPhoneNumber() != null ? student.getPhoneNumber() : "-");
                data.put("formFullName", form != null ? form.getFullName() : "-");
                data.put("programStudi1", form != null ? form.getProgramStudi1() : "-");
                data.put("programStudi2", form != null ? form.getProgramStudi2() : "-");

                String vaNumber = fv.getVirtualAccountNumber() != null ? fv.getVirtualAccountNumber() : "-";
                data.put("virtualAccountNumber", vaNumber);

                String brivaNumber = "-";
                try {
                    List<CicilanRequest> cicilanList = cicilanRequestRepository
                        .findByStudentIdAndStatus(student.getId(), CicilanRequest.CicilanRequestStatus.APPROVED);
                    if (cicilanList != null && !cicilanList.isEmpty() && cicilanList.get(0).getBriva() != null) {
                        brivaNumber = cicilanList.get(0).getBriva();
                    }
                } catch (Exception e) {
                    log.debug("⚠️ [BRIVA] Error fetching from CICILAN_REQUEST: {}", e.getMessage());
                }
                data.put("brivaNumber", brivaNumber);

                Long paymentAmountValue = fv.getPaymentAmount();
                if ((paymentAmountValue == null || paymentAmountValue == 0) && !vaNumber.equals("-")) {
                    try {
                        VirtualAccount va = virtualAccountRepository.findByVaNumber(vaNumber).orElse(null);
                        if (va != null && va.getAmount() != null) {
                            paymentAmountValue = va.getAmount().longValue();
                        }
                    } catch (Exception e) {
                        log.debug("Could not fetch VA amount for {}: {}", vaNumber, e.getMessage());
                    }
                }
                data.put("paymentAmount", paymentAmountValue != null ? String.format("Rp %,d", paymentAmountValue) : "-");
                data.put("paymentAmountRaw", paymentAmountValue);

                String validationStatusStr = fv.getValidationStatus() != null ? fv.getValidationStatus().toString() : "PENDING";
                String paymentStatus = "PENDING";
                String installmentStatus = "PENDING";
                String examStatus = "PENDING";

                Optional<RegistrationStatus> paymentRegStatus = registrationStatusRepository
                        .findByUserAndStage(user, RegistrationStatus.RegistrationStage.PAYMENT_BRIVA);
                if (paymentRegStatus.isPresent() && paymentRegStatus.get().getStatus() == RegistrationStatus.RegistrationStatus_Enum.SELESAI) {
                    paymentStatus = "PAID";
                }
                Optional<RegistrationStatus> cicilanRegStatus = registrationStatusRepository
                        .findByUserAndStage(user, RegistrationStatus.RegistrationStage.PAYMENT_CICILAN_1);
                if (cicilanRegStatus.isPresent()) {
                    installmentStatus = cicilanRegStatus.get().getStatus() == RegistrationStatus.RegistrationStatus_Enum.SELESAI ? "PAID" : "PENDING";
                }
                Optional<RegistrationStatus> examRegStatus = registrationStatusRepository
                        .findByUserAndStage(user, RegistrationStatus.RegistrationStage.PSYCHO_EXAM);
                if (examRegStatus.isPresent() && examRegStatus.get().getStatus() == RegistrationStatus.RegistrationStatus_Enum.SELESAI) {
                    examStatus = "COMPLETED";
                }

                data.put("validationStatus", validationStatusStr);
                data.put("paymentStatus", paymentStatus);
                data.put("installmentStatus", installmentStatus);
                data.put("paymentMethod", "BRIVA");

                String repairStatusStr = "BELUM_PERBAIKAN";
                Optional<FormRepairStatus> repairStatusOpt = formRepairStatusRepository.findByFormValidationId(fv.getId());
                if (repairStatusOpt.isPresent()) {
                    repairStatusStr = repairStatusOpt.get().getStatus().toString();
                }
                data.put("repairStatus", repairStatusStr);
                data.put("revisionReason", fv.getRejectionReason() != null ? fv.getRejectionReason() : "");
                data.put("rejectionReason", fv.getRejectionReason() != null ? fv.getRejectionReason() : "");

                String reEnrollmentStatus = "NOT_STARTED";
                Optional<RegistrationStatus> reEnrollRegStatus = registrationStatusRepository
                        .findByUserAndStage(user, RegistrationStatus.RegistrationStage.DAFTAR_ULANG);
                if (reEnrollRegStatus.isPresent() && reEnrollRegStatus.get().getStatus() == RegistrationStatus.RegistrationStatus_Enum.SELESAI) {
                    reEnrollmentStatus = "COMPLETED";
                } else if (reEnrollRegStatus.isPresent()) {
                    reEnrollmentStatus = "IN_PROGRESS";
                }
                data.put("reEnrollmentStatus", reEnrollmentStatus);

                // Add reenrollmentId so frontend can call openReenrollDetailModal with the correct ID
                try {
                    reEnrollmentRepository.findByStudent_Id(student.getId())
                            .ifPresent(re -> data.put("reenrollmentId", re.getId()));
                } catch (Exception e) {
                    log.debug("Could not fetch reenrollmentId for student {}: {}", student.getId(), e.getMessage());
                }
                if (!data.containsKey("reenrollmentId")) {
                    data.put("reenrollmentId", null);
                }

                data.put("examToken", fv.getExamToken() != null ? fv.getExamToken() : "");
                data.put("hasToken", fv.getExamToken() != null && !fv.getExamToken().isEmpty());
                data.put("examStatus", examStatus);

                String waveType = "REGULAR_TEST";
                if (form != null && form.getPeriod() != null && form.getPeriod().getWaveType() != null) {
                    waveType = form.getPeriod().getWaveType().toString();
                }
                data.put("waveType", waveType);
                data.put("createdAt", fv.getCreatedAt());
                data.put("submittedAt", form != null ? form.getSubmittedAt() : null);
                data.put("updatedAt", fv.getUpdatedAt());

                return data;
            }).collect(Collectors.toList());
    }

    public Map<String, Object> getFormDetails(Long formValidationId) {
        FormValidation validation = formValidationRepository.findById(formValidationId)
                .orElseThrow(() -> new RuntimeException("Form validation tidak ditemukan"));

        AdmissionForm form = validation.getAdmissionForm();
        Student student = validation.getStudent();
        Map<String, Object> details = new HashMap<>();
        details.put("id", validation.getId());
        details.put("studentId", student.getId());
        details.put("formId", form.getId());
        details.put("selectionTypeId", form.getSelectionTypeId() != null ? form.getSelectionTypeId() : "-");
        details.put("jenisSeleksiId", form.getJenisSeleksiId() != null ? form.getJenisSeleksiId() : "-");
        details.put("periodId", form.getPeriod() != null ? form.getPeriod().getId() : "-");

        Map<String, Object> studentInfo = new HashMap<>();
        studentInfo.put("fullName", student.getFullName());
        studentInfo.put("email", student.getUser().getEmail());
        studentInfo.put("phoneNumber", student.getPhoneNumber());
        studentInfo.put("nik", form.getNik());
        studentInfo.put("birthDate", form.getBirthDate() != null ? form.getBirthDate() : "-");
        studentInfo.put("birthPlace", form.getBirthPlace() != null ? form.getBirthPlace() : "-");
        studentInfo.put("gender", form.getGender() != null ? form.getGender() : "-");
        studentInfo.put("religion", form.getReligion() != null ? form.getReligion() : "-");
        studentInfo.put("informationSource", form.getInformationSource() != null ? form.getInformationSource() : "-");
        details.put("student", studentInfo);

        Map<String, Object> programInfo = new HashMap<>();
        programInfo.put("pilihan1", form.getProgramStudi1() != null ? form.getProgramStudi1() : "-");
        programInfo.put("pilihan2", form.getProgramStudi2() != null ? form.getProgramStudi2() : "-");
        programInfo.put("pilihan3", form.getProgramStudi3() != null ? form.getProgramStudi3() : "-");
        programInfo.put("tipeForm", form.getFormType() != null ? form.getFormType().toString() : "-");
        details.put("programChoice", programInfo);

        Map<String, Object> addressInfo = new HashMap<>();
        addressInfo.put("alamatMedan", form.getAddressMedan() != null ? form.getAddressMedan() : "-");
        addressInfo.put("residenceInfo", form.getResidenceInfo() != null ? form.getResidenceInfo() : "-");
        addressInfo.put("kelurahan", form.getSubdistrict() != null ? form.getSubdistrict() : "-");
        addressInfo.put("kecamatan", form.getDistrict() != null ? form.getDistrict() : "-");
        addressInfo.put("kota", form.getCity() != null ? form.getCity() : "-");
        addressInfo.put("provinsi", form.getProvince() != null ? form.getProvince() : "-");
        details.put("address", addressInfo);

        Map<String, Object> parentInfo = new HashMap<>();
        parentInfo.put("fatherName", form.getFatherName() != null ? form.getFatherName() : "-");
        parentInfo.put("fatherNik", form.getFatherNik() != null ? form.getFatherNik() : "-");
        parentInfo.put("fatherBirthDate", form.getFatherBirthDate() != null ? form.getFatherBirthDate() : "-");
        parentInfo.put("fatherEducation", form.getFatherEducation() != null ? form.getFatherEducation() : "-");
        parentInfo.put("fatherOccupation", form.getFatherOccupation() != null ? form.getFatherOccupation() : "-");
        parentInfo.put("fatherIncome", form.getFatherIncome() != null ? form.getFatherIncome() : "-");
        parentInfo.put("fatherPhone", form.getFatherPhone() != null ? form.getFatherPhone() : "-");
        parentInfo.put("fatherStatus", form.getFatherStatus() != null ? form.getFatherStatus() : "-");
        parentInfo.put("motherName", form.getMotherName() != null ? form.getMotherName() : "-");
        parentInfo.put("motherNik", form.getMotherNik() != null ? form.getMotherNik() : "-");
        parentInfo.put("motherBirthDate", form.getMotherBirthDate() != null ? form.getMotherBirthDate() : "-");
        parentInfo.put("motherEducation", form.getMotherEducation() != null ? form.getMotherEducation() : "-");
        parentInfo.put("motherOccupation", form.getMotherOccupation() != null ? form.getMotherOccupation() : "-");
        parentInfo.put("motherIncome", form.getMotherIncome() != null ? form.getMotherIncome() : "-");
        parentInfo.put("motherPhone", form.getMotherPhone() != null ? form.getMotherPhone() : "-");
        parentInfo.put("motherStatus", form.getMotherStatus() != null ? form.getMotherStatus() : "-");
        parentInfo.put("parentSubdistrict", form.getParentSubdistrict() != null ? form.getParentSubdistrict() : "-");
        parentInfo.put("parentCity", form.getParentCity() != null ? form.getParentCity() : "-");
        parentInfo.put("parentProvince", form.getParentProvince() != null ? form.getParentProvince() : "-");
        parentInfo.put("parentPhone", form.getParentPhone() != null ? form.getParentPhone() : "-");
        parentInfo.put("parentAddress",
                (form.getParentSubdistrict() != null ? form.getParentSubdistrict() : "") + ", " +
                (form.getParentCity() != null ? form.getParentCity() : "") + ", " +
                (form.getParentProvince() != null ? form.getParentProvince() : ""));
        details.put("parents", parentInfo);

        Map<String, Object> schoolInfo = new HashMap<>();
        schoolInfo.put("namaSekolah", form.getSchoolOrigin() != null ? form.getSchoolOrigin() : "-");
        schoolInfo.put("jurusan", form.getSchoolMajor() != null ? form.getSchoolMajor() : "-");
        schoolInfo.put("tahunLulus", form.getSchoolYear() != null ? form.getSchoolYear() : "-");
        schoolInfo.put("nisn", form.getNisn() != null ? form.getNisn() : "-");
        schoolInfo.put("kota", form.getSchoolCity() != null ? form.getSchoolCity() : "-");
        schoolInfo.put("provinsi", form.getSchoolProvince() != null ? form.getSchoolProvince() : "-");
        details.put("school", schoolInfo);

        Map<String, Object> documentInfo = new HashMap<>();
        documentInfo.put("photoIdPath", form.getPhotoIdPath() != null ? form.getPhotoIdPath() : "-");
        documentInfo.put("certificatePath", form.getCertificatePath() != null ? form.getCertificatePath() : "-");
        documentInfo.put("transcriptPath", form.getTranscriptPath() != null ? form.getTranscriptPath() : "-");
        documentInfo.put("nilaiFilePath", form.getNilaiFilePath() != null ? form.getNilaiFilePath() : "-");
        documentInfo.put("rankingFilePath", form.getRankingFilePath() != null ? form.getRankingFilePath() : "-");
        details.put("documents", documentInfo);

        details.put("additionalInfo", form.getAdditionalInfo() != null ? form.getAdditionalInfo() : "-");
        details.put("formStatus", form.getStatus() != null ? form.getStatus().toString() : "-");
        details.put("submittedAt", form.getSubmittedAt() != null ? form.getSubmittedAt() : "-");
        details.put("createdAt", form.getCreatedAt() != null ? form.getCreatedAt() : "-");
        details.put("updatedAt", form.getUpdatedAt() != null ? form.getUpdatedAt() : "-");

        Map<String, Object> validationInfo = new HashMap<>();
        validationInfo.put("validationStatus", validation.getValidationStatus() != null ? validation.getValidationStatus().toString() : "-");
        validationInfo.put("paymentStatus", validation.getPaymentStatus() != null ? validation.getPaymentStatus().toString() : "-");
        validationInfo.put("vaNumber", validation.getVirtualAccountNumber() != null ? validation.getVirtualAccountNumber() : "-");
        validationInfo.put("paymentAmount", validation.getPaymentAmount() != null ? validation.getPaymentAmount() : "-");
        validationInfo.put("createdAt", validation.getCreatedAt() != null ? validation.getCreatedAt() : "-");
        validationInfo.put("submittedAt", form.getSubmittedAt() != null ? form.getSubmittedAt() : "-");
        details.put("validation", validationInfo);

        String waveType = "REGULAR_TEST";
        if (form.getPeriod() != null && form.getPeriod().getWaveType() != null) {
            waveType = form.getPeriod().getWaveType().toString();
        }
        details.put("waveType", waveType);

        return details;
    }

    public Map<String, Object> getAdmissionFormStudentDetails(Long studentId) {
        Optional<AdmissionForm> admissionFormOpt = admissionFormRepository.findAll().stream()
                .filter(f -> f.getStudent() != null && f.getStudent().getId().equals(studentId))
                .findFirst();

        if (admissionFormOpt.isEmpty()) {
            throw new RuntimeException("Formulir pendaftaran tidak ditemukan");
        }

        AdmissionForm form = admissionFormOpt.get();
        Map<String, Object> details = new HashMap<>();

        String waveType = "N/A";
        if (form.getPeriod() != null && form.getPeriod().getWaveType() != null) {
            waveType = form.getPeriod().getWaveType().toString();
        }
        details.put("registrationPeriodWaveType", waveType);

        String jenisSeleksiName = "N/A";
        if (form.getJenisSeleksiId() != null) {
            jenisSeleksiRepository.findById(form.getJenisSeleksiId())
                    .ifPresent(js -> details.put("jenisSeleksiName", js.getNama()));
        }
        if (!details.containsKey("jenisSeleksiName")) {
            details.put("jenisSeleksiName", jenisSeleksiName);
        }

        String programStudiName = "N/A";
        try {
            List<CicilanRequest> cicilanList = cicilanRequestRepository
                    .findByStudentIdAndStatus(studentId, CicilanRequest.CicilanRequestStatus.APPROVED);
            if (!cicilanList.isEmpty() && cicilanList.get(0).getProgramStudi() != null) {
                programStudiName = cicilanList.get(0).getProgramStudi().getNama();
            }
        } catch (Exception e) {
            log.warn("⚠️ Error fetching from CICILAN: {}", e.getMessage());
        }
        if (programStudiName.equals("N/A") && form.getProgramStudi1() != null && !form.getProgramStudi1().isEmpty()) {
            programStudiName = form.getProgramStudi1();
        }
        details.put("programStudiName", programStudiName);

        return details;
    }

    @Transactional
    public Map<String, Object> updateRepairStatus(Long studentId, String repairStatus) {
        List<FormValidation> validations = formValidationRepository.findAll().stream()
                .filter(fv -> fv.getStudent() != null && fv.getStudent().getId().equals(studentId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();

        if (validations.isEmpty()) {
            return Map.of("success", true, "message", "Repair status tidak perlu di-update", "studentId", studentId);
        }

        FormValidation validation = validations.get(0);
        Optional<FormRepairStatus> repairStatusOptional = formRepairStatusRepository.findByFormValidationId(validation.getId());
        FormRepairStatus repairStatusRecord = repairStatusOptional.orElseGet(() -> FormRepairStatus.builder()
                .formValidation(validation)
                .status(FormRepairStatus.RepairStatus.BELUM_PERBAIKAN)
                .createdAt(LocalDateTime.now())
                .build());

        repairStatusRecord.setStatus("SUDAH_PERBAIKAN".equals(repairStatus)
                ? FormRepairStatus.RepairStatus.SUDAH_PERBAIKAN
                : FormRepairStatus.RepairStatus.BELUM_PERBAIKAN);
        repairStatusRecord.setUpdatedAt(LocalDateTime.now());
        formRepairStatusRepository.save(repairStatusRecord);

        log.info("✅ Repair status updated to {} for student {}", repairStatus, studentId);
        return Map.of(
                "success", true,
                "message", "Repair status berhasil diperbarui",
                "studentId", studentId,
                "repairStatus", repairStatus,
                "validationId", validation.getId(),
                "repairStatusRecordId", repairStatusRecord.getId() != null ? repairStatusRecord.getId() : 0L
        );
    }

    // ===== PRIVATE EMAIL HELPERS =====

    private void sendApprovalEmail(String studentEmail, String studentName) {
        try {
            String subject = "🎓 SELAMAT! Pendaftaran Anda Telah Disetujui - Lihat Nomor Registrasi & Virtual Account";
            String htmlContent = String.format("""
                <html><body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f9f9f9; border-radius: 8px;">
                        <div style="text-align: center; padding: 20px; background-color: #28a745; border-radius: 8px 8px 0 0; color: white;">
                            <h1 style="margin: 0; font-size: 32px;">🎉 SELAMAT!</h1>
                            <p style="margin: 10px 0 0 0; font-size: 18px;">Anda Telah Menyelesaikan Pendaftaran</p>
                        </div>
                        <div style="padding: 30px; background-color: white;">
                            <p>Dear <strong>%s</strong>,</p>
                            <p>Formulir pendaftaran Anda telah <strong>DISETUJUI ✅</strong>. Silakan login ke dashboard PMB untuk melihat nomor registrasi dan virtual account BRIVA Anda.</p>
                            <p style="margin-top: 30px; color: #999; font-size: 12px;">---<br/>Tim PMB<br/>HKBP Nommensen</p>
                        </div>
                    </div>
                </body></html>""", studentName);
            emailService.sendHtmlEmail(studentEmail, subject, htmlContent);
        } catch (Exception e) {
            log.error("❌ Error sending approval email to {}: {}", studentEmail, e.getMessage());
        }
    }

    private void sendRejectionEmail(String studentEmail, String studentName, String rejectionReason) {
        try {
            String subject = "❌ Formulir Pendaftaran Anda Ditolak - PMB HKBP Nommensen";
            String htmlContent = String.format("""
                <html><body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f9f9f9; border-radius: 8px;">
                        <div style="text-align: center; padding: 20px; background-color: #dc3545; border-radius: 8px 8px 0 0; color: white;">
                            <h1 style="margin: 0; font-size: 28px;">❌ PEMBERITAHUAN PENOLAKAN</h1>
                        </div>
                        <div style="padding: 30px; background-color: white;">
                            <p>Dear <strong>%s</strong>,</p>
                            <p>Formulir pendaftaran Anda <strong>tidak dapat diterima</strong>.</p>
                            <div style="background-color: #f8d7da; border-left: 4px solid #dc3545; padding: 15px; margin: 20px 0;">
                                <p style="margin: 0; color: #721c24;"><strong>Alasan:</strong> %s</p>
                            </div>
                            <p style="margin-top: 30px; color: #999; font-size: 12px;">---<br/>Tim PMB<br/>HKBP Nommensen</p>
                        </div>
                    </div>
                </body></html>""", studentName, rejectionReason);
            emailService.sendHtmlEmail(studentEmail, subject, htmlContent);
        } catch (Exception e) {
            log.error("❌ Error sending rejection email to {}: {}", studentEmail, e.getMessage());
        }
    }

    private void sendRevisionNeededEmail(String studentEmail, String studentName, String revisionReason, Integer revisionNumber) {
        try {
            String subject = "✏️ REVISI DIPERLUKAN (Revisi ke-" + revisionNumber + ") - PMB HKBP Nommensen";
            String htmlContent = String.format("""
                <html><body style="font-family: Arial, sans-serif; line-height: 1.6; color: #333;">
                    <div style="max-width: 600px; margin: 0 auto; padding: 20px; background-color: #f9f9f9; border-radius: 8px;">
                        <div style="text-align: center; padding: 20px; background-color: #ff9800; border-radius: 8px 8px 0 0; color: white;">
                            <h1 style="margin: 0; font-size: 28px;">✏️ REVISI DIPERLUKAN (Revisi ke-%d)</h1>
                        </div>
                        <div style="padding: 30px; background-color: white;">
                            <p>Dear <strong>%s</strong>,</p>
                            <p>Ada beberapa data yang perlu Anda perbaiki sebelum formulir dapat disetujui.</p>
                            <div style="background-color: #ffe0b2; border-left: 4px solid #ff9800; padding: 15px; margin: 20px 0;">
                                <p style="margin: 0; color: #bf360c;"><strong>Data yang perlu diperbaiki:</strong> %s</p>
                            </div>
                            <p>Silakan login ke dashboard PMB dan perbaiki formulir Anda.</p>
                            <p style="margin-top: 30px; color: #999; font-size: 12px;">---<br/>Tim PMB<br/>HKBP Nommensen</p>
                        </div>
                    </div>
                </body></html>""", revisionNumber, studentName, revisionReason);
            emailService.sendHtmlEmail(studentEmail, subject, htmlContent);
        } catch (Exception e) {
            log.error("❌ Error sending revision email to {}: {}", studentEmail, e.getMessage());
        }
    }
}