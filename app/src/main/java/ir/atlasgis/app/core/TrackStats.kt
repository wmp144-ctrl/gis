package ir.atlasgis.app.core

import ir.atlasgis.app.data.Track
import ir.atlasgis.app.data.TrackPoint
import kotlin.math.abs

/** One sample of a track, positioned by cumulative distance along the line. */
data class TrackSample(
    val distanceMeters: Double,
    val elevation: Double?,
    val speedMps: Double?,
    val elapsedSeconds: Long,
)

/** One kilometre of a track (the last one may be shorter). */
data class TrackSplit(
    val index: Int,
    val distanceMeters: Double,
    val durationSeconds: Long,
    val ascentMeters: Double,
    val descentMeters: Double,
) {
    val speedMps: Double get() = if (durationSeconds > 0) distanceMeters / durationSeconds.toDouble() else 0.0
}

data class TrackAnalysis(
    val samples: List<TrackSample>,
    val splits: List<TrackSplit>,
    val totalDistanceMeters: Double,
    val totalSeconds: Long,
    val movingSeconds: Long,
    val stoppedSeconds: Long,
    val averageSpeedMps: Double,
    val averageMovingSpeedMps: Double,
    val ascentMeters: Double,
    val descentMeters: Double,
    val minElevation: Double?,
    val maxElevation: Double?,
) {
    val hasElevation: Boolean
        get() {
            val min = minElevation ?: return false
            val max = maxElevation ?: return false
            return max - min >= 2.0 && samples.count { it.elevation != null } >= 3
        }
}

/**
 * Everything the track detail screen needs, computed from the stored points.
 *
 * Time is taken from the point timestamps when they are usable and spread over the
 * distance otherwise, which is what an imported GPX without `<time>` elements needs.
 */
