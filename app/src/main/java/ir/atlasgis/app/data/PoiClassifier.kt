package ir.atlasgis.app.data

/**
 * One vocabulary for every POI tag scheme found in offline vector tiles:
 * OpenMapTiles (`subclass` / `class`), Shortbread (`amenity`, `shop`, `tourism`,
 * `leisure`), Mapbox Streets (`maki`, `type`) and plain OSM tags.
 *
 * Each raw tag maps to a compact icon key. That key names the runtime symbol image
 * (`atlas-<key>`) in [MapSymbolIcons], the Persian caption in [labels] and the
 * Compose icon used by the search sheet, so a fuel station looks the same
 * everywhere in the app.
 */
object PoiClassifier {

    const val GENERIC = "generic"

    /** Canonical tags (lower case, ASCII OSM values) mapped to an icon key. */
    val tags: Map<String, String> = buildMap {
        // Named helper: `put(alias, key)` inside a local `put` would recurse.
        fun register(key: String, vararg aliases: String) {
            aliases.forEach { alias -> this.put(alias, key) }
        }
        register("fuel", "fuel", "gas_station", "petrol_station", "filling_station", "gasoline", "gas")
        register("charging", "charging_station", "ev_charging", "ev_station", "charging", "electric_vehicle_charging")
        register("parking", "parking", "parking_entrance", "parking_space", "parking_lot", "garage_entrance")
        register("bicycle_parking", "bicycle_parking", "bike_parking")
        register(
            "shop", "shop", "supermarket", "grocery", "convenience", "greengrocer", "department_store",
            "mall", "kiosk", "general", "wholesale", "variety_store", "shopping", "shopping_centre",
            "shopping_center", "marketplace_shop", "retail",
        )
        register("market", "marketplace", "market", "bazaar", "farmers_market")
        register("bakery", "bakery", "pastry", "confectionery", "cake", "dessert")
        register("butcher", "butcher", "seafood", "fishmonger", "deli", "deli_counter")
        register("cafe", "cafe", "coffee", "coffee_shop", "tea", "tea_house", "cafeteria", "juice")
        register("restaurant", "restaurant", "food_court", "dining", "bistro", "food_and_drink", "food")
        register("fastfood", "fast_food", "fastfood", "burger", "pizza", "sandwich")
        register("bar", "bar", "pub", "beer", "beer_garden", "nightclub", "nightlife", "biergarten")
        register("icecream", "ice_cream", "icecream", "gelato", "frozen_yogurt")
        register("alcohol", "alcohol", "wine", "liquor", "beverages", "wine_shop", "spirits")
        register("florist", "florist", "flowers", "garden_centre", "garden_center", "nursery")
        register("hardware", "hardware", "doityourself", "diy", "tools", "paint", "building_materials")
        register("laundry", "laundry", "dry_cleaning", "laundromat", "tailor", "clothes", "fashion", "shoes")
        register(
            "electronics", "electronics", "mobile_phone", "computer", "hifi", "appliance",
            "furniture", "interior_decoration", "household", "books", "stationery", "gift",
            "jewelry", "jewellery", "photo", "sports_shop", "outdoor", "bicycle",
        )
        register(
            "hospital", "hospital", "clinic", "doctors", "doctor", "dentist", "medical_services",
            "health", "urgent_care", "health_care", "medical", "pharmacy_dispensary",
        )
        register("pharmacy", "pharmacy", "chemist", "drugstore", "medicine")
        register("veterinary", "veterinary", "vet", "animal_boarding", "animal_shelter")
        register(
            "school", "school", "kindergarten", "college", "driving_school", "language_school",
            "music_school", "education", "training", "childcare",
        )
        register("university", "university", "campus", "academy")
        register("library", "library", "public_library")
        register("museum", "museum", "gallery", "art_gallery", "arts_centre", "heritage")
        register(
            "attraction", "attraction", "artwork", "monument", "memorial", "tourist_attraction",
            "landmark", "tourism", "tourist", "zoo", "theme_park", "aquarium",
        )
        register("cinema", "cinema", "theatre", "theater", "music_venue", "arts_and_entertainment", "movies")
        register("hotel", "hotel", "hostel", "guest_house", "motel", "alpine_hut", "chalet", "lodging", "resort")
        register("bank", "bank", "bureau_de_change", "financial", "money_transfer")
        register("atm", "atm", "cash_machine", "cash")
        register("post", "post_office", "post_box", "postal", "post")
        register("police", "police", "ranger_station", "police_station")
        register("fire", "fire_station", "fire")
        register("worship", "place_of_worship", "religion", "worship", "shrine", "monastery", "convent")
        register("church", "church", "cathedral", "chapel", "basilica")
        register("mosque", "mosque", "musalla", "husayniya", "hussainiya", "masjid")
        register("synagogue", "synagogue")
        register("temple", "temple", "hindu_temple", "buddhist_temple", "temple_buddhist", "temple_hindu")
        register("bus", "bus_stop", "bus_station", "bus", "coach_station", "transport", "public_transport")
        register("train", "railway", "train_station", "station", "halt", "railway_station", "rail", "subway_entrance")
        register("tram", "tram", "tram_stop", "light_rail")
        register("airport", "airport", "aerodrome", "airfield", "terminal", "helipad", "air_travel")
        register("park", "park", "garden", "national_park", "nature_reserve", "recreation_ground", "green_space")
        register("forest", "forest", "wood", "tree", "vegetation")
        register("playground", "playground", "children", "child_care", "play_area")
        register(
            "sport", "sports_centre", "fitness_centre", "fitness_center", "gym", "stadium", "pitch",
            "track", "sports_hall", "sport_and_leisure", "sport", "tennis", "basketball", "football",
            "soccer", "golf", "golf_course", "ski", "skiing", "surfing", "kayak", "climbing",
        )
        register("swimming", "swimming_pool", "swimming", "water_park", "pool", "bathing")
        register("water", "water", "spring", "fountain", "drinking_water", "water_tower", "waterfall")
        register("toilets", "toilets", "wc", "restroom", "sanitary", "public_bath")
        register("view", "viewpoint", "panorama", "observation", "lookout", "scenic")
        register("peak", "peak", "summit", "mountain", "hill", "volcano", "ridge")
        register(
            "camp", "camp_site", "campsite", "camping", "caravan_site", "wilderness_hut", "shelter",
            "picnic_site", "picnic_table", "holiday_village",
        )
        register("repair", "car_repair", "garage", "tyre", "tire", "repair", "car_parts", "car_service")
        register("carwash", "car_wash", "carwash", "car_wash_service")
        register("car", "car_rental", "car_sharing", "car", "vehicle", "motorcycle", "parking_garage")
        register("taxi", "taxi", "taxi_rank", "taxi_stand")
        register("industrial", "industrial", "factory", "works", "warehouse", "logistics", "power", "quarry")
        register(
            "office", "office", "company", "government", "embassy", "courthouse", "townhall",
            "administrative", "civic", "public_building",
        )
        register("residential", "residential", "apartment", "apartments", "house", "flats", "dormitory")
        register("info", "information", "tourist_information", "information_point", "infopoint")
    }

