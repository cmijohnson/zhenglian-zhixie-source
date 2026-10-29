package com.eagleslab.yingge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eagleslab.yingge.ui.AppViewModel
import com.eagleslab.yingge.ui.YinggeApp
import com.eagleslab.yingge.ui.theme.YinggeTheme

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels { AppViewModel.factory(application as YinggeApplication) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val ui by viewModel.ui.collectAsStateWithLifecycle()
            YinggeTheme(ui.theme) { YinggeApp(viewModel) }
        }
    }

    override fun onStop() {
        // Access tokens never survive backgrounding; the next protected request
        // performs a single-flight refresh-token rotation.
        (application as YinggeApplication).tokenVault.clearAllAccessTokens()
        super.onStop()
    }
}
