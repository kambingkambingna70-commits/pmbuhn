package com.uhn.pmb.controller;

import com.uhn.pmb.entity.ContactInfo;
import com.uhn.pmb.entity.SystemLink;
import com.uhn.pmb.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/api/settings")
@RequiredArgsConstructor
public class SystemSettingsController {

    private final SystemSettingsService systemSettingsService;

    @GetMapping("/contact-info")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> getContactInfo() {
        try {
            var opt = systemSettingsService.getContactInfo();
            Map<String, Object> resp = new HashMap<>();
            resp.put("success", true);
            if (opt.isEmpty()) {
                resp.put("message", "No contact info");
                resp.put("data", null);
            } else {
                resp.put("data", opt.get());
            }
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/contact-info")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> updateContactInfo(@RequestBody ContactInfo contactInfo) {
        try {
            ContactInfo saved = systemSettingsService.saveContactInfo(contactInfo);
            return ResponseEntity.ok(Map.of("success", true, "message", "Contact info updated", "data", saved));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/system-links")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> getAllSystemLinks() {
        try {
            var links = systemSettingsService.getAllSystemLinks();
            return ResponseEntity.ok(Map.of("success", true, "data", links, "total", links.size()));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/system-links/active")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> getActiveSystemLinks() {
        try {
            var links = systemSettingsService.getActiveSystemLinks();
            return ResponseEntity.ok(Map.of("success", true, "data", links));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/system-links/{id}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> getSystemLink(@PathVariable Integer id) {
        try {
            var opt = systemSettingsService.getSystemLinkById(id);
            if (opt.isEmpty()) {
                return ResponseEntity.ok(Map.of("success", false, "message", "Link not found"));
            }
            return ResponseEntity.ok(Map.of("success", true, "data", opt.get()));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/system-links")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> createSystemLink(@RequestBody SystemLink systemLink) {
        try {
            SystemLink saved = systemSettingsService.createSystemLink(systemLink);
            return ResponseEntity.ok(Map.of("success", true, "message", "Link created", "data", saved));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PutMapping("/system-links/{id}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> updateSystemLink(@PathVariable Integer id, @RequestBody SystemLink systemLink) {
        try {
            SystemLink saved = systemSettingsService.updateSystemLink(id, systemLink);
            return ResponseEntity.ok(Map.of("success", true, "message", "Link updated", "data", saved));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @DeleteMapping("/system-links/{id}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> deleteSystemLink(@PathVariable Integer id) {
        try {
            systemSettingsService.deleteSystemLink(id);
            return ResponseEntity.ok(Map.of("success", true, "message", "Link deleted"));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("success", false, "message", e.getMessage()));
        }
    }
}