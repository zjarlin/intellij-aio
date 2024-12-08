package com.addzero.addl.action.anycodegen.impl

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class GenJimmerAll :  AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val e1 = e
        val e2 = e
        GenExcelDTO().actionPerformed(e)
        GenJimmerController().actionPerformed(e1)
        GenJimmerDTO().actionPerformed(e2)
    }


}