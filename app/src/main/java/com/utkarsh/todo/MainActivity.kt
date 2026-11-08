package com.utkarsh.todo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.utkarsh.todo.core.designsystem.theme.PaperTheme
import com.utkarsh.todo.core.navigation.AppNavHost
import com.utkarsh.todo.feature.customize.domain.AppSettings
import com.utkarsh.todo.feature.customize.domain.SettingsRepository
import com.utkarsh.todo.feature.customize.ui.toDensity
import com.utkarsh.todo.feature.customize.ui.toPattern
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Density and paper texture follow the Customize screen live, app-wide.
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
            PaperTheme(density = settings.density.toDensity(), pattern = settings.background.toPattern()) { AppNavHost() }
        }
    }
}
