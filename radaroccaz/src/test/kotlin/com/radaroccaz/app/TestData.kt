package com.radaroccaz.app

import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Gearbox
import com.radaroccaz.app.data.Listing
import com.radaroccaz.app.data.SellerType

fun car(
    id: String,
    price: Int,
    km: Int? = 80_000,
    year: Int? = 2019,
    source: String = "leboncoin",
    title: String = "Peugeot 308 1.5 BlueHDi 130 Allure",
    make: String? = "Peugeot",
    fuel: Fuel? = Fuel.DIESEL,
    gearbox: Gearbox? = Gearbox.MANUELLE,
    seller: SellerType? = SellerType.PARTICULIER,
    postalCode: String? = "33000",
    city: String? = "Bordeaux",
    options: Set<String> = emptySet(),
    images: List<String> = emptyList(),
) = Listing(
    id = "$source:$id", source = source, title = title, price = price, url = "https://example.org/$id",
    make = make, mileageKm = km, year = year, fuel = fuel, gearbox = gearbox, sellerType = seller,
    city = city, postalCode = postalCode, options = options, imageUrls = images,
)
