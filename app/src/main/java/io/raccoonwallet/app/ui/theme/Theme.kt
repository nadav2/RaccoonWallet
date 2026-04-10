package io.raccoonwallet.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val VaultLightScheme = lightColorScheme(
    primary = VaultPrimaryLight,
    onPrimary = VaultOnPrimaryLight,
    primaryContainer = VaultPrimaryContainerLight,
    onPrimaryContainer = VaultOnPrimaryContainerLight,
    secondary = VaultSecondaryLight,
    onSecondary = VaultOnSecondaryLight,
    secondaryContainer = VaultSecondaryContainerLight,
    onSecondaryContainer = VaultOnSecondaryContainerLight,
    background = BackgroundLight, onBackground = OnBackgroundLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight, onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorLight, onError = OnErrorLight,
    errorContainer = ErrorContainerLight, onErrorContainer = OnErrorContainerLight
)

private val VaultDarkScheme = darkColorScheme(
    primary = VaultPrimaryDark,
    onPrimary = VaultOnPrimaryDark,
    primaryContainer = VaultPrimaryContainerDark,
    onPrimaryContainer = VaultOnPrimaryContainerDark,
    secondary = VaultSecondaryDark,
    onSecondary = VaultOnSecondaryDark,
    secondaryContainer = VaultSecondaryContainerDark,
    onSecondaryContainer = VaultOnSecondaryContainerDark,
    background = BackgroundDark, onBackground = OnBackgroundDark,
    surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark, onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = ErrorDark, onError = OnErrorDark,
    errorContainer = ErrorContainerDark, onErrorContainer = OnErrorContainerDark
)

private val SignerLightScheme = lightColorScheme(
    primary = SignerPrimaryLight,
    onPrimary = SignerOnPrimaryLight,
    primaryContainer = SignerPrimaryContainerLight,
    onPrimaryContainer = SignerOnPrimaryContainerLight,
    secondary = SignerSecondaryLight,
    onSecondary = SignerOnSecondaryLight,
    secondaryContainer = SignerSecondaryContainerLight,
    onSecondaryContainer = SignerOnSecondaryContainerLight,
    background = BackgroundLight, onBackground = OnBackgroundLight,
    surface = SurfaceLight, onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight, onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = ErrorLight, onError = OnErrorLight,
    errorContainer = ErrorContainerLight, onErrorContainer = OnErrorContainerLight
)

private val SignerDarkScheme = darkColorScheme(
    primary = SignerPrimaryDark,
    onPrimary = SignerOnPrimaryDark,
    primaryContainer = SignerPrimaryContainerDark,
    onPrimaryContainer = SignerOnPrimaryContainerDark,
    secondary = SignerSecondaryDark,
    onSecondary = SignerOnSecondaryDark,
    secondaryContainer = SignerSecondaryContainerDark,
    onSecondaryContainer = SignerOnSecondaryContainerDark,
    background = BackgroundDark, onBackground = OnBackgroundDark,
    surface = SurfaceDark, onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark, onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
    error = ErrorDark, onError = OnErrorDark,
    errorContainer = ErrorContainerDark, onErrorContainer = OnErrorContainerDark
)

/**
 * @param isSigner null = no mode selected yet (ModeSelect), uses Vault theme as default
 */
@Composable
fun RaccoonWalletTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isSigner: Boolean? = null,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme && isSigner == true -> SignerDarkScheme
        darkTheme -> VaultDarkScheme
        isSigner == true -> SignerLightScheme
        else -> VaultLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
