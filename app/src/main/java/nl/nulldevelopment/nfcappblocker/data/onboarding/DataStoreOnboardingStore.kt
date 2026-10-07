package nl.nulldevelopment.nfcappblocker.data.onboarding

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class DataStoreOnboardingStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : OnboardingStore {

    override fun observe(): Flow<OnboardingProgress> = dataStore.data
        .map { prefs ->
            val step = prefs[STEP]?.let { name -> OnboardingStep.entries.firstOrNull { it.name == name } }
            when {
                prefs[DONE] == true -> OnboardingProgress.Done
                step != null -> OnboardingProgress.InProgress(step)
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

    override suspend fun finish() {
        dataStore.edit { it[DONE] = true }
    }

    override suspend fun reset() = setStep(OnboardingStep.Welcome)

    private companion object {
        val STEP = stringPreferencesKey("onboarding_step")
        val DONE = booleanPreferencesKey("onboarding_done")
    }
}
