package com.uhn.pmb.service;

import com.uhn.pmb.entity.RegistrationStatus;
import com.uhn.pmb.entity.RegistrationStatus.RegistrationStage;
import com.uhn.pmb.entity.RegistrationStatus.RegistrationStatus_Enum;
import com.uhn.pmb.entity.User;
import com.uhn.pmb.repository.RegistrationStatusRepository;
import com.uhn.pmb.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class RegistrationStatusService {
    
    @Autowired
    private RegistrationStatusRepository registrationStatusRepository;

    @Autowired
    private UserRepository userRepository;

    // ===== Email-based helpers =====
    public User getUserByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User tidak ditemukan: " + email));
    }

    public List<RegistrationStatus> getUserStatusesByEmail(String email) {
        User user = getUserByEmail(email);
        return getUserStatuses(user);
    }

    public Optional<RegistrationStatus> getStatusByEmail(String email, RegistrationStage stage) {
        User user = getUserByEmail(email);
        return getStatus(user, stage);
    }

    public boolean canUserEditByEmail(String email, RegistrationStage stage) {
        User user = getUserByEmail(email);
        return canUserEdit(user, stage);
    }

    public Long getEditTimeRemainingByEmail(String email, RegistrationStage stage) {
        User user = getUserByEmail(email);
        return getEditTimeRemaining(user, stage);
    }

    public RegistrationStatus markAsCompletedByEmail(String email, RegistrationStage stage, String dataJson) {
        User user = getUserByEmail(email);
        return markAsCompleted(user, stage, dataJson);
    }

    public RegistrationStatus updateStatusDataByEmail(String email, RegistrationStage stage, String dataJson) {
        User user = getUserByEmail(email);
        return updateStatusData(user, stage, dataJson);
    }

    // ===== Core methods =====
    public RegistrationStatus getOrCreateStatus(User user, RegistrationStage stage) {
        Optional<RegistrationStatus> existing = registrationStatusRepository.findByUserAndStage(user, stage);
        if (existing.isPresent()) {
            return existing.get();
        }
        RegistrationStatus newStatus = RegistrationStatus.builder()
                .user(user)
                .stage(stage)
                .status(RegistrationStatus_Enum.MENUNGGU_VERIFIKASI)
                .canEdit(true)
                .adminVerified(false)
                .editCount(0)
                .build();
        return registrationStatusRepository.save(newStatus);
    }

    public RegistrationStatus markAsCompleted(User user, RegistrationStage stage, String dataJson) {
        RegistrationStatus status = getOrCreateStatus(user, stage);
        LocalDateTime now = LocalDateTime.now();
        status.setStatus(RegistrationStatus_Enum.SELESAI);
        status.setSubmissionDate(now);
        status.setEditDeadline(now.plusHours(24));
        status.setCanEdit(true);
        status.setDataJson(dataJson);
        status.setEditCount((status.getEditCount() != null ? status.getEditCount() : 0) + 1);
        return registrationStatusRepository.save(status);
    }

    public boolean canUserEdit(User user, RegistrationStage stage) {
        Optional<RegistrationStatus> statusOpt = registrationStatusRepository.findByUserAndStage(user, stage);
        if (statusOpt.isEmpty()) {
            return false;
        }
        RegistrationStatus status = statusOpt.get();
        if (status.getAdminVerified() != null && status.getAdminVerified()) {
            return false;
        }
        if (status.getStatus() == RegistrationStatus_Enum.MENUNGGU_VERIFIKASI) {
            return true;
        }
        if (status.getStatus() == RegistrationStatus_Enum.SELESAI && status.getEditDeadline() != null) {
            return LocalDateTime.now().isBefore(status.getEditDeadline());
        }
        return false;
    }

    public Long getEditTimeRemaining(User user, RegistrationStage stage) {
        Optional<RegistrationStatus> statusOpt = registrationStatusRepository.findByUserAndStage(user, stage);
        if (statusOpt.isEmpty() || statusOpt.get().getEditDeadline() == null) {
            return 0L;
        }
        LocalDateTime deadline = statusOpt.get().getEditDeadline();
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(deadline)) {
            return java.time.temporal.ChronoUnit.HOURS.between(now, deadline);
        }
        return 0L;
    }

    public RegistrationStatus updateStatusData(User user, RegistrationStage stage, String dataJson) {
        if (!canUserEdit(user, stage)) {
            throw new IllegalStateException("User tidak bisa edit tahapan ini");
        }
        Optional<RegistrationStatus> statusOpt = registrationStatusRepository.findByUserAndStage(user, stage);
        if (statusOpt.isEmpty()) {
            throw new IllegalStateException("Status tidak ditemukan");
        }
        RegistrationStatus status = statusOpt.get();
        status.setDataJson(dataJson);
        status.setUpdatedAt(LocalDateTime.now());
        status.setEditCount((status.getEditCount() != null ? status.getEditCount() : 0) + 1);
        return registrationStatusRepository.save(status);
    }

    public RegistrationStatus rejectByAdmin(User user, RegistrationStage stage, String adminEmail, String notes) {
        Optional<RegistrationStatus> statusOpt = registrationStatusRepository.findByUserAndStage(user, stage);
        if (statusOpt.isEmpty()) {
            throw new IllegalStateException("Status tidak ditemukan");
        }
        RegistrationStatus status = statusOpt.get();
        status.setAdminVerified(true);
        status.setVerifiedBy(adminEmail);
        status.setVerificationDate(LocalDateTime.now());
        status.setAdminNotes(notes);
        status.setCanEdit(false);
        status.setStatus(RegistrationStatus_Enum.REJECTED);
        return registrationStatusRepository.save(status);
    }

    public RegistrationStatus approveByAdmin(User user, RegistrationStage stage, String adminEmail, String notes) {
        Optional<RegistrationStatus> statusOpt = registrationStatusRepository.findByUserAndStage(user, stage);
        if (statusOpt.isEmpty()) {
            throw new IllegalStateException("Status tidak ditemukan");
        }
        RegistrationStatus status = statusOpt.get();
        status.setAdminVerified(true);
        status.setVerifiedBy(adminEmail);
        status.setVerificationDate(LocalDateTime.now());
        status.setAdminNotes(notes);
        status.setCanEdit(false);
        return registrationStatusRepository.save(status);
    }

    public List<RegistrationStatus> getUserStatuses(User user) {
        return registrationStatusRepository.findByUserOrderByCreatedAtDesc(user);
    }

    public Optional<RegistrationStatus> getStatus(User user, RegistrationStage stage) {
        return registrationStatusRepository.findByUserAndStage(user, stage);
    }
}