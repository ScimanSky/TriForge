package com.triforge.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triforge.app.data.AuthRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

sealed interface Session {
    data object Loading : Session
    data object SignedOut : Session
    data class SignedIn(val uid: String) : Session
}

class SessionViewModel(auth: AuthRepository) : ViewModel() {

    val session: StateFlow<Session> = auth.userId
        .map { uid -> if (uid == null) Session.SignedOut else Session.SignedIn(uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Session.Loading)
}
