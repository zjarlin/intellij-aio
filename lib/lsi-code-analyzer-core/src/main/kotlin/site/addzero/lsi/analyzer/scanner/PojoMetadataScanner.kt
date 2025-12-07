package site.addzero.lsi.analyzer.scanner

import site.addzero.util.lsi.clazz.LsiClass

class LsiClassScanner : MetadataScanner<LsiClass> {

    override fun support(lsiClass: LsiClass): Boolean {
        val qualifiedName = lsiClass.qualifiedName
        if (qualifiedName != null && qualifiedName.startsWith(GENERATED_PACKAGE_PREFIX)) {
            return false
        }

        if (lsiClass.isEnum) {
            return false
        }

        return lsiClass.isPojo
    }

    override fun scan(lsiClass: LsiClass) = lsiClass

    companion object {
        private const val GENERATED_PACKAGE_PREFIX = "site.addzero.generated."
    }
}
