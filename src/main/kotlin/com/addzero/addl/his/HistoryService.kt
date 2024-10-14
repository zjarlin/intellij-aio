package com.addzero.addl.his

import com.addzero.addl.FormDTO
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(
    name = "com.myplugin.MyHis",
    storages = [Storage("MyHis.xml")]
)
//@Service
class HistoryService : PersistentStateComponent<HistoryService.State> {

    // 内部类存储历史记录列表
    class State {
        var historyList: MutableSet<FormDTO> = mutableSetOf()
    }

    private var state = State()

    // 获取当前保存的历史记录
    override fun getState(): State {
        return state
    }

    // 加载持久化的历史记录
    override fun loadState(state: State) {
        this.state = state
    }

    // 添加或更新历史记录
    fun addRecord(dto: FormDTO) {
        val historyList = state.historyList
        if (dto.tableName != "示例表名") {
            historyList.add(dto)
        }
    }

    // 获取所有历史记录
    fun getHistory(): MutableSet<FormDTO> {
        val historyList = state.historyList
        return historyList
    }
}