package com.addzero.util.lsi_impl.impl.psi.model

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile

data class PsiEleInfo(val packageName: String, val directoryPath: String)

data class PsiCtx(
    val editor: Editor?,
    val psiClass: PsiClass?,
    val psiFile: PsiFile?,
    val virtualFile: VirtualFile,
    val any: Array<PsiClass>?,
)
