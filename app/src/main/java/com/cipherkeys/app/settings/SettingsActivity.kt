package com.cipherkeys.app.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.cipherkeys.app.data.KeyboardMode
import com.cipherkeys.app.data.KeyboardSettings
import com.cipherkeys.app.data.KeyboardTheme
import com.cipherkeys.app.data.SettingsRepository
import com.cipherkeys.app.data.ThemeColorSet
import com.cipherkeys.app.ui.CipherKeysTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** ViewModel bridging the Compose UI to [SettingsRepository]'s suspend/Flow API. */
class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(KeyboardSettings())
    val uiState: StateFlow<KeyboardSettings> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.settingsFlow.collect { _uiState.value = it }
        }
    }

    fun setDefaultMode(mode: KeyboardMode) = viewModelScope.launch { repository.setDefaultMode(mode) }
    fun setAutoEncode(enabled: Boolean) = viewModelScope.launch { repository.setAutoEncode(enabled) }
    fun setAutoDecode(enabled: Boolean) = viewModelScope.launch { repository.setAutoDecode(enabled) }
    fun setAutocorrect(enabled: Boolean) = viewModelScope.launch { repository.setAutocorrect(enabled) }
    fun setVibration(enabled: Boolean) = viewModelScope.launch { repository.setVibration(enabled) }
    fun setKeySound(enabled: Boolean) = viewModelScope.launch { repository.setKeySound(enabled) }
    fun setHeightScale(scale: Float) = viewModelScope.launch { repository.setKeyboardHeightScale(scale) }
    fun setTheme(theme: KeyboardTheme) = viewModelScope.launch { repository.setTheme(theme) }

    fun setCustomMapping(letter: Char, tokensCsv: String) = viewModelScope.launch {
        val tokens = tokensCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        val updated = _uiState.value.customMappings.toMutableMap()
        if (tokens.isEmpty()) updated.remove(letter) else updated[letter] = tokens
        repository.setCustomMappings(updated)
    }

    fun setCustomColors(colors: ThemeColorSet) = viewModelScope.launch { repository.setCustomColors(colors) }

    fun setBackgroundImagePath(path: String?) = viewModelScope.launch { repository.setBackgroundImagePath(path) }
    fun setUseImageBackground(enabled: Boolean) = viewModelScope.launch { repository.setUseImageBackground(enabled) }
    fun setBackgroundOverlayAlpha(alpha: Float) = viewModelScope.launch { repository.setBackgroundOverlayAlpha(alpha) }

    class Factory(private val repository: SettingsRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(repository) as T
        }
    }
}

class SettingsActivity : ComponentActivity() {

    private val viewModel: SettingsViewModel by viewModels {
        SettingsViewModel.Factory(SettingsRepository(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CipherKeysTheme {
                SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
