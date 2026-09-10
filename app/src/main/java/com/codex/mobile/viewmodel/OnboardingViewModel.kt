package com.codex.mobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.codex.mobile.data.LanguageOption
import com.codex.mobile.data.UserPreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class OnboardingStage { LANGUAGE, LOGIN, NAME, BIO, DONE }

data class OnboardingUiState(
    val stage: OnboardingStage = OnboardingStage.LANGUAGE,
    val selectedLanguage: LanguageOption? = null,
    val name: String = "",
    val bio: String = "",
    val isLoggedIn: Boolean = false,
    val isLoggingIn: Boolean = false,
    val authUrl: String? = null,
    val errorMessage: String? = null
)

class OnboardingViewModel(private val prefsStore: UserPreferencesStore) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state

    fun selectLanguage(option: LanguageOption) {
        _state.value = _state.value.copy(selectedLanguage = option, stage = OnboardingStage.LOGIN)
        viewModelScope.launch {
            prefsStore.setLanguage(option.code, option.nativeName)
        }
    }

    fun onLoginStarted() {
        _state.value = _state.value.copy(isLoggingIn = true, errorMessage = null)
    }

    fun onLoginSuccess() {
        _state.value = _state.value.copy(isLoggedIn = true, isLoggingIn = false, stage = OnboardingStage.NAME)
    }

    fun onLoginError(message: String) {
        _state.value = _state.value.copy(errorMessage = message, isLoggingIn = false)
    }

    fun setAuthUrl(url: String) {
        _state.value = _state.value.copy(authUrl = url)
    }

    fun submitName(name: String) {
        _state.value = _state.value.copy(name = name, stage = OnboardingStage.BIO)
    }

    fun submitBio(bio: String) {
        val current = _state.value
        _state.value = current.copy(bio = bio, stage = OnboardingStage.DONE)
        viewModelScope.launch {
            prefsStore.setUserIdentity(current.name, bio)
            prefsStore.markOnboardingDone()
        }
    }

    fun skipBio() {
        submitBio("")
    }

    /**
     * Fully resets in-memory onboarding state back to the language-picker
     * stage. Without this, logging out only changed the Screen enum in
     * MainActivity while OnboardingViewModel's stage stayed DONE from the
     * prior session — the LaunchedEffect(onboardingState.stage) watcher
     * would then immediately fire again and bounce the user straight back
     * into Chat, making "Log out" appear to do nothing.
     */
    fun resetForLogout() {
        _state.value = OnboardingUiState()
    }
}
