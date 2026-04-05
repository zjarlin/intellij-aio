package site.addzero.kcp.spreadpack.ide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpreadPackStubRendererTest {

    @Test
    fun `rendered file exposes carrier properties and generated overload names`() {
        val generatedFiles = SpreadPackStubRenderer.renderGeneratedFiles(
            candidates = listOf(
                IdeGeneratedOverloadCandidate(
                    original = IdeFunctionModel(
                        callableFqName = "site.addzero.example.renderAlias",
                        packageName = "site.addzero.example",
                        ownerClassFqName = null,
                        ownerTypeParameters = emptyList(),
                        name = "renderAlias",
                        typeParameters = emptyList(),
                        parameters = emptyList(),
                        returnType = classType("kotlin.Unit"),
                        visibilityKeyword = null,
                        isSuspend = false,
                        isOperator = false,
                        isInfix = false,
                    ),
                    generatedName = "renderAlias",
                    generatedParameters = listOf(
                        IdeValueParameterModel(
                            name = "title",
                            type = classType("kotlin.String"),
                            hasDefaultValue = false,
                            isVararg = false,
                        ),
                        IdeValueParameterModel(
                            name = "maxLines",
                            type = classType("kotlin.Int"),
                            hasDefaultValue = true,
                            isVararg = false,
                        ),
                    ),
                    expansions = emptyList(),
                ),
            ),
            carrierStubs = listOf(
                IdeGeneratedCarrierStub(
                    packageName = "site.addzero.example",
                    classId = "site.addzero.example.RenderAliasArgs",
                    classShortName = "RenderAliasArgs",
                    fields = listOf(
                        IdeCarrierFieldModel(
                            name = "title",
                            type = classType("kotlin.String"),
                            hasDefaultValue = false,
                        ),
                        IdeCarrierFieldModel(
                            name = "maxLines",
                            type = classType("kotlin.Int"),
                            hasDefaultValue = true,
                        ),
                    ),
                ),
            ),
        )

        assertEquals(1, generatedFiles.size)

        val generatedFile = generatedFiles.single()
        assertEquals("site/addzero/example/${SpreadPackIdeaConstants.stubFileName}", generatedFile.relativePath)
        assertEquals("site.addzero.example", generatedFile.packageName)
        assertTrue(generatedFile.topLevelCallableNames.contains("renderAlias"))
        assertTrue(generatedFile.topLevelCallableNames.contains("title"))
        assertTrue(generatedFile.topLevelCallableNames.contains("maxLines"))
        assertTrue(generatedFile.topLevelClassifierNames.isEmpty())
        assertTrue(
            generatedFile.content.contains(
                "var site.addzero.example.RenderAliasArgs.title: kotlin.String",
            ),
        )
        assertTrue(
            generatedFile.content.contains(
                "fun renderAlias(title: kotlin.String, maxLines: kotlin.Int = kotlin.TODO()): kotlin.Unit",
            ),
        )
    }

    @Test
    fun `rendered file list is empty when nothing is generated`() {
        val generatedFiles = SpreadPackStubRenderer.renderGeneratedFiles(
            candidates = emptyList(),
            carrierStubs = emptyList(),
        )

        assertTrue(generatedFiles.isEmpty())
    }

    private fun classType(
        classId: String,
    ) = IdeClassTypeModel(
        classId = classId,
        arguments = emptyList(),
        nullable = false,
        renderedText = classId,
    )
}
