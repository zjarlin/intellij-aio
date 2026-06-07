package site.addzero.composebuddy.deadcode

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.nio.file.Files
import java.nio.file.Path

object DeadCodeManifestJson {
    private val gson: Gson = GsonBuilder()
        .setPrettyPrinting()
        .disableHtmlEscaping()
        .create()

    fun write(path: Path, manifest: DeadCodeManifest) {
        Files.write(path, gson.toJson(manifest).toByteArray(Charsets.UTF_8))
    }

    fun read(path: Path): DeadCodeManifest {
        return gson.fromJson(String(Files.readAllBytes(path), Charsets.UTF_8), DeadCodeManifest::class.java)
    }
}
