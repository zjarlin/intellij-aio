package site.addzero.smart.intentions.kotlin.entityfieldmerge

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile

class EntityFieldMergeSupportTest : BasePlatformTestCase() {
    fun testPlansSameConflictAndNewFields() {
        val target = configureClass(
            "Target.kt",
            """
            package demo

            interface Target {
                val id: Long?
                val same: String?
                val conflict: Int?
            }
            """.trimIndent(),
        )
        val source = configureClass(
            "Source.kt",
            """
            package demo

            class Source(
                val same: String?,
                val conflict: String?,
                val fresh: Boolean?,
            )
            """.trimIndent(),
        )

        val targetModel = EntityFieldCollector.collect(target)
        val sourceModel = EntityFieldCollector.collect(source)
        assertNotNull(targetModel)
        assertNotNull(sourceModel)

        val plan = EntityFieldMergePlanner.createPlan(targetModel!!, listOf(sourceModel!!))
        assertNotNull(plan)

        val byName = plan!!.rows.associateBy { row -> row.sourceField.name }
        assertEquals(EntityFieldMergeStatus.SAME, byName.getValue("same").status)
        assertEquals(EntityFieldMergeStatus.CONFLICT, byName.getValue("conflict").status)
        assertEquals(EntityFieldMergeStatus.NEW, byName.getValue("fresh").status)
        assertTrue(byName.getValue("fresh").defaultSelected)
        assertFalse(byName.getValue("same").defaultSelected)
        assertFalse(byName.getValue("conflict").defaultSelected)
    }

    fun testAppliesNewFieldsToJimmerInterfaceBody() {
        val target = configureClass(
            "Target.kt",
            """
            package demo

            import org.babyfish.jimmer.sql.Entity

            @Entity
            interface Target {
                val id: Long?
            }
            """.trimIndent(),
        )
        val source = configureClass(
            "Source.kt",
            """
            package demo

            import java.time.LocalDateTime

            class Source(
                val alarmName: String?,
                val createTime: LocalDateTime?,
            )
            """.trimIndent(),
        )
        val plan = EntityFieldMergePlanner.createPlan(
            target = EntityFieldCollector.collect(target)!!,
            sources = listOf(EntityFieldCollector.collect(source)!!),
        )!!
        val rows = plan.rows.filter { row -> row.status == EntityFieldMergeStatus.NEW }

        EntityFieldMergeApplier.apply(project, plan, rows)

        assertEquals(
            """
            package demo

            import org.babyfish.jimmer.sql.Entity
            import java.time.LocalDateTime

            @Entity
            interface Target {
                val id: Long?
                val alarmName: String?
                val createTime: LocalDateTime?
            }
            """.trimIndent(),
            target.containingKtFile.text,
        )
    }

    fun testAppliesNewFieldsToDataClassConstructor() {
        val target = configureClass(
            "Target.kt",
            """
            package demo

            data class Target(
                val id: Long?,
            )
            """.trimIndent(),
        )
        val source = configureClass(
            "Source.kt",
            """
            package demo

            data class Source(
                val name: String?,
            )
            """.trimIndent(),
        )
        val plan = EntityFieldMergePlanner.createPlan(
            target = EntityFieldCollector.collect(target)!!,
            sources = listOf(EntityFieldCollector.collect(source)!!),
        )!!
        val rows = plan.rows.filter { row -> row.status == EntityFieldMergeStatus.NEW }

        EntityFieldMergeApplier.apply(project, plan, rows)

        assertEquals(
            """
            package demo

            data class Target(
                val id: Long?,
                val name: String?,
            )
            """.trimIndent(),
            target.containingKtFile.text,
        )
    }

    private fun configureClass(
        fileName: String,
        text: String,
    ): KtClass {
        val file = myFixture.configureByText(fileName, text) as KtFile
        return file.declarations.filterIsInstance<KtClass>().single()
    }
}
