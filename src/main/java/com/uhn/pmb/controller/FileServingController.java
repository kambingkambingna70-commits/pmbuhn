package com.uhn.pmb.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Endpoint untuk serve uploaded files (photos, documents, etc)
 */
@Slf4j
@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "*")
public class FileServingController {

    /**
     * Serve file by path
     * Example: GET /api/files/view/uploads/admission-forms/1/photo.jpg
     */
    @GetMapping("/view/**")
    public ResponseEntity<?> viewFile(HttpServletRequest request,
            @RequestParam(value = "download", required = false) boolean download) {
        try {
            // Extract the actual file path from URL
            // /api/files/view/uploads/admission-forms/1/photo.jpg -> uploads/admission-forms/1/photo.jpg
            String fullPath = request.getRequestURI().replace("/api/files/view/", "");
            
            log.debug("📁 Attempting to serve file: {}", fullPath);
            
            // Security: prevent directory traversal
            if (fullPath.contains("..") || fullPath.contains("~") || fullPath.startsWith("/")) {
                log.warn("⚠️ Potential directory traversal attempt: {}", fullPath);
                return ResponseEntity.badRequest().body("Invalid file path");
            }
            
            // Try to find file in multiple locations
            Path filePath = null;
            File file = null;
            
            // Try direct path first
            file = new File(fullPath);
            if (!file.exists()) {
                // Try with project root prefix
                file = new File(new File(".").getAbsolutePath(), fullPath);
            }
            if (!file.exists()) {
                // Try from current working directory
                file = Paths.get(fullPath).toFile();
            }
            
            if (!file.exists()) {
                log.warn("❌ File not found: {}", fullPath);
                return ResponseEntity.notFound().build();
            }
            
            if (!file.isFile()) {
                log.warn("⚠️ Path is not a file: {}", fullPath);
                return ResponseEntity.badRequest().body("Path is not a file");
            }
            
            // Determine content type based on file extension
            String fileName = file.getName();
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                mediaType = MediaType.IMAGE_JPEG;
            } else if (fileName.endsWith(".png")) {
                mediaType = MediaType.IMAGE_PNG;
            } else if (fileName.endsWith(".gif")) {
                mediaType = MediaType.IMAGE_GIF;
            } else if (fileName.endsWith(".webp")) {
                mediaType = MediaType.valueOf("image/webp");
            } else if (fileName.endsWith(".pdf")) {
                mediaType = MediaType.APPLICATION_PDF;
            }
            
            log.info("✅ Serving file: {} ({})", fileName, mediaType);
            
            Resource resource = new FileSystemResource(file);
            String disposition = download ? "attachment" : "inline";
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + fileName + "\"")
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("❌ Error serving file: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error serving file: " + e.getMessage());
        }
    }
    
    /**
     * Alternative endpoint for file by ID
     * Example: GET /api/files/show?path=uploads/admission-forms/1/photo.jpg
     */
    @GetMapping("/show")
    public ResponseEntity<?> showFile(@RequestParam String path) {
        try {
            log.debug("📁 Showing file: {}", path);
            
            // Security: prevent directory traversal
            if (path.contains("..") || path.contains("~") || path.startsWith("/")) {
                log.warn("⚠️ Potential directory traversal attempt: {}", path);
                return ResponseEntity.badRequest().body("Invalid file path");
            }
            
            File file = new File(path);
            if (!file.exists()) {
                file = new File(new File(".").getAbsolutePath(), path);
            }
            if (!file.exists()) {
                log.warn("❌ File not found: {}", path);
                return ResponseEntity.notFound().build();
            }
            
            if (!file.isFile()) {
                log.warn("⚠️ Path is not a file: {}", path);
                return ResponseEntity.badRequest().body("Path is not a file");
            }
            
            // Determine content type
            String fileName = file.getName();
            MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
            
            if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                mediaType = MediaType.IMAGE_JPEG;
            } else if (fileName.endsWith(".png")) {
                mediaType = MediaType.IMAGE_PNG;
            } else if (fileName.endsWith(".gif")) {
                mediaType = MediaType.IMAGE_GIF;
            } else if (fileName.endsWith(".webp")) {
                mediaType = MediaType.valueOf("image/webp");
            } else if (fileName.endsWith(".pdf")) {
                mediaType = MediaType.APPLICATION_PDF;
            }
            
            log.info("✅ Serving file: {} ({})", fileName, mediaType);
            
            Resource resource = new FileSystemResource(file);
            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + fileName + "\"")
                    .body(resource);
                    
        } catch (Exception e) {
            log.error("❌ Error showing file: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    /**
     * Direct file serving for /uploads/** paths
     * Redirects from /api/files to /uploads/** handler
     */
    @GetMapping(value = "", produces = "application/octet-stream")
    public void redirectUploads() {
        // This handler catches /api/files requests and acts as fallback
    }
}

// NOTE: Also add a ResourceHandler in WebConfig for /uploads/**
// spring.web.resources.static-locations=file:uploads/,classpath:/static/
