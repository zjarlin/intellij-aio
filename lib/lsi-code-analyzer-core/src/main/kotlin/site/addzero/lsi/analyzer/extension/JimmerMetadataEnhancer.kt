package site.addzero.lsi.analyzer.extension

import site.addzero.lsi.analyzer.jimmer.JimmerAnnotations
import site.addzero.lsi.analyzer.jimmer.JimmerEntityMetadata
import site.addzero.lsi.analyzer.metadata.PojoMetadata
import site.addzero.util.lsi.clazz.LsiClass

/**
 * Jimmer 框架元数据增强器
 * 
 * 处理 Jimmer 特有的注解：@Entity, @Table, @Column, @OneToMany 等
 */
class JimmerMetadataEnhancer : LsiMetadataEnhancer<JimmerEntityMetadata> {
    
    override val id: String = "jimmer"
    override val name: String = "Jimmer Framework Enhancer"
    override val priority: Int = 10
    
    override fun support(lsiClass: LsiClass): Boolean {
        val annotationNames = lsiClass.annotations.mapNotNull { it.qualifiedName }
        return JimmerAnnotations.isJimmerEntity(annotationNames)
    }
    
    override fun enhance(lsiClass: LsiClass, base: PojoMetadata): JimmerEntityMetadata {
        return JimmerEntityMetadata.from(lsiClass)
    }
    
    override fun getEnhancedType(): Class<JimmerEntityMetadata> = JimmerEntityMetadata::class.java
}
