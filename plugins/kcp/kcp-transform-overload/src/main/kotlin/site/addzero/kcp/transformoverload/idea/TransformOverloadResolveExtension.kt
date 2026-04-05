package site.addzero.kcp.transformoverload.idea

import com.intellij.openapi.diagnostic.Logger
import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.projectStructure.KaModule
import org.jetbrains.kotlin.analysis.api.projectStructure.KaSourceModule
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtension
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtensionFile
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtensionNavigationTargetsProvider
import org.jetbrains.kotlin.analysis.api.resolve.extensions.KaResolveExtensionProvider
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@OptIn(KaExperimentalApi::class)
class TransformOverloadResolveExtensionProvider : KaResolveExtensionProvider() {

    private val logger = Logger.getInstance(TransformOverloadResolveExtensionProvider::class.java)

    override fun provideExtensionsFor(module: KaModule): List<KaResolveExtension> {
        if (module !is KaSourceModule) {
            return emptyList()
        }
        val stubService = module.project.getService(TransformOverloadStubService::class.java)
        val generatedFiles = stubService.getGeneratedFiles()
        if (generatedFiles.isEmpty()) {
            stubService.scheduleRefresh()
            logger.info(
                "Providing dynamic transform-overload resolve extension for ${module.moduleDescription}, " +
                    "stub cache is currently empty",
            )
        } else {
            logger.info(
                "Providing dynamic transform-overload resolve extension for ${module.moduleDescription}, " +
                    "${generatedFiles.size} stub file(s) currently cached",
            )
        }
        return listOf(TransformOverloadResolveExtension(stubService))
    }
}

@OptIn(KaExperimentalApi::class)
private class TransformOverloadResolveExtension(
    private val stubService: TransformOverloadStubService,
) : KaResolveExtension() {

    override fun getKtFiles(): List<KaResolveExtensionFile> {
        return stubService.getGeneratedFiles().map(::TransformOverloadResolveExtensionFile)
    }

    override fun getContainedPackages(): Set<FqName> {
        return stubService.getGeneratedFiles()
            .mapTo(linkedSetOf()) { generatedFile -> FqName(generatedFile.packageName) }
    }
}

@OptIn(KaExperimentalApi::class)
private class TransformOverloadResolveExtensionFile(
    private val generatedFile: IdeGeneratedFile,
) : KaResolveExtensionFile() {

    val packageFqName: FqName = FqName(generatedFile.packageName)

    override fun getFileName(): String = TransformOverloadIdeaConstants.stubFileName

    override fun getFilePackageName(): FqName = packageFqName

    override fun getTopLevelClassifierNames(): Set<Name> {
        return generatedFile.topLevelClassifierNames.mapTo(linkedSetOf(), Name::identifier)
    }

    override fun getTopLevelCallableNames(): Set<Name> {
        return generatedFile.topLevelCallableNames.mapTo(linkedSetOf(), Name::identifier)
    }

    override fun buildFileText(): String = generatedFile.content

    override fun createNavigationTargetsProvider(): KaResolveExtensionNavigationTargetsProvider {
        return EmptyNavigationTargetsProvider
    }
}

@OptIn(KaExperimentalApi::class)
private object EmptyNavigationTargetsProvider : KaResolveExtensionNavigationTargetsProvider() {
    override fun KaSession.getNavigationTargets(
        element: org.jetbrains.kotlin.psi.KtElement,
    ): Collection<PsiElement> = emptyList()
}
