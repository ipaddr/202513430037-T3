package com.example.app_jalanin.ui.login

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import org.junit.Rule
import org.junit.Test

class SelectDriverTypeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun allDriverTypesVisible() {
        composeRule.setContent { SelectDriverTypeScreen() }
        composeRule.onNodeWithText("Driver Motor").assertIsDisplayed()
        composeRule.onNodeWithText("Driver Mobil").assertIsDisplayed()
        composeRule.onNodeWithText("Driver Pengganti").assertIsDisplayed()
        composeRule.onNodeWithText("Pemilik Kendaraan").assertIsDisplayed()
    }
}
