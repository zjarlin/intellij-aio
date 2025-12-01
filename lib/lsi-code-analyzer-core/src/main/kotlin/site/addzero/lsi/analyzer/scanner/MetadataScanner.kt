package site.addzero.lsi.analyzer.scanner

import site.addzero.util.lsi.clazz.LsiClass

interface MetadataScanner<T> {
    fun support(lsiClass: LsiClass): Boolean
    fun scan(lsiClass: LsiClass): T
}

inline fun <reified T> List<MetadataScanner<*>>.findScanner(lsiClass: LsiClass): MetadataScanner<T>? {
    @Suppress("UNCHECKED_CAST")
    return this.find { it.support(lsiClass) } as? MetadataScanner<T>
}
