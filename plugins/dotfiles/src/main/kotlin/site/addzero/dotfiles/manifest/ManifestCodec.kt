package site.addzero.dotfiles.manifest

import com.google.gson.Gson
import com.google.gson.GsonBuilder

class ManifestCodec(
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create(),
) {
    fun decode(content: String): ManifestSpec {
        return runCatching { gson.fromJson(content, ManifestSpec::class.java) }
            .getOrElse { ManifestSpec() }
    }

    fun decodeOrNull(content: String): ManifestSpec? {
        return runCatching { gson.fromJson(content, ManifestSpec::class.java) }
            .getOrNull()
    }

    fun encode(spec: ManifestSpec): String = gson.toJson(spec)
}
