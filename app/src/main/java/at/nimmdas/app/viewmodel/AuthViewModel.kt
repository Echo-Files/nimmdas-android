package at.nimmdas.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import at.nimmdas.app.NimmdasApp
import at.nimmdas.app.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AuthViewModel(app: Application) : AndroidViewModel(app) {
    private val apiClient = (app as NimmdasApp).apiClient

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun login(email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = apiClient.api.login(AuthRequest(action = "login", email = email, password = password))
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    apiClient.saveToken(body.token)
                    apiClient.saveUserInfo(
                        body.user.id ?: "",
                        body.user.name,
                        body.user.email ?: "",
                        body.user.avatar
                    )
                    onSuccess()
                } else {
                    val errorBody = response.errorBody()?.string()
                    _error.value = parseError(errorBody) ?: "Login fehlgeschlagen"
                }
            } catch (e: Exception) {
                _error.value = "Netzwerkfehler: ${e.localizedMessage}"
            }
            _isLoading.value = false
        }
    }

    fun register(name: String, email: String, password: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = apiClient.api.register(
                    AuthRequest(action = "register", name = name, email = email, password = password)
                )
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    apiClient.saveToken(body.token)
                    apiClient.saveUserInfo(
                        body.user.id ?: "",
                        body.user.name,
                        body.user.email ?: "",
                        body.user.avatar
                    )
                    onSuccess()
                } else {
                    val errorBody = response.errorBody()?.string()
                    _error.value = parseError(errorBody) ?: "Registrierung fehlgeschlagen"
                }
            } catch (e: Exception) {
                _error.value = "Netzwerkfehler: ${e.localizedMessage}"
            }
            _isLoading.value = false
        }
    }

    fun loginWithGoogle(idToken: String, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = apiClient.api.login(
                    AuthRequest(action = "google", idToken = idToken)
                )
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    apiClient.saveToken(body.token)
                    apiClient.saveUserInfo(
                        body.user.id ?: "",
                        body.user.name,
                        body.user.email ?: "",
                        body.user.avatar
                    )
                    onSuccess()
                } else {
                    val errorBody = response.errorBody()?.string()
                    _error.value = parseError(errorBody) ?: "Google Login fehlgeschlagen"
                }
            } catch (e: Exception) {
                _error.value = "Netzwerkfehler: ${e.localizedMessage}"
            }
            _isLoading.value = false
        }
    }

    private fun parseError(body: String?): String? {
        return try {
            com.google.gson.Gson().fromJson(body, ApiError::class.java).error
        } catch (e: Exception) { null }
    }
}
