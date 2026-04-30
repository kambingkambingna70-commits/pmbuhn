package com.uhn.pmb.service;

import com.uhn.pmb.entity.Sma;
import com.uhn.pmb.repository.SmaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class SmaService {

    private final SmaRepository smaRepository;

    public List<Sma> search(String query) {
        if (query == null || query.trim().length() < 2) {
            return List.of();
        }
        return smaRepository.searchByQuery(query.trim());
    }

    public List<Sma> findAllActive() {
        return smaRepository.findByIsActiveTrue();
    }

    public List<Sma> findAll() {
        return smaRepository.findAll();
    }

    public Sma create(Map<String, String> body) {
        String nama = body.get("nama");
        if (nama == null || nama.trim().isEmpty()) {
            throw new IllegalArgumentException("Nama sekolah wajib diisi");
        }
        String npsn = body.get("npsn");
        if (npsn != null && !npsn.trim().isEmpty() && smaRepository.existsByNpsn(npsn.trim())) {
            throw new IllegalArgumentException("NPSN sudah terdaftar: " + npsn);
        }
        Sma sma = Sma.builder()
                .nama(nama.trim())
                .bentuk(body.getOrDefault("bentuk", "SMA").trim())
                .npsn(npsn != null && !npsn.trim().isEmpty() ? npsn.trim() : null)
                .kota(body.get("kota") != null ? body.get("kota").trim() : null)
                .provinsi(body.get("provinsi") != null ? body.get("provinsi").trim() : null)
                .isActive(true)
                .build();
        sma = smaRepository.save(sma);
        log.info("SMA created: {} ({})", sma.getNama(), sma.getId());
        return sma;
    }

    public Sma update(Long id, Map<String, String> body) {
        Sma sma = smaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("SMA tidak ditemukan: " + id));
        if (body.containsKey("nama") && !body.get("nama").trim().isEmpty()) {
            sma.setNama(body.get("nama").trim());
        }
        if (body.containsKey("bentuk")) sma.setBentuk(body.get("bentuk").trim());
        if (body.containsKey("npsn")) {
            String npsn = body.get("npsn").trim();
            if (!npsn.isEmpty() && !npsn.equals(sma.getNpsn()) && smaRepository.existsByNpsn(npsn)) {
                throw new IllegalArgumentException("NPSN sudah terdaftar: " + npsn);
            }
            sma.setNpsn(npsn.isEmpty() ? null : npsn);
        }
        if (body.containsKey("kota")) sma.setKota(body.get("kota").trim());
        if (body.containsKey("provinsi")) sma.setProvinsi(body.get("provinsi").trim());
        if (body.containsKey("isActive")) sma.setIsActive(Boolean.parseBoolean(body.get("isActive")));
        sma = smaRepository.save(sma);
        log.info("SMA updated: {} ({})", sma.getNama(), sma.getId());
        return sma;
    }

    public void deactivate(Long id) {
        Sma sma = smaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("SMA tidak ditemukan: " + id));
        sma.setIsActive(false);
        smaRepository.save(sma);
        log.info("SMA deactivated: {} ({})", sma.getNama(), id);
    }
}