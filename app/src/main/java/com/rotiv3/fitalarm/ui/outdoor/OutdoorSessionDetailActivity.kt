package com.rotiv3.fitalarm.ui.outdoor

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.rotiv3.fitalarm.R
import com.rotiv3.fitalarm.data.local.OutdoorAchievementDao
import com.rotiv3.fitalarm.data.local.OutdoorSessionDao
import com.rotiv3.fitalarm.data.model.OutdoorAchievement
import com.rotiv3.fitalarm.data.model.OutdoorSession
import com.rotiv3.fitalarm.databinding.ActivityOutdoorSessionDetailBinding
import com.rotiv3.fitalarm.location.OutdoorTrackingService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class OutdoorSessionDetailActivity : AppCompatActivity(), OnMapReadyCallback {

    @Inject lateinit var sessionDao: OutdoorSessionDao
    @Inject lateinit var achievementDao: OutdoorAchievementDao

    private lateinit var binding: ActivityOutdoorSessionDetailBinding
    private var googleMap: GoogleMap? = null
    private var session: OutdoorSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOutdoorSessionDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        val eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: ""

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        lifecycleScope.launch {
            val loaded = loadSession(eventId)
            session = loaded
            if (loaded != null) {
                bindStats(loaded)
                googleMap?.let { drawRoute(it, loaded) }
                loadAchievementsWithRetry(loaded.subType)
                // Show FAB once data is ready
                binding.fabShare.visibility = View.VISIBLE
            }
        }

        binding.fabShare.setOnClickListener { shareSession() }
    }

    private suspend fun loadSession(eventId: String): OutdoorSession? {
        repeat(10) {
            val s = sessionDao.getSession(eventId)
            if (s != null) return s
            delay(500)
        }
        return sessionDao.getSession(eventId)
    }

    private fun bindStats(s: OutdoorSession) {
        val emoji = OutdoorSession.subTypeEmoji(s.subType)
        val label = OutdoorSession.subTypeLabel(s.subType)
        binding.toolbar.title = "$emoji $label"
        binding.tvActivityLabel.text = "$emoji ${s.eventTitle}"
        binding.tvDate.text = SimpleDateFormat("EEEE, MMMM d, yyyy • HH:mm", Locale.getDefault())
            .format(Date(s.startTime))
        binding.tvDistance.text = OutdoorTrackingService.formatDistance(s.totalDistanceMeters)
        binding.tvTime.text = OutdoorTrackingService.formatTime(s.durationSeconds)
        binding.tvPace.text = OutdoorTrackingService.calculatePace(s.totalDistanceMeters, s.durationSeconds)
    }

    private suspend fun loadAchievementsWithRetry(subType: String) {
        var unlocked = emptyList<OutdoorAchievement>()
        repeat(6) {
            delay(1000)
            unlocked = achievementDao.getUnlockedByType(subType)
            if (unlocked.isNotEmpty()) return@repeat
        }
        if (unlocked.isEmpty()) return

        binding.tvAchievementsHeader.visibility = View.VISIBLE
        binding.llAchievements.removeAllViews()
        unlocked.forEach { ach -> addAchievementRow(ach) }
    }

    private fun addAchievementRow(ach: OutdoorAchievement) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }
        val emoji = TextView(this).apply {
            text = ach.emoji
            textSize = 22f
            setPadding(0, 0, 12, 0)
        }
        val textBlock = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(this).apply {
            text = ach.title
            textSize = 14f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val desc = TextView(this).apply {
            text = ach.description
            textSize = 12f
            setTextColor(0xFFAAAAAA.toInt())
        }
        textBlock.addView(title)
        textBlock.addView(desc)
        row.addView(emoji)
        row.addView(textBlock)
        binding.llAchievements.addView(row)
    }

    // ─── Share ──────────────────────────────────────────────────────────────────

    private fun shareSession() {
        val s = session ?: return
        val map = googleMap ?: run { shareTextOnly(s); return }

        map.snapshot { bitmap ->
            if (bitmap != null) {
                val imageUri = saveBitmapToCache(bitmap)
                if (imageUri != null) {
                    launchShareIntent(buildShareText(s), imageUri)
                } else {
                    shareTextOnly(s)
                }
            } else {
                shareTextOnly(s)
            }
        }
    }

    private fun buildShareText(s: OutdoorSession): String {
        val emoji = OutdoorSession.subTypeEmoji(s.subType)
        val label = OutdoorSession.subTypeLabel(s.subType)
        return buildString {
            appendLine("$emoji Just completed: ${s.eventTitle}")
            appendLine()
            appendLine("📍 Distance: ${OutdoorTrackingService.formatDistance(s.totalDistanceMeters)}")
            appendLine("⏱ Duration: ${OutdoorTrackingService.formatTime(s.durationSeconds)}")
            appendLine("⚡ Pace: ${OutdoorTrackingService.calculatePace(s.totalDistanceMeters, s.durationSeconds)} /km")
            appendLine()
            append("#FitAlarm #$label #Fitness")
        }
    }

    private fun saveBitmapToCache(bitmap: Bitmap): Uri? {
        return try {
            val dir = File(cacheDir, "shared").also { it.mkdirs() }
            val file = File(dir, "route_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }
            FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        } catch (e: Exception) {
            null
        }
    }

    private fun launchShareIntent(text: String, imageUri: Uri) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_STREAM, imageUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                "Share Activity"
            )
        )
    }

    private fun shareTextOnly(s: OutdoorSession) {
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, buildShareText(s))
                },
                "Share Activity"
            )
        )
    }

    // ─── Map ────────────────────────────────────────────────────────────────────

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = false
        session?.let { drawRoute(map, it) }
    }

    private fun drawRoute(map: GoogleMap, s: OutdoorSession) {
        val latLngs = parseRoute(s.routeJson)
        if (latLngs.isEmpty()) return

        map.addPolyline(
            PolylineOptions()
                .addAll(latLngs)
                .width(8f)
                .color(Color.parseColor("#4CAF50"))
                .geodesic(true)
        )

        map.addMarker(
            MarkerOptions()
                .position(latLngs.first())
                .title("Start")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )

        if (latLngs.size > 1) {
            map.addMarker(
                MarkerOptions()
                    .position(latLngs.last())
                    .title("Finish")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }

        if (latLngs.size == 1) {
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngs.first(), 16f))
        } else {
            val boundsBuilder = LatLngBounds.builder()
            latLngs.forEach { boundsBuilder.include(it) }
            map.moveCamera(CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 80))
        }
    }

    private fun parseRoute(json: String): List<LatLng> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                LatLng(obj.getDouble("lat"), obj.getDouble("lng"))
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        const val EXTRA_EVENT_ID = "extra_event_id"
    }
}