fun analyzeTrack(track: Track): TrackAnalysis {
    val points = track.points
    if (points.size < 2) {
        return TrackAnalysis(
            samples = emptyList(),
            splits = emptyList(),
            totalDistanceMeters = 0.0,
            totalSeconds = 0,
            movingSeconds = 0,
            stoppedSeconds = 0,
            averageSpeedMps = 0.0,
            averageMovingSpeedMps = 0.0,
            ascentMeters = 0.0,
            descentMeters = 0.0,
            minElevation = null,
            maxElevation = null,
        )
    }

    val distances = DoubleArray(points.size)
    for (i in 1 until points.size) {
        val previous = points[i - 1]
        val current = points[i]
        distances[i] = distances[i - 1] + distanceMeters(
            LatLon(previous.lat, previous.lon),
            LatLon(current.lat, current.lon),
        )
    }
    val totalDistance = distances.last()

    val timestampsUsable = points.first().timestamp > 0 &&
        points.last().timestamp > points.first().timestamp
    val firstTimestamp = points.first().timestamp
    val spanSeconds = if (timestampsUsable) {
        ((points.last().timestamp - firstTimestamp) / 1000).coerceAtLeast(1)
    } else {
        track.durationSeconds.coerceAtLeast(1)
    }

    fun elapsedAt(index: Int): Long {
        if (timestampsUsable) {
            return ((points[index].timestamp - firstTimestamp) / 1000).coerceAtLeast(0)
        }
        return if (totalDistance > 0) {
            (spanSeconds * (distances[index] / totalDistance)).toLong()
        } else {
            0
        }
    }

    val samples = ArrayList<TrackSample>(points.size)
    for (i in points.indices) {
        val previous = if (i > 0) points[i - 1] else null
        val speed = if (previous != null && timestampsUsable) {
            val seconds = ((points[i].timestamp - previous.timestamp) / 1000.0)
            if (seconds in 1.0..120.0) {
                (distances[i] - distances[i - 1]) / seconds
            } else {
                null
            }
        } else {
            null
        }
        samples += TrackSample(
            distanceMeters = distances[i],
            elevation = points[i].elevation,
            speedMps = speed,
            elapsedSeconds = elapsedAt(i),
        )
    }

    var ascent = 0.0
    var descent = 0.0
    var minElevation: Double? = null
    var maxElevation: Double? = null
    for (point in points) {
        val elevation = point.elevation ?: continue
        if (!elevation.isFinite()) continue
        val min = minElevation
        val max = maxElevation
        minElevation = if (min == null) elevation else minOf(min, elevation)
        maxElevation = if (max == null) elevation else maxOf(max, elevation)
    }
    var lastElevation: Double? = null
    for (point in points) {
        val elevation = point.elevation ?: continue
        if (!elevation.isFinite()) continue
        val previous = lastElevation
        if (previous != null) {
            val delta = elevation - previous
            if (abs(delta) >= 1.0) {
                if (delta > 0) ascent += delta else descent += -delta
                lastElevation = elevation
            }
        } else {
            lastElevation = elevation
        }
    }

    val splits = ArrayList<TrackSplit>()
    var splitStartDistance = 0.0
    var splitStartTime = elapsedAt(0)
    var splitAscent = 0.0
    var splitDescent = 0.0
    var lastElevationForSplit: Double? = points[0].elevation
    var splitIndex = 1

    for (i in 1 until points.size) {
        val elevation = points[i].elevation
        val previousElevation = lastElevationForSplit
        if (elevation != null && previousElevation != null) {
            val delta = elevation - previousElevation
            if (abs(delta) >= 1.0) {
                if (delta > 0) splitAscent += delta else splitDescent += -delta
                lastElevationForSplit = elevation
            }
        } else if (elevation != null) {
            lastElevationForSplit = elevation
        }
        val isBoundary = distances[i] >= splitIndex * 1000.0
        val isLast = i == points.size - 1
        if (isBoundary || isLast) {
            val endTime = elapsedAt(i)
            splits += TrackSplit(
                index = splitIndex,
                distanceMeters = distances[i] - splitStartDistance,
                durationSeconds = (endTime - splitStartTime).coerceAtLeast(0),
                ascentMeters = splitAscent,
                descentMeters = splitDescent,
            )
            splitIndex++
            splitStartDistance = distances[i]
            splitStartTime = endTime
            splitAscent = 0.0
            splitDescent = 0.0
            lastElevationForSplit = elevation ?: lastElevationForSplit
        }
    }
    // A boundary landing exactly on the last point would otherwise add an empty
    // extra split with zero distance.
    splits.removeAll { it.distanceMeters < 1.0 && splits.size > 1 }

    val totalSeconds = if (timestampsUsable) {
        ((points.last().timestamp - firstTimestamp) / 1000).coerceAtLeast(0)
    } else {
        track.durationSeconds.coerceAtLeast(0)
    }
    val movingSeconds = track.movingSeconds.takeIf { it > 0 }
        ?: track.durationSeconds.takeIf { it > 0 && track.durationSeconds <= totalSeconds }
        ?: totalSeconds
    val averageSpeed = if (totalSeconds > 0) totalDistance / totalSeconds else 0.0
    val averageMovingSpeed = if (movingSeconds > 0) totalDistance / movingSeconds else 0.0

    return TrackAnalysis(
        samples = samples,
        splits = splits,
        totalDistanceMeters = totalDistance,
        totalSeconds = totalSeconds,
        movingSeconds = movingSeconds,
        stoppedSeconds = (totalSeconds - movingSeconds).coerceAtLeast(0),
        averageSpeedMps = averageSpeed,
        averageMovingSpeedMps = averageMovingSpeed,
        ascentMeters = if (ascent > 0) ascent else track.ascentMeters,
        descentMeters = if (descent > 0) descent else track.descentMeters,
        minElevation = minElevation,
        maxElevation = maxElevation,
    )
}

/** Points reduced to at most [maxPoints] samples, keeping the shape of the profile. */
fun downsample(samples: List<TrackSample>, maxPoints: Int): List<TrackSample> {
    if (samples.size <= maxPoints || maxPoints < 3) return samples
    val step = samples.size / maxPoints.toDouble()
    val result = ArrayList<TrackSample>(maxPoints)
    for (i in 0 until maxPoints) {
        result += samples[(i * step).toInt().coerceIn(0, samples.size - 1)]
    }
    if (result.last() !== samples.last()) result[result.lastIndex] = samples.last()
    return result
}

/** Convenience for the chart: a distance/elevation pair list, empty without elevation. */
fun elevationProfile(analysis: TrackAnalysis): List<Pair<Double, Double>> =
    analysis.samples.mapNotNull { sample ->
        val elevation = sample.elevation ?: return@mapNotNull null
        sample.distanceMeters to elevation
    }

@Suppress("unused")
private fun TrackPoint.coordinate(): LatLon = LatLon(lat, lon)
