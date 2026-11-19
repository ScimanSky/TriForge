package com.triforge.app.ui.account

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triforge.app.R
import com.triforge.app.data.AccountRepository
import com.triforge.app.data.AuthRepository
import com.triforge.app.data.DataExport
import com.triforge.app.data.ServiceException
import com.triforge.app.data.StoredDataCounts
import com.triforge.app.ui.profile.Notice
import com.triforge.domain.AccountGate
import com.triforge.domain.AccountInfo
import com.triforge.domain.ConsentType
import com.triforge.domain.Onboarding
import java.io.OutputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the phone does for this screen that the view model cannot do alone. */
interface DeviceIo {
    /** Crops and shrinks a picked photo into the profile JPEG. */
    suspend fun avatarFrom(uri: Uri): ByteArray

    fun openOutput(uri: Uri): OutputStream?

    /** Empties the local cache, signs out and restarts the app. */
    suspend fun afterAccountDeleted()
}

/** The long operations of the privacy page; one at a time. */
enum class PrivacyTask { EXPORT, WITHDRAW_HEALTH, DELETE_ACCOUNT, CONSENT }

data class AccountUiState(
    val loaded: Boolean = false,
    val account: AccountInfo = AccountInfo(),
    val email: String = "",
    val usesPassword: Boolean = false,
    val avatar: ByteArray? = null,
    val avatarBusy: Boolean = false,
    val identitySaving: Boolean = false,
    val identityNotice: Notice? = null,
    val counts: StoredDataCounts? = null,
    val task: PrivacyTask? = null,
    val notice: Notice? = null,
) {
    val gate: AccountGate get() = Onboarding.gate(account)
}

/** The account, its photo and consents, and the privacy operations on them. */
class AccountViewModel(
    private val uid: String,
    private val repository: AccountRepository,
    private val auth: AuthRepository,
    private val export: DataExport,
    private val io: DeviceIo,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState(email = auth.email, usesPassword = auth.usesPassword))
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.account(uid)
                // Unreadable means no consents: the athlete is asked again
                // rather than let through.
                .catch { _state.update { it.copy(loaded = true) } }
                .collect { account -> _state.update { it.copy(loaded = true, account = account) } }
        }
        viewModelScope.launch {
            repository.avatar(uid)
                .catch { }
                .collect { jpeg -> _state.update { it.copy(avatar = jpeg) } }
        }
    }

    // -- Identity ---------------------------------------------------------

    fun saveIdentity(name: String, bio: String, onSaved: () -> Unit = {}) {
        if (_state.value.identitySaving) return
        if (name.isBlank()) {
            _state.update { it.copy(identityNotice = Notice(R.string.account_error_name, isError = true)) }
            return
        }
        _state.update { it.copy(identitySaving = true, identityNotice = null) }
        viewModelScope.launch {
            val ok = runCatching { repository.saveIdentity(uid, name, bio) }.isSuccess
            _state.update {
                it.copy(
                    identitySaving = false,
                    identityNotice = if (ok) null else Notice(R.string.profile_error_save, isError = true),
                )
            }
            if (ok) onSaved()
        }
    }

    fun clearIdentityNotice() = _state.update { it.copy(identityNotice = null) }

    fun setAvatar(uri: Uri) {
        if (_state.value.avatarBusy) return
        _state.update { it.copy(avatarBusy = true, identityNotice = null) }
        viewModelScope.launch {
            val ok = runCatching { repository.saveAvatar(uid, io.avatarFrom(uri)) }.isSuccess
            _state.update {
                it.copy(
                    avatarBusy = false,
                    identityNotice = if (ok) null else Notice(R.string.account_error_photo, isError = true),
                )
            }
        }
    }

    fun removeAvatar() {
        viewModelScope.launch { runCatching { repository.removeAvatar(uid) } }
    }

    fun saveBirthYear(year: Int, onSaved: () -> Unit) {
        viewModelScope.launch {
            if (runCatching { repository.saveBirthYear(uid, year) }.isSuccess) {
                onSaved()
            } else {
                _state.update { it.copy(notice = Notice(R.string.profile_error_save, isError = true)) }
            }
        }
    }

    fun completeOnboarding() {
        viewModelScope.launch { runCatching { repository.completeOnboarding(uid) } }
    }

    fun sendPasswordReset() {
        val email = _state.value.email
        viewModelScope.launch {
            val notice = runCatching { auth.sendPasswordReset(email) }.fold(
                onSuccess = { Notice(R.string.settings_password_sent, listOf(email)) },
                onFailure = { Notice(R.string.settings_password_error, isError = true) },
            )
            _state.update { it.copy(notice = notice) }
        }
    }

    // -- Consents ---------------------------------------------------------

    /** Records several answers at once, as the privacy step of the first launch does. */
    fun answerConsents(answers: Map<ConsentType, Boolean>, onSaved: () -> Unit = {}) =
        privacyTask(PrivacyTask.CONSENT) {
            repository.saveConsents(uid, answers)
            onSaved()
        }

    fun setAiCoach(granted: Boolean) = answerConsents(mapOf(ConsentType.AI_COACH to granted))

    fun withdrawHealthConsent() = privacyTask(PrivacyTask.WITHDRAW_HEALTH) {
        repository.withdrawHealthConsent(uid)
    }

    // -- Data -------------------------------------------------------------

    fun loadCounts() {
        viewModelScope.launch {
            val counts = repository.counts(uid)
            _state.update { it.copy(counts = counts) }
        }
    }

    fun exportTo(uri: Uri) = privacyTask(PrivacyTask.EXPORT) {
        val out = io.openOutput(uri) ?: error("no output")
        export.write(uid, out)
        _state.update { it.copy(notice = Notice(R.string.privacy_export_done)) }
    }

    fun deleteAccount() = privacyTask(PrivacyTask.DELETE_ACCOUNT) {
        repository.deleteAccount()
        io.afterAccountDeleted()
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    private fun privacyTask(task: PrivacyTask, block: suspend () -> Unit) {
        if (_state.value.task != null) return
        _state.update { it.copy(task = task, notice = null) }
        viewModelScope.launch {
            try {
                block()
            } catch (error: ServiceException) {
                val text = if (error.code == "network") R.string.privacy_error_network else R.string.privacy_error_generic
                _state.update { it.copy(notice = Notice(text, isError = true)) }
            } catch (error: Exception) {
                _state.update { it.copy(notice = Notice(R.string.privacy_error_generic, isError = true)) }
            } finally {
                _state.update { it.copy(task = null) }
            }
        }
    }
}
