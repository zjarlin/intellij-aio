package site.addzero.lsi.analyzer.scanner

import site.addzero.lsi.analyzer.metadata.PojoMetadata
import site.addzero.util.lsi.clazz.LsiClass

class PojoMetadataScanner : MetadataScanner<PojoMetadata> {

    override fun support(lsiClass: LsiClass): Boolean {
        return lsiClass.isPojo
    }

    override fun scan(lsiClass: LsiClass) = PojoMetadata.from(lsiClass)
}
