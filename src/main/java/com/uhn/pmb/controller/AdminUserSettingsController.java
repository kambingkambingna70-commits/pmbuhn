package com.uhn.pmb.controller;

import com.uhn.pmb.dto.ApiResponse;
import com.uhn.pmb.entity.SystemConfiguration;
import com.uhn.pmb.service.AdminUserSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminUserSettingsController {

    private final AdminUserSettingsService adminUserSettingsService;

    @GetMapping("/api/users")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> getAllUsers() {
        try {
            List<Map<String, Object>> userDtos = adminUserSettingsService.getAllUsers();
            log.info("✅ Retrieved {} users", userDtos.size());
            return ResponseEntity.ok(Map.of("success", true, "data", userDtos, "total", userDtos.size()));
        } catch (Exception e) {
            log.error("❌ Error fetching users: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @PutMapping("/api/users/{id}/role")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> updateUserRole(@PathVariable Long id, @RequestBody Map<String, String> request) {
        try {
            adminUserSettingsService.updateUserRole(id, request.get("role"));
            return ResponseEntity.ok(new ApiResponse(true, "Role berhasil diubah"));
        } catch (Exception e) {
            log.error("❌ Error updating user role: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @DeleteMapping("/api/users/{id}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        try {
            String currentEmail = SecurityContextHolder.getContext().getAuthentication().getName();
            adminUserSettingsService.deleteUser(id, currentEmail);
            return ResponseEntity.ok(new ApiResponse(true, "Akun berhasil dihapus"));
        } catch (Exception e) {
            log.error("❌ Error deleting user: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, "Error: " + e.getMessage()));
        }
    }

    @GetMapping("/api/settings")
    public ResponseEntity<?> getSystemSettings() {
        try {
            Map<String, String> settingsMap = adminUserSettingsService.getActiveSettings();
            return ResponseEntity.ok(Map.of("success", true, "data", settingsMap));
        } catch (Exception e) {
            log.error("❌ Error getting settings: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/api/settings/{key}")
    public ResponseEntity<?> getSetting(@PathVariable String key) {
        try {
            Optional<SystemConfiguration> setting = adminUserSettingsService.getActiveSetting(key);
            if (setting.isEmpty()) {
                return ResponseEntity.ok(Map.of("value", ""));
            }
            return ResponseEntity.ok(Map.of("success", true, "key", setting.get().getConfigKey(), "value", setting.get().getConfigValue()));
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("value", ""));
        }
    }

    @PutMapping("/api/settings/{key}")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> updateSetting(@PathVariable String key, @RequestBody Map<String, String> request) {
        try {
            String value = request.get("value");
            if (value == null) throw new RuntimeException("Value tidak boleh kosong");
            adminUserSettingsService.saveSetting(key, value);
            return ResponseEntity.ok(Map.of("success", true, "message", "Setting berhasil disimpan", "key", key, "value", value));
        } catch (Exception e) {
            log.error("❌ Error updating setting: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @GetMapping("/api/gform-link")
    @PreAuthorize("hasAnyRole('ADMIN_PUSAT', 'ADMIN_VALIDASI')")
    public ResponseEntity<?> getExamGFormLink() {
        try {
            Optional<SystemConfiguration> config = adminUserSettingsService.getGformLink();
            if (config.isEmpty()) {
                return ResponseEntity.ok(Map.of("success", true, "gformLink", "", "message", "Belum ada link Google Form yang diset"));
            }
            return ResponseEntity.ok(Map.of("success", true, "gformLink", config.get().getConfigValue()));
        } catch (Exception e) {
            log.error("❌ Error getting gform link: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @PostMapping("/api/gform-link")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> setExamGFormLink(@RequestBody Map<String, String> request) {
        try {
            String gformLink = request.get("gformLink");
            if (gformLink == null || gformLink.trim().isEmpty()) throw new RuntimeException("Link Google Form tidak boleh kosong");
            adminUserSettingsService.saveGformLink(gformLink);
            return ResponseEntity.ok(Map.of("success", true, "message", "Link Google Form berhasil disimpan", "gformLink", gformLink));
        } catch (Exception e) {
            log.error("❌ Error setting gform link: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }

    @DeleteMapping("/api/gform-link")
    @PreAuthorize("hasRole('ADMIN_PUSAT')")
    public ResponseEntity<?> deleteExamGFormLink() {
        try {
            adminUserSettingsService.deleteGformLink();
            return ResponseEntity.ok(new ApiResponse(true, "Link Google Form berhasil dihapus"));
        } catch (Exception e) {
            log.error("❌ Error deleting gform link: {}", e.getMessage());
            return ResponseEntity.badRequest().body(new ApiResponse(false, e.getMessage()));
        }
    }
}