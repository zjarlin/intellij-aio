package site.addzero.util.ddlgenerator

import site.addzero.util.lsi.anno.LsiAnnotation
import site.addzero.util.lsi.clazz.LsiClass
import site.addzero.util.lsi.field.LsiField
import site.addzero.util.lsi.method.LsiMethod
import site.addzero.util.lsi.type.LsiType

/**
 * Mock Jimmer实体的测试辅助对象
 */

// ============ Mock Annotation ============

fun mockAnnotation(
    qualifiedName: String,
    simpleName: String,
    attributes: Map<String, Any?> = emptyMap()
): LsiAnnotation = object : LsiAnnotation {
    override val qualifiedName: String = qualifiedName
    override val simpleName: String = simpleName
    override val attributes: Map<String, Any?> = attributes
    override fun getAttribute(name: String): Any? = attributes[name]
    override fun hasAttribute(name: String): Boolean = attributes.containsKey(name)
}

// ============ Mock Type ============

fun mockType(
    name: String,
    qualifiedName: String,
    isNullable: Boolean = true,
    isPrimitive: Boolean = false,
    isCollectionType: Boolean = false,
    typeParameters: List<LsiType> = emptyList()
): LsiType = object : LsiType {
    override val name: String = name
    override val qualifiedName: String = qualifiedName
    override val presentableText: String = name
    override val annotations: List<LsiAnnotation> = if (isNullable) {
        listOf(mockAnnotation("kotlin.Nullable", "Nullable"))
    } else {
        listOf(mockAnnotation("kotlin.NonNull", "NonNull"))
    }
    override val isCollectionType: Boolean = isCollectionType
    override val isNullable: Boolean = isNullable
    override val typeParameters: List<LsiType> = typeParameters
    override val isPrimitive: Boolean = isPrimitive
    override val componentType: LsiType? = null
    override val isArray: Boolean = false
    override val lsiClass: LsiClass? = null
}

// ============ Mock Field ============

fun mockField(
    name: String,
    typeName: String,
    qualifiedTypeName: String = "kotlin.$typeName",
    comment: String? = null,
    annotations: List<LsiAnnotation> = emptyList(),
    isNullable: Boolean = true,
    isPrimitive: Boolean = false,
    columnName: String? = null,
    defaultValue: String? = null
): LsiField = object : LsiField {
    override val name: String = name
    override val typeName: String = typeName
    override val type: LsiType = mockType(typeName, qualifiedTypeName, isNullable, isPrimitive)
    override val comment: String? = comment
    override val annotations: List<LsiAnnotation> = annotations
    override val isStatic: Boolean = false
    override val isConstant: Boolean = false
    override val isVar: Boolean = true
    override val isLateInit: Boolean = false
    override val isCollectionType: Boolean = false
    override val defaultValue: String? = defaultValue
    override val columnName: String? = columnName
    override val declaringClass: LsiClass? = null
    override val fieldTypeClass: LsiClass? = null
    override val isNestedObject: Boolean = false
    override val children: List<LsiField> = emptyList()
}

// ============ Mock Class ============

fun mockClass(
    name: String,
    qualifiedName: String,
    tableName: String,
    comment: String? = null,
    fields: List<LsiField> = emptyList(),
    annotations: List<LsiAnnotation> = emptyList(),
    isInterface: Boolean = true
): LsiClass = object : LsiClass {
    override val name: String = name
    override val qualifiedName: String = qualifiedName
    override val comment: String? = comment
    override val fields: List<LsiField> = fields
    override val annotations: List<LsiAnnotation> = listOf(
        mockAnnotation("org.babyfish.jimmer.sql.Entity", "Entity"),
        mockAnnotation("org.babyfish.jimmer.sql.Table", "Table", mapOf("name" to tableName))
    ) + annotations
    override val isInterface: Boolean = isInterface
    override val isEnum: Boolean = false
    override val isCollectionType: Boolean = false
    override val isPojo: Boolean = true
    override val superClasses: List<LsiClass> = emptyList()
    override val interfaces: List<LsiClass> = emptyList()
    override val methods: List<LsiMethod> = emptyList()
}

// ============ Jimmer实体Mock工厂 ============

/**
 * 创建Mock SysUser实体
 */
fun createMockSysUser(): LsiClass {
    val idField = mockField(
        name = "id",
        typeName = "Long",
        qualifiedTypeName = "kotlin.Long",
        comment = "主键",
        annotations = listOf(
            mockAnnotation("org.babyfish.jimmer.sql.Id", "Id"),
            mockAnnotation("org.babyfish.jimmer.sql.GeneratedValue", "GeneratedValue")
        ),
        isNullable = false,
        isPrimitive = true
    )

    val phoneField = mockField(
        name = "phone",
        typeName = "String",
        comment = "手机号",
        annotations = listOf(
            mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
        ),
        isNullable = true,
        columnName = "phone"
    )

    val emailField = mockField(
        name = "email",
        typeName = "String",
        comment = "电子邮箱",
        annotations = listOf(
            mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
        ),
        isNullable = false,
        columnName = "email"
    )

    val usernameField = mockField(
        name = "username",
        typeName = "String",
        comment = "用户名",
        annotations = listOf(
            mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
        ),
        isNullable = false
    )

    val passwordField = mockField(
        name = "password",
        typeName = "String",
        comment = "密码",
        isNullable = false
    )

    val avatarField = mockField(
        name = "avatar",
        typeName = "String",
        comment = "头像",
        isNullable = true
    )

    val nicknameField = mockField(
        name = "nickname",
        typeName = "String",
        comment = "昵称",
        isNullable = true
    )

    val genderField = mockField(
        name = "gender",
        typeName = "String",
        comment = "性别",
        isNullable = true
    )

    return mockClass(
        name = "SysUser",
        qualifiedName = "site.addzero.model.entity.SysUser",
        tableName = "sys_user",
        comment = "用户",
        fields = listOf(
            idField, phoneField, emailField, usernameField,
            passwordField, avatarField, nicknameField, genderField
        )
    )
}

