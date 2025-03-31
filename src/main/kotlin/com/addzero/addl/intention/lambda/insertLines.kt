//package com.addzero.addl.intention
//
//import com.intellij.openapi.editor.Editor
//
//fun Editor.insertLines(lines: List<String>) {
//        val caretOffset = caretModel.offset
//        val caretLine = caretModel.logicalPosition.line
//        val lineStartOffset = document.getLineStartOffset(caretLine)
//        val lineText = document.text.substring(lineStartOffset, caretOffset)
//        val indentation = lineText.takeWhile { it.isWhitespace() }
//        val indentedResults = lines.joinToString("\n") { if (it == lines.first()) it else "$indentation$it" }
//        if (caches.containsKey(caretOffset)) {
//            caches.remove(caretOffset)
//        } else {
//            caches[caretOffset] = lines
//        }
//        document.insertString(caretOffset, indentedResults)
//    }
