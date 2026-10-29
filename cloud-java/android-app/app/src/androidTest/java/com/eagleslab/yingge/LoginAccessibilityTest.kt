package com.eagleslab.yingge

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.eagleslab.yingge.ui.screens.LoginScreen
import com.eagleslab.yingge.ui.theme.YinggeTheme
import org.junit.Rule
import org.junit.Test

class LoginAccessibilityTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun loginContainsExplicitAccountPasswordAndServerControls() {
        compose.setContent { YinggeTheme { LoginScreen("", false, { _, _, _, _ -> }, onRegister = { _, _ -> }) } }
        compose.onNodeWithText("账号").assertIsDisplayed()
        compose.onNodeWithText("密码").assertIsDisplayed()
        compose.onNodeWithText("服务器地址").assertIsDisplayed()
        compose.onNodeWithText("安全登录").assertIsDisplayed()
    }
}
