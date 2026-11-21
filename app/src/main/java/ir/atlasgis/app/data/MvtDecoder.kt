package ir.atlasgis.app.data

/**
 * Minimal Mapbox Vector Tile reader used to build the offline place index.
 *
 * Only what the index needs is decoded: string properties and point geometry of
 * each feature. That keeps the reader dependency-free and fast — a z15 POI tile
 * is parsed in well under a millisecond.
 */
object MvtDecoder {

    /** A point feature of one tile, with coordinates normalised to 0..1 inside the tile. */
    data class Point(val layer: String, val tags: Map<String, String>, val x: Double, val y: Double)

    /**
     * Layer names declared by one tile, without decoding a single feature.
     *
     * Archives that ship no `vector_layers` metadata still name their layers inside
     * every tile; this is how the style learns about `poi`, `transportation_name` or
     * any other layer an unusual schema uses.
     */
    fun layerNames(bytes: ByteArray): List<String> {
        val reader = Reader(bytes)
        val names = ArrayList<String>(16)
        while (reader.hasMore()) {
            val key = reader.readVarint()
            val field = (key ushr 3).toInt()
            val wire = (key and 0x7).toInt()
            if (field == 3 && wire == 2) {
                readLayerName(reader.readBytes())?.let { names.add(it) }
            } else {
                reader.skip(wire)
            }
        }
        return names
    }

    private fun readLayerName(bytes: ByteArray): String? {
        val reader = Reader(bytes)
        while (reader.hasMore()) {
            val key = reader.readVarint()
            val field = (key ushr 3).toInt()
            val wire = (key and 0x7).toInt()
            if (field == 1 && wire == 2) return String(reader.readBytes(), Charsets.UTF_8)
            reader.skip(wire)
        }
        return null
    }

    fun decode(bytes: ByteArray): List<Point> {
        val reader = Reader(bytes)
        val result = ArrayList<Point>(64)
        while (reader.hasMore()) {
            val key = reader.readVarint()
            val field = (key ushr 3).toInt()
            val wire = (key and 0x7).toInt()
            if (field == 3 && wire == 2) {
                readLayer(reader.readBytes(), result)
            } else {
                reader.skip(wire)
            }
        }
        return result
    }

    private class RawFeature(val tags: IntArray, val type: Int, val geometry: IntArray)

    private fun readLayer(bytes: ByteArray, result: MutableList<Point>) {
        val reader = Reader(bytes)
        var name = ""
        var extent = 4096
        val rawFeatures = ArrayList<RawFeature>(32)
        val keys = ArrayList<String>(32)
        val values = ArrayList<String>(32)

        while (reader.hasMore()) {
            val key = reader.readVarint()
            val field = (key ushr 3).toInt()
            val wire = (key and 0x7).toInt()
            when {
                field == 1 && wire == 2 -> name = String(reader.readBytes(), Charsets.UTF_8)
                field == 2 && wire == 2 -> rawFeatures.add(readFeature(reader.readBytes()))
                field == 3 && wire == 2 -> keys.add(String(reader.readBytes(), Charsets.UTF_8))
                field == 4 && wire == 2 -> values.add(readValue(reader.readBytes()))
                field == 5 && wire == 0 -> extent = reader.readVarint().toInt().takeIf { it > 0 } ?: 4096
                else -> reader.skip(wire)
            }
        }
        if (name.isEmpty() || rawFeatures.isEmpty()) return

        rawFeatures.forEach { feature ->
            if (feature.type != TYPE_POINT) return@forEach
            val point = decodePoint(feature.geometry) ?: return@forEach
            val tags = LinkedHashMap<String, String>(feature.tags.size / 2)
            var i = 0
            while (i + 1 < feature.tags.size) {
                val keyIndex = feature.tags[i]
                val valueIndex = feature.tags[i + 1]
                if (keyIndex in keys.indices && valueIndex in values.indices) {
                    tags[keys[keyIndex]] = values[valueIndex]
                }
                i += 2
            }
            result.add(
                Point(
                    layer = name,
                    tags = tags,
                    x = (point[0] / extent.toDouble()).coerceIn(0.0, 1.0),
                    y = (point[1] / extent.toDouble()).coerceIn(0.0, 1.0),
                ),
            )
        }
    }

