package com.shakilpatel.auth.service.ejb;

import io.minio.*;
import io.minio.errors.*;
import jakarta.annotation.PostConstruct;
import jakarta.ejb.Stateless;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Stateless
public class FileStorageBean {

    @ConfigProperty(name = "minio.endpoint")
    private String minioEndpoint;

    @ConfigProperty(name = "minio.access.key")
    private String accessKey;

    @ConfigProperty(name = "minio.secret.key")
    private String secretKey;

    @ConfigProperty(name = "minio.bucket.profiles")
    private String profilesBucket;

    @ConfigProperty(name = "minio.public.url")
    private String publicUrl;

    @ConfigProperty(name = "upload.max.file.size", defaultValue = "5242880")
    private Long maxFileSize; // 5MB

    @ConfigProperty(name = "upload.allowed.extensions", defaultValue = "jpg,jpeg,png,gif,webp")
    private String allowedExtensions;

    private MinioClient minioClient;

    @PostConstruct
    public void init() {
        minioClient = MinioClient.builder()
                .endpoint(minioEndpoint)
                .credentials(accessKey, secretKey)
                .build();

        // Ensure bucket exists
        try {
            boolean exists = minioClient.bucketExists(
                    BucketExistsArgs.builder().bucket(profilesBucket).build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder().bucket(profilesBucket).build()
                );
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MinIO", e);
        }
    }

    /**
     * Upload profile picture
     *
     * @return Public URL of uploaded file
     */
    public String uploadProfilePicture(byte[] fileData, String originalFilename, Long userId)
            throws IOException {

        // Validate file size
        if (fileData.length > maxFileSize) {
            throw new IllegalArgumentException("File size exceeds maximum allowed size");
        }

        // Validate extension
        String extension = getFileExtension(originalFilename);
        if (!isAllowedExtension(extension)) {
            throw new IllegalArgumentException("File type not allowed. Allowed: " + allowedExtensions);
        }

        // Generate unique filename
        String fileName = String.format("profiles/%d/%s.%s",
                userId, UUID.randomUUID().toString(), extension);

        try (InputStream inputStream = new ByteArrayInputStream(fileData)) {
            // Upload to MinIO
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(profilesBucket)
                            .object(fileName)
                            .stream(inputStream, fileData.length, -1)
                            .contentType(getContentType(extension))
                            .build()
            );

            // Return public URL
            return String.format("%s/%s/%s", publicUrl, profilesBucket, fileName);

        } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IOException("Failed to upload file to storage", e);
        }
    }

    /**
     * Delete profile picture
     */
    public void deleteProfilePicture(String fileUrl) throws IOException {
        try {
            // Extract object name from URL
            String objectName = fileUrl.substring(fileUrl.indexOf(profilesBucket) + profilesBucket.length() + 1);

            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(profilesBucket)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new IOException("Failed to delete file from storage", e);
        }
    }

    /**
     * Get file from MinIO
     */
    public InputStream getFile(String fileUrl) throws IOException {
        try {
            String objectName = fileUrl.substring(fileUrl.indexOf(profilesBucket) + profilesBucket.length() + 1);

            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(profilesBucket)
                            .object(objectName)
                            .build()
            );
        } catch (Exception e) {
            throw new IOException("Failed to retrieve file from storage", e);
        }
    }

    // Helper methods
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private boolean isAllowedExtension(String extension) {
        return allowedExtensions.toLowerCase().contains(extension.toLowerCase());
    }

    private String getContentType(String extension) {
        return switch (extension.toLowerCase()) {
            case "jpg", "jpeg" ->
                "image/jpeg";
            case "png" ->
                "image/png";
            case "gif" ->
                "image/gif";
            case "webp" ->
                "image/webp";
            default ->
                "application/octet-stream";
        };
    }
}
