package site.addzero.smart.intentions.modulelock

class ModuleLockState {
    var showLockedModules: Boolean = false
    var lockedModules: MutableList<LockedModuleState> = mutableListOf()
}

class LockedModuleState() {
    var moduleName: String = ""
    var rootPaths: MutableList<String> = mutableListOf()

    constructor(moduleName: String, rootPaths: Collection<String>) : this() {
        this.moduleName = moduleName
        this.rootPaths = rootPaths.toMutableList()
    }
}
