package com.platform.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.platform.android.data.ApiService
import com.platform.android.data.SessionStore

class PlatformViewModelFactory(
    private val api: ApiService,
    private val sessionStore: SessionStore,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return PlatformViewModel(api, sessionStore) as T
    }
}
