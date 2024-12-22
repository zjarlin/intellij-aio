package com.addzero.common.enums.dict

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue

/**
 * 用户状态
 *
 * @author AutoDDL
 * @date 2024-12-22 10:42:57
 */
enum class EnumUserStatus(
    val code: String, private val desc: String
) {
    /**
     * 正常
     */
    ZHENG_CHANG("1", "正常"),
    /**
     * 禁用
     */
    JIN_YONG("0", "禁用");

    @JsonValue // 指定序列化时使用的值
    fun toValue(): String = desc

    companion object {
        @JsonCreator
        fun fromCode(code: String): EnumUserStatus? = values().find { it.code == code }
    }
}