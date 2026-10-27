package com.andika.watersupply

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.andika.watersupply.ui.WaterApp
import com.andika.watersupply.ui.theme.WaterSupplyTheme
import org.junit.Rule
import org.junit.Test

class WaterUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun recordUndoAndNavigate() {
        compose.waitUntil(10_000) { compose.onAllNodesWithText("+1 gelas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("+1 gelas").performScrollTo().performClick()
        compose.onNodeWithText("Urungkan").assertExists().performClick()
        compose.onNodeWithText("Riwayat").performClick()
        compose.onNodeWithText("Tambah catatan").performScrollTo().performClick()
        compose.onNodeWithText("Batal").performClick()
        compose.onNodeWithText("Pengaturan").performClick()
        compose.onNodeWithText("Ubah target").performScrollTo().performClick()
        compose.onNodeWithText("Jumlah gelas").performTextReplacement("9,5")
        compose.onNodeWithText("Simpan").performClick()
        compose.onNodeWithText("9,5 gelas").assertExists()
        compose.onNodeWithText("Ubah target").performClick()
        compose.onNodeWithText("Jumlah gelas").performTextReplacement("8")
        compose.onNodeWithText("Simpan").performClick()
    }
    @Test fun largeTextKeepsActionsAndNavigationReachable() {
        compose.activity.runOnUiThread {
            compose.activity.setContent {
                val density = LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                    WaterSupplyTheme { WaterApp() }
                }
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("+½ gelas").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("+1 gelas").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("+½ gelas").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Pengaturan").performClick()
        compose.onNodeWithText("Ubah target").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Atur jadwal").performScrollTo().performClick()
        compose.onNodeWithText("Jam khusus").assertIsDisplayed().performClick()
        compose.onNodeWithText("Tambah jam").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Batal").performClick()
    }
}
