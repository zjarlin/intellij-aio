package com.addzero.util.lsi_impl.impl.clazz.method

import com.addzero.util.lsi_impl.impl.clazz.anno.comment
import java.lang.reflect.Method


fun Method.comment(): String? = this.annotations.comment()
