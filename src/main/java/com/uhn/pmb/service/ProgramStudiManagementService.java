package com.uhn.pmb.service;

import com.uhn.pmb.dto.ProgramStudiRequest;
import com.uhn.pmb.entity.JenisSeleksi;
import com.uhn.pmb.entity.ProgramStudi;
import com.uhn.pmb.entity.SelectionProgramStudi;
import com.uhn.pmb.repository.JenisSeleksiRepository;
import com.uhn.pmb.repository.ProgramStudiRepository;
import com.uhn.pmb.repository.SelectionProgramStudiRepository;
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
public class ProgramStudiManagementService {

    private final ProgramStudiRepository programStudiRepository;
    private final SelectionProgramStudiRepository selectionProgramStudiRepository;
    private final JenisSeleksiRepository jenisSeleksiRepository;

    @Transactional
    public ProgramStudi createProgramStudi(ProgramStudiRequest request, Long jenisSeleksiId) {
        JenisSeleksi jenisSeleksi = jenisSeleksiRepository.findById(jenisSeleksiId)
                .orElseThrow(() -> new RuntimeException("Jenis Seleksi tidak ditemukan"));
        ProgramStudi programStudi = buildFromRequest(request);
        programStudi = programStudiRepository.save(programStudi);
        log.info("Program studi created: {} (ID: {})", programStudi.getNama(), programStudi.getId());
        return programStudi;
    }

    @Transactional
    public ProgramStudi createProgramStudiFull(ProgramStudiRequest request) {
        if (programStudiRepository.existsByKode(request.getKode())) {
            throw new IllegalArgumentException("Kode program studi sudah digunakan: " + request.getKode());
        }
        ProgramStudi programStudi = buildFromRequest(request);
        programStudi = programStudiRepository.save(programStudi);
        log.info("Program studi created: {} (ID: {})", programStudi.getNama(), programStudi.getId());
        return programStudi;
    }

    @Transactional
    public ProgramStudi updateProgramStudiById(Long id, ProgramStudiRequest request) {
        ProgramStudi programStudi = programStudiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Program Studi tidak ditemukan"));
        if (!programStudi.getKode().equals(request.getKode()) &&
                programStudiRepository.existsByKode(request.getKode())) {
            throw new IllegalArgumentException("Kode program studi sudah digunakan");
        }
        programStudi.setKode(request.getKode());
        programStudi.setNama(request.getNama());
        programStudi.setDeskripsi(request.getDeskripsi());
        programStudi.setIsMedical(request.getIsMedical() != null ? request.getIsMedical() : false);
        programStudi.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        programStudi.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        programStudi.setHargaTotalPerTahun(request.getHargaTotalPerTahun() != null ? request.getHargaTotalPerTahun() : 0L);
        programStudi.setCicilan1(request.getCicilan1() != null ? request.getCicilan1() : 0L);
        programStudi.setCicilan2(request.getCicilan2() != null ? request.getCicilan2() : 0L);
        programStudi.setCicilan3(request.getCicilan3() != null ? request.getCicilan3() : 0L);
        programStudi.setCicilan4(request.getCicilan4() != null ? request.getCicilan4() : 0L);
        programStudi.setCicilan5(request.getCicilan5() != null ? request.getCicilan5() : 0L);
        programStudi.setCicilan6(request.getCicilan6() != null ? request.getCicilan6() : 0L);
        programStudi.setUpdatedAt(LocalDateTime.now());
        programStudiRepository.save(programStudi);
        log.info("Program studi updated: {}", programStudi.getNama());
        return programStudi;
    }

    @Transactional
    public void deleteProgramStudiById(Long id) {
        ProgramStudi programStudi = programStudiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Program Studi tidak ditemukan"));
        if (selectionProgramStudiRepository.existsByProgramStudi_Id(id)) {
            throw new IllegalStateException("Program Studi masih digunakan di jenis seleksi lain");
        }
        programStudiRepository.delete(programStudi);
        log.info("Program studi deleted: {}", programStudi.getNama());
    }

    @Transactional
    public void linkToJenisSeleksi(ProgramStudi programStudi, List<Long> jenisSeleksiIds) {
        if (jenisSeleksiIds != null && !jenisSeleksiIds.isEmpty()) {
            for (Long jenisSeleksiId : jenisSeleksiIds) {
                JenisSeleksi jenisSeleksi = jenisSeleksiRepository.findById(jenisSeleksiId).orElse(null);
                if (jenisSeleksi != null) {
                    SelectionProgramStudi sps = SelectionProgramStudi.builder()
                            .jenisSeleksi(jenisSeleksi)
                            .programStudi(programStudi)
                            .isActive(true)
                            .build();
                    selectionProgramStudiRepository.save(sps);
                }
            }
            log.info("Linked program studi to {} jenis seleksi", jenisSeleksiIds.size());
        }
    }

