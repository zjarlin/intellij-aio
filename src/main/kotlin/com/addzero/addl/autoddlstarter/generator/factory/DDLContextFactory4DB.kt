package com.addzero.addl.autoddlstarter.generator.factory

import com.addzero.addl.autoddlstarter.generator.consts.MYSQL
import com.addzero.addl.autoddlstarter.generator.entity.DDLContext
import com.addzero.addl.autoddlstarter.generator.entity.mockkDDLContext
import org.jetbrains.kotlin.psi.KtClass

object DDLContextFactory4DB {


    fun createDDLContext4DB(psiClass: KtClass, databaseType: String = MYSQL): DDLContext {

        return mockkDDLContext()

    }


}
