package com.addzero.addl.ktututil

import com.addzero.addl.ktututil.JlCollUtil.differenceBy
import com.addzero.addl.ktututil.JlCollUtil.intersectBy

data class Person(
    val id: Int,
    val name: String,
    val age: Int
)

fun main() {
    val list1 = listOf(
        Person(1, "Alice", 20),
        Person(2, "Bob", 25),
        Person(3, "Charlie", 30)
    )

    val list2 = listOf(
        Person(1, "Alice", 20),
        Person(4, "David", 35),
        Person(5, "Eve", 28)
    )

    // 使用多个条件找出交集
    val intersection = list1.intersectBy(list2,
        { p1, p2 -> p1.name == p2.name },
        { p1, p2 -> p1.age == p2.age }
    )

    println("交集：")
    intersection.forEach { println(it) }

    // 使用多个条件找出差集
    val difference = list1.differenceBy(list2,
        { p1, p2 -> p1.name == p2.name },
        { p1, p2 -> p1.age == p2.age }
    )

    println("\n差集：")
    difference.forEach { println(it) }
}
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

    /**
     * 根据多个比较条件计算两个集合的交集。
     *
     * @param T 集合元素的类型
     * @param other 另一个集合
     * @param predicates 多个 lambda 表达式，用于自定义比较规则
     * @return 交集（同时存在于两个集合中的元素）
     */
    fun <T> Collection<T>.intersectBy(
        other: Collection<T>,
        vararg predicates: (T, T) -> Boolean,
    ): List<T> {
        return this.filter { item ->
            other.any { otherItem ->
                predicates.all { predicate -> predicate(item, otherItem) }
            }
        }
    }
}