    @Transactional
    public void unlinkFromJenisSeleksi(ProgramStudi programStudi, Long jenisSeleksiId) {
        Optional<SelectionProgramStudi> existing = selectionProgramStudiRepository
                .findByJenisSeleksi_IdAndProgramStudi_Id(jenisSeleksiId, programStudi.getId());
        existing.ifPresent(sps -> {
            selectionProgramStudiRepository.delete(sps);
            log.info("Unlinked program studi from jenis seleksi: {}", jenisSeleksiId);
        });
    }

    @Transactional
    public ProgramStudi updateProgramStudi(ProgramStudi programStudi, ProgramStudiRequest request) {
        programStudi.setKode(request.getKode());
        programStudi.setNama(request.getNama());
        programStudi.setDeskripsi(request.getDeskripsi());
        programStudi.setIsMedical(request.getIsMedical());
        programStudi.setHargaTotalPerTahun(request.getHargaTotalPerTahun());
        programStudi.setCicilan1(request.getCicilan1());
        programStudi.setCicilan2(request.getCicilan2());
        programStudi.setCicilan3(request.getCicilan3());
        programStudi.setCicilan4(request.getCicilan4());
        programStudi.setCicilan5(request.getCicilan5());
        programStudi.setCicilan6(request.getCicilan6());
        programStudi.setSortOrder(request.getSortOrder());
        programStudi = programStudiRepository.save(programStudi);
        log.info("Program studi updated: {}", programStudi.getNama());
        return programStudi;
    }

    public ProgramStudi getProgramStudiById(Long id) {
        return programStudiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Program Studi tidak ditemukan"));
    }

    public List<ProgramStudi> getAllProgramStudi() {
        return programStudiRepository.findAll();
    }

