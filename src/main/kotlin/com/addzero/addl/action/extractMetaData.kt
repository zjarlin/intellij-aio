//
//package com.addzero.addl.action
//
//import com.intellij.psi.*
//import org.jetbrains.kotlin.psi.*
// public void extractMetaData(PsiFile file) {
//        if (file instanceof PsiJavaFile) {
//            PsiJavaFile javaFile = (PsiJavaFile) file;
//            PsiElement[] elements = javaFile.getChildren();
//            for (PsiElement element : elements) {
//                if (element instanceof PsiInterface) {
//                    PsiInterface interfaceElement = (PsiInterface) element;
//
//                    // 获取接口的文档注释
//                    String interfaceDocComment = interfaceElement.getDocComment();
//
//                    // 遍历接口的成员（字段、方法等）
//                    PsiMember[] members = interfaceElement.getMembers();
//                    for (PsiMember member : members) {
//                        if (member instanceof PsiField) {
//                            PsiField field = (PsiField) member;
//
//                            // 获取字段的文档注释
//                            String fieldDocComment = field.getDocComment();
//                        }
//                    }
//                }
//            }
//        }
//    }