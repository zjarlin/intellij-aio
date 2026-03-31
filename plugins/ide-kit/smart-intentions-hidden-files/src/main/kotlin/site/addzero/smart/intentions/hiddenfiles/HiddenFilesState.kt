package site.addzero.smart.intentions.hiddenfiles

class HiddenFilesState {
    var showHiddenFiles: Boolean = false
    var hiddenPaths: MutableList<HiddenPathState> = mutableListOf()
}

class HiddenPathState() {
    var path: String = ""
    var directory: Boolean = false

    constructor(path: String, directory: Boolean) : this() {
        this.path = path
        this.directory = directory
    }
}
