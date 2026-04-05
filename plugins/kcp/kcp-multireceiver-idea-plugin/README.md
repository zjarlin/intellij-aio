# Multireceiver IDEA Plugin

Provides IDE support for `site.addzero.kcp.multireceiver`.

- Does not bundle the compiler plugin jar into the IDEA plugin anymore
- Avoids K2 classloader conflicts caused by loading compiler plugin classes inside the IDE process
- Currently only provides lightweight project diagnostics while full K2 IDE support is being redesigned
