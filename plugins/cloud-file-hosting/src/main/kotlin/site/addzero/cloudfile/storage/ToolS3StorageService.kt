package site.addzero.cloudfile.storage

import com.intellij.openapi.diagnostic.Logger
import site.addzero.s3.AwsS3StorageClient
import site.addzero.s3.api.S3ClientConfig
import site.addzero.s3.api.S3StorageClient
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.io.File
import java.net.URI

/**
 * Storage service implementation using site.addzero:tool-s3 library
 * Supports S3, MinIO, Cloudflare R2, and other S3-compatible storage
 */
class ToolS3StorageService(
    private val endpoint: String,
    private val region: String,
    private val bucket: String,
    private val accessKey: String,
    private val secretKey: String
) : StorageService {

    override val logger = Logger.getInstance(ToolS3StorageService::class.java)

    private val config = S3ClientConfig(
        endpoint = endpoint,
        accessKey = accessKey,
        secretKey = secretKey,
        region = region.ifBlank { "us-east-1" },
        pathStyleAccess = true
    )

    private val client: S3StorageClient by lazy {
        createS3Client()
    }

    private fun createS3Client(): S3StorageClient {
        val s3Client = S3Client.builder()
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
                )
            )
            .region(Region.of(config.region))
            .endpointOverride(URI.create(endpoint))
            .serviceConfiguration(
                S3Configuration.builder()
                    .pathStyleAccessEnabled(config.pathStyleAccess)
                    .build()
            )
            .build()

        return AwsS3StorageClient(s3Client, config)
    }

    override fun testConnection(): Boolean {
        return try {
            client.bucketExists(bucket)
        } catch (e: Exception) {
            logger.warn("S3 connection test failed: ${e.message}")
            false
        }
    }

    override fun uploadFile(localFile: File, remotePath: String, namespace: String?): StorageService.UploadResult {
        val fullPath = buildFullPath(remotePath, namespace)

        return try {
            val result = client.putObject(
                bucketName = bucket,
                key = fullPath,
                file = localFile,
                contentType = detectContentType(localFile)
            )

            StorageService.UploadResult(
                success = result.isSuccess,
                etag = result.message, // The library returns etag in message for success
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

    override fun uploadBytes(data: ByteArray, remotePath: String, namespace: String?, contentType: String): StorageService.UploadResult {
        val fullPath = buildFullPath(remotePath, namespace)

        return try {
            val result = client.putObject(
                bucketName = bucket,
                key = fullPath,
                data = data,
                contentType = contentType
            )

            StorageService.UploadResult(
                success = result.isSuccess,
                etag = result.message,
                remotePath = fullPath
            )
        } catch (e: Exception) {
            logger.error("Failed to upload bytes to S3: $remotePath", e)
            StorageService.UploadResult(
                success = false,
                error = e.message
            )
        }
    }

    override fun downloadFile(remotePath: String, localFile: File, namespace: String?): Boolean {
        val fullPath = buildFullPath(remotePath, namespace)

        return try {
            val data = client.getObject(bucket, fullPath)
            if (data != null) {
                localFile.parentFile?.mkdirs()
                localFile.writeBytes(data)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            logger.error("Failed to download file from S3: $fullPath", e)
            false
        }
    }

    override fun deleteFile(remotePath: String, namespace: String?): Boolean {
        val fullPath = buildFullPath(remotePath, namespace)

        return try {
            val result = client.deleteObject(bucket, fullPath)
            result.isSuccess
        } catch (e: Exception) {
            logger.error("Failed to delete file from S3: $fullPath", e)
            false
        }
    }

    override fun listFiles(prefix: String, namespace: String?): List<StorageService.RemoteFileInfo> {
        val fullPrefix = buildFullPath(prefix, namespace)

        return try {
            val objects = client.listObjects(bucket, fullPrefix, recursive = true)

            objects.map { obj ->
                StorageService.RemoteFileInfo(
                    key = obj.key.removePrefix(fullPrefix).removePrefix("/"),
                    size = obj.size,
                    lastModified = obj.lastModified?.toEpochMilli() ?: 0L,
                    etag = obj.etag ?: "",
                    isDirectory = obj.key.endsWith("/")
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
            val metadata = client.getObjectMetadata(bucket, fullPath)
            metadata?.let {
                StorageService.RemoteFileInfo(
                    key = remotePath,
                    size = it.size,
                    lastModified = it.lastModified?.toEpochMilli() ?: 0L,
                    etag = it.etag ?: "",
                    isDirectory = false
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun exists(remotePath: String, namespace: String?): Boolean {
        val fullPath = buildFullPath(remotePath, namespace)
        return client.objectExists(bucket, fullPath)
    }

    override fun close() {
        // AwsS3StorageClient doesn't have a close method
        // The underlying S3Client is managed internally
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
