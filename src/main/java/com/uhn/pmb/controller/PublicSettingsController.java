package com.uhn.pmb.controller;

import com.uhn.pmb.service.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public/settings")
@RequiredArgsConstructor
public class PublicSettingsController {

    private final SystemSettingsService systemSettingsService;

    @GetMapping("/contact-info")
    public ResponseEntity<?> getContactInfo() {
        try {
            var opt = systemSettingsService.getContactInfo();
            if (opt.isEmpty()) {
                return ResponseEntity.ok(Map.of("success", true, "message", "No contact info", "data", (Object)null));
            }
            return ResponseEntity.ok(Map.of("success", true, "data", opt.get()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/system-links")
    public ResponseEntity<?> getSystemLinksPublic() {
        try {
            var links = systemSettingsService.getActiveSystemLinks();
            return ResponseEntity.ok(Map.of("success", true, "data", links, "total", links.size()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/system-links/name/{name}")
    public ResponseEntity<?> getSystemLinkByName(@PathVariable String name) {
        try {
            var opt = systemSettingsService.getSystemLinkByName(name);
            if (opt.isPresent()) {
                return ResponseEntity.ok(Map.of("success", true, "data", opt.get()));
            }
            return ResponseEntity.ok(Map.of("success", false, "message", "Link not found: " + name));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @GetMapping("/system-links/type/{type}")
    public ResponseEntity<?> getSystemLinksByType(@PathVariable String type) {
        try {
            var links = systemSettingsService.getSystemLinksByType(type);
            return ResponseEntity.ok(Map.of("success", true, "data", links, "total", links.size()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("success", false, "message", e.getMessage()));
        }
    }
}