package site.addzero.lsi.analyzer.scanner

import site.addzero.lsi.analyzer.metadata.PojoMetadata
import site.addzero.util.lsi.clazz.LsiClass

class PojoMetadataScanner : MetadataScanner<PojoMetadata> {
    
    override fun support(lsiClass: LsiClass): Boolean {
        if (lsiClass.isInterface || lsiClass.isEnum) return false
        return lsiClass.isPojo
    }
    
    override fun scan(lsiClass: LsiClass): PojoMetadata = PojoMetadata.from(lsiClass)
}
