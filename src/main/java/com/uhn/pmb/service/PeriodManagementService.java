package com.uhn.pmb.service;

import com.uhn.pmb.dto.RegistrationPeriodRequest;
import com.uhn.pmb.dto.SelectionTypeRequest;
import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.*;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PeriodManagementService {

    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final PeriodJenisSeleksiRepository periodJenisSeleksiRepository;
    private final JenisSeleksiRepository jenisSeleksiRepository;
    private final SelectionTypeRepository selectionTypeRepository;
    private final EntityManager entityManager;

    @Transactional
    public RegistrationPeriod createPeriod(RegistrationPeriodRequest request) {
        log.info("Creating registration period: {}", request.getName());
        RegistrationPeriod period = RegistrationPeriod.builder()
                .name(request.getName())
                .regStartDate(request.getRegStartDate())
                .regEndDate(request.getRegEndDate())
                .examDate(request.getExamDate())
                .examEndDate(request.getExamEndDate())
                .announcementDate(request.getAnnouncementDate())
                .reenrollmentStartDate(request.getReenrollmentStartDate())
                .reenrollmentEndDate(request.getReenrollmentEndDate())
                .description(request.getDescription())
                .requirements(request.getRequirements())
                .waveType(request.getWaveType() != null ? RegistrationPeriod.WaveType.valueOf(request.getWaveType()) : RegistrationPeriod.WaveType.REGULAR_TEST)
                .status(RegistrationPeriod.Status.OPEN)
                .build();
        period = registrationPeriodRepository.save(period);
        log.info("Period created: {} (ID: {})", period.getName(), period.getId());
        linkJenisSeleksi(period, request.getJenisSeleksiIds());
        return period;
    }

    @Transactional
    public RegistrationPeriod updatePeriod(Long id, RegistrationPeriodRequest request) {
        RegistrationPeriod period = registrationPeriodRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Periode tidak ditemukan"));
        period.setName(request.getName());
        period.setRegStartDate(request.getRegStartDate());
        period.setRegEndDate(request.getRegEndDate());
        period.setExamDate(request.getExamDate());
        period.setExamEndDate(request.getExamEndDate());
        period.setAnnouncementDate(request.getAnnouncementDate());
        period.setReenrollmentStartDate(request.getReenrollmentStartDate());
        period.setReenrollmentEndDate(request.getReenrollmentEndDate());
        period.setDescription(request.getDescription());
        period.setRequirements(request.getRequirements());
        if (request.getWaveType() != null) {
            period.setWaveType(RegistrationPeriod.WaveType.valueOf(request.getWaveType()));
        }
        registrationPeriodRepository.save(period);
        periodJenisSeleksiRepository.deleteByPeriod_Id(id);
        entityManager.flush();
        if (request.getJenisSeleksiIds() != null && !request.getJenisSeleksiIds().isEmpty()) {
            linkJenisSeleksi(period, request.getJenisSeleksiIds());
        }
        log.info("Period updated: ID={}, Name={}", id, period.getName());
        return period;
    }

    @Transactional
    public void deletePeriod(Long id) {
        RegistrationPeriod period = registrationPeriodRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Periode tidak ditemukan"));
        periodJenisSeleksiRepository.deleteByPeriod_Id(id);
        entityManager.flush();
        registrationPeriodRepository.delete(period);
        log.info("Period deleted: ID={}, Name={}", id, period.getName());
    }

    @Transactional
    public void linkJenisSeleksi(RegistrationPeriod period, List<Long> jenisSeleksiIds) {
        List<Long> idsToLink = jenisSeleksiIds;
        if (idsToLink == null || idsToLink.isEmpty()) {
            Optional<JenisSeleksi> kedokteran = jenisSeleksiRepository.findByCode("KEDOKTERAN");
            Optional<JenisSeleksi> nonKedokteran = jenisSeleksiRepository.findByCode("NON_KEDOKTERAN");
            if (kedokteran.isPresent() && nonKedokteran.isPresent()) {
                idsToLink = new ArrayList<>();
                idsToLink.add(kedokteran.get().getId());
                idsToLink.add(nonKedokteran.get().getId());
            } else {
                log.warn("KEDOKTERAN or NON_KEDOKTERAN not found. Skipping auto-add.");
                return;
            }
        }
        for (Long jenisSeleksiId : idsToLink) {
            JenisSeleksi jenisSeleksi = jenisSeleksiRepository.findById(jenisSeleksiId).orElse(null);
            if (jenisSeleksi != null) {
                PeriodJenisSeleksi pjs = PeriodJenisSeleksi.builder()
                        .period(period)
                        .jenisSeleksi(jenisSeleksi)
                        .isActive(true)
                        .build();
                periodJenisSeleksiRepository.save(pjs);
            }
        }
        log.info("Linked {} jenis seleksi to period: {}", idsToLink.size(), period.getName());
    }

    public RegistrationPeriod getPeriodById(Long id) {
        return registrationPeriodRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Period tidak ditemukan"));
    }

    public List<RegistrationPeriod> getAllPeriods() {
        return registrationPeriodRepository.findAll();
    }

    public List<JenisSeleksi> getAllAvailableJenisSeleksi() {
        return jenisSeleksiRepository.findByIsActiveTrueOrderBySortOrder();
    }

    public List<Map<String, Object>> getJenisSeleksiByPeriod(Long periodId) {
        RegistrationPeriod period = getPeriodById(periodId);
        List<PeriodJenisSeleksi> list = periodJenisSeleksiRepository.findByPeriod_IdAndIsActiveTrue(periodId);
        return list.stream().map(pjs -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", pjs.getId());
            map.put("jenisSeleksiId", pjs.getJenisSeleksi().getId());
            map.put("code", pjs.getJenisSeleksi().getCode());
            map.put("nama", pjs.getJenisSeleksi().getNama());
            map.put("deskripsi", pjs.getJenisSeleksi().getDeskripsi());
            map.put("logoUrl", pjs.getJenisSeleksi().getLogoUrl());
            map.put("harga", pjs.getJenisSeleksi().getHarga());
            map.put("isActive", pjs.getIsActive());
            return map;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void createSelectionType(SelectionTypeRequest request) {
        RegistrationPeriod period = getPeriodById(request.getPeriodId());
        SelectionType selectionType = SelectionType.builder()
                .period(period)
                .name(request.getName())
                .description(request.getDescription())
                .requireRanking(request.getRequireRanking())
                .requireTesting(request.getRequireTesting())
                .formType(request.getFormType())
                .price(request.getPrice())
                .isActive(true)
                .build();
        selectionTypeRepository.save(selectionType);
    }

    public List<Map<String, Object>> getSelectionTypesByPeriod(Long periodId) {
        RegistrationPeriod period = getPeriodById(periodId);
        List<SelectionType> types = selectionTypeRepository.findByPeriod_Id(periodId);
        return types.stream().map(type -> {
            Map<String, Object> dto = new HashMap<>();
            dto.put("id", type.getId());
            dto.put("name", type.getName());
            dto.put("description", type.getDescription());
            dto.put("formType", type.getFormType().toString());
            dto.put("requireRanking", type.getRequireRanking());
            dto.put("requireTesting", type.getRequireTesting());
            dto.put("price", type.getPrice());
            dto.put("isActive", type.getIsActive());
            dto.put("createdAt", type.getCreatedAt());
            return dto;
        }).collect(Collectors.toList());
    }

    @Transactional
    public void deleteSelectionType(Long id) {
        SelectionType type = selectionTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Jenis seleksi tidak ditemukan"));
        selectionTypeRepository.delete(type);
        log.info("Selection type deleted: {}", type.getName());
    }
}