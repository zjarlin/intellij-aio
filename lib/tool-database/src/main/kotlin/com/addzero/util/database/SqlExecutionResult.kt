package com.addzero.util.database

data class SqlExecutionResult(
    val success: Boolean,
    val rowsAffected: Int = 0,
    val resultData: List<Map<String, Any?>> = emptyList(),
    val errorMessage: String? = null,
    val executionTimeMs: Long = 0
) {
    companion object {
        fun success(rowsAffected: Int = 0, executionTimeMs: Long = 0): SqlExecutionResult {
            return SqlExecutionResult(
                success = true,
                rowsAffected = rowsAffected,
                executionTimeMs = executionTimeMs
            )
        }

        fun successWithData(
            data: List<Map<String, Any?>>,
            executionTimeMs: Long = 0
        ): SqlExecutionResult {
            return SqlExecutionResult(
                success = true,
                rowsAffected = data.size,
                resultData = data,
                executionTimeMs = executionTimeMs
            )
        }

        fun failure(errorMessage: String, executionTimeMs: Long = 0): SqlExecutionResult {
            return SqlExecutionResult(
                success = false,
                errorMessage = errorMessage,
                executionTimeMs = executionTimeMs
            )
        }
    }
}
