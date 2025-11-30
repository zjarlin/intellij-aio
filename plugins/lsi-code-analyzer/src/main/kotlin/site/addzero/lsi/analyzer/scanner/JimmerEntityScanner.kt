package site.addzero.lsi.analyzer.scanner

import site.addzero.lsi.analyzer.jimmer.JimmerAnnotations
import site.addzero.lsi.analyzer.jimmer.JimmerEntityMetadata
import site.addzero.util.lsi.clazz.LsiClass

class JimmerEntityScanner : MetadataScanner<JimmerEntityMetadata> {
    
    override fun support(lsiClass: LsiClass): Boolean {
        val annotationNames = lsiClass.annotations.mapNotNull { it.qualifiedName }
        return JimmerAnnotations.isJimmerEntity(annotationNames)
    }
    
    override fun scan(lsiClass: LsiClass): JimmerEntityMetadata = JimmerEntityMetadata.from(lsiClass)
}
