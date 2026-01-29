package site.addzero.gradle.sleep

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object ModuleSleepIcons {
  @JvmField
  val Banner: Icon = IconLoader.getIcon("/icons/moduleSleepBanner.svg", ModuleSleepIcons::class.java)

  @JvmField
  val OpenTabs: Icon = IconLoader.getIcon("/icons/moduleSleepOpenTabs.svg", ModuleSleepIcons::class.java)

  @JvmField
  val KeepFile: Icon = IconLoader.getIcon("/icons/moduleSleepKeepFile.svg", ModuleSleepIcons::class.java)

  @JvmField
  val Restore: Icon = IconLoader.getIcon("/icons/moduleSleepRestore.svg", ModuleSleepIcons::class.java)
}
