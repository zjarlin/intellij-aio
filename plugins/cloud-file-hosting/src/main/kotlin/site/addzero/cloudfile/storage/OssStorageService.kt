package site.addzero.cloudfile.storage

import com.aliyun.oss.OSSClientBuilder
import com.aliyun.oss.OSSException
import com.aliyun.oss.model.OSSObjectSummary
import com.intellij.openapi.diagnostic.Logger
import java.io.File

/**
 * Aliyun OSS storage service implementation
 */
class OssStorageService(
    private val endpoint: String,
    private val bucket: String,
    private val accessKeyId: String,
    private val accessKeySecret: String
) : StorageService {

    override val logger = Logger.getInstance(OssStorageService::class.java)

    private val ossClient by lazy {
        OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret)
    }

    override fun testConnection(): Boolean {
        return try {
            ossClient.doesBucketExist(bucket)
        } catch (e: Exception) {
            logger.warn("OSS connection test failed: ${e.message}")
            false
        }
    }

    override fun uploadFile(localFile: File, remotePath: String, namespace: String?): StorageService.UploadResult {
        val fullPath = buildFullPath(remotePath, namespace)

        return try {
            val result = ossClient.putObject(bucket, fullPath, localFile)

            StorageService.UploadResult(
                success = true,
                etag = result.eTag,
                remotePath = fullPath
            )
        } catch (e: OSSException) {
            logger.error("Failed to upload file to OSS: ${localFile.path}", e)
            StorageService.UploadResult(
                success = false,
                error = "${e.errorCode}: ${e.errorMessage}"
            )
        } catch (e: Exception) {
            logger.error("Failed to upload file to OSS: ${localFile.path}", e)
            StorageService.UploadResult(
                success = false,
                error = e.message
            )
        }
    }

    override fun uploadBytes(data: ByteArray, remotePath: String, namespace: String?, contentType: String): StorageService.UploadResult {
        val fullPath = buildFullPath(remotePath, namespace)

        return try {
            val result = ossClient.putObject(bucket, fullPath, java.io.ByteArrayInputStream(data))

            StorageService.UploadResult(
                success = true,
                etag = result.eTag,
                remotePath = fullPath
            )
        } catch (e: OSSException) {
            logger.error("Failed to upload bytes to OSS: $remotePath", e)
            StorageService.UploadResult(
                success = false,
                error = "${e.errorCode}: ${e.errorMessage}"
            )
        } catch (e: Exception) {
            logger.error("Failed to upload bytes to OSS: $remotePath", e)
            StorageService.UploadResult(
                success = false,
                error = e.message
            )
        }
    }

    override fun downloadFile(remotePath: String, localFile: File, namespace: String?): Boolean {
        val fullPath = buildFullPath(remotePath, namespace)

        return try {
            val ossObject = ossClient.getObject(bucket, fullPath)
            ossObject.use { obj ->
                localFile.parentFile?.mkdirs()
                localFile.outputStream().use { output ->
                    obj.objectContent.copyTo(output)
                }
            }
            true
        } catch (e: Exception) {
            logger.error("Failed to download file from OSS: $fullPath", e)
            false
        }
    }

    override fun deleteFile(remotePath: String, namespace: String?): Boolean {
        val fullPath = buildFullPath(remotePath, namespace)

        return try {
            ossClient.deleteObject(bucket, fullPath)
            true
        } catch (e: Exception) {
            logger.error("Failed to delete file from OSS: $fullPath", e)
            false
        }
    }

    override fun listFiles(prefix: String, namespace: String?): List<StorageService.RemoteFileInfo> {
        val fullPrefix = buildFullPath(prefix, namespace)

        return try {
            val listing = ossClient.listObjects(bucket, fullPrefix)
            listing.objectSummaries.map { obj ->
                obj.toRemoteFileInfo(fullPrefix)
            }
        } catch (e: Exception) {
            logger.error("Failed to list files from OSS: $fullPrefix", e)
            emptyList()
        }
    }

    override fun getFileInfo(remotePath: String, namespace: String?): StorageService.RemoteFileInfo? {
        val fullPath = buildFullPath(remotePath, namespace)

        return try {
            val metadata = ossClient.getObjectMetadata(bucket, fullPath)
            StorageService.RemoteFileInfo(
                key = remotePath,
                size = metadata.contentLength,
                lastModified = metadata.lastModified.time,
                etag = metadata.eTag ?: "",
                isDirectory = false
            )
        } catch (e: Exception) {
            null
        }
    }

    override fun exists(remotePath: String, namespace: String?): Boolean {
        return try {
            ossClient.doesObjectExist(bucket, buildFullPath(remotePath, namespace))
        } catch (e: Exception) {
            false
        }
    }

    override fun close() {
        ossClient.shutdown()
    }

    private fun buildFullPath(remotePath: String, namespace: String?): String {
        return if (namespace != null) {
            "cloudfile/$namespace/$remotePath"
        } else {
            "cloudfile/global/$remotePath"
        }
    }

    private fun OSSObjectSummary.toRemoteFileInfo(prefix: String): StorageService.RemoteFileInfo {
        return StorageService.RemoteFileInfo(
            key = key.removePrefix(prefix).removePrefix("/"),
            size = size,
            lastModified = lastModified.time,
            etag = eTag ?: "",
            isDirectory = key.endsWith("/")
        )
    }
}
