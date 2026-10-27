package com.henriquesebastiao.downtify.feature.connect

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.henriquesebastiao.downtify.core.designsystem.theme.DowntifyTheme
import com.henriquesebastiao.downtify.core.model.Capabilities
import com.henriquesebastiao.downtify.core.model.ServerInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val info = ServerInfo("cf12", "nas.local", "Downtify", "3.2.0", 1, true, Capabilities())

    @Test
    fun connectIsDisabledUntilAnAddressIsTyped() {
        var typed = ""
        var connected = false
        compose.setContent {
            DowntifyTheme {
                ConnectScreen(
                    state = ConnectUiState(address = typed, searching = false),
                    onAddressChange = { typed = it },
                    onPickDiscovered = {},
                    onConnect = { connected = true },
                    onCodeChange = {},
                    onPair = {},
                    onBackToAddress = {},
                    onScan = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Connect").assertIsNotEnabled()
        compose.onNodeWithText("Step 1 of 2").assertExists()
        compose.onNodeWithText("Server address").performTextInput("192.168.1.20")
        assertEquals("192.168.1.20", typed)
        assertTrue(!connected)
    }

    @Test
    fun connectCallsBackWhenAnAddressIsPresent() {
        var connected = false
        compose.setContent {
            DowntifyTheme {
                ConnectScreen(
                    state = ConnectUiState(address = "http://192.168.1.20:8000", searching = false),
                    onAddressChange = {},
                    onPickDiscovered = {},
                    onConnect = { connected = true },
                    onCodeChange = {},
                    onPair = {},
                    onBackToAddress = {},
                    onScan = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Connect").assertIsEnabled().performClick()
        assertTrue(connected)
    }

    @Test
    fun pairStepShowsTheWrongCodeErrorAndNeedsAFullCode() {
        compose.setContent {
            DowntifyTheme {
                ConnectScreen(
                    state = ConnectUiState(
                        step = ConnectStep.Pair("http://192.168.1.20:8000", info),
                        code = "K7QM",
                        error = ConnectError.WrongCode,
                    ),
                    onAddressChange = {},
                    onPickDiscovered = {},
                    onConnect = {},
                    onCodeChange = {},
                    onPair = {},
                    onBackToAddress = {},
                    onScan = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Pair").assertIsNotEnabled()
        compose.onNodeWithText("Step 2 of 2").assertExists()
        compose.onNodeWithText("Pairing code").assertExists()
    }

    @Test
    fun aFullCodePairsFromTheFooter() {
        var paired = false
        compose.setContent {
            DowntifyTheme {
                ConnectScreen(
                    state = ConnectUiState(
                        step = ConnectStep.Pair("http://192.168.1.20:8000", info),
                        code = "K7QM2XPD",
                    ),
                    onAddressChange = {},
                    onPickDiscovered = {},
                    onConnect = {},
                    onCodeChange = {},
                    onPair = { paired = true },
                    onBackToAddress = {},
                    onScan = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Pair").assertIsEnabled().performClick()
        assertTrue(paired)
    }

    @Test
    fun whileConnectingTheFooterWaitsInsteadOfConnectingAgain() {
        var connected = 0
        compose.setContent {
            DowntifyTheme {
                ConnectScreen(
                    state = ConnectUiState(address = "http://192.168.1.20:8000", searching = false, busy = true),
                    onAddressChange = {},
                    onPickDiscovered = {},
                    onConnect = { connected++ },
                    onCodeChange = {},
                    onPair = {},
                    onBackToAddress = {},
                    onScan = {},
                )
            }
        }
        compose.onNodeWithContentDescription("Connect").assertIsNotEnabled().performClick()
        assertEquals(0, connected)
    }
}
