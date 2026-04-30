package com.uhn.pmb.service;

import com.uhn.pmb.dto.JenisSeleksiRequest;
import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class JenisSeleksiService {

    private final JenisSeleksiRepository jenisSeleksiRepository;
    private final PeriodJenisSeleksiRepository periodJenisSeleksiRepository;
    private final SelectionProgramStudiRepository selectionProgramStudiRepository;
    private final ProgramStudiRepository programStudiRepository;
    private final EntityManager entityManager;

    public List<JenisSeleksi> getAllActive() {
        return jenisSeleksiRepository.findByIsActiveTrueOrderBySortOrder();
    }

    public List<JenisSeleksi> getAll() {
        return jenisSeleksiRepository.findAllByOrderBySortOrder();
    }

    public Optional<JenisSeleksi> getById(Long id) {
        return jenisSeleksiRepository.findById(id);
    }

    public Optional<JenisSeleksi> getByCode(String code) {
        return jenisSeleksiRepository.findByCode(code);
    }

    public JenisSeleksi create(JenisSeleksi jenisSeleksi) {
        if (jenisSeleksiRepository.existsByCode(jenisSeleksi.getCode())) {
            throw new IllegalArgumentException("Code sudah digunakan: " + jenisSeleksi.getCode());
        }
        jenisSeleksi.setCreatedAt(LocalDateTime.now());
        jenisSeleksi.setUpdatedAt(LocalDateTime.now());
        if (jenisSeleksi.getSortOrder() == null) {
            jenisSeleksi.setSortOrder(jenisSeleksiRepository.findAll().size());
        }
        if (jenisSeleksi.getIsActive() == null) {
            jenisSeleksi.setIsActive(true);
        }
        return jenisSeleksiRepository.save(jenisSeleksi);
    }

    public JenisSeleksi createWithProgramStudi(JenisSeleksiRequest request) {
        JenisSeleksi jenisSeleksi = JenisSeleksi.builder()
                .code(request.getCode())
                .nama(request.getNama())
                .deskripsi(request.getDeskripsi())
                .fasilitas(request.getFasilitas())
                .logoUrl(request.getLogoUrl())
                .harga(request.getHarga())
                .isActive(true)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();
        jenisSeleksi = create(jenisSeleksi);

        List<Long> programStudiIds = request.getProgramStudiIds();
        if (programStudiIds == null || programStudiIds.isEmpty()) {
            // Auto-link based on code
            boolean isKedokteran = request.getCode() != null &&
                    (request.getCode().toUpperCase().contains("KEDOKTERAN") ||
                     request.getCode().toUpperCase().contains("DOKTER"));
            if (isKedokteran) {
                programStudiIds = programStudiRepository.findAll().stream()
                        .filter(p -> p.getNama() != null && p.getNama().toLowerCase().contains("dokter"))
                        .map(ProgramStudi::getId)
                        .collect(Collectors.toList());
            } else {
                programStudiIds = programStudiRepository.findAll().stream()
                        .filter(p -> p.getNama() == null || !p.getNama().toLowerCase().contains("dokter"))
                        .map(ProgramStudi::getId)
                        .collect(Collectors.toList());
            }
        }
        linkProgramStudi(jenisSeleksi, programStudiIds);
        return jenisSeleksi;
    }

    public JenisSeleksi update(Long id, JenisSeleksi updates) {
        JenisSeleksi existing = jenisSeleksiRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Jenis Seleksi tidak ditemukan: " + id));
        if (!existing.getCode().equals(updates.getCode()) &&
                jenisSeleksiRepository.existsByCode(updates.getCode())) {
            throw new IllegalArgumentException("Code sudah digunakan: " + updates.getCode());
        }
        existing.setCode(updates.getCode());
        existing.setNama(updates.getNama());
        existing.setDeskripsi(updates.getDeskripsi());
        existing.setFasilitas(updates.getFasilitas());
        existing.setLogoUrl(updates.getLogoUrl());
        existing.setHarga(updates.getHarga());
        existing.setIsActive(updates.getIsActive());
        existing.setSortOrder(updates.getSortOrder());
        existing.setUpdatedAt(LocalDateTime.now());
        return jenisSeleksiRepository.save(existing);
    }

    public JenisSeleksi updateWithProgramStudi(Long id, JenisSeleksiRequest request) {
        JenisSeleksi updates = JenisSeleksi.builder()
                .code(request.getCode())
                .nama(request.getNama())
                .deskripsi(request.getDeskripsi())
                .fasilitas(request.getFasilitas())
                .logoUrl(request.getLogoUrl())
                .harga(request.getHarga())
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();
        JenisSeleksi jenisSeleksi = update(id, updates);
        selectionProgramStudiRepository.deleteByJenisSeleksi_Id(id);
        if (request.getProgramStudiIds() != null && !request.getProgramStudiIds().isEmpty()) {
            linkProgramStudi(jenisSeleksi, request.getProgramStudiIds());
        }
        return jenisSeleksi;
    }

    public void delete(Long id) {
        if (!jenisSeleksiRepository.existsById(id)) {
            throw new IllegalArgumentException("Jenis Seleksi tidak ditemukan: " + id);
        }
        periodJenisSeleksiRepository.deleteByJenisSeleksi_Id(id);
        selectionProgramStudiRepository.deleteByJenisSeleksi_Id(id);
        entityManager.flush();
        jenisSeleksiRepository.deleteById(id);
    }

    public JenisSeleksi toggleActive(Long id) {
        JenisSeleksi js = jenisSeleksiRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Jenis Seleksi tidak ditemukan: " + id));
        js.setIsActive(!js.getIsActive());
        js.setUpdatedAt(LocalDateTime.now());
        return jenisSeleksiRepository.save(js);
    }

    private void linkProgramStudi(JenisSeleksi jenisSeleksi, List<Long> programStudiIds) {
        for (Long programStudiId : programStudiIds) {
            ProgramStudi programStudi = programStudiRepository.findById(programStudiId).orElse(null);
            if (programStudi != null) {
                SelectionProgramStudi sps = SelectionProgramStudi.builder()
                        .jenisSeleksi(jenisSeleksi)
                        .programStudi(programStudi)
                        .isActive(true)
                        .build();
                selectionProgramStudiRepository.save(sps);
            }
        }
    }
}