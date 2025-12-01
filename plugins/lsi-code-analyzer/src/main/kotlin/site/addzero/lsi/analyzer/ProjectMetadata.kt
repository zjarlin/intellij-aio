package site.addzero.lsi.analyzer

import site.addzero.lsi.analyzer.jimmer.JimmerEntityMetadata
import site.addzero.lsi.analyzer.metadata.PojoMetadata

data class ProjectMetadata(
    val projectPath: String,
    val pojoList: List<PojoMetadata>,
    val jimmerEntityList: List<JimmerEntityMetadata>,
    val scanTime: Long = System.currentTimeMillis()
) {
    val allEntities: List<PojoMetadata>
        get() = pojoList + jimmerEntityList.map { it.base }
}
