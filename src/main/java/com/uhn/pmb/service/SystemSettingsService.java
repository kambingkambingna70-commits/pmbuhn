package com.uhn.pmb.service;

import com.uhn.pmb.entity.ContactInfo;
import com.uhn.pmb.entity.SystemLink;
import com.uhn.pmb.repository.ContactInfoRepository;
import com.uhn.pmb.repository.SystemLinkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class SystemSettingsService {

    private final ContactInfoRepository contactInfoRepository;
    private final SystemLinkRepository systemLinkRepository;

    public Optional<ContactInfo> getContactInfo() {
        List<ContactInfo> all = contactInfoRepository.findAll();
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    public ContactInfo saveContactInfo(ContactInfo contactInfo) {
        List<ContactInfo> existing = contactInfoRepository.findAll();
        ContactInfo toSave;
        if (!existing.isEmpty()) {
            toSave = existing.get(0);
            toSave.setAddress(contactInfo.getAddress());
            toSave.setPhone(contactInfo.getPhone());
            toSave.setEmail(contactInfo.getEmail());
            toSave.setOperatingHours(contactInfo.getOperatingHours());
        } else {
            toSave = contactInfo;
        }
        return contactInfoRepository.save(toSave);
    }

    public List<SystemLink> getAllSystemLinks() {
        return systemLinkRepository.findAll();
    }

    public List<SystemLink> getActiveSystemLinks() {
        return systemLinkRepository.findByIsActiveTrue();
    }

    public Optional<SystemLink> getSystemLinkById(Integer id) {
        return systemLinkRepository.findById(id);
    }

    public Optional<SystemLink> getSystemLinkByName(String name) {
        return systemLinkRepository.findByLinkName(name);
    }

    public List<SystemLink> getSystemLinksByType(String type) {
        return systemLinkRepository.findByLinkType(type);
    }

    public SystemLink createSystemLink(SystemLink systemLink) {
        return systemLinkRepository.save(systemLink);
    }

    public SystemLink updateSystemLink(Integer id, SystemLink systemLink) {
        SystemLink existing = systemLinkRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Link not found: " + id));
        existing.setLinkName(systemLink.getLinkName());
        existing.setLinkType(systemLink.getLinkType());
        existing.setLinkUrl(systemLink.getLinkUrl());
        existing.setDescription(systemLink.getDescription());
        existing.setIsActive(systemLink.getIsActive());
        return systemLinkRepository.save(existing);
    }

    public void deleteSystemLink(Integer id) {
        if (!systemLinkRepository.existsById(id)) {
            throw new RuntimeException("Link not found: " + id);
        }
        systemLinkRepository.deleteById(id);
    }
}