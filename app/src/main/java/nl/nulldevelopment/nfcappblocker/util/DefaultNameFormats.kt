package nl.nulldevelopment.nfcappblocker.util

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import nl.nulldevelopment.nfcappblocker.R
import javax.inject.Inject

/** Localised default names ("Tag 1", "Profile 1"), kept out of ViewModels so they test without Android. */
interface DefaultNameFormats {
    fun tag(number: Int): String
    fun profile(number: Int): String
}

class AndroidDefaultNameFormats @Inject constructor(@ApplicationContext private val context: Context) : DefaultNameFormats {
    override fun tag(number: Int): String = context.getString(R.string.tag_default_name, number)
    override fun profile(number: Int): String = context.getString(R.string.profile_default_name, number)
}
