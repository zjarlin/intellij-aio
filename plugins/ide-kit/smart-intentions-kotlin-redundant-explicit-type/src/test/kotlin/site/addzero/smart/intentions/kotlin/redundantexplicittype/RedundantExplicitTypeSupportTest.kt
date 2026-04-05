package site.addzero.smart.intentions.kotlin.redundantexplicittype

import com.intellij.openapi.progress.ProcessCanceledException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedundantExplicitTypeSupportTest {
    @Test
    fun fallsBackForDanglingFileModuleFailure() {
        val exception = IllegalArgumentException(
            "Dangling file module cannot depend on another dangling file module unless it's a code fragment",
        )

        assertTrue(RedundantExplicitTypeSupport.shouldFallbackToNonCopyAnalysis(exception))
    }

    @Test
    fun doesNotFallbackForProcessCanceledException() {
        val exception = ProcessCanceledException()

        assertFalse(RedundantExplicitTypeSupport.shouldFallbackToNonCopyAnalysis(exception))
    }
}
