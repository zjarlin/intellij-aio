package site.addzero.lsi.analyzer.scanner

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import site.addzero.util.lsi.anno.LsiAnnotation
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField
import site.addzero.util.lsi.method.LsiMethod

class LsiClassScannerTest {

    private val scanner = LsiClassScanner()

    // Test implementation of LsiClass for serialization testing
    data class TestLsiClass(
        override val name: String?,
        override val qualifiedName: String?,
        override val comment: String?,
        override val fields: List<LsiField>,
        override val annotations: List<LsiAnnotation>,
        override val isInterface: Boolean,
        override val isEnum: Boolean,
        override val isCollectionType: Boolean,
        override val isPojo: Boolean,
        override val superClasses: List<LsiClass>,
        override val interfaces: List<LsiClass>,
        override val methods: List<LsiMethod>
    ) : LsiClass

    @Test
    fun `should support pojo interfaces`() {
        val lsiClass = testClass(
            qualifiedName = "com.example.jimmer.ATable",
            isInterface = true,
            isPojo = true
        )

        assertTrue(scanner.support(lsiClass))
    }

    @Test
    fun `should ignore generated metadata classes`() {
        val generated = testClass(
            qualifiedName = "site.addzero.generated.pojometa.LsiClassList",
            isPojo = true
        )

        assertFalse(scanner.support(generated))
    }

    @Test
    fun `should serialize and deserialize LsiClass correctly`() {
        val gson: Gson = GsonBuilder().setPrettyPrinting().create()

        // Create a test LsiClass instance with simple data
        val originalClass = TestLsiClass(
            name = "TestClass",
            qualifiedName = "com.example.TestClass",
            comment = "A test class",
            fields = emptyList(),
            annotations = emptyList(),
            isInterface = false,
            isEnum = false,
            isCollectionType = false,
            isPojo = true,
            superClasses = emptyList(),
            interfaces = emptyList(),
            methods = emptyList()
        )

        // Serialize to JSON
        val json = gson.toJson(originalClass)

        // Deserialize back to TestLsiClass
        val deserializedClass = gson.fromJson(json, TestLsiClass::class.java)

        // Verify the deserialized object has the same properties
        assertEquals(originalClass.name, deserializedClass.name)
        assertEquals(originalClass.qualifiedName, deserializedClass.qualifiedName)
        assertEquals(originalClass.comment, deserializedClass.comment)
        assertEquals(originalClass.isInterface, deserializedClass.isInterface)
        assertEquals(originalClass.isEnum, deserializedClass.isEnum)
        assertEquals(originalClass.isCollectionType, deserializedClass.isCollectionType)
        assertEquals(originalClass.isPojo, deserializedClass.isPojo)
        assertEquals(originalClass.fields.size, deserializedClass.fields.size)
        assertEquals(originalClass.annotations.size, deserializedClass.annotations.size)
        assertEquals(originalClass.superClasses.size, deserializedClass.superClasses.size)
        assertEquals(originalClass.interfaces.size, deserializedClass.interfaces.size)
        assertEquals(originalClass.methods.size, deserializedClass.methods.size)
    }

    private fun testClass(
        qualifiedName: String,
        isInterface: Boolean = false,
        isEnum: Boolean = false,
        isPojo: Boolean = false
    ): LsiClass = object : LsiClass {
        override val name: String? = qualifiedName.substringAfterLast('.')
        override val qualifiedName: String? = qualifiedName
        override val comment: String? = null
        override val fields: List<LsiField> = emptyList()
        override val annotations: List<LsiAnnotation> = emptyList()
        override val isInterface: Boolean = isInterface
        override val isEnum: Boolean = isEnum
        override val isCollectionType: Boolean = false
        override val isPojo: Boolean = isPojo
        override val superClasses: List<LsiClass> = emptyList()
        override val interfaces: List<LsiClass> = emptyList()
        override val methods: List<LsiMethod> = emptyList()
    }
}
