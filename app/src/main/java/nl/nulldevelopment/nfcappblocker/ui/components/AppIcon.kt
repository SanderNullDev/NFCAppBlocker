package nl.nulldevelopment.nfcappblocker.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import nl.nulldevelopment.nfcappblocker.apps.AppIconLoader

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppIconEntryPoint {
    fun appIconLoader(): AppIconLoader
}

/** An installed app's icon; an empty space of the same size while loading or when uninstalled. */
@Composable
fun AppIcon(packageName: String, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val context = LocalContext.current
    val loader = remember(context) {
        EntryPointAccessors.fromApplication(context.applicationContext, AppIconEntryPoint::class.java).appIconLoader()
    }
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val icon by produceState<ImageBitmap?>(loader.cached(packageName), packageName, sizePx) {
        value = loader.load(packageName, sizePx)
    }
    val current = icon
    if (current == null) {
        Box(modifier.size(size))
    } else {
        // Decorative: the app's name is always shown next to it.
        Image(current, contentDescription = null, modifier = modifier.size(size))
    }
}
