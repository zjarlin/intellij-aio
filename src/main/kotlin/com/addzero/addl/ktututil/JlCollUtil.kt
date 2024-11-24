package com.addzero.addl.ktututil

object JlCollUtil {


    /**
     * 根据多个比较条件计算两个集合的差集。
     *
     * @param T 集合元素的类型
     * @param other 另一个集合
     * @param predicates 多个 lambda 表达式，用于自定义比较规则
     * @return 差集（当前集合中不存在于 `other` 集合中的元素）
     */
    fun <T> Collection<T>.differenceBy(
        other: Collection<T>,
        vararg predicates: (T, T) -> Boolean,
    ): List<T> {
        return this.filter { item ->
            other.none { otherItem ->
                predicates.all { predicate -> predicate(item, otherItem) }
            }
        }
    }
}