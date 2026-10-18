package com.example.dondeestoy

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.dondeestoy.ui.theme.DondeEstoyTheme
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.viewinterop.AndroidView
import org.maplibre.android.maps.MapView
import android.annotation.SuppressLint
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer
import androidx.compose.material3.Card
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.annotations.Polygon
import org.maplibre.android.annotations.PolygonOptions
import java.util.Locale
import java.util.Date
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType

private const val UMBRAL_MOVIMIENTO_KMH = 2f
private const val UMBRAL_GPS_IMPRECISO_KMH = 6f

class MainActivity : ComponentActivity() {

    private var mapViewForCallbacks: MapView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MapLibre.getInstance(
            this,
            BuildConfig.MAPTILER_API_KEY,
            WellKnownTileServer.MapTiler
        )

        setContent {
            DondeEstoyTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    val context = LocalContext.current
                    val preferencias = remember {
                        getSharedPreferences("donde_estoy_preferencias", MODE_PRIVATE)
                    }
                    var menuAbierto by remember { mutableStateOf(false) }
                    var mostrarAcercaDe by remember { mutableStateOf(false) }
                    var mostrarBuscarCerca by remember { mutableStateOf(false) }
                    var mostrarConfiguracion by remember { mutableStateOf(false) }
                    var centrarSolicitud by remember { mutableStateOf(0) }
                    var lugaresCercanos by remember { mutableStateOf<List<NearbyPlace>>(emptyList()) }
                    var revisionLugares by remember { mutableStateOf(0) }
                    var lugarSeleccionado by remember { mutableStateOf<NearbyPlace?>(null) }
                    var numeroEmergencia by remember {
                        mutableStateOf(preferencias.getString("numero_emergencia", "112") ?: "112")
                    }
                    var numeroEmergenciaBorrador by remember { mutableStateOf(numeroEmergencia) }
                    var intervaloGpsSegundos by remember {
                        mutableStateOf(preferencias.getInt("intervalo_gps", 5))
                    }
                    var intervaloGpsBorrador by remember { mutableStateOf(intervaloGpsSegundos) }
                    var mantenerPantallaEncendida by remember {
                        mutableStateOf(preferencias.getBoolean("mantener_pantalla", false))
                    }
                    var mantenerPantallaBorrador by remember { mutableStateOf(mantenerPantallaEncendida) }
                    var ubicacion by remember {
                        mutableStateOf<Location?>(null)
                    }
                    var referenciaVia by remember { mutableStateOf<String?>(null) }
                    var ultimaConsultaVia by remember { mutableStateOf<Location?>(null) }
                    var momentoConsultaVia by remember { mutableStateOf(0L) }
                    var idConsultaVia by remember { mutableStateOf(0) }

                    DisposableEffect(mantenerPantallaEncendida) {
                        if (mantenerPantallaEncendida) {
                            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        } else {
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                        onDispose {
                            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }

                    MapaConUbicacion(
                        ubicacion = ubicacion,
                        centrarSolicitud = centrarSolicitud,
                        lugares = lugaresCercanos,
                        revisionLugares = revisionLugares,
                        lifecycle = this@MainActivity.lifecycle,
                        savedInstanceState = savedInstanceState,
                        onMapViewCreated = { mapViewForCallbacks = it },
                        onLugarSeleccionado = { lugarSeleccionado = it }
                    )

                    LocalizacionScreen(intervaloGpsSegundos, referenciaVia) { nuevaUbicacion ->
                        ubicacion = nuevaUbicacion
                        val ahora = android.os.SystemClock.elapsedRealtime()
                        val posicionAnterior = ultimaConsultaVia
                        val debeConsultar = posicionAnterior == null ||
                            (ahora - momentoConsultaVia >= 15_000L &&
                                posicionAnterior.distanceTo(nuevaUbicacion) >= 50f)
                        if (debeConsultar) {
                            ultimaConsultaVia = Location(nuevaUbicacion)
                            momentoConsultaVia = ahora
                            referenciaVia = "Buscando la vía cercana…"
                            val consulta = ++idConsultaVia
                            Thread {
                                val resultado = runCatching { RoadLookup.nearbyRoad(nuevaUbicacion) }
                                    .getOrNull()
                                runOnUiThread {
                                    if (consulta == idConsultaVia) {
                                        referenciaVia = resultado ?: "Vía cercana no disponible"
                                    }
                                }
                            }.start()
                        }
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(start = 16.dp, top = 4.dp)
                    ) {
                        Button(onClick = { menuAbierto = true }) {
                            Text("☰ MENÚ")
                        }

                        DropdownMenu(
                            expanded = menuAbierto,
                            onDismissRequest = { menuAbierto = false },
                            modifier = Modifier.width(280.dp)
                        ) {
                            Text(
                                text = "☰  MENÚ",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .height(1.dp)
                                    .background(Color.LightGray)
                            )
                            listOf(
                                "Mi ubicación" to "📍",
                                "Buscar cerca" to "🔎",
                                "Configuración" to "⚙️",
                                "Emergencia" to "🆘",
                                "Compartir ubicación" to "📤",
                                "Copiar ubicación" to "📋",
                                "Acerca de" to "ℹ️"
                            ).forEachIndexed { index, (opcion, icono) ->
                                if (index == 5) {
                                    Spacer(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp)
                                            .height(1.dp)
                                            .background(Color.LightGray)
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(opcion, fontSize = 14.sp) },
                                    leadingIcon = { Text(icono, fontSize = 18.sp) },
                                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                                    onClick = {
                                        menuAbierto = false
                                        when (opcion) {
                                            "Mi ubicación" -> {
                                                if (ubicacion == null) {
                                                    Toast.makeText(
                                                        context,
                                                        "Esperando una posición GPS",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    centrarSolicitud++
                                                }
                                            }

                                            "Emergencia" -> {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_DIAL,
                                                        Uri.fromParts("tel", numeroEmergencia, null)
                                                    )
                                                )
                                            }

                                            "Compartir ubicación" -> {
                                                val location = ubicacion
                                                if (location == null) {
                                                    Toast.makeText(
                                                        context,
                                                        "Esperando una posición GPS",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    val compartir = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, crearTextoUbicacion(location))
                                                    }
                                                    context.startActivity(
                                                        Intent.createChooser(
                                                            compartir,
                                                            "Compartir ubicación"
                                                        )
                                                    )
                                                }
                                            }

                                            "Copiar ubicación" -> {
                                                val location = ubicacion
                                                if (location == null) {
                                                    Toast.makeText(
                                                        context,
                                                        "Esperando una posición GPS",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                } else {
                                                    val portapapeles = context.getSystemService(ClipboardManager::class.java)
                                                    portapapeles.setPrimaryClip(
                                                        ClipData.newPlainText(
                                                            "Mi posición actual",
                                                            crearTextoUbicacion(location)
                                                        )
                                                    )
                                                    Toast.makeText(
                                                        context,
                                                        "Ubicación copiada",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }

                                            "Buscar cerca" -> mostrarBuscarCerca = true

                                            "Configuración" -> {
                                                numeroEmergenciaBorrador = numeroEmergencia
                                                intervaloGpsBorrador = intervaloGpsSegundos
                                                mantenerPantallaBorrador = mantenerPantallaEncendida
                                                mostrarConfiguracion = true
                                            }

                                            "Acerca de" -> mostrarAcercaDe = true
                                        }
                                    }
                                )
                            }
                        }
                    }

                    if (mostrarBuscarCerca) {
                        AlertDialog(
                            onDismissRequest = { mostrarBuscarCerca = false },
                            title = { Text("¿QUÉ BUSCAS?") },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .heightIn(max = 400.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    Text(
                                        "La búsqueda usa tu posición GPS y necesita Internet para consultar OpenStreetMap.",
                                        modifier = Modifier.padding(bottom = 8.dp),
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        "↓ Desliza para ver todas las opciones",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp),
                                        fontSize = 11.sp,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        color = Color.DarkGray
                                    )
                                    listOf(
                                        "💊" to "Farmacias",
                                        "🏥" to "Hospitales y centros de salud",
                                        "🚏" to "Transporte público",
                                        "🚓" to "Comisarías de policía",
                                        "⛽" to "Gasolineras",
                                        "☕" to "Cafeterías",
                                        "🍴" to "Restaurantes",
                                        "🏪" to "Supermercados",
                                        "🅿️" to "Aparcamientos"
                                    ).forEach { (icono, categoria) ->
                                        TextButton(
                                            onClick = {
                                                mostrarBuscarCerca = false
                                                val origen = ubicacion
                                                val tipo = NearbyCategory.fromLabel(categoria)
                                                if (origen == null || tipo == null) {
                                                    Toast.makeText(context, "Esperando una posición GPS", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    lugaresCercanos = emptyList()
                                                    revisionLugares++
                                                    Toast.makeText(context, "Buscando ${tipo.label} cerca de ti…", Toast.LENGTH_SHORT).show()
                                                    Thread {
                                                        try {
                                                            val resultados = NearbySearch.search(tipo, origen)
                                                            runOnUiThread {
                                                                lugaresCercanos = resultados
                                                                revisionLugares++
                                                                val mensaje = if (resultados.isEmpty()) {
                                                                    "No se encontraron ${tipo.label.lowercase(Locale.getDefault())} en 3 km"
                                                                } else {
                                                                    "${resultados.size} resultados en el mapa. Toca un punto para ver detalles."
                                                                }
                                                                Toast.makeText(context, mensaje, Toast.LENGTH_LONG).show()
                                                            }
                                                        } catch (_: Exception) {
                                                            runOnUiThread {
                                                                Toast.makeText(
                                                                    context,
                                                                    "No se pudo consultar OpenStreetMap. Revisa tu conexión e inténtalo de nuevo.",
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                            }
                                                        }
                                                    }.start()
                                                }
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(icono, fontSize = 18.sp)
                                            Spacer(Modifier.width(14.dp))
                                            Text(categoria, modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(onClick = { mostrarBuscarCerca = false }) {
                                    Text("Cerrar")
                                }
                            }
                        )
                    }

                    if (mostrarConfiguracion) {
                        AlertDialog(
                            onDismissRequest = { mostrarConfiguracion = false },
                            title = { Text("Configuración") },
                            text = {
                                Column(
                                    modifier = Modifier
                                        .heightIn(max = 460.dp)
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    OutlinedTextField(
                                        value = numeroEmergenciaBorrador,
                                        onValueChange = { numeroEmergenciaBorrador = it.take(18) },
                                        label = { Text("Número de emergencia") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        "Al pulsar Emergencia se abrirá el marcador, sin llamar automáticamente.",
                                        modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                                        fontSize = 12.sp
                                    )
                                    Text("Frecuencia de actualización GPS")
                                    listOf(5, 10, 30).forEach { segundos ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable { intervaloGpsBorrador = segundos },
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            RadioButton(
                                                selected = intervaloGpsBorrador == segundos,
                                                onClick = { intervaloGpsBorrador = segundos }
                                            )
                                            Text("Cada $segundos segundos")
                                        }
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Mantener pantalla encendida", modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = mantenerPantallaBorrador,
                                            onCheckedChange = { mantenerPantallaBorrador = it }
                                        )
                                    }
                                }
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        val numeroLimpio = numeroEmergenciaBorrador.trim()
                                        val digitos = numeroLimpio.count { it.isDigit() }
                                        if (digitos !in 3..15 || numeroLimpio.any { !it.isDigit() && it != '+' }) {
                                            Toast.makeText(context, "Escribe un número de emergencia válido", Toast.LENGTH_SHORT).show()
                                        } else {
                                            numeroEmergencia = numeroLimpio
                                            intervaloGpsSegundos = intervaloGpsBorrador
                                            mantenerPantallaEncendida = mantenerPantallaBorrador
                                            preferencias.edit()
                                                .putString("numero_emergencia", numeroEmergencia)
                                                .putInt("intervalo_gps", intervaloGpsSegundos)
                                                .putBoolean("mantener_pantalla", mantenerPantallaEncendida)
                                                .apply()
                                            mostrarConfiguracion = false
                                            Toast.makeText(context, "Configuración guardada", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                ) {
                                    Text("Guardar")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { mostrarConfiguracion = false }) {
                                    Text("Cancelar")
                                }
                            }
                        )
                    }

                    lugarSeleccionado?.let { lugar ->
                        AlertDialog(
                            onDismissRequest = { lugarSeleccionado = null },
                            title = {
                                val tipoLugar = lugar.transportType ?: lugar.category.label
                                Text("${lugar.category.icon} ${tipoLugar.uppercase(Locale.getDefault())}")
                            },
                            text = {
                                Column {
                                    Text(lugar.name, fontWeight = FontWeight.SemiBold)
                                    Text(lugar.distanceLabel(), modifier = Modifier.padding(top = 8.dp))
                                    Text(
                                        lugar.address ?: "Dirección no disponible en OpenStreetMap",
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            },
                            confirmButton = {
                                Row {
                                    TextButton(
                                        onClick = {
                                            val uri = Uri.parse(
                                                "geo:${lugar.position.latitude},${lugar.position.longitude}?q=${lugar.position.latitude},${lugar.position.longitude}(${Uri.encode(lugar.name)})"
                                            )
                                            val intent = Intent(Intent.ACTION_VIEW, uri)
                                            try {
                                                startActivity(intent)
                                            } catch (_: ActivityNotFoundException) {
                                                Toast.makeText(context, "No hay una aplicación de mapas instalada", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    ) {
                                        Text("IR")
                                    }
                                    lugar.phone?.let { telefono ->
                                        TextButton(
                                            onClick = {
                                                startActivity(
                                                    Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", telefono, null))
                                                )
                                            }
                                        ) {
                                            Text("LLAMAR")
                                        }
                                    }
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { lugarSeleccionado = null }) {
                                    Text("Cerrar")
                                }
                            }
                        )
                    }

                    if (mostrarAcercaDe) {
                        AlertDialog(
                            onDismissRequest = { mostrarAcercaDe = false },
                            title = { Text("Acerca de ¿Dónde Estoy?") },
                            text = {
                                Text(
                                    "Versión ${BuildConfig.VERSION_NAME}\n\n" +
                                        "Aplicación de localización personal para consultar tu posición GPS, " +
                                        "la precisión y la orientación sobre el mapa, y compartirla cuando tú lo decidas.\n\n" +
                                        "Autor y titular: Félix Sánchez Azcona\n" +
                                        "© 2026 Félix Sánchez Azcona. Todos los derechos reservados."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = { mostrarAcercaDe = false }) {
                                    Text("Cerrar")
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapViewForCallbacks?.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapViewForCallbacks?.onLowMemory()
    }
}


private fun crearTextoUbicacion(location: Location): String {
    val latitud = String.format(Locale.US, "%.6f", location.latitude)
    val longitud = String.format(Locale.US, "%.6f", location.longitude)
    val enlaceMapa =
        "https://www.openstreetmap.org/?mlat=$latitud&mlon=$longitud#map=17/$latitud/$longitud"

    return buildString {
        appendLine("📍 Mi posición actual")
        appendLine(
            "Fecha y hora: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date(location.time))}"
        )
        appendLine("Coordenadas: $latitud, $longitud")
        appendLine("Precisión: ±${location.accuracy.roundToInt()} m")
        if (location.hasAltitude()) appendLine("Altitud: ${location.altitude.roundToInt()} m")
        if (location.hasSpeed()) {
            appendLine("Velocidad: ${String.format(Locale.getDefault(), "%.1f", location.speed * 3.6f)} km/h")
        }
        if (location.hasBearing()) appendLine("Rumbo GPS: ${location.bearing.roundToInt()}°")
        append("Mapa: $enlaceMapa")
    }
}


fun crearPuntosCirculo(
    latitud: Double,
    longitud: Double,
    radioMetros: Double
): List<LatLng> {

    val puntos = mutableListOf<LatLng>()

    val radioTierra = 6_378_137.0
    val segmentos = 48

    val latRad = Math.toRadians(latitud)
    val lonRad = Math.toRadians(longitud)
    val distanciaAngular = radioMetros / radioTierra

    for (i in 0 until segmentos) {

        val angulo = 2.0 * Math.PI * i / segmentos

        val lat2 = Math.asin(
            Math.sin(latRad) * Math.cos(distanciaAngular) +
                    Math.cos(latRad) * Math.sin(distanciaAngular) * Math.cos(angulo)
        )

        val lon2 = lonRad + Math.atan2(
            Math.sin(angulo) * Math.sin(distanciaAngular) * Math.cos(latRad),
            Math.cos(distanciaAngular) -
                    Math.sin(latRad) * Math.sin(lat2)
        )

        puntos.add(
            LatLng(
                Math.toDegrees(lat2),
                Math.toDegrees(lon2)
            )
        )
    }

    return puntos
}

@Composable
fun MapaConUbicacion(
    ubicacion: Location?,
    centrarSolicitud: Int,
    lugares: List<NearbyPlace> = emptyList(),
    revisionLugares: Int = 0,
    lifecycle: Lifecycle,
    savedInstanceState: Bundle? = null,
    onMapViewCreated: (MapView?) -> Unit = {},
    onLugarSeleccionado: (NearbyPlace) -> Unit = {}
) {

    var mapaLibre by remember {
        mutableStateOf<MapLibreMap?>(null)
    }

    var marcador by remember {
        mutableStateOf<Marker?>(null)
    }
    var circuloPrecision by remember {
        mutableStateOf<Polygon?>(null)
    }
    var ultimaSolicitudCentrada by remember {
        mutableStateOf(0)
    }
    val marcadoresLugar = remember { mutableMapOf<Marker, NearbyPlace>() }
    var ultimaRevisionLugares by remember { mutableStateOf(-1) }
    val seleccionarLugarActualizado = rememberUpdatedState(onLugarSeleccionado)
    val context = LocalContext.current
    val mapView = remember(context) {
        MapView(context).apply {
            onCreate(savedInstanceState)
            getMapAsync { map ->
                mapaLibre = map
                map.setOnMarkerClickListener { marker ->
                    val lugar = marcadoresLugar[marker]
                    if (lugar != null) {
                        seleccionarLugarActualizado.value(lugar)
                        true
                    } else {
                        false
                    }
                }
                map.setStyle(
                    "https://api.maptiler.com/maps/streets-v4/style.json?key=${BuildConfig.MAPTILER_API_KEY}"
                )
            }
        }
    }

    DisposableEffect(lifecycle, mapView) {
        var started = false
        var resumed = false
        var destroyed = false

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!started) {
                    mapView.onStart()
                    started = true
                }
                Lifecycle.Event.ON_RESUME -> if (!resumed) {
                    mapView.onResume()
                    resumed = true
                }
                Lifecycle.Event.ON_PAUSE -> if (resumed) {
                    mapView.onPause()
                    resumed = false
                }
                Lifecycle.Event.ON_STOP -> if (started) {
                    mapView.onStop()
                    started = false
                }
                Lifecycle.Event.ON_DESTROY -> if (!destroyed) {
                    if (resumed) mapView.onPause()
                    if (started) mapView.onStop()
                    resumed = false
                    started = false
                    mapView.onDestroy()
                    destroyed = true
                }
                else -> Unit
            }
        }

        lifecycle.addObserver(observer)
        onMapViewCreated(mapView)
        onDispose {
            lifecycle.removeObserver(observer)
            if (resumed) mapView.onPause()
            if (started) mapView.onStop()
            if (!destroyed) mapView.onDestroy()
            onMapViewCreated(null)
        }
    }

    AndroidView(
        factory = { mapView },

        update = {

            val map = mapaLibre
            val location = ubicacion

            if (map != null && location != null) {

                val posicion = LatLng(
                    location.latitude,
                    location.longitude
                )

                val puntosCirculo = crearPuntosCirculo(
                    location.latitude,
                    location.longitude,
                    location.accuracy.toDouble()
                )

                if (circuloPrecision == null) {

                    circuloPrecision = map.addPolygon(
                        PolygonOptions()
                            .addAll(puntosCirculo)
                            .fillColor(android.graphics.Color.argb(45, 30, 136, 229))
                            .strokeColor(android.graphics.Color.argb(160, 30, 136, 229))
                    )

                } else {

                    circuloPrecision?.points = puntosCirculo
                }

                if (marcador == null) {

                    marcador = map.addMarker(
                        MarkerOptions()
                            .position(posicion)
                            .title("Mi posición")
                    )

                    map.moveCamera(
                        org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                            posicion,
                            16.0
                        )
                    )

                } else {

                    marcador?.position = posicion

                    if (centrarSolicitud != ultimaSolicitudCentrada) {
                        map.animateCamera(
                            org.maplibre.android.camera.CameraUpdateFactory.newLatLngZoom(
                                posicion,
                                16.0
                            )
                        )
                        ultimaSolicitudCentrada = centrarSolicitud
                    }
                }

                if (revisionLugares != ultimaRevisionLugares) {
                    marcadoresLugar.keys.toList().forEach(map::removeMarker)
                    marcadoresLugar.clear()
                    lugares.forEach { lugar ->
                        val markerLugar = map.addMarker(
                            MarkerOptions()
                                .position(lugar.position)
                                .title(lugar.name)
                                .snippet("${lugar.transportType ?: lugar.category.label} · ${lugar.distanceLabel()}")
                        )
                        marcadoresLugar[markerLugar] = lugar
                    }
                    ultimaRevisionLugares = revisionLugares
                    if (lugares.isNotEmpty()) {
                        val limites = LatLngBounds.Builder()
                            .include(posicion)
                        lugares.forEach { lugar -> limites.include(lugar.position) }

                        val densidad = mapView.resources.displayMetrics.density
                        val altoMapa = mapView.height.coerceAtLeast(1)
                        val margenHorizontal = (24 * densidad).roundToInt()
                        val margenSuperior = minOf(
                            (96 * densidad).roundToInt(),
                            (altoMapa * 0.18f).roundToInt()
                        )
                        val margenInferior = minOf(
                            (300 * densidad).roundToInt(),
                            (altoMapa * 0.38f).roundToInt()
                        )
                        map.animateCamera(
                            org.maplibre.android.camera.CameraUpdateFactory.newLatLngBounds(
                                limites.build(),
                                margenHorizontal,
                                margenSuperior,
                                margenHorizontal,
                                margenInferior
                            )
                        )
                    }
                }
            }
        },

        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun MapaPrueba() {

    AndroidView(
        factory = { context ->

            MapView(context).apply {
                onCreate(null)

                getMapAsync { map ->

                    map.setStyle(
                        "https://api.maptiler.com/maps/streets-v4/style.json?key=${BuildConfig.MAPTILER_API_KEY}"
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
@SuppressLint("MissingPermission")
fun LocalizacionScreen(
    intervaloGpsSegundos: Int = 5,
    referenciaVia: String? = null,
    onLocationChanged: (Location) -> Unit
) {

    val context = LocalContext.current

    // -------------------------------------------------
    // BRÚJULA
    // -------------------------------------------------



    var grados by remember {
        mutableStateOf(0f)
    }

    DisposableEffect(Unit) {

        val compass = CompassSensor(context) { nuevaOrientacion ->
            grados = nuevaOrientacion
        }

        compass.start()

        onDispose {
            compass.stop()
        }
    }

    val direccion = when (grados) {
        in 337.5f..360f, in 0f..22.5f -> "N"
        in 22.5f..67.5f -> "NE"
        in 67.5f..112.5f -> "E"
        in 112.5f..157.5f -> "SE"
        in 157.5f..202.5f -> "S"
        in 202.5f..247.5f -> "SO"
        in 247.5f..292.5f -> "O"
        in 292.5f..337.5f -> "NO"
        else -> "N"
    }


    // -------------------------------------------------
    // PERMISO DE UBICACIÓN
    // -------------------------------------------------

    var tienePermiso by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var ubicacion by remember {
        mutableStateOf<Location?>(null)
    }

    var rumbo by remember {
        mutableStateOf<Float?>(null)
    }

    var velocidadKmh by remember {
        mutableStateOf(0f)
    }

    var enMovimiento by remember {
        mutableStateOf(false)
    }

    val solicitarPermiso =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permisos ->

            tienePermiso =
                permisos[Manifest.permission.ACCESS_FINE_LOCATION] == true
        }


    // -------------------------------------------------
    // SI NO TENEMOS PERMISO
    // -------------------------------------------------

    if (!tienePermiso) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {


            Text(
                text = "Para mostrar tu posición y la vía cercana con precisión, DondeEstoy necesita permiso de ubicación precisa. Si has elegido ubicación aproximada, vuelve a solicitar el permiso y activa «Ubicación precisa» en Android.",
                modifier = Modifier.padding(16.dp)
            )

            Button(
                onClick = {

                    solicitarPermiso.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            ) {

                Text("PERMITIR UBICACIÓN PRECISA")
            }
        }

    } else {

        // -------------------------------------------------
        // GPS
        // -------------------------------------------------

        val locationManager =
            remember {
                context.getSystemService(
                    LocationManager::class.java
                )
            }


        DisposableEffect(intervaloGpsSegundos) {

            val listener = object : LocationListener {

                override fun onLocationChanged(location: Location) {

                    ubicacion = location
                    onLocationChanged(location)
                    // Rumbo GPS
                    rumbo = if (location.hasBearing()) {
                        location.bearing
                    } else {
                        null
                    }

                    // Velocidad GPS: Android la proporciona en m/s.
                    val velocidadDetectadaKmh = if (location.hasSpeed()) {
                        location.speed * 3.6f
                    } else {
                        0f
                    }
                    val umbralPorPrecisionVelocidadKmh =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
                            location.speedAccuracyMetersPerSecond * 3.6f * 1.5f
                        } else {
                            0f
                        }
                    val umbralVelocidadKmh = maxOf(
                        UMBRAL_MOVIMIENTO_KMH,
                        if (location.accuracy > 8f) UMBRAL_GPS_IMPRECISO_KMH else 0f,
                        umbralPorPrecisionVelocidadKmh
                    )
                    velocidadKmh = if (velocidadDetectadaKmh < umbralVelocidadKmh) {
                        0f
                    } else {
                        velocidadDetectadaKmh
                    }

                    // Con precisión baja, filtramos velocidades pequeñas para evitar ruido GPS.
                    enMovimiento = velocidadKmh >= umbralVelocidadKmh
                }
                }

            @SuppressLint("MissingPermission")
            try {

                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    intervaloGpsSegundos * 1000L,
                    1f,
                    listener
                )

            } catch (e: SecurityException) {

                // El permiso se ha perdido.
            }


            onDispose {

                locationManager.removeUpdates(listener)
            }
        }


        // -------------------------------------------------
        // PANTALLA
        // -------------------------------------------------

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            


            Text(
                text = "Orientación: ${grados.roundToInt()}° $direccion",
                modifier = Modifier.padding(14.dp)
            )

            Brújula(grados = grados)

            Spacer(
                modifier = Modifier.weight(1f)
            )

            if (ubicacion == null) {

                Text(
                    text = "Buscando señal GPS...",
                    modifier = Modifier.padding(14.dp)
                )

            } else {
                val precision = ubicacion!!.accuracy
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .offset(y = (-30).dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFC107)
                    )
                ) {

                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {

                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            Text(
                                text = "Lat: ${
                                    String.format(Locale.US, "%.6f", ubicacion!!.latitude)
                                }",
                                modifier = Modifier.weight(1f),
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Text(
                                text = "Lon: ${
                                    String.format(Locale.US, "%.6f", ubicacion!!.longitude)
                                }",
                                modifier = Modifier.weight(1f),
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            Text(
                                text = "Precisión GPS: ±${precision.roundToInt()} m",
                                modifier = Modifier.weight(0.68f),
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                                maxLines = 1,
                                softWrap = false
                            )

                            Text(
                                text = if (ubicacion!!.hasAltitude()) {
                                    "Altitud: ${ubicacion!!.altitude.roundToInt()} m"
                                } else {
                                    "Altitud: —"
                                },
                                modifier = Modifier.weight(0.32f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                softWrap = false,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Start
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (rumbo != null) {
                                    "Rumbo GPS: ${rumbo!!.roundToInt()}°"
                                } else {
                                    "Rumbo GPS: —"
                                },
                                modifier = Modifier.weight(1f),
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )

                            Text(
                                text = if (ubicacion!!.hasSpeed()) {
                                    "Vel: %.1f km/h".format(velocidadKmh)
                                } else {
                                    "Vel: —"
                                },
                                modifier = Modifier.weight(1f),
                                fontSize = 14.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Estado: ",
                                fontSize = 14.sp
                            )

                            Text(
                                text = if (enMovimiento) "EN MOVIMIENTO" else "Parado",
                                fontSize = 13.sp
                            )
                        }

                        Text(
                            text = "📍 Cerca de: ${referenciaVia ?: "Buscando la vía cercana…"}",
                            modifier = Modifier.fillMaxWidth(),
                            fontSize = 12.sp,
                            maxLines = 1,
                            softWrap = false,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
@Composable

fun Brújula(grados: Float) {

    Box(
        modifier = Modifier
            .size(110.dp),
        contentAlignment = Alignment.Center
    ) {

        Canvas(
            modifier = Modifier
                .size(105.dp)
        ) {

            // Escala respecto a la brújula original de 280 px
            val escala = size.minDimension / 280f

            val centroX = size.width / 2
            val centroY = size.height / 2
            val radio = size.minDimension / 2 - (5f * escala)

            // Círculo exterior
            drawCircle(
                color = Color.DarkGray,
                radius = radio,
                style = Stroke(width = 3f * escala)
            )

            // Rosa de los vientos
            rotate(
                degrees = -grados,
                pivot = center
            ) {

                val direcciones = listOf(
                    "N", "E", "S", "O"
                )

                val angulos = listOf(
                    0f, 90f, 180f, 270f
                )

                for (i in direcciones.indices) {

                    val angulo = Math.toRadians(
                        (angulos[i] - 90).toDouble()
                    )

                    val distancia = radio - (30f * escala)

                    val x = centroX +
                            cos(angulo) * distancia

                    val y = centroY +
                            sin(angulo) * distancia

                    drawContext
                        .canvas
                        .nativeCanvas
                        .drawText(
                            direcciones[i],
                            x.toFloat(),
                            y.toFloat() + (8f * escala),
                            android.graphics.Paint().apply {
                                textSize = 24f * escala
                                textAlign =
                                    android.graphics.Paint.Align.CENTER
                                typeface =
                                    android.graphics.Typeface.DEFAULT_BOLD
                            }
                        )
                }

                // Marcas de los grados
                for (i in 0 until 36) {

                    val angulo = Math.toRadians(
                        (i * 10 - 90).toDouble()
                    )

                    val longitud =
                        if (i % 9 == 0) {
                            12f * escala
                        } else {
                            6f * escala
                        }

                    val x1 =
                        centroX +
                                cos(angulo) * (radio - 2f * escala)

                    val y1 =
                        centroY +
                                sin(angulo) * (radio - 2f * escala)

                    val x2 =
                        centroX +
                                cos(angulo) * (radio - longitud)

                    val y2 =
                        centroY +
                                sin(angulo) * (radio - longitud)

                    drawLine(
                        color = Color.DarkGray,
                        start = androidx.compose.ui.geometry.Offset(
                            x1.toFloat(),
                            y1.toFloat()
                        ),
                        end = androidx.compose.ui.geometry.Offset(
                            x2.toFloat(),
                            y2.toFloat()
                        ),
                        strokeWidth = 2f * escala
                    )
                }
            }

            // Flecha que indica hacia dónde apunta el teléfono
            drawLine(
                color = Color.Red,
                start = androidx.compose.ui.geometry.Offset(
                    centroX,
                    centroY + (15f * escala)
                ),
                end = androidx.compose.ui.geometry.Offset(
                    centroX,
                    centroY - (32f * escala)
                ),
                strokeWidth = 5f * escala
            )

            // Punta de la flecha
            val punta = centroY - (38f * escala)

            val izquierda =
                androidx.compose.ui.geometry.Offset(
                    centroX - (9f * escala),
                    centroY - (24f * escala)
                )

            val derecha =
                androidx.compose.ui.geometry.Offset(
                    centroX + (9f * escala),
                    centroY - (24f * escala)
                )

            val puntaOffset =
                androidx.compose.ui.geometry.Offset(
                    centroX,
                    punta
                )

            drawLine(
                color = Color.Red,
                start = puntaOffset,
                end = izquierda,
                strokeWidth = 5f * escala
            )

            drawLine(
                color = Color.Red,
                start = puntaOffset,
                end = derecha,
                strokeWidth = 5f * escala
            )

            // Centro
            drawCircle(
                color = Color.DarkGray,
                radius = 5f * escala,
                center = center
            )
        }
    }
}
