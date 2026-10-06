package nl.nulldevelopment.nfcappblocker.feature.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import nl.nulldevelopment.nfcappblocker.data.profiles.Profile
import nl.nulldevelopment.nfcappblocker.data.profiles.ProfileRepository
import javax.inject.Inject

@HiltViewModel
class ProfileListViewModel @Inject constructor(repository: ProfileRepository) : ViewModel() {
    /** Null while loading. */
    val profiles: StateFlow<List<Profile>?> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
