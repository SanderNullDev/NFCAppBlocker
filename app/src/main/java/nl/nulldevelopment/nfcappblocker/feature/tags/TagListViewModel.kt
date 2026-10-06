package nl.nulldevelopment.nfcappblocker.feature.tags

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import nl.nulldevelopment.nfcappblocker.data.tags.PairedTag
import nl.nulldevelopment.nfcappblocker.data.tags.TagRepository
import javax.inject.Inject

@HiltViewModel
class TagListViewModel @Inject constructor(repository: TagRepository) : ViewModel() {
    /** Null while loading. */
    val tags: StateFlow<List<PairedTag>?> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
