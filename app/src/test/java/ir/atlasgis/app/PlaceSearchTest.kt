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
    fun aParkSearchDoesNotTurnIntoParkingOrViceVersa() {
        val park = PlaceIndexer.kindValuesFor("پارک")
        assertTrue("park tag expected, got $park", park.contains("park"))
        assertFalse("parking must not match پارک, got $park", park.contains("parking"))

        val parking = PlaceIndexer.kindValuesFor("پارکینگ")
        assertTrue("parking tag expected, got $parking", parking.contains("parking"))
    }

    @Test
    fun typeSearchAlsoFoldsAlefVariants() {
        assertTrue(PlaceIndexer.kindValuesFor("اب").contains("water"))
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

    @Test
    fun normalizeFoldsAlefVariantsSoTypedAlefFindsWrittenAlef() {
        // People commonly type «اب» for a place written «آب».
        assertEquals(PlaceIndexer.normalize("آب"), PlaceIndexer.normalize("اب"))
        assertTrue(PlaceIndexer.normalize("آب انبار").contains(PlaceIndexer.normalize("اب")))
        assertEquals("احمد", PlaceIndexer.normalize("أحمد"))
        assertEquals("اسماعیل", PlaceIndexer.normalize("إسماعيل"))
    }
}
