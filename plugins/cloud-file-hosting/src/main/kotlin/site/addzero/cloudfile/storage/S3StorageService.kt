package site.addzero.cloudfile.storage

import com.intellij.openapi.diagnostic.Logger
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.model.*
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.io.File
import java.net.URI
import java.time.Duration

/**
 * S3-compatible storage service implementation
 * Works with AWS S3, MinIO, Cloudflare R2, etc.
 */
class S3StorageService(
    private val endpoint: String,
    private val region: String,
    private val bucket: String,
    private val accessKey: String,
    private val secretKey: String
) : StorageService {

    override val logger = Logger.getInstance(S3StorageService::class.java)

    private val s3Client: S3Client by lazy {
        val credentials = AwsBasicCredentials.create(accessKey, secretKey)
        val builder = S3Client.builder()
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .region(Region.of(region))

        // Use custom endpoint if provided (for MinIO, etc.)
        if (endpoint.isNotBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                .serviceConfiguration(S3Configuration.builder()
                    .pathStyleAccessEnabled(true)
                    .build())
        }

        builder.build()
    }

    private val s3Presigner: S3Presigner by lazy {
        val credentials = AwsBasicCredentials.create(accessKey, secretKey)
        val builder = S3Presigner.builder()
            .credentialsProvider(StaticCredentialsProvider.create(credentials))
            .region(Region.of(region))

        if (endpoint.isNotBlank()) {
            builder.endpointOverride(URI.create(endpoint))
        }

        builder.build()
    }

    override fun testConnection(): Boolean {
        return try {
            s3Client.headBucket { it.bucket(bucket) }
            true
        } catch (e: Exception) {
            logger.warn("S3 connection test failed: ${e.message}")
            false
        }
    }

    override fun uploadFile(localFile: File, remotePath: String, namespace: String?): StorageService.UploadResult {
        val fullPath = buildFullPath(remotePath, namespace)

        return try {
            val request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(fullPath)
                .contentType(detectContentType(localFile))
                .build()

            val response = s3Client.putObject(request, RequestBody.fromFile(localFile))

            StorageService.UploadResult(
                success = true,
                etag = response.eTag(),
                remotePath = fullPath
            )
        } catch (e: Exception) {
            logger.error("Failed to upload file to S3: ${localFile.path}", e)
            StorageService.UploadResult(
                success = false,
                error = e.message
            )
        }
    }

    override fun downloadFile(remotePath: String, localFile: File, namespace: String?): Boolean {
        val fullPath = buildFullPath(remotePath, namespace)

        return try {
            val request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(fullPath)
                .build()

            s3Client.getObject(request).use { inputStream ->
                localFile.parentFile?.mkdirs()
                localFile.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to download file from S3: $fullPath", e)
            false
        }
    }

    override fun deleteFile(remotePath: String, namespace: String?): Boolean {
        val fullPath = buildFullPath(remotePath, namespace)

        return try {
            val request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(fullPath)
                .build()

            s3Client.deleteObject(request)
            true
        } catch (e: Exception) {
            logger.error("Failed to delete file from S3: $fullPath", e)
            false
        }
    }

    override fun listFiles(prefix: String, namespace: String?): List<StorageService.RemoteFileInfo> {
        val fullPrefix = buildFullPath(prefix, namespace)

        return try {
            val request = ListObjectsV2Request.builder()
                .bucket(bucket)
                .prefix(fullPrefix)
                .build()

            val response = s3Client.listObjectsV2(request)

            response.contents().map { obj ->
                StorageService.RemoteFileInfo(
                    key = obj.key().removePrefix(fullPrefix).removePrefix("/"),
                    size = obj.size(),
                    lastModified = obj.lastModified().toEpochMilli(),
                    etag = obj.eTag() ?: "",
                    isDirectory = obj.key().endsWith("/")
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to list files from S3: $fullPrefix", e)
            emptyList()
        }
    }

    override fun getFileInfo(remotePath: String, namespace: String?): StorageService.RemoteFileInfo? {
        val fullPath = buildFullPath(remotePath, namespace)

        return try {
            val request = HeadObjectRequest.builder()
                .bucket(bucket)
                .key(fullPath)
                .build()

            val response = s3Client.headObject(request)

            StorageService.RemoteFileInfo(
                key = remotePath,
                size = response.contentLength(),
                lastModified = response.lastModified().toEpochMilli(),
                etag = response.eTag() ?: "",
                isDirectory = false
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun exists(remotePath: String, namespace: String?): Boolean {
        return getFileInfo(remotePath, namespace) != null
    }

    override fun close() {
        s3Client.close()
        s3Presigner.close()
    }

    private fun buildFullPath(remotePath: String, namespace: String?): String {
        return if (namespace != null) {
            "cloudfile/$namespace/$remotePath"
        } else {
            "cloudfile/global/$remotePath"
        }
    }

    private fun detectContentType(file: File): String {
        return when (file.extension.lowercase()) {
            "txt" -> "text/plain"
            "json" -> "application/json"
            "xml" -> "application/xml"
            "html", "htm" -> "text/html"
            "css" -> "text/css"
            "js" -> "application/javascript"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            else -> "application/octet-stream"
        }
    }
}
