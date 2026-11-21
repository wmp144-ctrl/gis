package ir.atlasgis.app.ui.screens

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalance
import androidx.compose.material.icons.rounded.Apartment
import androidx.compose.material.icons.rounded.Attractions
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.Business
import androidx.compose.material.icons.rounded.CarRepair
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Church
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Cottage
import androidx.compose.material.icons.rounded.DirectionsBike
import androidx.compose.material.icons.rounded.DirectionsBus
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.EvStation
import androidx.compose.material.icons.rounded.Factory
import androidx.compose.material.icons.rounded.Fastfood
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Forest
import androidx.compose.material.icons.rounded.Hardware
import androidx.compose.material.icons.rounded.Hotel
import androidx.compose.material.icons.rounded.Icecream
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Liquor
import androidx.compose.material.icons.rounded.LocalAtm
import androidx.compose.material.icons.rounded.LocalBar
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalCarWash
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.LocalFlorist
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalGroceryStore
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.LocalLibrary
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.LocalPharmacy
import androidx.compose.material.icons.rounded.LocalPolice
import androidx.compose.material.icons.rounded.LocalPostOffice
import androidx.compose.material.icons.rounded.LocalTaxi
import androidx.compose.material.icons.rounded.Mosque
import androidx.compose.material.icons.rounded.Museum
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.SportsSoccer
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Synagogue
import androidx.compose.material.icons.rounded.TempleBuddhist
import androidx.compose.material.icons.rounded.Terrain
import androidx.compose.material.icons.rounded.TheaterComedy
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Tram
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.Wc
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import ir.atlasgis.app.data.PoiClassifier

/**
 * Compose counterparts of the map's POI plates. Same icon key, same swatch, so a
 * search result and the symbol on the map are visibly the same place type.
 */
fun poiIconFor(rawTag: String?): ImageVector = when (PoiClassifier.keyFor(rawTag)) {
    "fuel" -> Icons.Rounded.LocalGasStation
    "charging" -> Icons.Rounded.EvStation
    "parking", "bicycle_parking" -> if (PoiClassifier.keyFor(rawTag) == "bicycle_parking") {
        Icons.Rounded.DirectionsBike
    } else {
        Icons.Rounded.LocalParking
    }
    "shop" -> Icons.Rounded.LocalGroceryStore
    "market" -> Icons.Rounded.Storefront
    "bakery" -> Icons.Rounded.BakeryDining
    "butcher" -> Icons.Rounded.ContentCut
    "cafe" -> Icons.Rounded.LocalCafe
    "restaurant" -> Icons.Rounded.Restaurant
    "fastfood" -> Icons.Rounded.Fastfood
    "bar" -> Icons.Rounded.LocalBar
    "icecream" -> Icons.Rounded.Icecream
    "alcohol" -> Icons.Rounded.Liquor
    "florist" -> Icons.Rounded.LocalFlorist
    "hardware" -> Icons.Rounded.Hardware
    "laundry" -> Icons.Rounded.LocalLaundryService
    "electronics" -> Icons.Rounded.Store
    "hospital" -> Icons.Rounded.LocalHospital
    "pharmacy" -> Icons.Rounded.LocalPharmacy
    "veterinary" -> Icons.Rounded.Pets
    "school", "university" -> Icons.Rounded.School
    "library" -> Icons.Rounded.LocalLibrary
    "museum" -> Icons.Rounded.Museum
    "attraction" -> Icons.Rounded.Attractions
    "cinema" -> Icons.Rounded.TheaterComedy
    "hotel" -> Icons.Rounded.Hotel
    "bank" -> Icons.Rounded.AccountBalance
    "atm" -> Icons.Rounded.LocalAtm
    "post" -> Icons.Rounded.LocalPostOffice
    "police" -> Icons.Rounded.LocalPolice
    "fire" -> Icons.Rounded.LocalFireDepartment
    "worship" -> Icons.Rounded.Mosque
    "church" -> Icons.Rounded.Church
    "mosque" -> Icons.Rounded.Mosque
    "synagogue" -> Icons.Rounded.Synagogue
    "temple" -> Icons.Rounded.TempleBuddhist
    "bus" -> Icons.Rounded.DirectionsBus
    "train" -> Icons.Rounded.Train
    "tram" -> Icons.Rounded.Tram
    "airport" -> Icons.Rounded.Flight
    "park" -> Icons.Rounded.Park
    "forest" -> Icons.Rounded.Forest
    "playground" -> Icons.Rounded.ChildCare
    "sport" -> Icons.Rounded.SportsSoccer
    "swimming" -> Icons.Rounded.Pool
    "water" -> Icons.Rounded.WaterDrop
    "toilets" -> Icons.Rounded.Wc
    "view" -> Icons.Rounded.Landscape
    "peak" -> Icons.Rounded.Terrain
    "camp" -> Icons.Rounded.Cottage
    "repair" -> Icons.Rounded.CarRepair
    "carwash" -> Icons.Rounded.LocalCarWash
    "car" -> Icons.Rounded.DirectionsCar
    "taxi" -> Icons.Rounded.LocalTaxi
    "industrial" -> Icons.Rounded.Factory
    "office" -> Icons.Rounded.Business
    "residential" -> Icons.Rounded.Apartment
    "info" -> Icons.Rounded.Info
    else -> Icons.Rounded.Place
}

fun poiColorFor(rawTag: String?): Color = when (PoiClassifier.keyFor(rawTag)) {
    "fuel", "taxi" -> Color(0xFFF0A020)
    "charging", "park", "forest", "sport", "playground", "view", "camp" -> Color(0xFF22A06B)
    "parking", "bicycle_parking", "bus", "train", "tram", "airport", "carwash", "info",
    "laundry" -> Color(0xFF2C6FDB)
    "hospital", "pharmacy", "icecream", "florist", "fire" -> Color(0xFFE5484D)
    "shop", "market", "electronics", "hotel" -> Color(0xFF7C5CFF)
    "bakery", "butcher", "cafe" -> Color(0xFFC2703A)
    "restaurant", "fastfood" -> Color(0xFFF97316)
    "bar", "alcohol" -> Color(0xFFB45309)
    "school", "university", "library" -> Color(0xFF3E7BFA)
    "museum", "attraction", "cinema", "post" -> Color(0xFFB7791F)
    "bank", "atm", "veterinary" -> Color(0xFF0E9F6E)
    "worship", "church", "mosque", "synagogue", "temple" -> Color(0xFF6D5BD0)
    "swimming", "water" -> Color(0xFF2C6E9B)
    "police" -> Color(0xFF1D4ED8)
    "peak" -> Color(0xFF7A5C3E)
    else -> Color(0xFF8497AF)
}
