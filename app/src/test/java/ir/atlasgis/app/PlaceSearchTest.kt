package ir.atlasgis.app

import ir.atlasgis.app.data.PlaceIndexer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Search must work on Persian names and on the Persian names of place types. */
class PlaceSearchTest {

    @Test
    fun persianTypeWordsResolveToRawTagValues() {
        val hospitals = PlaceIndexer.kindValuesFor("بیمارستان")
        assertTrue("hospital tag expected, got $hospitals", hospitals.contains("hospital"))
        assertTrue("clinic tag expected, got $hospitals", hospitals.contains("clinic"))

        assertTrue(PlaceIndexer.kindValuesFor("نانوایی").contains("bakery"))
        assertTrue(PlaceIndexer.kindValuesFor("پمپ").contains("fuel"))
        assertTrue(PlaceIndexer.kindValuesFor("داروخانه").contains("pharmacy"))
    }

    @Test
    fun unrelatedAndGenericQueriesDoNotExpandToEverything() {
        assertTrue(PlaceIndexer.kindValuesFor("مکان").isEmpty())
        assertTrue(PlaceIndexer.kindValuesFor("xy").isEmpty())
        assertEquals(0, PlaceIndexer.kindValuesFor("ب").size)
    }

    @Test
    fun likeWildcardsAreEscaped() {
        assertEquals("50\\%\\_x\\\\y", PlaceIndexer.escapeLike("50%_x\\y"))
    }

    @Test
    fun normalizeFoldsArabicVariantsAndHalfSpace() {
        val normalized = PlaceIndexer.normalize("مسجد ‌جامع كريمي")
        assertEquals("مسجد جامع کریمی", normalized)
        assertFalse(normalized.contains('\u200C'))
        assertFalse(normalized.contains('ك'))
    }
}
