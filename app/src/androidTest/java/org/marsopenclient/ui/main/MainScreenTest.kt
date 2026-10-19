package org.marsopenclient.ui.main

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** UI tests for [org.marsopenclient.ui.main.MainScreen]. */
class MainScreenTest {

  @get:Rule val composeTestRule = createAndroidComposeRule<ComponentActivity>()

  @Before
  fun setup() {
    composeTestRule.setContent { MainScreen() }
  }

  @Test
  fun appHeader_andSections_exist() {
    composeTestRule.onNodeWithText("MarsOpenClient • Marstek API v3.1").assertExists()
    composeTestRule.onNodeWithText("Моніторинг").assertExists()
    composeTestRule.onNodeWithText("Режими ES").assertExists()
    composeTestRule.onNodeWithText("Система SYS").assertExists()
  }
}
