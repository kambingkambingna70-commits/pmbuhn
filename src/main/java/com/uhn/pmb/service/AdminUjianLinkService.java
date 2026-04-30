package com.uhn.pmb.service;

import com.uhn.pmb.dto.UjianLinkRequest;
import com.uhn.pmb.entity.GelombangLinkUjian;
import com.uhn.pmb.entity.RegistrationPeriod;
import com.uhn.pmb.repository.GelombangLinkUjianRepository;
import com.uhn.pmb.repository.RegistrationPeriodRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service untuk menangani business logic ujian link
 * Semua repository access ada di sini
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUjianLinkService {

    private final GelombangLinkUjianRepository ujianLinkRepository;
    private final RegistrationPeriodRepository registrationPeriodRepository;

    /**
     * Get all ujian links ordered by updated at desc
     */
    public List<GelombangLinkUjian> getAllLinks() {
        return ujianLinkRepository.findAllByOrderByUpdatedAtDesc();
    }

    /**
     * Get ujian link by registration period ID
     */
    public Optional<GelombangLinkUjian> getByPeriodId(Long periodId) {
        return ujianLinkRepository.findByRegistrationPeriodId(periodId);
    }

    /**
     * Create new ujian link (online or offline)
     * Validates: period exists, 1-to-1 relationship
     */
    public GelombangLinkUjian createLink(UjianLinkRequest request) {
        // Validate request
        if (request.getPeriodId() == null) {
            throw new RuntimeException("Period ID is required");
        }

        // Check if period exists
        RegistrationPeriod period = registrationPeriodRepository.findById(request.getPeriodId())
                .orElseThrow(() -> new RuntimeException("Registration period not found"));

        // Check if ujian link already exists for this period (1-to-1 relationship)
        Optional<GelombangLinkUjian> existing = ujianLinkRepository.findByRegistrationPeriodId(request.getPeriodId());
        if (existing.isPresent()) {
            throw new RuntimeException("Ujian link already exists for this period");
        }

        // Create and save new ujian link (either online or offline)
        GelombangLinkUjian ujianLink = GelombangLinkUjian.builder()
                .registrationPeriod(period)
                .linkUjian(request.getLinkUjian())
                .examDate(request.getExamDate())
                .examPlace(request.getExamPlace())
                .examTime(request.getExamTime())
                .build();

        log.info("✅ Ujian link saved successfully for period: {}", request.getPeriodId());
        return ujianLinkRepository.save(ujianLink);
    }

    /**
     * Update ujian link
     */
    public GelombangLinkUjian updateLink(UjianLinkRequest request) {
        // Validate request
        if (request.getPeriodId() == null || request.getLinkUjian() == null) {
            throw new RuntimeException("Period ID and link ujian are required");
        }

        // Find existing ujian link
        GelombangLinkUjian ujianLink = ujianLinkRepository.findByRegistrationPeriodId(request.getPeriodId())
                .orElseThrow(() -> new RuntimeException("Ujian link not found for this period"));

        // Update the ujian link
        ujianLink.setLinkUjian(request.getLinkUjian());

        log.info("✅ Ujian link updated successfully for period: {}", request.getPeriodId());
        return ujianLinkRepository.save(ujianLink);
    }

    /**
     * Delete ujian link by period ID
     */
    @Transactional
    public void deleteByPeriodId(Long periodId) {
        Optional<GelombangLinkUjian> existing = ujianLinkRepository.findByRegistrationPeriodId(periodId);
        if (existing.isEmpty()) {
            throw new RuntimeException("Ujian link not found");
        }

        ujianLinkRepository.deleteByRegistrationPeriodId(periodId);
        log.info("✅ Ujian link deleted successfully for period: {}", periodId);
    }

    /**
     * Create offline exam (without linkUjian)
     * Requires examDate, examPlace, examTime
     */
    public GelombangLinkUjian createOfflineExam(UjianLinkRequest request) {
        // Validate request
        if (request.getPeriodId() == null || request.getExamDate() == null ||
            request.getExamPlace() == null || request.getExamTime() == null) {
            throw new RuntimeException("Period ID, exam date, place, and time are required");
        }

        // Check if period exists
        RegistrationPeriod period = registrationPeriodRepository.findById(request.getPeriodId())
                .orElseThrow(() -> new RuntimeException("Registration period not found"));

        // Check if exam already exists for this period
        Optional<GelombangLinkUjian> existing = ujianLinkRepository.findByRegistrationPeriodId(request.getPeriodId());
        if (existing.isPresent()) {
            throw new RuntimeException("Exam already exists for this period");
        }

        // Create and save offline exam
        GelombangLinkUjian offlineExam = GelombangLinkUjian.builder()
                .registrationPeriod(period)
                .examDate(request.getExamDate())
                .examPlace(request.getExamPlace())
                .examTime(request.getExamTime())
                .build();

        log.info("✅ Offline exam saved successfully for period: {}", request.getPeriodId());
        return ujianLinkRepository.save(offlineExam);
    }

    /**
     * Delete offline exam by period ID
     */
    @Transactional
    public void deleteOfflineExam(Long periodId) {
        Optional<GelombangLinkUjian> existing = ujianLinkRepository.findByRegistrationPeriodId(periodId);
        if (existing.isEmpty()) {
            throw new RuntimeException("Offline exam not found");
        }

        ujianLinkRepository.deleteByRegistrationPeriodId(periodId);
        log.info("✅ Offline exam deleted successfully for period: {}", periodId);
    }
}
