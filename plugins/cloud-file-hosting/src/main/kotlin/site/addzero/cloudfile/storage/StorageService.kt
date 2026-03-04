package site.addzero.cloudfile.storage

import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.io.InputStream

/**
 * Abstract interface for cloud storage services
 * Supports S3-compatible and OSS protocols
 */
interface StorageService {

    val logger: Logger

    /**
     * Test connection to the storage service
     */
    fun testConnection(): Boolean

    /**
     * Upload a file to cloud storage
     * @param localFile Local file to upload
     * @param remotePath Remote path (key)
     * @param namespace Project namespace
     * @return Upload result with ETag
     */
    fun uploadFile(localFile: File, remotePath: String, namespace: String? = null): UploadResult

    /**
     * Upload raw bytes to cloud storage (for compressed data)
     * @param data Byte array to upload
     * @param remotePath Remote path (key)
     * @param namespace Project namespace
     * @param contentType MIME type of the content
     * @return Upload result with ETag
     */
    fun uploadBytes(data: ByteArray, remotePath: String, namespace: String? = null, contentType: String = "application/octet-stream"): UploadResult

    /**
     * Download a file from cloud storage
     * @param remotePath Remote path (key)
     * @param localFile Local file to save to
     * @param namespace Project namespace
     * @return true if successful
     */
    fun downloadFile(remotePath: String, localFile: File, namespace: String? = null): Boolean

    /**
     * Delete a file from cloud storage
     * @param remotePath Remote path (key)
     * @param namespace Project namespace
     */
    fun deleteFile(remotePath: String, namespace: String? = null): Boolean

    /**
     * List files in a prefix
     * @param prefix Path prefix
     * @param namespace Project namespace
     * @return List of remote file info
     */
    fun listFiles(prefix: String = "", namespace: String? = null): List<RemoteFileInfo>

    /**
     * Get file info
     * @param remotePath Remote path (key)
     * @param namespace Project namespace
     */
    fun getFileInfo(remotePath: String, namespace: String? = null): RemoteFileInfo?

    /**
     * Check if file exists
     */
    fun exists(remotePath: String, namespace: String? = null): Boolean

    /**
     * Close connection and cleanup resources
     */
    fun close()

    data class UploadResult(
        val success: Boolean,
        val etag: String? = null,
        val error: String? = null,
        val remotePath: String? = null
    )

    data class RemoteFileInfo(
        val key: String,
        val size: Long,
        val lastModified: Long,
        val etag: String,
        val isDirectory: Boolean = false
    )
}

/**
 * Factory for creating storage service instances
 */
object StorageServiceFactory {

    fun createS3Service(
        endpoint: String,
        region: String,
        bucket: String,
        accessKey: String,
        secretKey: String
    ): StorageService {
        return ToolS3StorageService(endpoint, region, bucket, accessKey, secretKey)
    }

    fun createOssService(
        endpoint: String,
        bucket: String,
        accessKeyId: String,
        accessKeySecret: String
    ): StorageService {
        return OssStorageService(endpoint, bucket, accessKeyId, accessKeySecret)
    }
}
