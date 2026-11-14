package com.radaroccaz.app

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.radaroccaz.app.data.Alert
import com.radaroccaz.app.data.AlertFrequency
import com.radaroccaz.app.data.Fuel
import com.radaroccaz.app.data.Gearbox
import com.radaroccaz.app.data.Priorities
import com.radaroccaz.app.data.SearchCriteria
import com.radaroccaz.app.data.SellerType
import com.radaroccaz.app.data.Settings
import com.radaroccaz.app.data.ThemeMode
import com.radaroccaz.app.data.VehicleOptions
import com.radaroccaz.app.domain.Ranking
import com.radaroccaz.app.domain.SourceState
import com.radaroccaz.app.domain.SourceStatus
import com.radaroccaz.app.ui.ResultsState
import com.radaroccaz.app.ui.alerts.AlertsScreen
import com.radaroccaz.app.ui.detail.ListingDetailScreen
import com.radaroccaz.app.ui.favorites.FavoritesScreen
import com.radaroccaz.app.ui.results.ResultsScreen
import com.radaroccaz.app.ui.search.SearchScreen
import com.radaroccaz.app.ui.settings.SettingsScreen
import com.radaroccaz.app.ui.settings.SourceItem
import com.radaroccaz.app.ui.theme.RadarTheme
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Renders each screen with realistic data, checks key content and saves PNGs to radaroccaz/build/screenshots. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w400dp-h880dp-xhdpi", application = android.app.Application::class)
class ScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val criteria = SearchCriteria(
        make = "Peugeot", model = "308", budgetMax = 15_000, mileageMax = 120_000, yearMin = 2017,
        fuels = setOf(Fuel.DIESEL, Fuel.ESSENCE), wantedOptions = setOf("gps", "camera", "carplay"),
        priorities = Priorities(price = 3, mileage = 2, age = 2, options = 2),
    )

    private val raw = listOf(
        car("1", 11_490, 96_000, 2018, "leboncoin", "Peugeot 308 1.5 BlueHDi 130 Allure", options = setOf("gps", "camera", "carplay", "carnet"), city = "Bordeaux", postalCode = "33000"),
        car("2", 13_990, 58_000, 2020, "autoscout24", "Peugeot 308 1.2 PureTech 130 GT Line", fuel = Fuel.ESSENCE, gearbox = Gearbox.AUTOMATIQUE, seller = SellerType.PRO, options = setOf("gps", "carplay", "garantie"), city = "Lyon", postalCode = "69003"),
        car("3", 9_800, 118_000, 2017, "leboncoin", "Peugeot 308 SW 1.6 BlueHDi 120 Active", options = setOf("gps"), city = "Nantes", postalCode = "44000"),
        car("4", 14_900, 42_000, 2021, "autoscout24", "Peugeot 308 1.5 BlueHDi 130 Allure Pack", seller = SellerType.PRO, options = setOf("gps", "camera", "carplay", "led"), city = "Lille", postalCode = "59000"),
        car("5", 12_490, 87_000, 2019, "leboncoin", "Peugeot 308 1.2 PureTech 110 Style", fuel = Fuel.ESSENCE, options = setOf("camera"), city = "Rennes", postalCode = "35000"),
        car("6", 10_990, 75_000, 2019, "leboncoin", "Peugeot 308 1.5 BlueHDi 100 Active Business", options = setOf("gps", "camera", "carplay", "premiere_main"), city = "Toulouse", postalCode = "31000"),
        car("7", 13_400, 101_000, 2018, "autoscout24", "Peugeot 308 2.0 BlueHDi 150 GT Line EAT8", gearbox = Gearbox.AUTOMATIQUE, seller = SellerType.PRO, city = "Paris", postalCode = "75015"),
        car("8", 12_200, 66_000, 2019, "leboncoin", "Peugeot 308 1.2 PureTech 130 Allure", fuel = Fuel.ESSENCE, options = setOf("carplay"), city = "Pau", postalCode = "64000"),
    ).map { it.copy(description = "Véhicule en très bon état, entretien suivi chez Peugeot. " + it.options.joinToString(", ") { o -> VehicleOptions.label(o) } + ". Contrôle technique OK, aucun frais à prévoir.", publishedAt = "2026-11-13") }

    private val results = Ranking.rank(raw, criteria)

    private val statuses = listOf(
        SourceStatus("leboncoin", "Leboncoin", SourceState.OK, 5, searchUrl = "https://www.leboncoin.fr"),
        SourceStatus("lacentrale", "La Centrale", SourceState.BLOCKED, message = "Le site a demandé une vérification anti-robot (code 403).", searchUrl = "https://www.lacentrale.fr"),
        SourceStatus("autoscout24", "AutoScout24", SourceState.OK, 3, searchUrl = "https://www.autoscout24.fr"),
    )

    private fun render(theme: ThemeMode = ThemeMode.LIGHT, content: @Composable () -> Unit) {
        compose.setContent {
            RadarTheme(theme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { Box { content() } }
            }
        }
        compose.waitForIdle()
    }

    private fun shot(name: String) {
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    @Config(qualifiers = "w400dp-h1900dp-xhdpi")
    fun search() {
        var searched = false
        render {
            SearchScreen(
                criteria = criteria,
                recent = listOf(criteria, SearchCriteria(make = "Renault", model = "Clio", budgetMax = 9_000)),
                demoMode = false,
                onChange = {}, onSearch = { searched = true }, onPickRecent = {}, onClearRecent = {}, onReset = {}, onOpenSettings = {},
                currentYear = 2026,
            )
        }
        compose.onNodeWithText("Trouvez la bonne occasion").assertIsDisplayed()
        compose.onNodeWithText("Caméra de recul").assertIsDisplayed()
        shot("search")
        compose.onNodeWithTag("search_button").performClick()
        assertTrue(searched)
    }

    @Test
    fun searchEmpty() {
        render {
            SearchScreen(SearchCriteria(), emptyList(), false, {}, {}, {}, {}, {}, {}, currentYear = 2026)
        }
        compose.onNodeWithText("Indiquez une marque ou un modèle").assertIsDisplayed()
        shot("search_empty")
    }

    @Test
    fun results() {
        var alert = false
        render { resultsScreen(ResultsState(criteria, statuses, results), onAlert = { alert = true }) }
        compose.onNodeWithText("Leboncoin · 5").assertIsDisplayed()
        compose.onNodeWithText("La Centrale · bloqué").assertIsDisplayed()
        compose.onNodeWithText("8 annonces retenues").assertIsDisplayed()
        shot("results")
        compose.onNodeWithTag("alert_button").performClick()
        assertTrue(alert)
    }

    @Test
    fun resultsDark() {
        render(ThemeMode.DARK) { resultsScreen(ResultsState(criteria, statuses, results, newIds = setOf("leboncoin:6"))) }
        compose.onNodeWithText("Nouveau").assertIsDisplayed()
        shot("results_dark")
    }

    @Test
    fun resultsLoading() {
        render { resultsScreen(ResultsState(criteria, statuses.map { it.copy(state = SourceState.LOADING) }, emptyList(), running = true)) }
        shot("results_loading")
    }

    @Test
    fun resultsAllFailed() {
        val failed = statuses.map { it.copy(state = SourceState.BLOCKED, count = 0) }
        render { resultsScreen(ResultsState(criteria, failed, emptyList())) }
        compose.onNodeWithText("Les sites n'ont pas répondu").assertIsDisplayed()
        compose.onNodeWithText("Essayer avec des annonces de démo").assertExists()
        shot("results_failed")
    }

    @Composable
    private fun resultsScreen(state: ResultsState, onAlert: () -> Unit = {}) = ResultsScreen(
        state = state, favorites = setOf("leboncoin:1"), hasAlert = false,
        onBack = {}, onSort = {}, onOpen = {}, onToggleFavorite = {}, onCreateAlert = onAlert,
        onRetry = {}, onUseDemo = {}, onOpenUrl = {},
    )

    @Test
    @Config(qualifiers = "w400dp-h1700dp-xhdpi")
    fun detail() {
        val best = Ranking.sort(results, com.radaroccaz.app.domain.SortOrder.BEST).first()
        render {
            ListingDetailScreen(best.listing, best, criteria.wantedOptions, favorite = true, {}, {}, {}, {})
        }
        compose.onNodeWithText("Caractéristiques").assertIsDisplayed()
        compose.onNodeWithText("Voir l'annonce sur ${if (best.listing.source == "leboncoin") "Leboncoin" else "AutoScout24"}").assertExists()
        shot("detail")
    }

    @Test
    fun alerts() {
        val now = 1_763_100_000_000L
        val alerts = listOf(
            Alert("a", "Peugeot 308 diesel", criteria, AlertFrequency.HOUR_1, lastCheckedAt = now - 12 * 60_000,
                unread = raw.take(3)),
            Alert("b", "Clio pour Léa", SearchCriteria(make = "Renault", model = "Clio", budgetMax = 9_000, mileageMax = 80_000),
                AlertFrequency.HOUR_12, enabled = false, lastCheckedAt = now - 26 * 3_600_000L),
            Alert("c", "Model 3", SearchCriteria(make = "Tesla", model = "Model 3", budgetMax = 28_000), AlertFrequency.HOUR_3,
                lastCheckedAt = now - 2 * 3_600_000L, lastError = "captcha"),
        )
        render {
            AlertsScreen(alerts, checking = false, notificationsAllowed = false, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}, now = now)
        }
        compose.onNodeWithText("3 nouvelles").assertIsDisplayed()
        compose.onNodeWithText("Vérifiée il y a 12 min").assertIsDisplayed()
        shot("alerts")
    }

    @Test
    fun alertsEmpty() {
        render { AlertsScreen(emptyList(), false, true, {}, { _, _ -> }, { _, _ -> }, {}, {}, {}, {}) }
        compose.onNodeWithText("Aucune alerte pour l'instant").assertIsDisplayed()
        shot("alerts_empty")
    }

    @Test
    fun favorites() {
        render { FavoritesScreen(raw.take(4), {}, {}, {}) }
        compose.onNodeWithText("Favoris").assertIsDisplayed()
        shot("favorites")
    }

    @Test
    @Config(qualifiers = "w400dp-h1300dp-xhdpi")
    fun settings() {
        render {
            SettingsScreen(
                Settings(enabledSources = setOf("leboncoin", "autoscout24")),
                listOf(
                    SourceItem("leboncoin", "Leboncoin", "Particuliers et professionnels"),
                    SourceItem("lacentrale", "La Centrale", "Particuliers et professionnels, cote La Centrale"),
                    SourceItem("autoscout24", "AutoScout24", "Surtout des professionnels, France et Europe"),
                ),
                {}, {},
            )
        }
        compose.onNodeWithText("Sites consultés").assertIsDisplayed()
        shot("settings")
    }
}
