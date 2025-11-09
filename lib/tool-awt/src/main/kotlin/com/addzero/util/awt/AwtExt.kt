package com.addzero.util.awt

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

fun setClipboard(content: String) {
    val defaultToolkit = Toolkit.getDefaultToolkit()
    val clipboard = defaultToolkit.systemClipboard
    val selection = StringSelection(content)
    clipboard.setContents(selection, selection)
}

