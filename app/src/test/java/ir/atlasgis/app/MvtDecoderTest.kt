package ir.atlasgis.app

import ir.atlasgis.app.data.MvtDecoder
import ir.atlasgis.app.data.PlaceIndexer
import ir.atlasgis.app.data.PoiClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

/**
 * The search index reads raw Mapbox Vector Tiles, so the wire-format reader and the
 * Persian name folding get their own tests: a silent regression here would leave the
 * search box empty without any error on screen.
 */
class MvtDecoderTest {

    // ------------------------------------------------------------- tile building

    private fun varint(value: Int): ByteArray {
        val out = ByteArrayOutputStream()
        var remaining = value
        while (true) {
            if (remaining and 0x7F.inv() == 0) {
                out.write(remaining)
                return out.toByteArray()
            }
            out.write((remaining and 0x7F) or 0x80)
            remaining = remaining ushr 7
        }
    }

    private fun tag(field: Int, wire: Int): ByteArray = varint((field shl 3) or wire)

    private fun lengthField(field: Int, payload: ByteArray): ByteArray =
        tag(field, 2) + varint(payload.size) + payload

    private fun varintField(field: Int, value: Int): ByteArray = tag(field, 0) + varint(value)

    private fun pack(values: List<Int>): ByteArray {
        val out = ByteArrayOutputStream()
        values.forEach { value -> out.write(varint(value)) }
        return out.toByteArray()
    }

    private fun pointGeometry(x: Int, y: Int): ByteArray =
        pack(listOf((1 shl 3) or 1, (x shl 1) xor (x shr 31), (y shl 1) xor (y shr 31)))

    private fun lineGeometry(x: Int, y: Int): ByteArray =
        pack(listOf((2 shl 3) or 1, (x shl 1) xor (x shr 31), (y shl 1) xor (y shr 31)))

    private fun feature(tags: List<Int>, type: Int, geometry: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(lengthField(2, pack(tags)))
        out.write(varintField(3, type))
        out.write(lengthField(4, geometry))
        return out.toByteArray()
    }

    private fun value(text: String): ByteArray =
        lengthField(1, text.toByteArray(Charsets.UTF_8))

    private fun layer(
        name: String,
        keys: List<String>,
        values: List<String>,
        features: List<ByteArray>,
        extent: Int = 4096,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(lengthField(1, name.toByteArray(Charsets.UTF_8)))
        features.forEach { out.write(lengthField(2, it)) }
        keys.forEach { out.write(lengthField(3, it.toByteArray(Charsets.UTF_8))) }
        values.forEach { out.write(lengthField(4, value(it))) }
        out.write(varintField(5, extent))
        return out.toByteArray()
    }

    // --------------------------------------------------------------------- tests

    @Test
    fun decodesPointFeaturesWithNamesAndTags() {
        val tile = ByteArrayOutputStream().apply {
            write(
                lengthField(
                    3,
                    layer(
                        name = "poi",
                        keys = listOf("name", "class"),
                        values = listOf("پمپ بنزین آزادی", "fuel"),
                        features = listOf(
                            feature(listOf(0, 0, 1, 1), type = 1, geometry = pointGeometry(2048, 1024)),
                        ),
                    ),
                ),
            )
        }.toByteArray()

        val points = MvtDecoder.decode(tile)
        assertEquals(1, points.size)
        val point = points.first()
        assertEquals("poi", point.layer)
        assertEquals("پمپ بنزین آزادی", point.tags["name"])
        assertEquals("fuel", point.tags["class"])
        assertEquals(0.5, point.x, 1e-9)
        assertEquals(0.25, point.y, 1e-9)
    }

    @Test
    fun skipsNonPointGeometry() {
        val tile = lengthField(
            3,
            layer(
                name = "transportation_name",
                keys = listOf("name"),
                values = listOf("خیابان ولیعصر"),
                features = listOf(
                    feature(listOf(0, 0), type = 2, geometry = lineGeometry(100, 100)),
                ),
            ),
        )
        assertTrue(MvtDecoder.decode(tile).isEmpty())
    }

    @Test
    fun unknownLayersDoNotBreakDecoding() {
        val tile = lengthField(
            3,
            layer(
                name = "building",
                keys = listOf("name"),
                values = listOf("برج میلاد"),
                features = listOf(feature(listOf(0, 0), type = 1, geometry = pointGeometry(10, 10))),
            ),
        )
        assertEquals("building", MvtDecoder.decode(tile).single().layer)
        assertEquals(0, MvtDecoder.decode(ByteArray(0)).size)
    }

    @Test
    fun onlyNamingLayersAreIndexed() {
        assertTrue(PlaceIndexer.isIndexableLayer("poi"))
        assertTrue(PlaceIndexer.isIndexableLayer("places"))
        assertTrue(PlaceIndexer.isIndexableLayer("poi_label"))
        assertTrue(PlaceIndexer.isIndexableLayer("water_name"))
        assertTrue(!PlaceIndexer.isIndexableLayer("building"))
        assertTrue(!PlaceIndexer.isIndexableLayer("transportation"))
    }

    @Test
    fun persianNamesFoldConsistently() {
        // Arabic kaf/yeh, ZWNJ and diacritics must not defeat a search.
        assertEquals("میدان ولیعصر", PlaceIndexer.normalize("ميدان وليعصر"))
        assertEquals("میدان ولیعصر", PlaceIndexer.normalize("میدان‌ولیعصر"))
        assertEquals("میدان ولیعصر", PlaceIndexer.normalize("  مِیدان  وَلِیعصر  "))
        assertEquals(PlaceIndexer.normalize("تهران"), PlaceIndexer.normalize("تهران"))
    }

    @Test
    fun poiTagsMapToGoogleLikeIconsAndPersianLabels() {
        assertEquals("fuel", PoiClassifier.keyFor("fuel"))
        assertEquals("fuel", PoiClassifier.keyFor("gas_station"))
        assertEquals("parking", PoiClassifier.keyFor("parking"))
        assertEquals("school", PoiClassifier.keyFor("school"))
        assertEquals("shop", PoiClassifier.keyFor("supermarket"))
        assertEquals("fastfood", PoiClassifier.keyFor("fast_food"))
        assertEquals("worship", PoiClassifier.keyFor("place_of_worship"))
        assertEquals("bus", PoiClassifier.keyFor("bus_stop"))
        assertEquals(PoiClassifier.GENERIC, PoiClassifier.keyFor(null))
        assertEquals("پمپ بنزین", PoiClassifier.labelFor("fuel"))
        assertEquals("پارکینگ", PoiClassifier.labelFor("parking"))
        assertEquals("atlas-fuel", PoiClassifier.imageId("fuel"))
    }
}
