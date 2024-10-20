package com.addzero.addl.util

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 一些常用变量
 *
 * @author zjarlin
 * @since 2023/03/02
 */
object Vars {
    val datePrefix: String = LocalDate.now().format(DateTimeFormatter.ISO_DATE)
    val timePrefix: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm_ss_SSS_"))
    val timeSuffix: String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm_ss_SSS"))
}