package ir.atlasgis.app.data

/**
 * Colour palette for the automatic vector style. Vector MBTiles carry geometry
 * only, so the app paints them; these presets keep that painting readable in
 * different light conditions without loading any external style file.
 */
data class MapPalette(
    val background: String,
    val water: String,
    val waterDeep: String,
    val waterway: String,
    val green: String,
    val forest: String,
    val park: String,
    val ice: String,
    val building: String,
    val buildingOutline: String,
    val roadCasing: String,
    val roadMotorway: String,
    val roadTrunk: String,
    val roadPrimary: String,
    val roadSecondary: String,
    val roadTertiary: String,
    val roadMinor: String,
    val roadService: String,
    val rail: String,
    val boundary: String,
    val poi: String,
    val unknownLand: String,
    val unknownOutline: String,
)

/**
 * Built-in map styles. `key` is what gets persisted in SQLite, so it must stay
 * stable even if the display order or the colours change later.
 */
enum class MapStylePreset(
    val key: String,
    val label: String,
    val description: String,
    val palette: MapPalette,
    /** ARGB swatches used by the picker: base, water, highlight. */
    val preview: List<Long>,
) {
    NIGHT(
        key = "night",
        label = "شبانه",
        description = "پس‌زمینهٔ سرمه‌ای با آب فیروزه‌ای و جاده‌های روشن؛ کم‌ترین خیرگی در شب",
        palette = MapPalette(
            background = "#0B1220",
            water = "#123C5B",
            waterDeep = "#0E3350",
            waterway = "#2C6E9B",
            green = "#163327",
            forest = "#112C1D",
            park = "#1B4A31",
            ice = "#2A4356",
            building = "#26313F",
            buildingOutline = "#3A4B61",
            roadCasing = "#0A1119",
            roadMotorway = "#F0B967",
            roadTrunk = "#E3AE6A",
            roadPrimary = "#C9D6E4",
            roadSecondary = "#B0C0D2",
            roadTertiary = "#9FB0C4",
            roadMinor = "#8FA2B8",
            roadService = "#6F7F93",
            rail = "#64748B",
            boundary = "#5E7896",
            poi = "#3ED598",
            unknownLand = "#1B2A3B",
            unknownOutline = "#3A4C63",
        ),
        preview = listOf(0xFF0B1220, 0xFF123C5B, 0xFF3ED598),
    ),
    LIGHT(
        key = "light",
        label = "روشن",
        description = "کاغذی و تمیز با جاده‌های سفید؛ خوانا در نور روز و فضای باز",
        palette = MapPalette(
            background = "#F4F6F8",
            water = "#C7E2F5",
            waterDeep = "#B7D9F0",
            waterway = "#93C5E8",
            green = "#DFEEDA",
            forest = "#CBDFC0",
            park = "#D5E8CC",
            ice = "#E8F1F6",
            building = "#E4E8EE",
            buildingOutline = "#D3DAE3",
            roadCasing = "#C9D2DC",
            roadMotorway = "#F6C87A",
            roadTrunk = "#F2BE7C",
            roadPrimary = "#FFFFFF",
            roadSecondary = "#FBFCFD",
            roadTertiary = "#FFFFFF",
            roadMinor = "#F7F8FA",
            roadService = "#ECEFF3",
            rail = "#A6B2C0",
            boundary = "#98A6B8",
            poi = "#1F9C74",
            unknownLand = "#EDF1F4",
            unknownOutline = "#D5DDE5",
        ),
        preview = listOf(0xFFF4F6F8, 0xFFC7E2F5, 0xFF1F9C74),
    ),
    OUTDOOR(
        key = "outdoor",
        label = "طبیعت‌گردی",
        description = "پس‌زمینهٔ خاکی و سبزهای طبیعی؛ مناسب کوه‌پیمایی و مسیرهای خارج از شهر",
        palette = MapPalette(
            background = "#F3EFE4",
            water = "#BCDAE7",
            waterDeep = "#A9CFDF",
            waterway = "#7FB6D0",
            green = "#DDE6C2",
            forest = "#C6D8A6",
            park = "#D3E3B4",
            ice = "#E9F0EA",
            building = "#E6DDCB",
            buildingOutline = "#D3C7B0",
            roadCasing = "#C9BFA8",
            roadMotorway = "#E8A657",
            roadTrunk = "#DFA45F",
            roadPrimary = "#FFFFFF",
            roadSecondary = "#FCFAF4",
            roadTertiary = "#FFFDF8",
            roadMinor = "#F7F3E9",
            roadService = "#EFE9DB",
            rail = "#8C8271",
            boundary = "#A89C82",
            poi = "#C2703A",
            unknownLand = "#EDE7D8",
            unknownOutline = "#D8CEB8",
        ),
        preview = listOf(0xFFF3EFE4, 0xFFBCDAE7, 0xFF6B8F4E),
    ),
    CONTRAST(
        key = "contrast",
        label = "کنتراست بالا",
        description = "سفید مطلق با خطوط پرکنتراست؛ مناسب آفتاب شدید و کم‌بینایی",
        palette = MapPalette(
            background = "#FFFFFF",
            water = "#93C6EA",
            waterDeep = "#7CB6E0",
            waterway = "#4E93C4",
            green = "#D7EBD3",
            forest = "#BFDFB8",
            park = "#CBE5C6",
            ice = "#E6F0F5",
            building = "#E9E9E9",
            buildingOutline = "#8A8A8A",
            roadCasing = "#8C8C8C",
            roadMotorway = "#E09B1F",
            roadTrunk = "#D8961F",
            roadPrimary = "#FFFFFF",
            roadSecondary = "#FFFFFF",
            roadTertiary = "#FFFFFF",
            roadMinor = "#F4F4F4",
            roadService = "#EFEFEF",
            rail = "#5A5A5A",
            boundary = "#4A4A4A",
            poi = "#0B6E4F",
            unknownLand = "#F2F2F2",
            unknownOutline = "#B0B0B0",
        ),
        preview = listOf(0xFFFFFFFF, 0xFF93C6EA, 0xFF0B6E4F),
    ),
    ;

    val isLight: Boolean
        get() = this == LIGHT || this == OUTDOOR || this == CONTRAST

    companion object {
        val DEFAULT = NIGHT

        fun fromKey(key: String?): MapStylePreset =
            entries.firstOrNull { it.key == key } ?: DEFAULT
    }
}
