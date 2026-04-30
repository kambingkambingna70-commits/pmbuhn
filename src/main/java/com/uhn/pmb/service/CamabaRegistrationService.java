package com.uhn.pmb.service;

import com.uhn.pmb.dto.ApiResponse;
import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service handling student-facing registration lookup operations:
 * gelombang (periods), jenis seleksi (formulas), selection types, program studi.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CamabaRegistrationService {

    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final SelectionTypeRepository selectionTypeRepository;
    private final JenisSeleksiRepository jenisSeleksiRepository;
    private final PeriodJenisSeleksiRepository periodJenisSeleksiRepository;
    private final SelectionProgramStudiRepository selectionProgramStudiRepository;

    /**
     * Get all gelombang with displayStatus calculation.
     */
    public List<Map<String, Object>> getAllGelombang() {
        List<RegistrationPeriod> periods = registrationPeriodRepository.findAll();
        periods.sort(Comparator.comparing(RegistrationPeriod::getRegStartDate));

        List<Map<String, Object>> result = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (RegistrationPeriod period : periods) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", period.getId());
            data.put("name", period.getName());
            data.put("description", period.getDescription());
            data.put("regStartDate", period.getRegStartDate());
            data.put("regEndDate", period.getRegEndDate());
            data.put("examDate", period.getExamDate());
            data.put("examEndDate", period.getExamEndDate());
            data.put("announcementDate", period.getAnnouncementDate());
            data.put("reenrollmentStartDate", period.getReenrollmentStartDate());
            data.put("reenrollmentEndDate", period.getReenrollmentEndDate());
            data.put("status", period.getStatus().toString());

            String displayStatus = "open";
            if (now.isBefore(period.getRegStartDate())) displayStatus = "notopen";
            else if (now.isAfter(period.getRegEndDate()) || period.getStatus() == RegistrationPeriod.Status.CLOSED)
                displayStatus = "closed";

            data.put("displayStatus", displayStatus);
            data.put("waveType", period.getWaveType() != null ? period.getWaveType().toString() : "REGULAR_TEST");
            result.add(data);
        }
        return result;
    }

    /**
     * Get formulas (jenis seleksi) optionally filtered by period.
     */
    public List<Map<String, Object>> getAllFormulas(Long periodId) {
        List<JenisSeleksi> jenisSeleksiList;

        if (periodId != null && periodId > 0) {
            log.info("📋 Fetching jenis seleksi for period ID: {}", periodId);
            if (registrationPeriodRepository.findById(periodId).isEmpty()) {
                return new ArrayList<>();
            }
            List<PeriodJenisSeleksi> links = periodJenisSeleksiRepository.findByPeriod_IdAndIsActiveTrue(periodId);
            jenisSeleksiList = new ArrayList<>();
            for (PeriodJenisSeleksi pjs : links) {
                jenisSeleksiList.add(pjs.getJenisSeleksi());
            }
        } else {
            jenisSeleksiList = jenisSeleksiRepository.findByIsActiveTrueOrderBySortOrder();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (JenisSeleksi jenis : jenisSeleksiList) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", jenis.getId());
            data.put("code", jenis.getCode());
            data.put("title", jenis.getNama());
            data.put("description", jenis.getDeskripsi());
            data.put("iconEmoji", jenis.getLogoUrl());
            data.put("price", jenis.getHarga());
            data.put("features", jenis.getFasilitas() != null
                    ? Arrays.asList(jenis.getFasilitas().split(",")) : new ArrayList<>());
            data.put("formType", jenis.getCode());
            data.put("isActive", true);
            result.add(data);
        }
        log.info("✅ Returning {} active jenis seleksi", result.size());
        return result;
    }

    /**
     * Get program studi connected to a jenis seleksi.
     */
    public List<Map<String, Object>> getProgramStudiByJenisSeleksi(Long jenisSeleksiId) {
        if (jenisSeleksiRepository.findById(jenisSeleksiId).isEmpty()) {
            return new ArrayList<>();
        }
        List<SelectionProgramStudi> connections =
                selectionProgramStudiRepository.findByJenisSeleksi_IdAndIsActiveTrue(jenisSeleksiId);

        List<Map<String, Object>> result = new ArrayList<>();
        for (SelectionProgramStudi conn : connections) {
            Map<String, Object> data = new HashMap<>();
            data.put("id", conn.getProgramStudi().getId());
            data.put("nama", conn.getProgramStudi().getNama());
            data.put("kode", conn.getProgramStudi().getKode());
            data.put("tipe", conn.getProgramStudi().getIsMedical() ? "Kedokteran" : "Non-Kedokteran");
            data.put("deskripsi", conn.getProgramStudi().getDeskripsi() != null
                    ? conn.getProgramStudi().getDeskripsi() : "");
            data.put("hargaTotalPerTahun", conn.getProgramStudi().getHargaTotalPerTahun() != null
                    ? conn.getProgramStudi().getHargaTotalPerTahun() : 0);
            data.put("cicilan1", conn.getProgramStudi().getCicilan1() != null ? conn.getProgramStudi().getCicilan1() : 0);
            data.put("cicilan2", conn.getProgramStudi().getCicilan2() != null ? conn.getProgramStudi().getCicilan2() : 0);
            data.put("cicilan3", conn.getProgramStudi().getCicilan3() != null ? conn.getProgramStudi().getCicilan3() : 0);
            data.put("cicilan4", conn.getProgramStudi().getCicilan4() != null ? conn.getProgramStudi().getCicilan4() : 0);
            data.put("cicilan5", conn.getProgramStudi().getCicilan5() != null ? conn.getProgramStudi().getCicilan5() : 0);
            data.put("cicilan6", conn.getProgramStudi().getCicilan6() != null ? conn.getProgramStudi().getCicilan6() : 0);
            result.add(data);
        }
        return result;
    }

    /**
     * Get selection type detail with requireTesting flag.
     */
    public Map<String, Object> getSelectionTypeDetail(Long selectionTypeId) {
        SelectionType selectionType = selectionTypeRepository.findById(selectionTypeId)
                .orElseThrow(() -> new RuntimeException("Selection type with ID " + selectionTypeId + " not found"));

        Map<String, Object> response = new HashMap<>();
        response.put("id", selectionType.getId());
        response.put("name", selectionType.getName() != null ? selectionType.getName() : "");
        response.put("requireTesting", selectionType.getRequireTesting() != null ? selectionType.getRequireTesting() : false);
        response.put("requireRanking", selectionType.getRequireRanking() != null ? selectionType.getRequireRanking() : false);
        response.put("description", selectionType.getDescription() != null ? selectionType.getDescription() : "");
        response.put("formType", selectionType.getFormType() != null ? selectionType.getFormType() : "");
        response.put("price", selectionType.getPrice() != null ? selectionType.getPrice() : 0L);
        return response;
    }

    /**
     * Get JenisSeleksi detail by ID (full detail).
     */
    public Map<String, Object> getJenisSeleksiDetail(Long jenisSeleksiId) {
        JenisSeleksi jenisSeleksi = jenisSeleksiRepository.findById(jenisSeleksiId)
                .orElseThrow(() -> new RuntimeException("Jenis seleksi with ID " + jenisSeleksiId + " not found"));

        Map<String, Object> response = new HashMap<>();
        response.put("id", jenisSeleksi.getId());
        response.put("code", jenisSeleksi.getCode() != null ? jenisSeleksi.getCode() : "");
        response.put("nama", jenisSeleksi.getNama() != null ? jenisSeleksi.getNama() : "");
        response.put("deskripsi", jenisSeleksi.getDeskripsi() != null ? jenisSeleksi.getDeskripsi() : "");
        response.put("fasilitas", jenisSeleksi.getFasilitas() != null ? jenisSeleksi.getFasilitas() : "");
        response.put("harga", jenisSeleksi.getHarga() != null ? jenisSeleksi.getHarga() : 0L);
        response.put("logoUrl", jenisSeleksi.getLogoUrl() != null ? jenisSeleksi.getLogoUrl() : "");
        response.put("isActive", jenisSeleksi.getIsActive() != null ? jenisSeleksi.getIsActive() : true);
        response.put("price", jenisSeleksi.getHarga() != null ? jenisSeleksi.getHarga() : 0L);
        response.put("requireTesting", true);
        response.put("requireRanking", false);
        response.put("formType", "BOTH");
        return response;
    }

    /**
     * Get JenisSeleksi by ID (alias/short detail).
     */
    public Map<String, Object> getJenisSeleksiById(Long id) {
        JenisSeleksi jenisSeleksi = jenisSeleksiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Jenis seleksi " + id + " not found"));

        Map<String, Object> response = new HashMap<>();
        response.put("id", jenisSeleksi.getId());
        response.put("code", jenisSeleksi.getCode());
        response.put("nama", jenisSeleksi.getNama());
        response.put("title", jenisSeleksi.getNama());
        response.put("deskripsi", jenisSeleksi.getDeskripsi());
        response.put("harga", jenisSeleksi.getHarga());
        response.put("price", jenisSeleksi.getHarga());
        response.put("logoUrl", jenisSeleksi.getLogoUrl());
        response.put("isActive", jenisSeleksi.getIsActive());
        return response;
    }
}
