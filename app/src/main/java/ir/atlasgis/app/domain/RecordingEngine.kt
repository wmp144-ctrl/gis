package ir.atlasgis.app.domain

import android.app.Application
import android.content.Context
import ir.atlasgis.app.core.LatLon
import ir.atlasgis.app.data.AppDatabase
import ir.atlasgis.app.data.GpsFix
import ir.atlasgis.app.data.LocationProvider
import ir.atlasgis.app.data.RecordingState
import ir.atlasgis.app.data.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Single owner of the GPS fix stream and of the track being recorded.
 *
 * The engine deliberately lives outside the ViewModel: a recording must survive the
 * activity being destroyed (screen off, app in background) and is kept alive by
 * [RecordingService], which observes the same flows. Everything here runs on the
 * application context, so no Activity or ViewModel can leak through it.
 */
class RecordingEngine private constructor(private val app: Application) {

    private val db = AppDatabase(app)
    private val location = LocationProvider(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _gps = MutableStateFlow<GpsFix?>(null)
    val gps: StateFlow<GpsFix?> = _gps.asStateFlow()

    private val _recording = MutableStateFlow(RecordingState())
    val recording: StateFlow<RecordingState> = _recording.asStateFlow()

    private val _livePoints = MutableStateFlow<List<LatLon>>(emptyList())
    val livePoints: StateFlow<List<LatLon>> = _livePoints.asStateFlow()

    /** Non-fatal status text for the UI (started, saved, discarded, ...). */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    /** Emitted after a finished track has been written to the database. */
    private val _savedTracks = MutableSharedFlow<Unit>(extraBufferCapacity = 2)
    val savedTracks: SharedFlow<Unit> = _savedTracks

    private var session: TrackSession? = null
    private var ticker: Job? = null
    private var observing = false
    private var lastPersistedAt = 0L
    private var lastLivePushAt = 0L

    val isActive: Boolean get() = session != null

    fun hasPermission(): Boolean = location.hasPermission()

    fun hasPrecisePermission(): Boolean = location.hasPrecisePermission()

    fun locationServicesEnabled(): Boolean = location.locationServicesEnabled()

    /** Starts location updates; repeated calls are harmless. */
    fun startLocation() {
        ensureObserving()
        location.start()
    }

    /**
     * Stops the GPS only when nothing is recording: while a session is active the
     * puck must keep moving even if the screen is paused.
     */
    fun stopLocationIfIdle() {
        if (session == null) location.stop()
    }

    private fun ensureObserving() {
        if (observing) return
        observing = true
        scope.launch {
            location.fix.collect { fix ->
                _gps.value = fix
                if (fix == null) return@collect
                val current = session ?: return@collect
                if (current.paused) return@collect
                if (!current.addFix(fix)) return@collect
                val now = System.currentTimeMillis()
                _recording.value = current.snapshot()
                if (now - lastLivePushAt >= 900L) {
                    lastLivePushAt = now
                    _livePoints.value = current.pointsForLivePreview()
                }
                if (now - lastPersistedAt >= 15_000L) {
                    lastPersistedAt = now
                    persist(current)
                }
            }
        }
        scope.launch {
            location.enabled.collect { enabled ->
                if (!enabled && session != null) {
                    _messages.tryEmit("GPS دستگاه خاموش است؛ پس از روشن‌شدن، ثبت ادامه می‌یابد")
                }
            }
        }
    }

    /** @return false when a recording is already running. */
    fun start(): Boolean {
        if (session != null) return false
        ensureObserving()
        val newSession = TrackSession(
            id = AppDatabase.newId(),
            name = TrackSession.newName(),
            startedAt = System.currentTimeMillis(),
        )
        session = newSession
        lastPersistedAt = System.currentTimeMillis()
        lastLivePushAt = 0L
        _livePoints.value = emptyList()
        _recording.value = newSession.snapshot()
        location.start()
        ticker?.cancel()
        ticker = scope.launch {
            // Cancellation is delivered by delay(); a `while (true)` is intentional
            // here because `isActive` would resolve to the engine's own property.
            while (true) {
                delay(1_000)
                session?.let { _recording.value = it.snapshot() }
            }
        }
        _messages.tryEmit("ثبت مسیر آغاز شد؛ با خاموش‌شدن صفحه هم ادامه می‌یابد")
        return true
    }

    fun pause(): Boolean {
        val current = session ?: return false
        current.pause()
        _recording.value = current.snapshot()
        persist(current)
        _messages.tryEmit("ثبت مسیر موقتاً متوقف شد")
        return true
    }

    fun resume(): Boolean {
        val current = session ?: return false
        current.resume()
        _recording.value = current.snapshot()
        return true
    }

    /**
     * Finalises the recording. A track with fewer than two points is discarded
     * together with the rows the 15-second auto-save may already have written.
     */
    fun stop(): Track? {
        val current = session ?: return null
        session = null
        ticker?.cancel()
        ticker = null
        if (current.pointCount < 2) {
            _recording.value = RecordingState()
            _livePoints.value = emptyList()
            scope.launch(Dispatchers.IO) { db.deleteTrack(current.id) }
            _messages.tryEmit("برای ذخیره مسیر، حداقل دو نقطه لازم است")
            return null
        }
        val track = current.toTrack(recording = false)
        _recording.value = RecordingState()
        _livePoints.value = emptyList()
        scope.launch {
            withContext(Dispatchers.IO) { db.upsertTrack(track) }
            _savedTracks.tryEmit(Unit)
            _messages.tryEmit("مسیر با ${track.points.size} نقطه ذخیره شد")
        }
        return track
    }

    /** Keeps the just-finished track when the process is going away. */
    fun flushBeforeExit() {
        val current = session ?: return
        if (current.pointCount >= 2) {
            runCatching { db.upsertTrack(current.toTrack(recording = false)) }
        }
    }

    private fun persist(current: TrackSession) {
        scope.launch(Dispatchers.IO) {
            // Stop may have finalised (or discarded) this session while the write was
            // queued; never overwrite the final row with a stale recording snapshot.
            if (session === current) db.upsertTrack(current.toTrack(recording = true))
        }
    }

    companion object {
        @Volatile
        private var instance: RecordingEngine? = null

        fun get(context: Context): RecordingEngine =
            instance ?: synchronized(this) {
                instance ?: RecordingEngine(context.applicationContext as Application)
                    .also { instance = it }
            }
    }
}
