plugins {
    id("site.addzero.buildlogic.intellij.intellij-platform")
}


dependencies {
    intellijPlatform {
        // Git4Idea for git push listener
        bundledPlugin("Git4Idea")
    }
}
