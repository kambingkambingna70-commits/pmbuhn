package com.uhn.pmb.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Service for handling file storage operations (uploads, path conversion).
 */
@Service
@Slf4j
public class FileStorageService {

    /**
     * Save uploaded file to disk under uploads/{directory}/{studentId}/
     *
     * @param file       the uploaded file
     * @param directory  sub-directory under uploads (e.g. "admission-forms")
     * @param studentId  student ID used as sub-folder
     * @param fileType   prefix for the filename
     * @return relative path for storage (e.g. "uploads/admission-forms/1/photoId_123.jpg")
     */
    public String saveFile(MultipartFile file, String directory, Long studentId, String fileType) throws Exception {
        if (file == null || file.isEmpty()) {
            return null;
        }
        String baseDir = System.getProperty("user.dir") + File.separator + "uploads"
                + File.separator + directory + File.separator + studentId;
        Path uploadPath = Paths.get(baseDir);
        Files.createDirectories(uploadPath);

        String originalFilename = file.getOriginalFilename();
        String extension = (originalFilename != null && originalFilename.contains("."))
                ? originalFilename.substring(originalFilename.lastIndexOf("."))
                : ".bin";
        String filename = fileType + "_" + System.currentTimeMillis() + extension;

        Path filePath = uploadPath.resolve(filename);
        Files.copy(file.getInputStream(), filePath);

        String relativePath = "uploads/" + directory + "/" + studentId + "/" + filename;
        log.info("✅ File saved: {}", relativePath);
        return relativePath;
    }

    /**
     * Convert an absolute file path to a URL-relative path (starting with /uploads/).
     */
    public String convertPathToUrl(String absolutePath) {
        if (absolutePath == null || absolutePath.isEmpty()) return null;
        try {
            String normalized = absolutePath.replace("\\", "/");
            int idx = normalized.indexOf("/uploads/");
            if (idx >= 0) return normalized.substring(idx);
            idx = normalized.indexOf("uploads/");
            if (idx >= 0) return "/" + normalized.substring(idx);
            log.warn("⚠️ convertPathToUrl: No 'uploads/' found in path: {}", absolutePath);
            return "/" + absolutePath;
        } catch (Exception e) {
            log.warn("⚠️ Error converting path to URL: {}", e.getMessage());
            return "/" + absolutePath;
        }
    }
}
