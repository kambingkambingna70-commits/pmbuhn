package com.uhn.pmb.controller;

import com.uhn.pmb.dto.AnnouncementDTO;
import com.uhn.pmb.dto.ApiResponse;
import com.uhn.pmb.dto.CreateAnnouncementRequest;
import com.uhn.pmb.service.AnnouncementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminAnnouncementController {

    private final AnnouncementService announcementService;

    @PostMapping("/api/announcements")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> createAnnouncement(@Valid @RequestBody CreateAnnouncementRequest request,
                                                 Authentication auth) {
        try {
            announcementService.create(request, auth.getName());
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(new ApiResponse(true, "Pengumuman berhasil dibuat"));
        } catch (Exception e) {
            log.error("Error creating announcement: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/api/announcements")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI', 'CAMABA')")
    public ResponseEntity<?> getAllAnnouncements() {
        try {
            List<AnnouncementDTO> dtos = announcementService.findAllActive().stream()
                    .map(AnnouncementDTO::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(Map.of("success", true, "data", dtos, "count", dtos.size()));
        } catch (Exception e) {
            log.error("Error fetching announcements: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/api/announcements/paginated")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI', 'CAMABA')")
    public ResponseEntity<?> getAnnouncementsPaginated(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Page<?> result = announcementService.findAllActivePaginated(PageRequest.of(page, size));
            List<AnnouncementDTO> dtos = result.getContent().stream()
                    .map(a -> AnnouncementDTO.fromEntity((com.uhn.pmb.entity.Announcement) a))
                    .collect(Collectors.toList());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", dtos);
            response.put("totalPages", result.getTotalPages());
            response.put("totalElements", result.getTotalElements());
            response.put("currentPage", page);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error fetching announcements: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/api/announcements/recent")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI', 'CAMABA')")
    public ResponseEntity<?> getRecentAnnouncements() {
        try {
            List<AnnouncementDTO> dtos = announcementService.findRecent().stream()
                    .map(AnnouncementDTO::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(Map.of("success", true, "data", dtos));
        } catch (Exception e) {
            log.error("Error fetching recent announcements: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/api/announcements/urgent")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI', 'CAMABA')")
    public ResponseEntity<?> getUrgentAnnouncements() {
        try {
            List<AnnouncementDTO> dtos = announcementService.findUrgent().stream()
                    .map(AnnouncementDTO::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(Map.of("success", true, "data", dtos, "count", dtos.size()));
        } catch (Exception e) {
            log.error("Error fetching urgent announcements: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/api/announcements/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI', 'CAMABA')")
    public ResponseEntity<?> getAnnouncementById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(Map.of("success", true, "data",
                    AnnouncementDTO.fromEntity(announcementService.findActiveById(id))));
        } catch (Exception e) {
            log.error("Error fetching announcement: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @PutMapping("/api/announcements/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> updateAnnouncement(@PathVariable Long id,
                                                @Valid @RequestBody CreateAnnouncementRequest request) {
        try {
            announcementService.update(id, request);
            return ResponseEntity.ok(new ApiResponse(true, "Pengumuman berhasil diperbarui"));
        } catch (Exception e) {
            log.error("Error updating announcement: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @DeleteMapping("/api/announcements/{id}")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> deleteAnnouncement(@PathVariable Long id) {
        try {
            announcementService.delete(id);
            return ResponseEntity.ok(new ApiResponse(true, "Pengumuman berhasil dihapus"));
        } catch (Exception e) {
            log.error("Error deleting announcement: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @PutMapping("/api/announcements/{id}/deactivate")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> deactivateAnnouncement(@PathVariable Long id) {
        try {
            announcementService.deactivate(id);
            return ResponseEntity.ok(new ApiResponse(true, "Pengumuman berhasil dinonaktifkan"));
        } catch (Exception e) {
            log.error("Error deactivating announcement: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/api/announcements/search/{keyword}")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI', 'CAMABA')")
    public ResponseEntity<?> searchAnnouncements(@PathVariable String keyword) {
        try {
            List<AnnouncementDTO> dtos = announcementService.search(keyword).stream()
                    .map(AnnouncementDTO::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(Map.of("success", true, "data", dtos, "count", dtos.size()));
        } catch (Exception e) {
            log.error("Error searching announcements: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }
}