    private fun readFeature(bytes: ByteArray): RawFeature {
        val reader = Reader(bytes)
        var tags = IntArray(0)
        var type = 0
        var geometry = IntArray(0)
        while (reader.hasMore()) {
            val key = reader.readVarint()
            val field = (key ushr 3).toInt()
            val wire = (key and 0x7).toInt()
            when {
                field == 2 && wire == 2 -> tags = readPackedUInt32(reader)
                field == 3 && wire == 0 -> type = reader.readVarint().toInt()
                field == 4 && wire == 2 -> geometry = readPackedUInt32(reader)
                else -> reader.skip(wire)
            }
        }
        return RawFeature(tags, type, geometry)
    }

    private fun readValue(bytes: ByteArray): String {
        val reader = Reader(bytes)
        while (reader.hasMore()) {
            val key = reader.readVarint()
            val field = (key ushr 3).toInt()
            val wire = (key and 0x7).toInt()
            when {
                field == 1 && wire == 2 -> return String(reader.readBytes(), Charsets.UTF_8)
                field == 2 && wire == 5 -> return trimNumber(Float.fromBits(reader.readFixed32().toInt()))
                field == 3 && wire == 1 -> return trimNumber(Double.fromBits(reader.readFixed64()))
                field == 4 && wire == 0 -> return reader.readVarint().toString()
                field == 5 && wire == 0 -> return reader.readVarint().toString()
                field == 6 && wire == 0 -> {
                    val raw = reader.readVarint()
                    return ((raw ushr 1) xor -(raw and 1)).toString()
                }
                field == 7 && wire == 0 -> return if (reader.readVarint() != 0L) "true" else "false"
                else -> reader.skip(wire)
            }
        }
        return ""
    }

    private fun trimNumber(value: Double): String =
        if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

    private fun trimNumber(value: Float): String = trimNumber(value.toDouble())

    private fun readPackedUInt32(reader: Reader): IntArray {
        val bytes = reader.readBytes()
        val packed = Reader(bytes)
        val values = ArrayList<Int>(8)
        while (packed.hasMore()) values.add(packed.readVarint().toInt())
        return values.toIntArray()
    }

    /** First MoveTo point of a point geometry; null when the geometry is not a point. */
    private fun decodePoint(geometry: IntArray): DoubleArray? {
        if (geometry.isEmpty()) return null
        var index = 0
        var x = 0.0
        var y = 0.0
        var first = true
        while (index < geometry.size) {
            val command = geometry[index]
            val id = command and 0x7
            val count = command ushr 3
            index++
            if (id == CMD_CLOSE_PATH) continue
            for (i in 0 until count) {
                if (index + 1 >= geometry.size) return null
                val dx = zigzag(geometry[index])
                val dy = zigzag(geometry[index + 1])
                index += 2
                x += dx
                y += dy
                if (first && id == CMD_MOVE_TO) {
                    first = false
                    return doubleArrayOf(x, y)
                }
            }
        }
        return null
    }

    private fun zigzag(value: Int): Int = (value ushr 1) xor -(value and 1)

    private const val TYPE_POINT = 1
    private const val CMD_MOVE_TO = 1
    private const val CMD_CLOSE_PATH = 7

    private class Reader(private val buffer: ByteArray) {
        private var position = 0

        fun hasMore(): Boolean = position < buffer.size

        fun readVarint(): Long {
            var result = 0L
            var shift = 0
            while (position < buffer.size) {
                val byte = buffer[position++].toInt() and 0xFF
                result = result or ((byte and 0x7F).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
                if (shift > 63) break
            }
            return result
        }

        fun readFixed32(): Long {
            var result = 0L
            for (i in 0 until 4) {
                if (position >= buffer.size) return result
                result = result or ((buffer[position++].toLong() and 0xFF) shl (8 * i))
            }
            return result
        }

        fun readFixed64(): Long {
            var result = 0L
            for (i in 0 until 8) {
                if (position >= buffer.size) return result
                result = result or ((buffer[position++].toLong() and 0xFF) shl (8 * i))
            }
            return result
        }

        fun readBytes(): ByteArray {
            val length = readVarint().toInt()
            if (length <= 0) return ByteArray(0)
            val end = (position + length).coerceAtMost(buffer.size)
            val slice = buffer.copyOfRange(position, end)
            position = end
            return slice
        }

        fun skip(wireType: Int) {
            when (wireType) {
                0 -> readVarint()
                1 -> position += 8
                2 -> {
                    val length = readVarint().toInt()
                    position += length
                }
                5 -> position += 4
                else -> position = buffer.size
            }
        }
    }
}
