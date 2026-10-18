package com.example.dondeestoy

import android.location.Location
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Obtiene una referencia legible de la vía más cercana a la posición GPS. */
object RoadLookup {
    @Throws(IOException::class)
    fun nearbyRoad(location: Location): String? {
        val key = BuildConfig.MAPTILER_API_KEY
        if (key.isBlank()) return null

        val coordinates = "${location.longitude},${location.latitude}"
        val road = reverseGeocode(coordinates, key, type = "road", limit = 1)
            .optJSONObject(0)
        road?.let { formatRoad(it) }?.let { return it }

        // Si no hay carretera cartografiada cerca, mostramos una dirección si existe.
        val address = reverseGeocode(coordinates, key, type = "address", limit = 1)
            .optJSONObject(0)
            ?: return null
        return address.optString("place_name").takeIf { it.isNotBlank() }
            ?: address.optString("text").takeIf { it.isNotBlank() }
    }

    private fun reverseGeocode(
        coordinates: String,
        key: String,
        type: String,
        limit: Int
    ): JSONArray {
        val endpoint = "https://api.maptiler.com/geocoding/$coordinates.json" +
            "?key=${URLEncoder.encode(key, "UTF-8")}&language=es&types=$type&limit=$limit"
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "DondeEstoy/1.0 (Android)")
        }

        try {
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NO_CONTENT) return JSONArray()
            if (responseCode !in 200..299) {
                throw IOException("No se pudo consultar la vía cercana")
            }
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            return JSONObject(response).optJSONArray("features") ?: JSONArray()
        } finally {
            connection.disconnect()
        }
    }

    private fun formatRoad(feature: JSONObject): String? {
        val properties = feature.optJSONObject("properties")
        val tags = properties?.optJSONObject("feature_tags")
        val reference = listOf("ref", "int_ref", "nat_ref")
            .asSequence()
            .mapNotNull { tag -> tags?.optString(tag)?.takeIf { it.isNotBlank() && it != "null" } }
            .firstOrNull()
        val name = feature.optString("text").takeIf { it.isNotBlank() }
            ?: feature.optString("place_name")
                .substringBefore(',')
                .takeIf { it.isNotBlank() }

        return listOfNotNull(reference, name)
            .distinct()
            .joinToString(" · ")
            .takeIf { it.isNotBlank() }
    }
}
