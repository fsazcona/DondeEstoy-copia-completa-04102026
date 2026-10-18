package com.example.dondeestoy

import android.location.Location
import org.json.JSONObject
import org.maplibre.android.geometry.LatLng
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

enum class NearbyCategory(
    val label: String,
    val icon: String,
    private val overpassSelector: String
) {
    FUEL("Gasolineras", "⛽", "[amenity=fuel]"),
    RESTAURANT("Restaurantes", "🍴", "[amenity~\"^(restaurant|fast_food|food_court)$\"]"),
    PHARMACY("Farmacias", "💊", "[amenity=pharmacy]"),
    HOSPITAL("Hospitales y centros de salud", "🏥", ""),
    SUPERMARKET("Supermercados", "🏪", "[shop=supermarket]"),
    CAFE("Cafeterías", "☕", "[amenity=cafe]"),
    PARKING("Aparcamientos", "🅿️", "[amenity~\"^(parking|parking_entrance)$\"]"),
    POLICE("Comisarías de policía", "🚓", "[amenity=police]"),
    PUBLIC_TRANSPORT("Transporte público", "🚏", "");

    fun query(latitude: Double, longitude: Double, radiusMeters: Int = 3000): String {
        val selectors = when (this) {
            HOSPITAL -> listOf(
                "[amenity=hospital]",
                "[amenity=clinic]",
                "[amenity=doctors]",
                "[healthcare=hospital]",
                "[healthcare=clinic]",
                "[healthcare=centre]",
                "[healthcare=doctor]"
            )
            PUBLIC_TRANSPORT -> listOf(
                "[highway=bus_stop]",
                "[amenity=bus_station]",
                "[public_transport=platform]",
                "[public_transport=station]",
                "[railway=station]",
                "[railway=halt]",
                "[railway=subway_entrance]",
                "[railway=tram_stop]",
                "[station=subway]",
                "[station=light_rail]"
            )
            else -> listOf(overpassSelector)
        }
        val clauses = selectors.joinToString("\n") { selector ->
            "nwr$selector(around:$radiusMeters,$latitude,$longitude);"
        }
        return "[out:json][timeout:20];\n($clauses);\nout center tags;"
    }

    companion object {
        fun fromLabel(label: String): NearbyCategory? = entries.firstOrNull { it.label == label }
    }
}

data class NearbyPlace(
    val name: String,
    val category: NearbyCategory,
    val position: LatLng,
    val distanceMeters: Float,
    val address: String?,
    val phone: String?,
    val transportType: String? = null
) {
    fun distanceLabel(): String = if (distanceMeters < 1000f) {
        "${distanceMeters.toInt()} m"
    } else {
        "${String.format(Locale.getDefault(), "%.1f", distanceMeters / 1000f)} km"
    }
}

object NearbySearch {
    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"

    @Throws(IOException::class)
    fun search(category: NearbyCategory, origin: Location): List<NearbyPlace> {
        val query = category.query(origin.latitude, origin.longitude)
        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 25_000
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("User-Agent", "DondeEstoy/1.0 (Android)")
        }

        try {
            val requestBody = "data=" + URLEncoder.encode(query, "UTF-8")
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }
            val responseCode = connection.responseCode
            val responseStream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val body = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) {
                throw IOException("OpenStreetMap respondió $responseCode")
            }

            val elements = JSONObject(body).optJSONArray("elements") ?: return emptyList()
            val results = ArrayList<NearbyPlace>(elements.length())
            for (index in 0 until elements.length()) {
                val element = elements.optJSONObject(index) ?: continue
                val center = element.optJSONObject("center")
                val latitude = element.optDouble("lat", center?.optDouble("lat", Double.NaN) ?: Double.NaN)
                val longitude = element.optDouble("lon", center?.optDouble("lon", Double.NaN) ?: Double.NaN)
                if (!latitude.isFinite() || !longitude.isFinite()) continue

                val tags = element.optJSONObject("tags") ?: JSONObject()
                val transportType = if (category == NearbyCategory.PUBLIC_TRANSPORT) {
                    when {
                        tags.optString("highway") == "bus_stop" ||
                            tags.optString("amenity") == "bus_station" -> "Autobús"
                        tags.optString("railway") == "subway_entrance" ||
                            tags.optString("station") == "subway" ||
                            tags.optString("subway") == "yes" -> "Metro"
                        tags.optString("railway") == "tram_stop" ||
                            tags.optString("station") == "light_rail" -> "Tranvía"
                        tags.optString("railway") == "station" ||
                            tags.optString("railway") == "halt" ||
                            tags.optString("station") == "train" -> "Tren"
                        else -> "Transporte público"
                    }
                } else null
                val nombreSanitario = if (category == NearbyCategory.HOSPITAL) {
                    when {
                        tags.optString("amenity") == "hospital" ||
                            tags.optString("healthcare") == "hospital" -> "Hospital"
                        tags.optString("amenity") == "doctors" ||
                            tags.optString("healthcare") == "doctor" -> "Consulta médica"
                        else -> "Centro de salud"
                    }
                } else null
                val name = tags.optString("name").takeIf { it.isNotBlank() }
                    ?: transportType?.let { "Parada de $it" }
                    ?: nombreSanitario
                    ?: category.label
                val address = tags.optString("addr:full").takeIf { it.isNotBlank() }
                    ?: listOfNotNull(
                        tags.optString("addr:street").takeIf { it.isNotBlank() },
                        tags.optString("addr:housenumber").takeIf { it.isNotBlank() }
                    ).joinToString(" ").takeIf { it.isNotBlank() }
                val phone = tags.optString("contact:phone").takeIf { it.isNotBlank() }
                    ?: tags.optString("phone").takeIf { it.isNotBlank() }
                val destination = Location("openstreetmap").apply {
                    this.latitude = latitude
                    this.longitude = longitude
                }
                results += NearbyPlace(
                    name = name,
                    category = category,
                    position = LatLng(latitude, longitude),
                    distanceMeters = origin.distanceTo(destination),
                    address = address,
                    phone = phone,
                    transportType = transportType
                )
            }

            return results
                .distinctBy { "${it.name.lowercase(Locale.ROOT)}:${it.position.latitude}:${it.position.longitude}" }
                .sortedBy { it.distanceMeters }
                .take(40)
        } finally {
            connection.disconnect()
        }
    }
}
