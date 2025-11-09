package site.addzero.util.lsi.clazz

import site.addzero.util.lsi.anno.LsiAnnotation
import site.addzero.util.lsi.field.LsiField
import site.addzero.util.lsi.method.LsiMethod

/**
 * 语言无关的类结构抽象接口
 * Lsi = Language Structure Interface
 */
interface LsiClass {
    /**
     * 获取类的简单名称
     */
    val name: String?

    /**
     * 获取类的全限定名
     */
    val qualifiedName: String?

    /**
     * 获取类的注释
     */
    val comment: String?

    /**
     * 获取类的所有字段
     */
    val fields: List<LsiField>

    /**
     * 获取类上的注解
     */
    val annotations: List<LsiAnnotation>

    /**
     * 判断是否为接口
     */
    val isInterface: Boolean

    /**
     * 判断是否为枚举
     */
    val isEnum: Boolean

    /**
     * 判断是否为集合类型
     */
    val isCollectionType: Boolean

    /**
     * 判断是否为 POJO 类
     * POJO 类的判断标准：
     * 1. 有实体注解：@Entity (JPA), @Table (Jimmer)
     * 2. 有数据类注解：@Data (Lombok/Kotlin), @Getter/@Setter (Lombok)
     * 3. 不是接口、不是枚举、不是抽象类
     */
    val isPojo: Boolean

    /**
     * 获取父类
     */
    val superClasses: List<LsiClass>

    /**
     * 获取实现的接口
     */
    val interfaces: List<LsiClass>



    val guessTableName: String
    val methods: List<LsiMethod>

}
