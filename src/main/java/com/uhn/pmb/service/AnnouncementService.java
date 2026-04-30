package com.uhn.pmb.service;

import com.uhn.pmb.dto.CreateAnnouncementRequest;
import com.uhn.pmb.entity.Announcement;
import com.uhn.pmb.repository.AnnouncementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AnnouncementService {

    private final AnnouncementRepository announcementRepository;

    public List<Announcement> findAllActive() {
        return announcementRepository.findAllActive();
    }

    public Page<Announcement> findAllActivePaginated(Pageable pageable) {
        return announcementRepository.findAllActive(pageable);
    }

    public Optional<Announcement> findById(Long id) {
        return announcementRepository.findById(id);
    }

    public Announcement findActiveById(Long id) {
        Announcement a = announcementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pengumuman tidak ditemukan"));
        if (!a.getIsActive()) {
            throw new RuntimeException("Pengumuman tidak aktif");
        }
        return a;
    }

    public List<Announcement> findRecent() {
        return announcementRepository.findRecent();
    }

    public List<Announcement> findUrgent() {
        return announcementRepository.findUrgent();
    }

    public List<Announcement> search(String keyword) {
        return announcementRepository.searchByTitle(keyword);
    }

    public Announcement save(Announcement announcement) {
        Announcement saved = announcementRepository.save(announcement);
        log.info("Announcement saved: {}", saved.getTitle());
        return saved;
    }

    public Announcement create(CreateAnnouncementRequest request, String createdByName) {
        Announcement announcement = Announcement.builder()
                .title(request.getTitle())
                .content(request.getContent())
                .description(request.getDescription())
                .createdByName(createdByName)
                .isActive(request.getIsActive() != null ? request.getIsActive() : true)
                .priority(request.getPriority() != null ? request.getPriority() : 0)
                .announcementType(Announcement.AnnouncementType.valueOf(
                        request.getAnnouncementType() != null ? request.getAnnouncementType() : "GENERAL"))
                .build();
        Announcement saved = announcementRepository.save(announcement);
        log.info("Announcement created by {}: {}", createdByName, saved.getTitle());
        return saved;
    }

    public Announcement update(Long id, CreateAnnouncementRequest request) {
        Announcement announcement = announcementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pengumuman tidak ditemukan"));
        announcement.setTitle(request.getTitle());
        announcement.setContent(request.getContent());
        announcement.setDescription(request.getDescription());
        announcement.setPriority(request.getPriority() != null ? request.getPriority() : 0);
        announcement.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        announcement.setAnnouncementType(Announcement.AnnouncementType.valueOf(
                request.getAnnouncementType() != null ? request.getAnnouncementType() : "GENERAL"));
        Announcement saved = announcementRepository.save(announcement);
        log.info("Announcement updated: {}", saved.getTitle());
        return saved;
    }

    public void delete(Long id) {
        Announcement a = announcementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pengumuman tidak ditemukan"));
        announcementRepository.delete(a);
        log.info("Announcement deleted: {}", a.getTitle());
    }

    public Announcement deactivate(Long id) {
        Announcement a = announcementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pengumuman tidak ditemukan"));
        a.setIsActive(false);
        return announcementRepository.save(a);
    }
}