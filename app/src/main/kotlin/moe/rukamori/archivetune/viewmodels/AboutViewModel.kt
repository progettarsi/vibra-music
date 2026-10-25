/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.about.AboutDependencyLicense
import moe.rukamori.archivetune.about.FetchAboutDependencyLicensesUseCase
import javax.inject.Inject

data class AboutLicensesState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val licenses: List<AboutDependencyLicense> = emptyList(),
)

@HiltViewModel
class AboutViewModel @Inject constructor(
    private val fetchDependencyLicenses: FetchAboutDependencyLicensesUseCase,
) : ViewModel() {
    private val _state = MutableStateFlow(AboutLicensesState())
    val state = _state.asStateFlow()

    init { retry() }

    fun retry() {
        viewModelScope.launch {
            _state.value = AboutLicensesState()
            fetchDependencyLicenses().fold(
                onSuccess = { licenses ->
                    _state.value = AboutLicensesState(loading = false,
                        licenses = (0 until licenses.size).map { licenses[it] })
                },
                onFailure = { _state.value = AboutLicensesState(loading = false, failed = true) },
            )
        }
    }
}
