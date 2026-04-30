package com.uhn.pmb.service;

import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminDataExportService {

    private final FormValidationRepository formValidationRepository;
    private final ReEnrollmentValidationRepository reEnrollmentValidationRepository;
    private final HasilAkhirRepository hasilAkhirRepository;
    private final AdmissionFormRepository admissionFormRepository;
    private final ReEnrollmentRepository reenrollmentRepository;
    private final ReEnrollmentDocumentRepository reenrollmentDocumentRepository;

    public List<Map<String, Object>> exportFormAndPayment() {
        return formValidationRepository.findAll().stream().map(fv -> {
            Map<String, Object> row = new HashMap<>();
            row.put("Nama", fv.getStudent().getFullName());
            row.put("Email", fv.getStudent().getUser().getEmail());
            row.put("Status Form", fv.getValidationStatus().toString());
            row.put("Status Pembayaran", fv.getPaymentStatus().toString());
            row.put("Nomor VA", fv.getVirtualAccountNumber());
            row.put("Jumlah", fv.getPaymentAmount());
            row.put("Tanggal Dibuat", fv.getCreatedAt());
            row.put("Tanggal Divalidasi", fv.getValidatedAt());
            return row;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> exportReEnrollmentData() {
        return reEnrollmentValidationRepository.findAll().stream().map(rv -> {
            Map<String, Object> row = new HashMap<>();
            row.put("Nama", rv.getStudent().getFullName());
            row.put("Email", rv.getStudent().getUser().getEmail());
            row.put("Status Validasi", rv.getValidationStatus().toString());
            row.put("Tanggal Dibuat", rv.getCreatedAt());
            row.put("Tanggal Divalidasi", rv.getValidatedAt());
            return row;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> exportHasilAkhirData() {
        return hasilAkhirRepository.findAll().stream().map(ha -> {
            Map<String, Object> row = new HashMap<>();
            String studentName = ha.getStudent() != null ? ha.getStudent().getFullName() : "N/A";
            String studentEmail = ha.getUser() != null ? ha.getUser().getEmail() : "N/A";
            row.put("Nama", studentName);
            row.put("Email", studentEmail);
            row.put("Nomor Registrasi", ha.getNomorRegistrasi() != null ? ha.getNomorRegistrasi() : "-");
            row.put("BRIVA Number", ha.getBrivaNumber() != null ? ha.getBrivaNumber() : "-");
            row.put("BRIVA Amount", ha.getBrivaAmount() != null ? ha.getBrivaAmount() : 0);
            row.put("Status", ha.getStatus() != null ? ha.getStatus().toString() : "PENDING");
            row.put("Tanggal Dibuat", ha.getCreatedAt());
            row.put("Tanggal Diupdate", ha.getUpdatedAt());
            return row;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getAdmissionFormsTable() {
        return admissionFormRepository.findAll().stream().map(form -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", form.getId());
            row.put("studentName", form.getFullName() != null ? form.getFullName() : "N/A");
            row.put("nik", form.getNik() != null ? form.getNik() : "N/A");
            row.put("email", form.getEmail() != null ? form.getEmail() : "N/A");
            row.put("phoneNumber", form.getPhoneNumber() != null ? form.getPhoneNumber() : "N/A");
            row.put("birthPlace", form.getBirthPlace() != null ? form.getBirthPlace() : "N/A");
            row.put("birthDate", form.getBirthDate() != null ? form.getBirthDate() : "N/A");
            row.put("gender", form.getGender() != null ? form.getGender() : "N/A");
            row.put("religion", form.getReligion() != null ? form.getReligion() : "N/A");
            row.put("city", form.getCity() != null ? form.getCity() : "N/A");
            row.put("province", form.getProvince() != null ? form.getProvince() : "N/A");
            row.put("schoolOrigin", form.getSchoolOrigin() != null ? form.getSchoolOrigin() : "N/A");
            row.put("schoolMajor", form.getSchoolMajor() != null ? form.getSchoolMajor() : "N/A");
            row.put("programStudi1", form.getProgramStudi1() != null ? form.getProgramStudi1() : "N/A");
            row.put("programStudi2", form.getProgramStudi2() != null ? form.getProgramStudi2() : "N/A");
            row.put("programStudi3", form.getProgramStudi3() != null ? form.getProgramStudi3() : "N/A");
            row.put("photoIdPath", form.getPhotoIdPath() != null ? form.getPhotoIdPath() : "");
            row.put("createdAt", form.getCreatedAt());
            row.put("updatedAt", form.getUpdatedAt());
            return row;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getReenrollmentsTable() {
        return reenrollmentRepository.findAll().stream().map(re -> {
            Map<String, Object> row = new HashMap<>();
            Student student = re.getStudent();
            row.put("id", re.getId());
            row.put("studentName", student != null ? student.getFullName() : "N/A");
            row.put("nik", student != null ? student.getNik() : "N/A");
            row.put("email", student != null ? (student.getUser() != null ? student.getUser().getEmail() : "N/A") : "N/A");
            row.put("status", re.getStatus() != null ? re.getStatus().toString() : "PENDING");
            row.put("validationNotes", re.getValidationNotes() != null ? re.getValidationNotes() : "");
            row.put("submittedAt", re.getSubmittedAt());
            row.put("validatedAt", re.getValidatedAt());
            row.put("createdAt", re.getCreatedAt());
            row.put("updatedAt", re.getUpdatedAt());

            List<Map<String, String>> documents = new ArrayList<>();
            addDocIfPresent(documents, "KTP", re.getKtpFile());
            addDocIfPresent(documents, "Ijazah", re.getIjazahFile());
            addDocIfPresent(documents, "Pas Foto", re.getPasphotoFile());
            addDocIfPresent(documents, "Kartu Keluarga", re.getKartuKeluargaFile());
            addDocIfPresent(documents, "Pakta Integritas", re.getPaktaIntegritasFile());
            addDocIfPresent(documents, "SKCK", re.getSkckFile());
            addDocIfPresent(documents, "Surat Bebas Narkoba", re.getSuratBebasNarkobaFile());
            row.put("documents", documents);
            return row;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getHasilAkhirTable() {
        return hasilAkhirRepository.findAll().stream().map(ha -> {
            Map<String, Object> row = new HashMap<>();
            Student student = ha.getStudent();
            row.put("id", ha.getId());
            row.put("studentName", student != null ? student.getFullName() : "N/A");
            row.put("nik", student != null ? student.getNik() : "N/A");
            row.put("email", student != null ? (student.getUser() != null ? student.getUser().getEmail() : "N/A") : "N/A");
            row.put("nomorRegistrasi", ha.getNomorRegistrasi() != null ? ha.getNomorRegistrasi() : "-");
            row.put("brivaNumber", ha.getBrivaNumber() != null ? ha.getBrivaNumber() : "-");
            row.put("brivaAmount", ha.getBrivaAmount() != null ? ha.getBrivaAmount().toString() : "0");
            row.put("jumlahCicilan", ha.getJumlahCicilan() != null ? ha.getJumlahCicilan() : 1);
            row.put("waveType", ha.getWaveType() != null ? ha.getWaveType().toString() : "N/A");
            row.put("selectionType", ha.getSelectionType() != null ? ha.getSelectionType() : "N/A");
            row.put("programStudiName", ha.getProgramStudiName() != null ? ha.getProgramStudiName() : "N/A");
            row.put("status", ha.getStatus() != null ? ha.getStatus().toString() : "PENDING");
            row.put("createdAt", ha.getCreatedAt());
            row.put("updatedAt", ha.getUpdatedAt());
            return row;
        }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getHasilAkhirByWave(RegistrationPeriod.WaveType wave) {
        return hasilAkhirRepository.findAll().stream()
                .filter(ha -> ha.getWaveType() != null && ha.getWaveType().equals(wave))
                .map(ha -> {
                    Map<String, Object> row = new HashMap<>();
                    Student student = ha.getStudent();
                    row.put("id", ha.getId());
                    row.put("studentName", student != null ? student.getFullName() : "N/A");
                    row.put("nik", student != null ? student.getNik() : "N/A");
                    row.put("email", student != null ? (student.getUser() != null ? student.getUser().getEmail() : "N/A") : "N/A");
                    row.put("nomorRegistrasi", ha.getNomorRegistrasi() != null ? ha.getNomorRegistrasi() : "-");
                    row.put("brivaNumber", ha.getBrivaNumber() != null ? ha.getBrivaNumber() : "-");
                    row.put("brivaAmount", ha.getBrivaAmount() != null ? ha.getBrivaAmount().toString() : "0");
                    row.put("jumlahCicilan", ha.getJumlahCicilan() != null ? ha.getJumlahCicilan() : 1);
                    row.put("waveType", ha.getWaveType().toString());
                    row.put("selectionType", ha.getSelectionType() != null ? ha.getSelectionType() : "N/A");
                    row.put("programStudiName", ha.getProgramStudiName() != null ? ha.getProgramStudiName() : "N/A");
                    row.put("status", ha.getStatus() != null ? ha.getStatus().toString() : "PENDING");
                    row.put("createdAt", ha.getCreatedAt());
                    row.put("updatedAt", ha.getUpdatedAt());
                    return row;
                }).collect(Collectors.toList());
    }

    /**
     * Normalize an absolute or relative file path to "uploads/reenrollment/..." and add to documents list.
     */
    private void addDocIfPresent(List<Map<String, String>> documents, String type, String rawPath) {
        if (rawPath == null || rawPath.isBlank()) return;
        // Normalize backslashes to forward slashes
        String path = rawPath.replace('\\', '/');
        // Strip absolute path prefix - keep from "uploads/" onward
        int uploadsIdx = path.indexOf("uploads/");
        if (uploadsIdx >= 0) {
            path = path.substring(uploadsIdx);
        }
        Map<String, String> doc = new HashMap<>();
        doc.put("type", type);
        doc.put("path", path);
        doc.put("filename", path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path);
        documents.add(doc);
    }
}