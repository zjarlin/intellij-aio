package site.addzero.util.lsi_impl.impl.reflection.java.method

import site.addzero.util.lsi_impl.impl.reflection.kt.anno.fieldComment
import java.lang.reflect.Method


fun Method.comment(): String? = this.annotations.fieldComment()
