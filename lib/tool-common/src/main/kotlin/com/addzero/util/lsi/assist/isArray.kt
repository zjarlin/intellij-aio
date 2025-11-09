package com.addzero.util.lsi.assist

fun String.isArray(): Boolean {

    return this.startsWith("Array<") || this.endsWith("[]")
}
