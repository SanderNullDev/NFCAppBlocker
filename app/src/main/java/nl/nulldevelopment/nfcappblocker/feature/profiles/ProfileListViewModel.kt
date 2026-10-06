package nl.nulldevelopment.nfcappblocker.feature.profiles

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.nulldevelopment.nfcappblocker.R
import nl.nulldevelopment.nfcappblocker.data.profiles.Profile
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileRepository
import nl.nulldevelopment.nfcappblocker.util.firstUnusedName
import javax.inject.Inject

@HiltViewModel
class ProfileListViewModel @Inject constructor(
    private val repository: ProfileRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    /** Null while loading. */
    val profiles: StateFlow<List<Profile>?> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Creates an empty profile with a default name, which the user then edits. */
    fun createProfile(onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            val name = firstUnusedName(repository.observeAll().first().map { it.name }) {
                context.getString(R.string.profile_default_name, it)
            }
            onCreated(repository.create(name, emptySet()))
        }
    }
}
