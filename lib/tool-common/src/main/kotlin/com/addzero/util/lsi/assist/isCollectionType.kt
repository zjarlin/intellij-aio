package com.addzero.util.lsi.assist

import com.addzero.util.lsi.constant.COLLECTION_TYPE_FQ_NAMES

fun String?.isCollectionType(): Boolean {
    this ?:return false
    val mayfqCollection = COLLECTION_TYPE_FQ_NAMES.any { this.startsWith(it) }


    val islastCollection = this.substringAfterLast(".").lowercase() in setOf(
        "list", "mutableList", "set", "mutableset", "map", "mutablemap",
        "collection", "mutablecollection", "arraylist", "linkedhashset",
        "hashset", "linkedhashmap", "hashmap"
    )
    val startWithUtil = this.startsWith("java.util.")
    val mayCollection = startWithUtil && islastCollection

//        when {
//            this.startsWith("java.util.") -> {
//                this.substringAfterLast(".") in setOf(
//                    "List", "MutableList", "Set", "MutableSet", "Map", "MutableMap",
//                    "Collection", "MutableCollection", "ArrayList", "LinkedHashSet",
//                    "HashSet", "LinkedHashMap", "HashMap"
//                )
//            }
//        }
    val sureCollection = mayfqCollection || mayCollection
    return sureCollection
}
