package com.zhisheng.weather.data

import android.content.Context
import com.zhisheng.weather.model.City
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

@Serializable
private data class LocalPlaceFile(val schema: Int, val places: List<LocalPlaceRecord>)

@Serializable
private data class LocalPlaceRecord(
    val id: String, val name: String, val affiliation: String,
    val lat: Double, val lon: Double, val aliases: List<String>, val level: String,
)

@Serializable
private data class LocalPlaceManifest(val schema: Int, val count: Int, val sha256: String)

/** Point gazetteer for explicit search, never an administrative containment test. */
internal class LocalPlaceIndex private constructor(records: List<LocalPlaceRecord>) {
    private data class Entry(val place: LocalPlaceRecord, val terms: Set<String>, val context: Set<String>)
    private val entries = records.map { place ->
        val aliases = place.aliases + place.name
        val initials = aliases.mapNotNull { value ->
            val words = value.trim().split(Regex("\\s+"))
            if (words.size in 2..8 && words.all { word -> word.all { it in 'a'..'z' || it in 'A'..'Z' } }) {
                words.joinToString("") { it.first().toString() }
            } else null
        }
        Entry(place, (aliases + initials).map(::normalizePlaceTerm).filter(String::isNotBlank).toSet(),
            (listOf(place.affiliation + place.name) + place.affiliation.split('·').flatMap { parent ->
                listOf(parent + place.name, parent.removeSuffix("省").removeSuffix("市") + place.name)
            }).map(::normalizePlaceTerm).toSet())
    }

    fun search(query: String, limit: Int = 30): List<City> {
        val parts = query.trim().take(120).split(Regex("\\s+")).map(::normalizePlaceTerm).filter(String::isNotBlank)
        if (parts.isEmpty() || limit <= 0) return emptyList()
        val joined = parts.joinToString("")
        return entries.mapNotNull { entry ->
            if (!parts.all { part -> entry.context.any { it.contains(part) } || entry.terms.any { it.contains(part) } }) return@mapNotNull null
            val score = when {
                normalizePlaceTerm(entry.place.name) == joined -> 0
                joined in entry.terms -> 1
                entry.terms.any { it.startsWith(joined) } -> 2
                else -> 3
            }
            score to entry
        }.sortedWith(compareBy<Pair<Int, Entry>> { it.first }.thenBy { it.second.place.name.length }.thenBy { it.second.place.id })
            .take(limit.coerceAtMost(100)).map { (_, entry) ->
                val place = entry.place
                City(place.name, place.affiliation, place.lat, place.lon, "geo:local:geonames:${place.id}",
                    coordinateKind = com.zhisheng.weather.model.CoordinateKind.ADMINISTRATIVE, addressSource = "geonames")
            }
    }

    fun areas(province: String? = null): List<String> {
        val places = entries.map { it.place }
        if (province == null) return (places.filter { it.id.startsWith("CN:") && it.level == "ADM1" }
            .map { it.name }.distinct().sorted() + listOf("香港", "澳门", "台湾"))
        return places.filter {
            when (province) {
                "香港" -> it.id.startsWith("HK:") && it.level == "ADM1"
                "澳门" -> it.id.startsWith("MO:") && it.level == "ADM1"
                "台湾" -> it.id.startsWith("TW:") && it.name != "台湾" &&
                    (it.level == "ADM2" || it.level == "ADM1" && it.name.endsWith("市"))
                else -> it.id.startsWith("CN:") && it.level == "ADM2" &&
                    (it.affiliation.split('·').firstOrNull() == province || it.name == province)
            }
        }.map { it.name }.distinct().sorted()
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        fun parse(bytes: ByteArray): LocalPlaceIndex {
            require(bytes.size <= 3_000_000) { "Local place index exceeds limit" }
            val file = json.decodeFromString<LocalPlaceFile>(bytes.toString(Charsets.UTF_8))
            require(file.schema == 1 && file.places.size <= 20_000) { "Unsupported local place index" }
            require(file.places.map { it.id }.distinct().size == file.places.size) { "Duplicate local IDs" }
            require(file.places.all { it.id.isNotBlank() && it.name.isNotBlank() &&
                it.level in setOf("ADM1", "ADM2", "ADM3") && validSearchCoordinates(it.lat, it.lon) }) { "Invalid local place" }
            return LocalPlaceIndex(file.places)
        }

        fun parseVerified(bytes: ByteArray, manifestText: String): LocalPlaceIndex {
            val manifest = json.decodeFromString<LocalPlaceManifest>(manifestText)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            require(manifest.schema == 1 && manifest.sha256 == hash) { "Local dataset checksum mismatch" }
            val index = parse(bytes)
            require(index.entries.size == manifest.count) { "Local dataset count mismatch" }
            return index
        }
    }
}

internal fun normalizePlaceTerm(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKD).lowercase(Locale.ROOT)
        .filter { Character.isLetterOrDigit(it) }

/** Load once off the UI thread. A damaged optional index never erases saved cities. */
internal object LocalPlaces {
    @Volatile private var index: LocalPlaceIndex? = null
    @Volatile private var attempted = false
    val unavailable: Boolean get() = attempted && index == null

    suspend fun areas(context: Context, province: String? = null): List<String> {
        search(context, "") // Loads and verifies the same index once.
        return index?.areas(province).orEmpty()
    }

    suspend fun search(context: Context, query: String): List<City> = withContext(Dispatchers.IO) {
        if (!attempted) synchronized(this@LocalPlaces) {
            if (!attempted) {
                index = runCatching {
                    val assets = context.applicationContext.assets
                    val bytes = assets.open("geo/local_places.json").use { it.readBytes() }
                    val manifest = assets.open("geo/local_places-manifest.json").bufferedReader().use { it.readText() }
                    LocalPlaceIndex.parseVerified(bytes, manifest)
                }.getOrNull()
                attempted = true
            }
        }
        index?.search(query).orEmpty()
    }
}
