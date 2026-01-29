package site.addzero.dotfiles.manifest

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class ManifestRepository(
    private val codec: ManifestCodec = ManifestCodec(),
) {
    fun loadUserManifest(): ManifestSpec {
        val path = DotfilesPaths.userManifestPath()
        if (!Files.exists(path)) return ManifestSpec()
        val content = String(Files.readAllBytes(path), StandardCharsets.UTF_8)
        return codec.decode(content)
    }

    fun saveUserManifest(spec: ManifestSpec) {
        val path = DotfilesPaths.userManifestPath()
        Files.createDirectories(path.parent)
        Files.write(path, codec.encode(spec).toByteArray(StandardCharsets.UTF_8))
    }
}
