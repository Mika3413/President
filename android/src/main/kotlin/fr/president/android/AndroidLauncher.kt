package fr.president.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import fr.president.game.PresidentGame

/** Activité Android : héberge le jeu libGDX. */
class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationChannels.create(this)
        requestNotificationPermission()
        val config = AndroidApplicationConfiguration().apply {
            useImmersiveMode = true
            numSamples = SAMPLES
            useAccelerometer = false
            useCompass = false
        }
        initialize(PresidentGame(AndroidPlatform(this)), config)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), PERMISSION_REQUEST)
        }
    }

    private companion object {
        const val SAMPLES = 2
        const val PERMISSION_REQUEST = 1
    }
}
