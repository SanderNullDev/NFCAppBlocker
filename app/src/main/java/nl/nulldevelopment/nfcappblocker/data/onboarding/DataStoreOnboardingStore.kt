package nl.nulldevelopment.nfcappblocker.data.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

class DataStoreOnboardingStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OnboardingStore {

    override fun observe(): Flow<OnboardingProgress> = dataStore.data
        .map { prefs ->
            val step = prefs[STEP]?.let { name -> OnboardingStep.entries.firstOrNull { it.name == name } }
            when {
                prefs[DONE] == true -> OnboardingProgress.Done
                step != null -> OnboardingProgress.InProgress(
                    step = step,
                    tagUuid = prefs[TAG]?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                    profileId = prefs[PROFILE],
                )
                else -> OnboardingProgress.NotStarted
            }
        }
        .distinctUntilChanged()

    override suspend fun setStep(step: OnboardingStep) {
        dataStore.edit {
            it[STEP] = step.name
            it[DONE] = false
        }
    }

    override suspend fun setTag(uuid: UUID) {
        dataStore.edit { it[TAG] = uuid.toString() }
    }

    override suspend fun setProfile(id: Long) {
        dataStore.edit { it[PROFILE] = id }
    }

    override suspend fun finish() {
        dataStore.edit { it[DONE] = true }
    }

    override suspend fun reset() {
        dataStore.edit {
            it[STEP] = OnboardingStep.Welcome.name
            it[DONE] = false
            it.remove(TAG)
            it.remove(PROFILE)
        }
    }

    private companion object {
        val STEP = stringPreferencesKey("onboarding_step")
        val DONE = booleanPreferencesKey("onboarding_done")
        val TAG = stringPreferencesKey("onboarding_tag")
        val PROFILE = longPreferencesKey("onboarding_profile")
    }
}
