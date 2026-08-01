package at.nimmdas.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import at.nimmdas.app.NimmdasApp
import at.nimmdas.app.data.model.Listing
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(app: Application) : AndroidViewModel(app) {
    private val apiClient = (app as NimmdasApp).apiClient

    private val _listings = MutableStateFlow<List<Listing>>(emptyList())
    val listings = _listings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _userName = MutableStateFlow<String?>(null)
    val userName = _userName.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    init {
        loadListings()
        loadUser()
    }

    fun loadListings() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val response = apiClient.api.getListings(limit = 30)
                if (response.isSuccessful) {
                    _listings.value = response.body() ?: emptyList()
                    _error.value = null
                } else {
                    // Never surface the raw error body — during a deploy the proxy returns a
                    // full HTML page, which used to be dumped onto the home screen.
                    _error.value = when (response.code()) {
                        502, 503, 504 -> "Server gerade nicht erreichbar – bitte kurz warten."
                        else -> "Inserate konnten nicht geladen werden (${response.code()})"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = "Keine Verbindung zum Server"
            }
            _isLoading.value = false
        }
    }

    private fun loadUser() {
        viewModelScope.launch {
            _userName.value = apiClient.getUserName()
        }
    }
}
