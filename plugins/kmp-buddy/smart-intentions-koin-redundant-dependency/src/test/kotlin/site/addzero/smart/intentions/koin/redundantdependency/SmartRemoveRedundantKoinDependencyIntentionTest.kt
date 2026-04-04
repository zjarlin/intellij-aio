package site.addzero.smart.intentions.koin.redundantdependency

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.psi.psiUtil.getStrictParentOfType

class SmartRemoveRedundantKoinDependencyIntentionTest : BasePlatformTestCase() {
    fun testRemovesRedundantDependencyAndRewritesUsages() {
        myFixture.configureByText(
            "Demo.kt",
            """
            class UserService {
                fun load() = "ok"
            }

            class StudentService(
                val userService: UserService,
            )

            class StudentFacade(
                private val studentService: StudentService,
                private val <caret>userService: UserService,
            ) {
                fun loadUser(): String {
                    return userService.load()
                }
            }
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)
        val parameter = element!!.getStrictParentOfType<org.jetbrains.kotlin.psi.KtParameter>()
        assertNotNull(parameter)

        val intention = SmartRemoveRedundantKoinDependencyIntention()
        assertTrue(intention.isAvailable(project, myFixture.editor, element))
        assertNotNull(RedundantKoinDependencySupport.findCandidate(parameter!!))
        intention.invoke(project, myFixture.editor, element)

        assertEquals(
            """
            class UserService {
                fun load() = "ok"
            }

            class StudentService(
                val userService: UserService,
            )

            class StudentFacade(
                private val studentService: StudentService,
            ) {
                fun loadUser(): String {
                    return this.studentService.userService.load()
                }
            }
            """.trimIndent(),
            myFixture.file.text,
        )
    }

    fun testDoesNotOfferWhenCarrierDependencyIsPrivate() {
        myFixture.configureByText(
            "Demo.kt",
            """
            class UserService {
                fun load() = "ok"
            }

            class StudentService(
                private val userService: UserService,
            )

            class StudentFacade(
                private val studentService: StudentService,
                private val <caret>userService: UserService,
            ) {
                fun loadUser(): String {
                    return userService.load()
                }
            }
            """.trimIndent(),
        )

        val element = myFixture.file.findElementAt(myFixture.caretOffset)
        assertNotNull(element)
        val resolvedElement = element!!

        val intention = SmartRemoveRedundantKoinDependencyIntention()
        assertFalse(intention.isAvailable(project, myFixture.editor, resolvedElement))
    }
}
