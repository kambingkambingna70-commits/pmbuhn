package com.uhn.pmb.service;

import com.uhn.pmb.dto.RegistrationPeriodRequest;
import com.uhn.pmb.entity.*;
import com.uhn.pmb.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class RegistrationPeriodService {

    private final RegistrationPeriodRepository registrationPeriodRepository;
    private final PeriodJenisSeleksiRepository periodJenisSeleksiRepository;
    private final JenisSeleksiRepository jenisSeleksiRepository;
    private final EntityManager entityManager;

    public List<RegistrationPeriod> findAll() {
        return registrationPeriodRepository.findAll();
    }

    public Optional<RegistrationPeriod> findById(Long id) {
        return registrationPeriodRepository.findById(id);
    }

    @Transactional
    public RegistrationPeriod create(RegistrationPeriodRequest request) {
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
                .waveType(request.getWaveType() != null
                        ? RegistrationPeriod.WaveType.valueOf(request.getWaveType().toUpperCase())
                        : RegistrationPeriod.WaveType.REGULAR_TEST)
                .status(RegistrationPeriod.Status.OPEN)
                .build();

        registrationPeriodRepository.save(period);
        linkJenisSeleksi(period, request.getJenisSeleksiIds());
        return period;
    }

    @Transactional
    public RegistrationPeriod update(Long id, RegistrationPeriodRequest request) {
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
        if (request.getWaveType() != null) period.setWaveType(RegistrationPeriod.WaveType.valueOf(request.getWaveType().toUpperCase()));

        registrationPeriodRepository.save(period);

        // Re-link jenis seleksi
        periodJenisSeleksiRepository.deleteByPeriod_Id(id);
        entityManager.flush();
        linkJenisSeleksi(period, request.getJenisSeleksiIds());

        return period;
    }

    @Transactional
    public void delete(Long id) {
        RegistrationPeriod period = registrationPeriodRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Periode tidak ditemukan"));
        periodJenisSeleksiRepository.deleteByPeriod_Id(id);
        entityManager.flush();
        registrationPeriodRepository.delete(period);
    }

    private void linkJenisSeleksi(RegistrationPeriod period, List<Long> jenisSeleksiIds) {
        if (jenisSeleksiIds == null || jenisSeleksiIds.isEmpty()) {
            jenisSeleksiIds = autoResolveJenisSeleksi();
        }
        for (Long jsId : jenisSeleksiIds) {
            jenisSeleksiRepository.findById(jsId).ifPresent(js -> {
                PeriodJenisSeleksi pjs = PeriodJenisSeleksi.builder()
                        .period(period)
                        .jenisSeleksi(js)
                        .isActive(true)
                        .build();
                periodJenisSeleksiRepository.save(pjs);
            });
        }
        log.info("✅ Linked {} jenis seleksi to period: {}", jenisSeleksiIds.size(), period.getName());
    }

    private List<Long> autoResolveJenisSeleksi() {
        List<Long> ids = new ArrayList<>();
        jenisSeleksiRepository.findByCode("KEDOKTERAN").ifPresent(js -> ids.add(js.getId()));
        jenisSeleksiRepository.findByCode("NON_KEDOKTERAN").ifPresent(js -> ids.add(js.getId()));
        return ids;
    }
}