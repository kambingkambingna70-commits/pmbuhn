package com.uhn.pmb.service;

import com.uhn.pmb.dto.ProgramStudiRequest;
import com.uhn.pmb.entity.ProgramStudi;
import com.uhn.pmb.repository.ProgramStudiRepository;
import com.uhn.pmb.repository.SelectionProgramStudiRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProgramStudiService {

    private final ProgramStudiRepository programStudiRepository;
    private final SelectionProgramStudiRepository selectionProgramStudiRepository;

    public List<ProgramStudi> findAll() {
        return programStudiRepository.findAllByOrderBySortOrder();
    }

    public List<ProgramStudi> findAllActive() {
        return programStudiRepository.findByIsActiveTrueOrderBySortOrder();
    }

    public Optional<ProgramStudi> findById(Long id) {
        return programStudiRepository.findById(id);
    }

    public ProgramStudi create(ProgramStudiRequest request) {
        if (programStudiRepository.existsByKode(request.getKode())) {
            throw new RuntimeException("Kode program studi sudah digunakan");
        }
        ProgramStudi ps = ProgramStudi.builder()
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
        programStudiRepository.save(ps);
        log.info("✅ ProgramStudi created: {}", ps.getNama());
        return ps;
    }

    public ProgramStudi update(Long id, ProgramStudiRequest request) {
        ProgramStudi ps = programStudiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Program Studi not found"));

        if (!ps.getKode().equals(request.getKode()) &&
                programStudiRepository.existsByKode(request.getKode())) {
            throw new RuntimeException("Kode program studi sudah digunakan");
        }

        ps.setKode(request.getKode());
        ps.setNama(request.getNama());
        ps.setDeskripsi(request.getDeskripsi());
        ps.setIsMedical(request.getIsMedical() != null ? request.getIsMedical() : false);
        ps.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        ps.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0);
        ps.setHargaTotalPerTahun(request.getHargaTotalPerTahun() != null ? request.getHargaTotalPerTahun() : 0L);
        ps.setCicilan1(request.getCicilan1() != null ? request.getCicilan1() : 0L);
        ps.setCicilan2(request.getCicilan2() != null ? request.getCicilan2() : 0L);
        ps.setCicilan3(request.getCicilan3() != null ? request.getCicilan3() : 0L);
        ps.setCicilan4(request.getCicilan4() != null ? request.getCicilan4() : 0L);
        ps.setCicilan5(request.getCicilan5() != null ? request.getCicilan5() : 0L);
        ps.setCicilan6(request.getCicilan6() != null ? request.getCicilan6() : 0L);
        ps.setUpdatedAt(LocalDateTime.now());
        programStudiRepository.save(ps);
        log.info("✅ ProgramStudi updated: {}", ps.getNama());
        return ps;
    }

    public void delete(Long id) {
        ProgramStudi ps = programStudiRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Program Studi not found"));
        if (selectionProgramStudiRepository.existsByProgramStudi_Id(id)) {
            throw new RuntimeException("Program Studi masih digunakan di jenis seleksi lain");
        }
        programStudiRepository.delete(ps);
        log.info("✅ ProgramStudi deleted: {}", ps.getNama());
    }
}
