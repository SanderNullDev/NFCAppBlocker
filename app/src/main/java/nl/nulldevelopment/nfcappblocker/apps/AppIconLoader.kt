package nl.nulldevelopment.nfcappblocker.apps

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import nl.nulldevelopment.nfcappblocker.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

/** Loads app icons off the main thread and keeps recently used ones in memory. */
@Singleton
class AppIconLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val cache = LruCache<String, ImageBitmap>(MAX_ICONS)

    fun cached(packageName: String): ImageBitmap? = cache.get(packageName)

    /** Null for an app that isn't installed. */
    suspend fun load(packageName: String, sizePx: Int): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        return withContext(ioDispatcher) {
            try {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(sizePx, sizePx)
                    .asImageBitmap()
                    .also { cache.put(packageName, it) }
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }
    }

    private companion object {
        const val MAX_ICONS = 200
    }
}