    /** Persian caption shown in search results and on the map's POI card. */
    val labels: Map<String, String> = mapOf(
        "fuel" to "پمپ بنزین",
        "charging" to "ایستگاه شارژ",
        "parking" to "پارکینگ",
        "bicycle_parking" to "پارکینگ دوچرخه",
        "shop" to "فروشگاه",
        "market" to "بازار",
        "bakery" to "نانوایی",
        "butcher" to "قصابی",
        "cafe" to "کافه",
        "restaurant" to "رستوران",
        "fastfood" to "فست‌فود",
        "bar" to "کافه‌بار",
        "icecream" to "بستنی‌فروشی",
        "alcohol" to "مشروبات",
        "florist" to "گل‌فروشی",
        "hardware" to "ابزارفروشی",
        "laundry" to "خشکشویی",
        "electronics" to "کالا و لوازم",
        "hospital" to "بیمارستان",
        "pharmacy" to "داروخانه",
        "veterinary" to "دامپزشکی",
        "school" to "مدرسه",
        "university" to "دانشگاه",
        "library" to "کتابخانه",
        "museum" to "موزه",
        "attraction" to "جاذبهٔ گردشگری",
        "cinema" to "سینما",
        "hotel" to "هتل",
        "bank" to "بانک",
        "atm" to "خودپرداز",
        "post" to "پست",
        "police" to "پلیس",
        "fire" to "آتش‌نشانی",
        "worship" to "نیایشگاه",
        "church" to "کلیسا",
        "mosque" to "مسجد",
        "synagogue" to "کنیسه",
        "temple" to "معبد",
        "bus" to "ایستگاه اتوبوس",
        "train" to "ایستگاه قطار",
        "tram" to "تراموا",
        "airport" to "فرودگاه",
        "park" to "پارک",
        "forest" to "جنگل",
        "playground" to "زمین بازی",
        "sport" to "ورزشگاه",
        "swimming" to "استخر",
        "water" to "آب",
        "toilets" to "سرویس بهداشتی",
        "view" to "چشم‌انداز",
        "peak" to "قله",
        "camp" to "محوطهٔ کمپینگ",
        "repair" to "تعمیرگاه",
        "carwash" to "کارواش",
        "car" to "خودرو",
        "taxi" to "تاکسی",
        "industrial" to "صنعتی",
        "office" to "اداری",
        "residential" to "مسکونی",
        "info" to "اطلاعات",
        GENERIC to "مکان",
    )

    /** Property names that carry a POI kind, in priority order. */
    val kindProperties: List<String> = listOf(
        "subclass", "amenity", "shop", "tourism", "leisure", "office", "maki", "type", "kind",
    )

    /** Coarse buckets used by Mapbox `category` / OpenMapTiles `class`. */
    private val coarse: Map<String, String> = mapOf(
        "food_and_drink" to "restaurant",
        "shopping" to "shop",
        "lodging" to "hotel",
        "education" to "school",
        "health" to "hospital",
        "transport" to "bus",
        "arts_and_entertainment" to "museum",
        "landmark" to "attraction",
        "sport_and_leisure" to "sport",
        "park_like" to "park",
        "commercial" to "shop",
        "medical" to "hospital",
        "historic" to "attraction",
    )

    /** Resolves any raw tag to an icon key, falling back to a substring match. */
    fun keyFor(raw: String?): String {
        val tag = raw?.trim()?.lowercase()?.replace(' ', '_') ?: return GENERIC
        if (tag.isEmpty()) return GENERIC
        tags[tag]?.let { return it }
        coarse[tag]?.let { return it }
        // Prefix/substring fallback: "clothing_store", "bakery_shop", "fuel_station"...
        tags.entries.firstOrNull { tag.contains(it.key) }?.let { return it.value }
        return GENERIC
    }

    fun labelFor(raw: String?): String = labels[keyFor(raw)] ?: labels.getValue(GENERIC)

    /** Style image id for an icon key. */
    fun imageId(key: String): String = "atlas-$key"
}
