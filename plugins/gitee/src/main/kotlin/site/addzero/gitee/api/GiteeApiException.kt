package site.addzero.gitee.api

/**
 * Exception for Gitee API errors
 */
class GiteeApiException(
    message: String,
    val statusCode: Int = 0,
    cause: Throwable? = null
) : Exception(message, cause)
