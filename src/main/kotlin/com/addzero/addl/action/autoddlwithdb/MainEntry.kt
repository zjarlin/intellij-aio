//package top.fallenangel.jimmergenerator // 定义包名
//
//import com.intellij.database.psi.DbElement // 导入数据库元素类
//import com.intellij.database.psi.DbTable // 导入数据库表类
//import com.intellij.database.util.DasUtil // 导入数据库实用工具类
//import com.intellij.openapi.actionSystem.AnAction // 导入动作类
//import com.intellij.openapi.actionSystem.AnActionEvent // 导入动作事件类
//import com.intellij.openapi.actionSystem.LangDataKeys // 导入语言数据键类
//import com.intellij.openapi.module.ModuleManager // 导入模块管理器类
//import com.intellij.psi.PsiElement // 导入 PSI 元素类
//import top.fallenangel.jimmergenerator.component.SettingStorageComponent // 导入设置存储组件
//import top.fallenangel.jimmergenerator.enums.DBType // 导入数据库类型枚举
//import top.fallenangel.jimmergenerator.enums.Language // 导入语言枚举
//import top.fallenangel.jimmergenerator.model.DbObj // 导入数据库对象模型
//import top.fallenangel.jimmergenerator.model.Context // 导入上下文模型
//import top.fallenangel.jimmergenerator.model.type.Annotation // 导入注解类型模型
//import top.fallenangel.jimmergenerator.model.type.Class // 导入类类型模型
//import top.fallenangel.jimmergenerator.model.type.Parameter // 导入参数类型模型
//import top.fallenangel.jimmergenerator.ui.Frame // 导入框架 UI 类
//import top.fallenangel.jimmergenerator.util.* // 导入所有工具类
//
//class MainEntry : AnAction() { // 定义 MainEntry 类，继承自 AnAction
//    override fun actionPerformed(event: AnActionEvent) { // 重写 actionPerformed 方法，处理动作事件
//        val project = event.project ?: return // 获取当前项目，如果为空则返回
//        val modules = ModuleManager.getInstance(project).modules // 获取当前项目的所有模块
//        val dbTables = event.getData(LangDataKeys.PSI_ELEMENT_ARRAY)?.map { it as DbTable } ?: return // 获取选中的数据库表
//        val dbType = DBType.valueOf(dbTables[0].dataSource.dbms) // 获取数据库类型
//
//        Context.setProject(project) // 设置上下文中的项目
//        Context.setDbType(dbType) // 设置上下文中的数据库类型
//
//        val tables = dbTables.map { // 遍历数据库表，生成 DbObj 对象
//            val tableAnnotation = Annotation( // 创建表注解
//                "Table", Constant.jimmerPackage, listOf( // 注解名称和包名
//                    Parameter("name", it.name, Class("String")) // 参数：表名
//                )
//            )
//            DbObj( // 创建 DbObj 对象，表示数据库表
//                null, true, it.name, // 表名
//                it.field2property(), false, // 字段转属性
//                Class(it.field2property()), // 字段类型
//                (SettingStorageComponent.tableDefaultAnnotations + tableAnnotation).toMutableList(), // 默认注解
//                it.comment // 表注释
//            ).also { table -> // 使用 also 扩展函数
//                DasUtil.getColumns(it) // 获取表的所有列
//                    .toList() // 转换为列表
//                    .map { column -> // 遍历每一列
//                        DbObj( // 创建 DbObj 对象，表示列
//                            column, true, column.name, // 列名
//                            column.field2property(uncapitalize = true), column.isBusinessKey, // 字段转属性，是否为业务主键
//                            column.captureType(Language.JAVA), // 列类型
//                            column.captureAnnotations(Language.JAVA), // 列注解
//                            column.comment // 列注释
//                        ).apply { table.add(this) } // 将列添加到表中
//                    }
//            }
//        }
//        Frame(modules, tables) // 创建并显示框架 UI
//    }
//
//    /**
//     * 检测是否应该显示菜单项
//     */
//    override fun update(event: AnActionEvent) { // 重写 update 方法，更新菜单项的可见性
//        val selected = event.getData(LangDataKeys.PSI_ELEMENT_ARRAY) // 获取选中的元素
//        event.presentation.isVisible = selected.shouldShowMainEntry() // 设置菜单项的可见性
//    }
//
//    private fun Array<PsiElement>?.shouldShowMainEntry(): Boolean { // 扩展函数，判断是否显示主菜单项
//        if (this == null) { // 如果数组为空
//            return false // 返回 false
//        }
//        return all { // 检查所有元素
//            if (it !is DbElement) { // 如果元素不是 DbElement
//                return@all false // 返回 false
//            }
//            it.typeName in arrayOf("table", "view", "表", "视图") // 检查元素类型是否为表或视图
//        }
//    }
//}