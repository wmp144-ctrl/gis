package ir.atlasgis.app.domain

import ir.atlasgis.app.core.LatLon
import ir.atlasgis.app.core.distanceMeters
import ir.atlasgis.app.core.newId
import ir.atlasgis.app.data.GpsFix
import ir.atlasgis.app.data.RecordingState
import ir.atlasgis.app.data.Track
import ir.atlasgis.app.data.TrackPoint
import kotlin.math.abs

/**
 * Accumulates GPS fixes into a track. Jitter is rejected at the source: a fix must be
 * reasonably accurate, newer than the last one, and either moved far enough or arrive
 * after a long enough pause — this keeps recorded distance honest while walking slowly.
 */
class TrackSession(
    val id: String,
    val name: String,
    val startedAt: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val points = ArrayList<TrackPoint>()
    private var distance = 0.0
    private var ascent = 0.0
    private var descent = 0.0
    private var maxSpeed = 0.0
    private var currentSpeed = 0.0
    private var lastElevation: Double? = null
    private var lastTimestamp = 0L
    private var lastMovementAt = startedAt

    var paused = false
        private set
    private var pausedAccumMs = 0L
    private var pauseStartedAt = 0L

    val pointCount: Int get() = points.size
    val distanceMeters: Double get() = distance

    fun pause() {
        if (paused) return
        paused = true
        pauseStartedAt = now()
    }

    fun resume() {
        if (!paused) return
        paused = false
        if (pauseStartedAt > 0) {
            pausedAccumMs += (now() - pauseStartedAt).coerceAtLeast(0)
            pauseStartedAt = 0
        }
    }

    fun elapsedSeconds(at: Long = now()): Long {
        val effectiveNow = if (paused && pauseStartedAt > 0) pauseStartedAt else at
        val raw = effectiveNow - startedAt - pausedAccumMs
        return (raw / 1000).coerceAtLeast(0)
    }

    /** @return true when the fix was accepted into the track. */
    fun addFix(fix: GpsFix): Boolean {
        if (paused) return false
        if (fix.accuracyMeters > 60f) return false
        if (fix.timestamp > 0 && fix.timestamp <= lastTimestamp) return false

        val point = LatLon(fix.lat, fix.lon)
        val previous = points.lastOrNull()
        var stepDistance = 0.0
        if (previous != null) {
            stepDistance = distanceMeters(LatLon(previous.lat, previous.lon), point)
            val elapsed = (fix.timestamp - previous.timestamp).coerceAtLeast(0)
            val movedEnough = stepDistance >= 2.0
            val longPause = elapsed >= 6_000 && stepDistance >= 0.8
            if (!movedEnough && !longPause) {
                // Still update elevation drift-free statistics only when position is accepted.
                return false
            }
            if (elapsed in 1..60_000) {
                val speed = stepDistance / (elapsed / 1000.0)
                // A GPS outlier can jump kilometres in a second. Rejecting it keeps
                // both the drawn line and the saved distance honest.
                if (!speed.isFinite() || speed > MAX_PLAUSIBLE_SPEED_MPS) return false
                currentSpeed = speed
                if (speed > maxSpeed) maxSpeed = speed
            }
            distance += stepDistance
            lastMovementAt = now()
        } else {
            currentSpeed = fix.speedMps?.toDouble()?.takeIf { it.isFinite() && it >= 0f } ?: 0.0
        }

        val elevation = fix.altitude
        if (elevation != null && elevation.isFinite()) {
            val previousElevation = lastElevation
            if (previousElevation != null) {
                val delta = elevation - previousElevation
                if (abs(delta) >= 1.0) {
                    if (delta > 0) ascent += delta else descent += -delta
                    lastElevation = elevation
                }
            } else {
                lastElevation = elevation
            }
        }

        points.add(
            TrackPoint(
                lat = fix.lat,
                lon = fix.lon,
                elevation = elevation?.takeIf { it.isFinite() },
                timestamp = if (fix.timestamp > 0) fix.timestamp else now(),
            ),
        )
        lastTimestamp = fix.timestamp
        return true
    }

    fun elapsedSinceLastMovement(): Long = (now() - lastMovementAt).coerceAtLeast(0)

    fun snapshot(): RecordingState = RecordingState(
        active = true,
        paused = paused,
        trackId = id,
        name = name,
        startedAt = startedAt,
        distanceMeters = distance,
        movingSeconds = elapsedSeconds(),
        ascentMeters = ascent,
        descentMeters = descent,
        maxSpeedMps = maxSpeed,
        currentSpeedMps = if (paused) 0.0 else currentSpeed,
        pointCount = points.size,
    )

    fun toTrack(recording: Boolean): Track {
        val ended = now()
        return Track(
            id = id,
            name = name,
            points = points.toList(),
            distanceMeters = distance,
            // Wall-clock duration includes pauses; moving time does not. Storing both
            // is what makes "stopped for 40 minutes" visible on the track card.
            durationSeconds = ((ended - startedAt) / 1000).coerceAtLeast(0),
            startedAt = startedAt,
            endedAt = ended,
            ascentMeters = ascent,
            descentMeters = descent,
            maxSpeedMps = maxSpeed,
            color = "#FF9F45",
            isRecording = recording,
            movingSeconds = elapsedSeconds(ended),
        )
    }

    fun pointsForLivePreview(): List<LatLon> =
        if (points.size >= 2) points.map { LatLon(it.lat, it.lon) } else emptyList()

    companion object {
        /** ~252 km/h: generous for a phone, impossible for a GPS jump. */
        private const val MAX_PLAUSIBLE_SPEED_MPS = 70.0

        fun newName(): String {
            val stamp = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale.US)
                .format(java.util.Date())
            return "مسیر $stamp"
        }

        fun emptyTrack(): Track = Track(
            id = newId(),
            name = "",
            points = emptyList(),
            distanceMeters = 0.0,
            durationSeconds = 0,
            startedAt = 0,
            endedAt = 0,
            ascentMeters = 0.0,
            descentMeters = 0.0,
            maxSpeedMps = 0.0,
            color = "#FF9F45",
            isRecording = false,
        )
    }
}
