package site.addzero.gradle.buddy.intentions.projectdep

/**
 * Utility for computing tree path distance between two Gradle module paths.
 *
 * Module paths like ":plugins:gradle-buddy:gradle-buddy-core" are treated as tree nodes.
 * Distance = depth(A) + depth(B) - 2 * depth(LCA(A, B))
 *
 * Examples:
 *   ":plugins:gradle-buddy:core" vs ":plugins:gradle-buddy:tasks" → distance 2
 *   ":plugins:gradle-buddy:core" vs ":lib:tool-awt" → distance 4
 *   ":plugins:gradle-buddy" vs ":plugins:gradle-buddy:core" → distance 1
 */
object ModulePathDistance {

    /**
     * Split a Gradle module path into segments.
     * ":" → emptyList, ":a:b:c" → ["a", "b", "c"]
     */
    fun segments(modulePath: String): List<String> {
        val trimmed = modulePath.trimStart(':')
        return if (trimmed.isEmpty()) emptyList() else trimmed.split(':')
    }

    /**
     * Compute the tree path distance between two module paths.
     */
    fun distance(pathA: String, pathB: String): Int {
        val segsA = segments(pathA)
        val segsB = segments(pathB)
        val lcaDepth = lcaDepth(segsA, segsB)
        return segsA.size + segsB.size - 2 * lcaDepth
    }

    /**
     * Depth of the Lowest Common Ancestor = length of the common prefix.
     */
    private fun lcaDepth(segsA: List<String>, segsB: List<String>): Int {
        val minLen = minOf(segsA.size, segsB.size)
        for (i in 0 until minLen) {
            if (segsA[i] != segsB[i]) return i
        }
        return minLen
    }
}
