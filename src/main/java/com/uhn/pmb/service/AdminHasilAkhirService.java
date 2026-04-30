package com.uhn.pmb.service;

import com.uhn.pmb.entity.HasilAkhir;
import com.uhn.pmb.repository.HasilAkhirRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminHasilAkhirService {

    private final HasilAkhirRepository hasilAkhirRepository;

    public Map<String, Object> uploadDokumenSementara(Long id, MultipartFile npmFile, MultipartFile ktmFile) {
        HasilAkhir hasilAkhir = hasilAkhirRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Hasil Akhir tidak ditemukan"));

        if (npmFile == null && ktmFile == null) {
            throw new IllegalArgumentException("Minimal satu file harus diupload (NPM atau KTM Sementara)");
        }

        try {
            String uploadDir = "uploads/hasil-akhir/" + hasilAkhir.getStudent().getId();
            Path uploadPath = Paths.get(uploadDir);
            Files.createDirectories(uploadPath);

            if (npmFile != null && !npmFile.isEmpty()) {
                String originalName = npmFile.getOriginalFilename();
                if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
                    throw new IllegalArgumentException("File NPM Sementara harus berformat PDF");
                }
                String npmFileName = "npm_sementara_" + System.currentTimeMillis() + ".pdf";
                Path npmPath = uploadPath.resolve(npmFileName);
                Files.copy(npmFile.getInputStream(), npmPath, StandardCopyOption.REPLACE_EXISTING);
                hasilAkhir.setNpmSementaraFile(uploadDir + "/" + npmFileName);
                log.info("NPM Sementara uploaded: {}", npmPath);
            }

            if (ktmFile != null && !ktmFile.isEmpty()) {
                String originalName = ktmFile.getOriginalFilename();
                if (originalName == null || !originalName.toLowerCase().endsWith(".pdf")) {
                    throw new IllegalArgumentException("File KTM Sementara harus berformat PDF");
                }
                String ktmFileName = "ktm_sementara_" + System.currentTimeMillis() + ".pdf";
                Path ktmPath = uploadPath.resolve(ktmFileName);
                Files.copy(ktmFile.getInputStream(), ktmPath, StandardCopyOption.REPLACE_EXISTING);
                hasilAkhir.setKtmSementaraFile(uploadDir + "/" + ktmFileName);
                log.info("KTM Sementara uploaded: {}", ktmPath);
            }

            hasilAkhirRepository.save(hasilAkhir);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "Dokumen sementara berhasil diupload");
            result.put("npmSementaraFile", hasilAkhir.getNpmSementaraFile());
            result.put("ktmSementaraFile", hasilAkhir.getKtmSementaraFile());
            return result;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Error uploading dokumen: " + e.getMessage(), e);
        }
    }

    public List<Map<String, Object>> getAllHasilAkhirForAdmin() {
        List<HasilAkhir> allHasilAkhir = hasilAkhirRepository.findAll();
        List<Map<String, Object>> results = new ArrayList<>();
        for (HasilAkhir ha : allHasilAkhir) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", ha.getId());
            item.put("studentName", ha.getStudent().getFullName());
            item.put("nomorRegistrasi", ha.getNomorRegistrasi());
            item.put("programStudi", ha.getProgramStudiName());
            item.put("status", ha.getStatus().toString());
            item.put("npmSementaraFile", ha.getNpmSementaraFile());
            item.put("ktmSementaraFile", ha.getKtmSementaraFile());
            results.add(item);
        }
        return results;
    }
}