package com.uhn.pmb.service;

import com.uhn.pmb.entity.SystemConfiguration;
import com.uhn.pmb.entity.User;
import com.uhn.pmb.repository.SystemConfigurationRepository;
import com.uhn.pmb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminUserSettingsService {

    private final UserRepository userRepository;
    private final SystemConfigurationRepository systemConfigRepository;

    // ===== USER MANAGEMENT =====

    public List<Map<String, Object>> getAllUsers() {
        return userRepository.findAll().stream().map(user -> {
            Map<String, Object> dto = new java.util.HashMap<>();
            dto.put("id", user.getId());
            dto.put("email", user.getEmail());
            dto.put("fullName", user.getEmail());
            dto.put("role", user.getRole().toString());
            return dto;
        }).collect(Collectors.toList());
    }

    public void updateUserRole(Long id, String roleStr) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));
        if (roleStr == null || roleStr.isEmpty()) {
            throw new RuntimeException("Role tidak valid");
        }
        User.UserRole newRole = User.UserRole.valueOf(roleStr);
        user.setRole(newRole);
        userRepository.save(user);
        log.info("User role updated: {} -> {}", user.getEmail(), newRole);
    }

    public void deleteUser(Long id, String currentEmail) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("User tidak ditemukan"));
        if (user.getEmail().equals(currentEmail)) {
            throw new RuntimeException("Tidak bisa menghapus akun Anda sendiri");
        }
        userRepository.delete(user);
        log.info("User deleted: {}", user.getEmail());
    }

    // ===== SYSTEM SETTINGS =====

    public Map<String, String> getActiveSettings() {
        List<SystemConfiguration> settings = systemConfigRepository.findByIsActive(true);
        Map<String, String> map = new java.util.HashMap<>();
        settings.forEach(s -> map.put(s.getConfigKey(), s.getConfigValue()));
        return map;
    }

    public Optional<SystemConfiguration> getActiveSetting(String key) {
        return systemConfigRepository.findByConfigKey(key)
                .filter(SystemConfiguration::getIsActive);
    }

    public SystemConfiguration saveSetting(String key, String value) {
        SystemConfiguration setting = systemConfigRepository.findByConfigKey(key).orElse(null);
        if (setting == null) {
            setting = SystemConfiguration.builder()
                    .configKey(key)
                    .configValue(value)
                    .configType(SystemConfiguration.ConfigType.STRING)
                    .isActive(true)
                    .build();
        } else {
            setting.setConfigValue(value);
            setting.setUpdatedAt(LocalDateTime.now());
        }
        return systemConfigRepository.save(setting);
    }

    public Optional<SystemConfiguration> getGformLink() {
        return systemConfigRepository.findByConfigKey("exam_gform_link")
                .filter(SystemConfiguration::getIsActive);
    }

    public SystemConfiguration saveGformLink(String gformLink) {
        SystemConfiguration existing = systemConfigRepository.findByConfigKey("exam_gform_link").orElse(null);
        if (existing != null) {
            if (!existing.getConfigValue().equals(gformLink)) {
                existing.setConfigValue(gformLink);
                existing.setUpdatedAt(LocalDateTime.now());
                systemConfigRepository.save(existing);
            }
            return existing;
        } else {
            SystemConfiguration config = SystemConfiguration.builder()
                    .configKey("exam_gform_link")
                    .configValue(gformLink)
                    .description("URL Google Form untuk ujian online")
                    .configType(SystemConfiguration.ConfigType.STRING)
                    .isActive(true)
                    .build();
            return systemConfigRepository.save(config);
        }
    }

    public void deleteGformLink() {
        SystemConfiguration config = systemConfigRepository.findByConfigKey("exam_gform_link")
                .orElseThrow(() -> new RuntimeException("Link Google Form tidak ditemukan"));
        systemConfigRepository.delete(config);
    }
}