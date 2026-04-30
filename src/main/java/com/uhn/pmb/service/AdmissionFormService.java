package com.uhn.pmb.service;

import com.uhn.pmb.dto.AdmissionFormDTO;
import com.uhn.pmb.dto.AdmissionFormSubmitRequest;
import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for student-facing admission form operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class AdmissionFormService {

    private final AdmissionFormRepository admissionFormRepository;
    private final FormValidationRepository formValidationRepository;
    private final FormRepairStatusRepository formRepairStatusRepository;
    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final SelectionTypeRepository selectionTypeRepository;
    private final JenisSeleksiRepository jenisSeleksiRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final RegistrationStatusService registrationStatusService;
    private final ValidationStatusTrackerService validationStatusTrackerService;
    private final StudentRegistrationService registrationService;
    private final FileStorageService fileStorageService;

    private Student resolveStudent(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return studentRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Student profile not found"));
    }

    /**
     * Check whether student has submitted a form and whether within 24h edit window.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> checkSubmissionStatus(String userEmail) {
        Student student = resolveStudent(userEmail);
        List<AdmissionForm> forms = admissionFormRepository.findByStudent_Id(student.getId());
        Map<String, Object> response = new HashMap<>();

        if (forms == null || forms.isEmpty()) {
            response.put("hasSubmitted", false);
            response.put("status", "NOT_SUBMITTED");
            response.put("message", "Anda belum mengirim formulir pendaftaran");
        } else {
            AdmissionForm latestForm = forms.get(forms.size() - 1);
            response.put("hasSubmitted", true);
            response.put("status", latestForm.getStatus().toString());
            response.put("formId", latestForm.getId());
            response.put("submittedAt", latestForm.getSubmittedAt());
            response.put("message", "Formulir sudah dikirim");

            if (latestForm.getSubmittedAt() != null) {
                long hoursElapsed = java.time.Duration.between(
                        latestForm.getSubmittedAt(), LocalDateTime.now()).toHours();
                boolean isEditable = hoursElapsed < 24;
                response.put("isEditable", isEditable);
                response.put("hoursElapsed", hoursElapsed);
                response.put("hoursRemaining", Math.max(0, 24 - hoursElapsed));
                response.put("editMessage", isEditable
                        ? "Anda masih bisa mengedit formulir selama " + (24 - hoursElapsed) + " jam"
                        : "Formulir sudah tidak dapat diedit. Sudah melewati 24 jam sejak pengiriman. " +
                          "Jika perlu perubahan, hubungi: +62-123-456-7890");
            }
        }
        return response;
    }

    /**
     * Get full admission form DTO for student.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getAdmissionFormData(String userEmail) {
        Student student = resolveStudent(userEmail);
        List<AdmissionForm> forms = admissionFormRepository.findByStudent_Id(student.getId());

        if (forms == null || forms.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("success", false);
            empty.put("message", "Tidak ada formulir yang tersimpan");
            empty.put("data", null);
            return empty;
        }

        AdmissionForm latestForm = forms.get(forms.size() - 1);
        AdmissionFormDTO formDTO = AdmissionFormDTO.builder()
                .id(latestForm.getId())
                .studentId(student.getId())
                .periodId(latestForm.getPeriod() != null ? latestForm.getPeriod().getId() : null)
                .periodName(latestForm.getPeriod() != null ? latestForm.getPeriod().getName() : null)
                .waveType(latestForm.getPeriod() != null && latestForm.getPeriod().getWaveType() != null
                        ? latestForm.getPeriod().getWaveType().toString() : "REGULAR_TEST")
                .selectionTypeId(latestForm.getSelectionTypeId())
                .selectionTypeName(latestForm.getJenisSeleksiId() != null
                        ? latestForm.getJenisSeleksiId().toString() : null)
                .jenisSeleksiId(latestForm.getJenisSeleksiId())
                .formType(latestForm.getFormType() != null ? latestForm.getFormType().toString() : null)
                .programStudi1(latestForm.getProgramStudi1())
                .programStudi2(latestForm.getProgramStudi2())
                .programStudi3(latestForm.getProgramStudi3())
                .additionalInfo(latestForm.getAdditionalInfo())
                .fullName(latestForm.getFullName())
                .nik(latestForm.getNik())
                .addressMedan(latestForm.getAddressMedan())
                .residenceInfo(latestForm.getResidenceInfo())
                .subdistrict(latestForm.getSubdistrict())
                .district(latestForm.getDistrict())
                .city(latestForm.getCity())
                .province(latestForm.getProvince())
                .phoneNumber(latestForm.getPhoneNumber())
                .email(latestForm.getEmail())
                .birthPlace(latestForm.getBirthPlace())
                .birthDate(latestForm.getBirthDate())
                .gender(latestForm.getGender())
                .religion(latestForm.getReligion())
                .informationSource(latestForm.getInformationSource())
                .fatherNik(latestForm.getFatherNik())
                .fatherName(latestForm.getFatherName())
                .fatherBirthDate(latestForm.getFatherBirthDate())
                .fatherEducation(latestForm.getFatherEducation())
                .fatherOccupation(latestForm.getFatherOccupation())
                .fatherIncome(latestForm.getFatherIncome())
                .fatherPhone(latestForm.getFatherPhone())
                .fatherStatus(latestForm.getFatherStatus())
                .motherNik(latestForm.getMotherNik())
                .motherName(latestForm.getMotherName())
                .motherBirthDate(latestForm.getMotherBirthDate())
                .motherEducation(latestForm.getMotherEducation())
                .motherOccupation(latestForm.getMotherOccupation())
                .motherIncome(latestForm.getMotherIncome())
                .motherPhone(latestForm.getMotherPhone())
                .motherStatus(latestForm.getMotherStatus())
                .parentSubdistrict(latestForm.getParentSubdistrict())
                .parentCity(latestForm.getParentCity())
                .parentProvince(latestForm.getParentProvince())
                .parentPhone(latestForm.getParentPhone())
                .schoolOrigin(latestForm.getSchoolOrigin())
                .schoolMajor(latestForm.getSchoolMajor())
                .schoolYear(latestForm.getSchoolYear())
                .nisn(latestForm.getNisn())
                .schoolCity(latestForm.getSchoolCity())
                .schoolProvince(latestForm.getSchoolProvince())
                .photoIdPath(latestForm.getPhotoIdPath())
                .certificatePath(latestForm.getCertificatePath())
                .transcriptPath(latestForm.getTranscriptPath())
                .status(latestForm.getStatus() != null ? latestForm.getStatus().toString() : null)
                .submittedAt(latestForm.getSubmittedAt())
                .createdAt(latestForm.getCreatedAt())
                .updatedAt(latestForm.getUpdatedAt())
                .build();

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", formDTO);
        response.put("message", "Data formulir berhasil diambil");
        return response;
    }

    /**
     * Get minimal dashboard data for current form.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getCurrentAdmissionFormData(String userEmail) {
        Student student = resolveStudent(userEmail);
        List<AdmissionForm> forms = admissionFormRepository.findByStudent_Id(student.getId());

        if (forms == null || forms.isEmpty()) {
            Map<String, Object> empty = new HashMap<>();
            empty.put("success", false);
            empty.put("message", "Belum ada formulir yang tersimpan");
            empty.put("data", null);
            return empty;
        }

        AdmissionForm latestForm = forms.get(forms.size() - 1);
        Map<String, Object> formData = new HashMap<>();
        formData.put("id", latestForm.getId());
        formData.put("studentId", student.getId());

        if (latestForm.getPeriod() != null) {
            formData.put("periodId", latestForm.getPeriod().getId());
            formData.put("periodName", latestForm.getPeriod().getName());
            String waveMode = latestForm.getPeriod().getWaveType() != null
                    ? latestForm.getPeriod().getWaveType().toString() : "REGULAR_TEST";
            formData.put("waveMode", waveMode);
        } else {
            formData.put("periodId", null);
            formData.put("periodName", null);
            formData.put("waveMode", null);
        }

        if (latestForm.getJenisSeleksiId() != null) {
            formData.put("jenisSeleksiId", latestForm.getJenisSeleksiId());
            String jenisSeleksiName = null;
            try {
                var js = jenisSeleksiRepository.findById(latestForm.getJenisSeleksiId());
                if (js.isPresent()) jenisSeleksiName = js.get().getNama();
            } catch (Exception ignored) {}
            formData.put("jenisSeleksiName", jenisSeleksiName);
        } else {
            formData.put("jenisSeleksiId", null);
            formData.put("jenisSeleksiName", null);
        }

        formData.put("selectionTypeId", latestForm.getSelectionTypeId());
        formData.put("programStudi1", latestForm.getProgramStudi1());
        formData.put("programStudi2", latestForm.getProgramStudi2());
        formData.put("programStudi3", latestForm.getProgramStudi3());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Data formulir berhasil diambil");
        response.put("data", formData);
        return response;
    }

    /**
     * Update admission form data from multipart request.
     */
    public Map<String, Object> updateAdmissionFormData(String userEmail, HttpServletRequest request) throws Exception {
        Student student = resolveStudent(userEmail);
        List<AdmissionForm> forms = admissionFormRepository.findByStudent_Id(student.getId());
        if (forms == null || forms.isEmpty()) {
            throw new RuntimeException("Tidak ada formulir yang tersimpan");
        }

        AdmissionForm latestForm = forms.get(forms.size() - 1);

        if (request instanceof org.springframework.web.multipart.MultipartHttpServletRequest multipart) {
            String programStudi1 = multipart.getParameter("programStudi1");
            String programStudi2 = multipart.getParameter("programStudi2");
            String programStudi3 = multipart.getParameter("programStudi3");
            String additionalInfo = multipart.getParameter("additionalInfo");
            if (programStudi1 != null && !programStudi1.isEmpty()) latestForm.setProgramStudi1(programStudi1);
            if (programStudi2 != null && !programStudi2.isEmpty()) latestForm.setProgramStudi2(programStudi2);
            if (programStudi3 != null && !programStudi3.isEmpty()) latestForm.setProgramStudi3(programStudi3);
            if (additionalInfo != null && !additionalInfo.isEmpty()) latestForm.setAdditionalInfo(additionalInfo);

            String fullName = multipart.getParameter("fullName");
            String nik = multipart.getParameter("nik");
            String addressMedan = multipart.getParameter("addressMedan");
            String residenceInfo = multipart.getParameter("residenceInfo");
            String subdistrict = multipart.getParameter("subdistrict");
            String district = multipart.getParameter("district");
            String city = multipart.getParameter("city");
            String province = multipart.getParameter("province");
            String phoneNumber = multipart.getParameter("phoneNumber");
            String email = multipart.getParameter("email");
            String birthPlace = multipart.getParameter("birthPlace");
            String birthDate = multipart.getParameter("birthDate");
            String gender = multipart.getParameter("gender");
            String religion = multipart.getParameter("religion");
            String informationSource = multipart.getParameter("informationSource");
            if (fullName != null && !fullName.isEmpty()) latestForm.setFullName(fullName);
            if (nik != null && !nik.isEmpty()) latestForm.setNik(nik);
            if (addressMedan != null && !addressMedan.isEmpty()) latestForm.setAddressMedan(addressMedan);
            if (residenceInfo != null && !residenceInfo.isEmpty()) latestForm.setResidenceInfo(residenceInfo);
            if (subdistrict != null && !subdistrict.isEmpty()) latestForm.setSubdistrict(subdistrict);
            if (district != null && !district.isEmpty()) latestForm.setDistrict(district);
            if (city != null && !city.isEmpty()) latestForm.setCity(city);
            if (province != null && !province.isEmpty()) latestForm.setProvince(province);
            if (phoneNumber != null && !phoneNumber.isEmpty()) latestForm.setPhoneNumber(phoneNumber);
            if (email != null && !email.isEmpty()) latestForm.setEmail(email);
            if (birthPlace != null && !birthPlace.isEmpty()) latestForm.setBirthPlace(birthPlace);
            if (birthDate != null && !birthDate.isEmpty()) latestForm.setBirthDate(birthDate);
            if (gender != null && !gender.isEmpty()) latestForm.setGender(gender);
            if (religion != null && !religion.isEmpty()) latestForm.setReligion(religion);
            if (informationSource != null && !informationSource.isEmpty())
                latestForm.setInformationSource(informationSource);

            String fatherNik = multipart.getParameter("fatherNik");
            String fatherName = multipart.getParameter("fatherName");
            String fatherBirthDate = multipart.getParameter("fatherBirthDate");
            String fatherEducation = multipart.getParameter("fatherEducation");
            String fatherOccupation = multipart.getParameter("fatherOccupation");
            String fatherIncome = multipart.getParameter("fatherIncome");
            String fatherPhone = multipart.getParameter("fatherPhone");
            String fatherStatus = multipart.getParameter("fatherStatus");
            if (fatherNik != null && !fatherNik.isEmpty()) latestForm.setFatherNik(fatherNik);
            if (fatherName != null && !fatherName.isEmpty()) latestForm.setFatherName(fatherName);
            if (fatherBirthDate != null && !fatherBirthDate.isEmpty()) latestForm.setFatherBirthDate(fatherBirthDate);
            if (fatherEducation != null && !fatherEducation.isEmpty()) latestForm.setFatherEducation(fatherEducation);
            if (fatherOccupation != null && !fatherOccupation.isEmpty())
                latestForm.setFatherOccupation(fatherOccupation);
            if (fatherIncome != null && !fatherIncome.isEmpty()) latestForm.setFatherIncome(fatherIncome);
            if (fatherPhone != null && !fatherPhone.isEmpty()) latestForm.setFatherPhone(fatherPhone);
            if (fatherStatus != null && !fatherStatus.isEmpty()) latestForm.setFatherStatus(fatherStatus);

            String motherNik = multipart.getParameter("motherNik");
            String motherName = multipart.getParameter("motherName");
            String motherBirthDate = multipart.getParameter("motherBirthDate");
            String motherEducation = multipart.getParameter("motherEducation");
            String motherOccupation = multipart.getParameter("motherOccupation");
            String motherIncome = multipart.getParameter("motherIncome");
            String motherPhone = multipart.getParameter("motherPhone");
            String motherStatus = multipart.getParameter("motherStatus");
            if (motherNik != null && !motherNik.isEmpty()) latestForm.setMotherNik(motherNik);
            if (motherName != null && !motherName.isEmpty()) latestForm.setMotherName(motherName);
            if (motherBirthDate != null && !motherBirthDate.isEmpty()) latestForm.setMotherBirthDate(motherBirthDate);
            if (motherEducation != null && !motherEducation.isEmpty()) latestForm.setMotherEducation(motherEducation);
            if (motherOccupation != null && !motherOccupation.isEmpty())
                latestForm.setMotherOccupation(motherOccupation);
            if (motherIncome != null && !motherIncome.isEmpty()) latestForm.setMotherIncome(motherIncome);
            if (motherPhone != null && !motherPhone.isEmpty()) latestForm.setMotherPhone(motherPhone);
            if (motherStatus != null && !motherStatus.isEmpty()) latestForm.setMotherStatus(motherStatus);

            String parentSubdistrict = multipart.getParameter("parentSubdistrict");
            String parentCity = multipart.getParameter("parentCity");
            String parentProvince = multipart.getParameter("parentProvince");
            String parentPhone = multipart.getParameter("parentPhone");
            if (parentSubdistrict != null && !parentSubdistrict.isEmpty())
                latestForm.setParentSubdistrict(parentSubdistrict);
            if (parentCity != null && !parentCity.isEmpty()) latestForm.setParentCity(parentCity);
            if (parentProvince != null && !parentProvince.isEmpty()) latestForm.setParentProvince(parentProvince);
            if (parentPhone != null && !parentPhone.isEmpty()) latestForm.setParentPhone(parentPhone);

            String schoolOrigin = multipart.getParameter("schoolOrigin");
            String schoolMajor = multipart.getParameter("schoolMajor");
            String schoolYearStr = multipart.getParameter("schoolYear");
            String nisn = multipart.getParameter("nisn");
            String schoolCity = multipart.getParameter("schoolCity");
            String schoolProvince = multipart.getParameter("schoolProvince");
            if (schoolOrigin != null && !schoolOrigin.isEmpty()) latestForm.setSchoolOrigin(schoolOrigin);
            if (schoolMajor != null && !schoolMajor.isEmpty()) latestForm.setSchoolMajor(schoolMajor);
            if (schoolYearStr != null && !schoolYearStr.isEmpty()) {
                try { latestForm.setSchoolYear(Integer.parseInt(schoolYearStr)); } catch (Exception ignored) {}
            }
            if (nisn != null && !nisn.isEmpty()) latestForm.setNisn(nisn);
            if (schoolCity != null && !schoolCity.isEmpty()) latestForm.setSchoolCity(schoolCity);
            if (schoolProvince != null && !schoolProvince.isEmpty()) latestForm.setSchoolProvince(schoolProvince);

            // File Uploads
            String uploadsPath = "uploads/admission-forms/" + student.getId();
            Files.createDirectories(Paths.get(uploadsPath));

            MultipartFile photoId = multipart.getFile("photoId");
            if (photoId != null && !photoId.isEmpty()) {
                String fileName = "photo_" + System.currentTimeMillis() + "_" + photoId.getOriginalFilename();
                Files.write(Paths.get(uploadsPath, fileName), photoId.getBytes());
                latestForm.setPhotoIdPath(uploadsPath + "/" + fileName);
            }
            MultipartFile certificate = multipart.getFile("certificate");
            if (certificate != null && !certificate.isEmpty()) {
                String fileName = "certificate_" + System.currentTimeMillis() + "_" + certificate.getOriginalFilename();
                Files.write(Paths.get(uploadsPath, fileName), certificate.getBytes());
                latestForm.setCertificatePath(uploadsPath + "/" + fileName);
            }
            MultipartFile transcript = multipart.getFile("transcript");
            if (transcript != null && !transcript.isEmpty()) {
                String fileName = "transcript_" + System.currentTimeMillis() + "_" + transcript.getOriginalFilename();
                Files.write(Paths.get(uploadsPath, fileName), transcript.getBytes());
                latestForm.setTranscriptPath(uploadsPath + "/" + fileName);
            }
        }

        latestForm.setUpdatedAt(LocalDateTime.now());
        admissionFormRepository.save(latestForm);

        AdmissionFormDTO formDTO = AdmissionFormDTO.fromEntity(latestForm);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "✅ Formulir berhasil diperbarui dengan semua 48 field");
        response.put("data", formDTO);
        return response;
    }

    /**
     * Submit admission form (creates form, formValidation, repairStatus, marks stage complete).
     */
    public Map<String, Object> submitAdmissionForm(String userEmail, AdmissionFormSubmitRequest request) throws Exception {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Student student = studentRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Student profile not found"));

        AdmissionForm form = new AdmissionForm();
        form.setStudent(student);
        form.setFullName(request.getFullName());
        form.setNik(request.getNik());
        form.setAddressMedan(request.getAddressMedan());
        form.setResidenceInfo(request.getResidenceInfo());
        form.setSubdistrict(request.getSubdistrict());
        form.setDistrict(request.getDistrict());
        form.setCity(request.getCity());
        form.setProvince(request.getProvince());
        form.setPhoneNumber(request.getPhoneNumber());
        form.setEmail(request.getEmail());
        form.setBirthPlace(request.getBirthPlace());
        form.setBirthDate(request.getBirthDate());
        form.setGender(request.getGender());
        form.setReligion(request.getReligion());
        form.setInformationSource(request.getInformationSource());
        form.setFatherNik(request.getFatherNik());
        form.setFatherName(request.getFatherName());
        form.setFatherBirthDate(request.getFatherBirthDate());
        form.setFatherEducation(request.getFatherEducation());
        form.setFatherOccupation(request.getFatherOccupation());
        form.setFatherIncome(request.getFatherIncome());
        form.setFatherPhone(request.getFatherPhone());
        form.setFatherStatus(request.getFatherStatus());
        form.setMotherNik(request.getMotherNik());
        form.setMotherName(request.getMotherName());
        form.setMotherBirthDate(request.getMotherBirthDate());
        form.setMotherEducation(request.getMotherEducation());
        form.setMotherOccupation(request.getMotherOccupation());
        form.setMotherIncome(request.getMotherIncome());
        form.setMotherPhone(request.getMotherPhone());
        form.setMotherStatus(request.getMotherStatus());
        form.setParentSubdistrict(request.getParentSubdistrict());
        form.setParentCity(request.getParentCity());
        form.setParentProvince(request.getParentProvince());
        form.setParentPhone(request.getParentPhone());
        form.setSchoolOrigin(request.getSchoolOrigin());
        form.setSchoolMajor(request.getSchoolMajor());
        form.setSchoolYear(request.getSchoolYear());
        form.setNisn(request.getNisn());
        form.setSchoolCity(request.getSchoolCity());
        form.setSchoolProvince(request.getSchoolProvince());
        form.setProgramStudi1(request.getProgramChoice1());
        form.setProgramStudi2(request.getProgramChoice2());
        form.setProgramStudi3(request.getProgramChoice3());

        // File Uploads
        try {
            if (request.getPhotoId() != null && !request.getPhotoId().isEmpty())
                form.setPhotoIdPath(fileStorageService.saveFile(request.getPhotoId(), "admission-forms", student.getId(), "photoId"));
            if (request.getCertificate() != null && !request.getCertificate().isEmpty())
                form.setCertificatePath(fileStorageService.saveFile(request.getCertificate(), "admission-forms", student.getId(), "certificate"));
            if (request.getTranscript() != null && !request.getTranscript().isEmpty())
                form.setTranscriptPath(fileStorageService.saveFile(request.getTranscript(), "admission-forms", student.getId(), "transcript"));
            if (request.getNilaiFile() != null && !request.getNilaiFile().isEmpty())
                form.setNilaiFilePath(fileStorageService.saveFile(request.getNilaiFile(), "admission-forms", student.getId(), "nilaiFile"));
            if (request.getRankingFile() != null && !request.getRankingFile().isEmpty())
                form.setRankingFilePath(fileStorageService.saveFile(request.getRankingFile(), "admission-forms", student.getId(), "rankingFile"));
        } catch (Exception fileEx) {
            log.warn("Warning uploading files: {}", fileEx.getMessage());
        }

        // Jenis Seleksi & Period
        Long periodId = request.getSelectionTypeId();
        Long jenisSeleksiId = request.getJenisSeleksiId();

        if (jenisSeleksiId == null || jenisSeleksiId <= 0) {
            throw new RuntimeException("Jenis Seleksi (Formula) ID adalah wajib!");
        }
        JenisSeleksi jenisSeleksi = jenisSeleksiRepository.findById(jenisSeleksiId)
                .orElseThrow(() -> new RuntimeException("Jenis Seleksi dengan ID " + jenisSeleksiId + " tidak ditemukan"));

        boolean isMedical = (jenisSeleksi.getCode() != null && jenisSeleksi.getCode().equalsIgnoreCase("MEDICAL"))
                || (jenisSeleksi.getNama() != null && jenisSeleksi.getNama().toLowerCase().contains("kedokteran"));
        form.setFormType(isMedical ? SelectionType.FormType.MEDICAL : SelectionType.FormType.NON_MEDICAL);
        form.setJenisSeleksiId(jenisSeleksi.getId());

        if (periodId == null || periodId <= 0) {
            throw new RuntimeException("Period/Gelombang ID harus valid.");
        }
        RegistrationPeriod period = registrationPeriodRepository.findById(periodId)
                .orElseThrow(() -> new RuntimeException("Period dengan ID " + periodId + " tidak ditemukan"));

        form.setPeriod(period);
        form.setJenisSeleksiId(jenisSeleksiId);
        form.setSelectionTypeId(jenisSeleksiId);
        form.setStatus(AdmissionForm.FormStatus.VERIFIED);
        form.setSubmittedAt(LocalDateTime.now());
        form.setUpdatedAt(LocalDateTime.now());
        form.setAdditionalInfo(null);

        AdmissionForm savedForm = admissionFormRepository.save(form);
        log.info("✅ FORM SAVED - ID: {}", savedForm.getId());

        FormValidation formValidation = FormValidation.builder()
                .admissionForm(form)
                .student(student)
                .validationStatus(FormValidation.ValidationStatus.PENDING)
                .paymentStatus(FormValidation.PaymentStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();
        formValidation = formValidationRepository.save(formValidation);

        FormRepairStatus repairStatus = FormRepairStatus.builder()
                .formValidation(formValidation)
                .status(FormRepairStatus.RepairStatus.BELUM_PERBAIKAN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        formRepairStatusRepository.save(repairStatus);

        registrationStatusService.markAsCompleted(
                user,
                RegistrationStatus.RegistrationStage.FORM_SUBMISSION,
                "Form submitted at " + LocalDateTime.now());

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Formulir pendaftaran berhasil disubmit. Data Anda telah terverifikasi!");
        response.put("formId", form.getId());
        response.put("status", "VERIFIED");
        response.put("estimatedDays", "3-5 hari kerja");
        log.info("Student {} submitted admission form: {}", userEmail, form.getId());
        return response;
    }

    /**
     * Update admission form's selected period and formula (jenis seleksi).
     */
    public Map<String, Object> updateAdmissionFormSelection(String userEmail, Map<String, Object> request) {
        Student student = resolveStudent(userEmail);

        Long periodId = null;
        Long jenisSeleksiId = null;

        if (request.containsKey("periodId")) {
            Object p = request.get("periodId");
            periodId = p instanceof Number ? ((Number) p).longValue() : Long.parseLong(String.valueOf(p));
        }
        if (request.containsKey("gelombangId") && periodId == null) {
            Object g = request.get("gelombangId");
            periodId = g instanceof Number ? ((Number) g).longValue() : Long.parseLong(String.valueOf(g));
        }
        if (request.containsKey("jenisSeleksiId")) {
            Object j = request.get("jenisSeleksiId");
            jenisSeleksiId = j instanceof Number ? ((Number) j).longValue() : Long.parseLong(String.valueOf(j));
        }
        if (request.containsKey("selectionTypeId") && jenisSeleksiId == null) {
            Object s = request.get("selectionTypeId");
            jenisSeleksiId = s instanceof Number ? ((Number) s).longValue() : Long.parseLong(String.valueOf(s));
        }

        List<AdmissionForm> forms = admissionFormRepository.findByStudent_Id(student.getId());
        if (forms.isEmpty()) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "No admission form to update (not a critical error)");
            return response;
        }

        AdmissionForm form = forms.get(forms.size() - 1);

        if (periodId != null && periodId > 0) {
            RegistrationPeriod period = registrationPeriodRepository.findById(periodId).orElse(null);
            if (period != null) form.setPeriod(period);
        }

        if (jenisSeleksiId != null && jenisSeleksiId > 0) {
            JenisSeleksi js = jenisSeleksiRepository.findById(jenisSeleksiId).orElse(null);
            if (js != null) {
                form.setJenisSeleksiId(js.getId());
                boolean isMedical = (js.getCode() != null && js.getCode().equalsIgnoreCase("MEDICAL"))
                        || (js.getNama() != null && js.getNama().toLowerCase().contains("kedokteran"));
                form.setFormType(isMedical ? SelectionType.FormType.MEDICAL : SelectionType.FormType.NON_MEDICAL);
            }
        }

        form.setUpdatedAt(LocalDateTime.now());
        AdmissionForm updated = admissionFormRepository.save(form);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Admission form updated successfully");
        response.put("formId", updated.getId());
        response.put("periodId", periodId);
        response.put("jenisSeleksiId", jenisSeleksiId);
        response.put("updatedAt", updated.getUpdatedAt());
        return response;
    }

    /**
     * Register for admission (delegates to registrationService).
     */
    public AdmissionForm registerForAdmission(String userEmail, Long periodId,
                                               Long selectionTypeId, String programStudi) {
        Student student = resolveStudent(userEmail);
        RegistrationPeriod period = registrationPeriodRepository.findById(periodId)
                .orElseThrow(() -> new RuntimeException("Period not found"));
        SelectionType selType = selectionTypeRepository.findById(selectionTypeId)
                .orElseThrow(() -> new RuntimeException("Selection type not found"));
        return registrationService.registerForAdmission(student, period, selType, programStudi);
    }

    /**
     * Get list of student's admission forms, newest first.
     */
    @Transactional(readOnly = true)
    public List<AdmissionForm> getAdmissionStatus(String userEmail) {
        Student student = resolveStudent(userEmail);
        List<AdmissionForm> forms = admissionFormRepository.findByStudent_Id(student.getId());
        forms.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return forms;
    }

    /**
     * Submit revision for an admission form.
     */
    public Map<String, Object> submitRevision(String userEmail, Long formId,
                                               String fullName, String nik, String birthDate, String birthPlace,
                                               String gender, String phoneNumber, String email,
                                               String addressMedan, String residenceInfo,
                                               String subdistrict, String district, String city,
                                               String province, String religion, String informationSource,
                                               String fatherNik, String fatherName, String fatherBirthDate,
                                               String fatherEducation, String fatherOccupation,
                                               String fatherIncome, String fatherPhone, String fatherStatus,
                                               String motherNik, String motherName, String motherBirthDate,
                                               String motherEducation, String motherOccupation,
                                               String motherIncome, String motherPhone, String motherStatus,
                                               String parentSubdistrict, String parentCity,
                                               String parentProvince, String parentPhone,
                                               String schoolOrigin, String schoolMajor, String schoolYear,
                                               String nisn, String schoolCity, String schoolProvince,
                                               MultipartFile photoId, MultipartFile certificate,
                                               MultipartFile transcript) throws Exception {
        Student student = resolveStudent(userEmail);
        AdmissionForm form = admissionFormRepository.findById(formId)
                .orElseThrow(() -> new RuntimeException("Admission form not found"));

        if (!form.getStudent().getId().equals(student.getId())) {
            throw new RuntimeException("You don't have permission to revise this form");
        }

        log.info("🔄 Processing revision for form {}, student {}", formId, userEmail);

        form.setFullName(fullName);
        form.setNik(nik);
        form.setBirthDate(birthDate);
        form.setBirthPlace(birthPlace);
        form.setGender(gender);
        form.setPhoneNumber(phoneNumber);
        form.setEmail(email);
        form.setAddressMedan(addressMedan);
        form.setResidenceInfo(residenceInfo);
        form.setSubdistrict(subdistrict);
        form.setDistrict(district);
        form.setCity(city);
        form.setProvince(province);
        form.setReligion(religion);
        form.setInformationSource(informationSource);
        form.setFatherNik(fatherNik);
        form.setFatherName(fatherName);
        form.setFatherBirthDate(fatherBirthDate);
        form.setFatherEducation(fatherEducation);
        form.setFatherOccupation(fatherOccupation);
        form.setFatherIncome(fatherIncome);
        form.setFatherPhone(fatherPhone);
        form.setFatherStatus(fatherStatus);
        form.setMotherNik(motherNik);
        form.setMotherName(motherName);
        form.setMotherBirthDate(motherBirthDate);
        form.setMotherEducation(motherEducation);
        form.setMotherOccupation(motherOccupation);
        form.setMotherIncome(motherIncome);
        form.setMotherPhone(motherPhone);
        form.setMotherStatus(motherStatus);
        form.setParentSubdistrict(parentSubdistrict);
        form.setParentCity(parentCity);
        form.setParentProvince(parentProvince);
        form.setParentPhone(parentPhone);
        form.setSchoolOrigin(schoolOrigin);
        form.setSchoolMajor(schoolMajor);
        if (schoolYear != null && !schoolYear.isEmpty()) {
            try { form.setSchoolYear(Integer.parseInt(schoolYear)); } catch (NumberFormatException ignored) {}
        }
        form.setNisn(nisn);
        form.setSchoolCity(schoolCity);
        form.setSchoolProvince(schoolProvince);

        // File Uploads
        String uploadDir = "uploads/admission-forms/" + student.getId();
        new File(uploadDir).mkdirs();
        if (photoId != null && !photoId.isEmpty()) {
            String fname = UUID.randomUUID() + "_" + photoId.getOriginalFilename();
            Files.write(Paths.get(uploadDir + "/" + fname), photoId.getBytes());
            form.setPhotoIdPath(uploadDir + "/" + fname);
        }
        if (certificate != null && !certificate.isEmpty()) {
            String fname = UUID.randomUUID() + "_" + certificate.getOriginalFilename();
            Files.write(Paths.get(uploadDir + "/" + fname), certificate.getBytes());
            form.setCertificatePath(uploadDir + "/" + fname);
        }
        if (transcript != null && !transcript.isEmpty()) {
            String fname = UUID.randomUUID() + "_" + transcript.getOriginalFilename();
            Files.write(Paths.get(uploadDir + "/" + fname), transcript.getBytes());
            form.setTranscriptPath(uploadDir + "/" + fname);
        }

        form.setUpdatedAt(LocalDateTime.now());
        AdmissionForm savedForm = admissionFormRepository.save(form);

        validationStatusTrackerService.updateStatusToMenunggu(savedForm.getId());

        Optional<FormValidation> validationOpt = formValidationRepository.findByAdmissionFormId(savedForm.getId());
        if (validationOpt.isPresent()) {
            FormValidation validation = validationOpt.get();
            Optional<FormRepairStatus> repairStatusOpt =
                    formRepairStatusRepository.findByFormValidationId(validation.getId());
            if (repairStatusOpt.isPresent()) {
                FormRepairStatus repairStatus = repairStatusOpt.get();
                repairStatus.setStatus(FormRepairStatus.RepairStatus.SUDAH_PERBAIKAN);
                repairStatus.setUpdatedAt(LocalDateTime.now());
                formRepairStatusRepository.save(repairStatus);
                log.info("✅ FormRepairStatus updated to SUDAH_PERBAIKAN for validation {}", validation.getId());
            }
        }

        log.info("✅ Revision submitted for form {}: status -> MENUNGGU, repair status -> SUDAH_PERBAIKAN", formId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Formulir revisi berhasil dikirim. Status validasi direset ke MENUNGGU VALIDASI");
        response.put("formId", savedForm.getId());
        response.put("updatedAt", savedForm.getUpdatedAt());
        return response;
    }
}
