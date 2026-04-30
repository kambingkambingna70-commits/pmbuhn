package com.uhn.pmb.controller;

import com.uhn.pmb.service.PublicationScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/api/publication-schedule")
@RequiredArgsConstructor
@Slf4j
public class PublicationScheduleController {

    private final PublicationScheduleService publicationScheduleService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> getAllSchedules() {
        return ResponseEntity.ok(publicationScheduleService.getAllSchedules());
    }

    @GetMapping("/{periodId}")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> getScheduleByPeriod(@PathVariable Long periodId) {
        return ResponseEntity.ok(publicationScheduleService.getScheduleByPeriod(periodId));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> createOrUpdateSchedule(@RequestBody Map<String, Object> request,
                                                     Authentication auth) {
        try {
            Long periodId = Long.valueOf(request.get("periodId").toString());
            String publishDateTimeStr = request.get("publishDateTime").toString();
            String notes = request.containsKey("notes") ? (String) request.get("notes") : null;
            return ResponseEntity.ok(publicationScheduleService.createOrUpdate(
                    periodId, publishDateTimeStr, notes, auth.getName()));
        } catch (Exception e) {
            log.error("Error saving publication schedule: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/{periodId}/publish-now")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> publishNow(@PathVariable Long periodId, Authentication auth) {
        try {
            return ResponseEntity.ok(publicationScheduleService.publishNow(periodId, auth.getName()));
        } catch (Exception e) {
            log.error("Error publishing results: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> deleteSchedule(@PathVariable Long id) {
        publicationScheduleService.deleteSchedule(id);
        return ResponseEntity.ok(Map.of("success", true, "message", "Jadwal dihapus"));
    }
}