package fr.president.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import com.badlogic.gdx.backends.android.AndroidApplication
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import fr.president.game.PresidentGame

/** Activité Android : héberge le jeu libGDX. */
class AndroidLauncher : AndroidApplication() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        recordUncaughtErrors()
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

    /**
     * Une erreur qui échapperait au jeu (autre fil, code natif d'Android) est enregistrée avant la
     * fermeture : l'écran d'accueil la signale au lancement suivant.
     */
    private fun recordUncaughtErrors() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val appContext = applicationContext
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            AndroidPlatform.writeCrash(appContext, "fil ${thread.name}\n${Log.getStackTraceString(error)}")
            previous?.uncaughtException(thread, error)
        }
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