/**
 * 创建Mock Product实体
 */
fun createMockProduct(): LsiClass {
    val idField = mockField(
        name = "id",
        typeName = "Long",
        qualifiedTypeName = "kotlin.Long",
        comment = "主键",
        annotations = listOf(
            mockAnnotation("org.babyfish.jimmer.sql.Id", "Id"),
            mockAnnotation("org.babyfish.jimmer.sql.GeneratedValue", "GeneratedValue")
        ),
        isNullable = false,
        isPrimitive = true
    )

    val nameField = mockField(
        name = "name",
        typeName = "String",
        comment = "产品名称",
        annotations = listOf(
            mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
        ),
        isNullable = false
    )

    val codeField = mockField(
        name = "code",
        typeName = "String",
        comment = "产品编码",
        annotations = listOf(
            mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
        ),
        isNullable = false
    )

    val descriptionField = mockField(
        name = "description",
        typeName = "String",
        comment = "产品描述",
        isNullable = true
    )

    val accessMethodField = mockField(
        name = "accessMethod",
        typeName = "String",
        comment = "设备接入方式",
        annotations = listOf(
            mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
        ),
        isNullable = false
    )

    val authMethodField = mockField(
        name = "authMethod",
        typeName = "String",
        comment = "认证方式",
        annotations = listOf(
            mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
        ),
        isNullable = false
    )

    val enabledField = mockField(
        name = "enabled",
        typeName = "Boolean",
        qualifiedTypeName = "kotlin.Boolean",
        comment = "是否启用",
        annotations = listOf(
            mockAnnotation("org.babyfish.jimmer.sql.Key", "Key"),
            mockAnnotation("org.babyfish.jimmer.sql.Default", "Default", mapOf("value" to "1"))
        ),
        isNullable = false,
        isPrimitive = true,
        defaultValue = "true"
    )

    return mockClass(
        name = "Product",
        qualifiedName = "site.addzero.model.entity.Product",
        tableName = "product",
        comment = "产品实体类",
        fields = listOf(
            idField, nameField, codeField, descriptionField,
            accessMethodField, authMethodField, enabledField
        )
    )
}

/**
 * 创建Mock SysRole实体
 */
fun createMockSysRole(): LsiClass {
    val idField = mockField(
        name = "id",
        typeName = "Long",
        qualifiedTypeName = "kotlin.Long",
        comment = "主键",
        annotations = listOf(
            mockAnnotation("org.babyfish.jimmer.sql.Id", "Id"),
            mockAnnotation("org.babyfish.jimmer.sql.GeneratedValue", "GeneratedValue")
        ),
        isNullable = false,
        isPrimitive = true
    )

    val roleNameField = mockField(
        name = "roleName",
        typeName = "String",
        comment = "角色名称",
        annotations = listOf(
            mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
        ),
        isNullable = false,
        columnName = "role_name"
    )

    val roleCodeField = mockField(
        name = "roleCode",
        typeName = "String",
        comment = "角色编码",
        annotations = listOf(
            mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
        ),
        isNullable = false,
        columnName = "role_code"
    )

    val descriptionField = mockField(
        name = "description",
        typeName = "String",
        comment = "角色描述",
        isNullable = true
    )

    val sortField = mockField(
        name = "sort",
        typeName = "Int",
        qualifiedTypeName = "kotlin.Int",
        comment = "排序",
        isNullable = true,
        isPrimitive = true
    )

    return mockClass(
        name = "SysRole",
        qualifiedName = "site.addzero.model.entity.SysRole",
        tableName = "sys_role",
        comment = "角色",
        fields = listOf(idField, roleNameField, roleCodeField, descriptionField, sortField)
    )
}

/**
 * 创建Mock ProductCategory实体
 */
fun createMockProductCategory(): LsiClass {
    val idField = mockField(
        name = "id",
        typeName = "Long",
        qualifiedTypeName = "kotlin.Long",
        comment = "主键",
        annotations = listOf(
            mockAnnotation("org.babyfish.jimmer.sql.Id", "Id"),
            mockAnnotation("org.babyfish.jimmer.sql.GeneratedValue", "GeneratedValue")
        ),
        isNullable = false,
        isPrimitive = true
    )

    val nameField = mockField(
        name = "name",
        typeName = "String",
        comment = "分类名称",
        annotations = listOf(
            mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
        ),
        isNullable = false
    )

    val codeField = mockField(
        name = "code",
        typeName = "String",
        comment = "分类编码",
        annotations = listOf(
            mockAnnotation("org.babyfish.jimmer.sql.Key", "Key")
        ),
        isNullable = false
    )

    val parentIdField = mockField(
        name = "parentId",
        typeName = "Long",
        qualifiedTypeName = "kotlin.Long",
        comment = "父级分类ID",
        isNullable = true,
        isPrimitive = true,
        columnName = "parent_id"
    )

    val levelField = mockField(
        name = "level",
        typeName = "Int",
        qualifiedTypeName = "kotlin.Int",
        comment = "层级",
        isNullable = false,
        isPrimitive = true,
        defaultValue = "1"
    )

    return mockClass(
        name = "ProductCategory",
        qualifiedName = "site.addzero.model.entity.ProductCategory",
        tableName = "product_category",
        comment = "产品分类",
        fields = listOf(idField, nameField, codeField, parentIdField, levelField)
    )
}
