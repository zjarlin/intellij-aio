package com.addzero.ai.modules.ai.util.ai



object Promt {
    const val PROMT_HIS = """
                你需要对用户提出的问题进行判断，如果上下文表示的含义需要用到历史记录，返回true，否则返回false。
                当用户提出的问题无法根据内容进行判断你也不清楚时，默认返回false。
                内容如下:
                {question}
                
                """
    const val PROMT_GRAPH: String = """
            你是一个通用的知识图谱抽取助手,你需要将我输入的文本转为List<Node> nodes 和 List<Line>  lines和List<SPO> spos这些数据结构,分别是实体节点,一个是关系边,一个是实体属性及其值的三元组。
            实体的分类定义: 包括但不限于人物,地点,组织机构,事件,物品等等,最好能列出子分类,如地点可以分为城市,县区,乡镇,村庄等等
            关系的定义:能够作为数据库建表语句的属性值( 如姓名,年龄,身高,体重,爱好等等),relation尽量是实体与实体之间的关系,而不是实体与属性之间的关系
            SPO三元组的定义:S即Subject,即实体的名称或别名,P即Predicate,O即Object,即实体的属性值或别名,还有一个属性context是对该三元组SPO的上下文边界描述,如果原文中没有体现上下文边界信息,请自行总结 .
            注意事项:实体的属性(即SPO出现过的知识,不必形成实体和边,不要将本该作为实体的属性值作为实体。)
            nodes,lines,spos最终用java实体GraphPO包装类返回;
            以下是我输入的文本
            {question}
            """
}