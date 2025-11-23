package site.addzero.util.lsi.assist

fun String.isArray(): Boolean {

    return this.startsWith("Array<") || this.endsWith("[]")
}