    public List<Map<String, Object>> getAllProgramStudiWithDetails() {
        return programStudiRepository.findAllByOrderBySortOrder().stream()
                .map(ps -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", ps.getId());
                    map.put("kode", ps.getKode());
                    map.put("nama", ps.getNama());
                    map.put("deskripsi", ps.getDeskripsi() != null ? ps.getDeskripsi() : "");
                    map.put("isMedical", ps.getIsMedical());
                    map.put("isActive", ps.getIsActive());
                    map.put("sortOrder", ps.getSortOrder());
                    map.put("hargaTotalPerTahun", ps.getHargaTotalPerTahun() != null ? ps.getHargaTotalPerTahun() : 0L);
                    map.put("cicilan1", ps.getCicilan1() != null ? ps.getCicilan1() : 0L);
                    map.put("cicilan2", ps.getCicilan2() != null ? ps.getCicilan2() : 0L);
                    map.put("cicilan3", ps.getCicilan3() != null ? ps.getCicilan3() : 0L);
                    map.put("cicilan4", ps.getCicilan4() != null ? ps.getCicilan4() : 0L);
                    map.put("cicilan5", ps.getCicilan5() != null ? ps.getCicilan5() : 0L);
                    map.put("cicilan6", ps.getCicilan6() != null ? ps.getCicilan6() : 0L);
                    return map;
                }).collect(Collectors.toList());
    }

    public List<Map<String, Object>> getActiveProgramStudiSimple() {
        return programStudiRepository.findByIsActiveTrueOrderBySortOrder().stream()
                .map(ps -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", ps.getId());
                    map.put("kode", ps.getKode());
                    map.put("nama", ps.getNama());
                    map.put("deskripsi", ps.getDeskripsi() != null ? ps.getDeskripsi() : "");
                    map.put("isMedical", ps.getIsMedical());
                    return map;
                }).collect(Collectors.toList());
    }

    public List<ProgramStudi> getActiveProgramStudi() {
        return programStudiRepository.findByIsActiveTrueOrderBySortOrder();
    }

    public List<Map<String, Object>> getProgramStudiByJenisSeleksi(Long jenisSeleksiId) {
        jenisSeleksiRepository.findById(jenisSeleksiId)
                .orElseThrow(() -> new RuntimeException("Jenis Seleksi tidak ditemukan"));
        return selectionProgramStudiRepository.findByJenisSeleksi_IdAndIsActiveTrue(jenisSeleksiId)
                .stream().map(sps -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", sps.getId());
                    map.put("programStudiId", sps.getProgramStudi().getId());
                    map.put("kode", sps.getProgramStudi().getKode());
                    map.put("nama", sps.getProgramStudi().getNama());
                    map.put("isMedical", sps.getProgramStudi().getIsMedical());
                    map.put("isActive", sps.getIsActive());
                    return map;
                }).collect(Collectors.toList());
    }

    @Transactional
    public Map<String, Object> bulkInitializeProgramStudi() {
        List<Map<String, Object>> programsToInsert = new ArrayList<>();
        programsToInsert.add(Map.of("kode", "pendidikan-dokter", "nama", "Pendidikan Dokter", "isMedical", true, "sortOrder", 1));
        programsToInsert.add(Map.of("kode", "profesi-dokter", "nama", "Profesi Dokter", "isMedical", true, "sortOrder", 2));
        programsToInsert.add(Map.of("kode", "teknik-sipil", "nama", "Teknik Sipil", "isMedical", false, "sortOrder", 3));
        programsToInsert.add(Map.of("kode", "teknik-mesin", "nama", "Teknik Mesin", "isMedical", false, "sortOrder", 4));
        programsToInsert.add(Map.of("kode", "teknik-elektro", "nama", "Teknik Elektro", "isMedical", false, "sortOrder", 5));
        programsToInsert.add(Map.of("kode", "informatika", "nama", "Informatika", "isMedical", false, "sortOrder", 6));
        programsToInsert.add(Map.of("kode", "pend-biologi-inggris", "nama", "Pend. Biologi Inggris", "isMedical", false, "sortOrder", 7));
        programsToInsert.add(Map.of("kode", "pend-ekonomi", "nama", "Pend. Ekonomi", "isMedical", false, "sortOrder", 8));
        programsToInsert.add(Map.of("kode", "pend-agama-kristen", "nama", "Pend. Agama Kristen", "isMedical", false, "sortOrder", 9));
        programsToInsert.add(Map.of("kode", "pend-pancasila-kewarganegaraan", "nama", "Pend. Pancasila & Kewarganegaraan", "isMedical", false, "sortOrder", 10));
        programsToInsert.add(Map.of("kode", "pend-bahasa-sastra-indonesia", "nama", "Pend. Bahasa & Sastra Indonesia", "isMedical", false, "sortOrder", 11));
        programsToInsert.add(Map.of("kode", "pend-fisika", "nama", "Pend. Fisika", "isMedical", false, "sortOrder", 12));
        programsToInsert.add(Map.of("kode", "pend-ipa", "nama", "Pend. IPA", "isMedical", false, "sortOrder", 13));
        programsToInsert.add(Map.of("kode", "pend-profesi-guru", "nama", "Pend. Profesi Guru", "isMedical", false, "sortOrder", 14));
        programsToInsert.add(Map.of("kode", "ekonomi-pembangunan", "nama", "Ekonomi Pembangunan", "isMedical", false, "sortOrder", 15));
        programsToInsert.add(Map.of("kode", "manajemen", "nama", "Manajemen", "isMedical", false, "sortOrder", 16));
        programsToInsert.add(Map.of("kode", "akuntansi", "nama", "Akuntansi", "isMedical", false, "sortOrder", 17));
        programsToInsert.add(Map.of("kode", "adm-perpajakan-d3", "nama", "Administrasi Perpajakan (D3)", "isMedical", false, "sortOrder", 18));

        int inserted = 0;
        for (Map<String, Object> prog : programsToInsert) {
            String kode = (String) prog.get("kode");
            if (programStudiRepository.existsByKode(kode)) {
                log.info("Program studi {} already exists, skip", kode);
                continue;
            }
            ProgramStudi programStudi = ProgramStudi.builder()
                    .kode(kode)
                    .nama((String) prog.get("nama"))
                    .deskripsi("")
                    .isMedical((Boolean) prog.get("isMedical"))
                    .isActive(true)
                    .sortOrder((Integer) prog.get("sortOrder"))
                    .build();
            programStudiRepository.save(programStudi);
            inserted++;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("message", "Program studi bulk initialized: " + inserted + " inserted, " +
                (programsToInsert.size() - inserted) + " skipped");
        result.put("inserted", inserted);
        result.put("total", programsToInsert.size());
        return result;
    }

    private ProgramStudi buildFromRequest(ProgramStudiRequest request) {
        return ProgramStudi.builder()
                .kode(request.getKode())
                .nama(request.getNama())
                .deskripsi(request.getDeskripsi())
                .isMedical(request.getIsMedical() != null ? request.getIsMedical() : false)
                .isActive(true)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .hargaTotalPerTahun(request.getHargaTotalPerTahun() != null ? request.getHargaTotalPerTahun() : 0L)
                .cicilan1(request.getCicilan1() != null ? request.getCicilan1() : 0L)
                .cicilan2(request.getCicilan2() != null ? request.getCicilan2() : 0L)
                .cicilan3(request.getCicilan3() != null ? request.getCicilan3() : 0L)
                .cicilan4(request.getCicilan4() != null ? request.getCicilan4() : 0L)
                .cicilan5(request.getCicilan5() != null ? request.getCicilan5() : 0L)
                .cicilan6(request.getCicilan6() != null ? request.getCicilan6() : 0L)
                .build();
    }
}