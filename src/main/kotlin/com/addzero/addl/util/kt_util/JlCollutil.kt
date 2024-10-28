package com.addzero.common.kt_util

fun <E> List<E>.removeAt(i: Int) {
    this.toMutableList().removeAt(i)
}
 fun <T> List<T>.add(currentNode: T) {
    this.toMutableList().add(currentNode)

}

fun <E> List<E>.addAll(allLeafNodes: List<E>) {
    this.toMutableList().addAll(allLeafNodes)
}
 fun <E> List<E>.removeIf(predicate: (E) -> Boolean) {
    this.toMutableList().removeIf(predicate)
}