package es.carlospb.modoguardia.alarm

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import es.carlospb.modoguardia.data.GuardSettings
import es.carlospb.modoguardia.data.SoundSource
import es.carlospb.modoguardia.data.VolumeMode
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Sonido + volumen + vibración de la alarma. Siempre por el uso ALARM de Android: no le afecta el
 * modo silencio ni el volumen de timbre, y "No molestar" lo deja pasar salvo en "silencio total".
 *
 * Cadena de reserva del sonido: tono elegido → sirena propia → ToneGenerator. Si un paso falla,
 * se pasa al siguiente: la alarma nunca se queda muda por un archivo de sonido roto.
 */
class AlarmPlayer(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var tone: ToneGenerator? = null
    private var focus: AudioFocusRequest? = null
    private var vibrator: Vibrator? = null

    /** Arranca. Devuelve un aviso para el usuario si algo no fue como se configuró. */
    fun start(settings: GuardSettings): String? {
        val notes = mutableListOf<String>()
        applyVolume(settings)?.let(notes::add)
        requestFocus()
        val uri = when (settings.soundSource) {
            SoundSource.SIREN -> GuardNotifications.sirenUri(context)
            SoundSource.SYSTEM_ALARM -> RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
                ?: GuardNotifications.sirenUri(context).also { notes += "El tono de alarma del sistema es «Ninguno»: suena la sirena de Modo Guardia." }
        }
        if (!play(uri)) {
            if (uri != GuardNotifications.sirenUri(context) && play(GuardNotifications.sirenUri(context))) {
                notes += "No se pudo reproducir el tono del sistema: suena la sirena de Modo Guardia."
            } else {
                startTone()
                notes += "No se pudo reproducir ningún archivo de sonido: suena un tono de emergencia."
            }
        }
        if (settings.vibrate) startVibration()
        return notes.joinToString("\n").ifEmpty { null }
    }

    fun stop() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        tone?.runCatching { stopTone() }
        tone?.release()
        tone = null
        vibrator?.cancel()
        vibrator = null
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
        VolumeRestore.restoreIfPending(context)
    }

    private fun applyVolume(s: GuardSettings): String? {
        val stream = AudioManager.STREAM_ALARM
        val current = audio.getStreamVolume(stream)
        val max = audio.getStreamMaxVolume(stream)
        val min = audio.getStreamMinVolume(stream)
        var note: String? = null
        val target = when (s.volumeMode) {
            VolumeMode.MAX -> max
            VolumeMode.CUSTOM -> (max * s.customVolumePercent / 100f).roundToInt().coerceIn(maxOf(min, 1), max)
            VolumeMode.CURRENT -> if (current <= 0) {
                note = "El volumen de alarma estaba a 0: se ha subido al 60 % mientras suena."
                ceil(max * 0.6).toInt()
            } else {
                current
            }
        }
        if (target == current) return note
        VolumeRestore.save(context, current)
        return try {
            audio.setStreamVolume(stream, target, 0)
            note
        } catch (e: SecurityException) {
            // Android lo rechaza p. ej. si el cambio alteraría "No molestar". No se fuerza.
            Log.w(TAG, "No se pudo cambiar el volumen de alarma", e)
            VolumeRestore.clear(context)
            "Android no permitió cambiar el volumen de alarma: suena con el volumen actual."
        }
    }

    private fun requestFocus() {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(GuardNotifications.alarmAudioAttributes())
            .setOnAudioFocusChangeListener { /* La alarma no se calla por perder el foco. */ }
            .build()
        // El resultado no condiciona nada: durante una llamada el foco se deniega y aun así debe sonar.
        audio.requestAudioFocus(request)
        focus = request
    }

    private fun play(uri: Uri): Boolean {
        player?.release()
        player = null
        return try {
            player = MediaPlayer().apply {
                setAudioAttributes(GuardNotifications.alarmAudioAttributes())
                setDataSource(context, uri)
                isLooping = true
                setWakeMode(context, PowerManager.PARTIAL_WAKE_LOCK)
                setOnErrorListener { mp, what, extra ->
                    Log.e(TAG, "MediaPlayer error $what/$extra: se pasa al tono de emergencia")
                    mp.release()
                    if (player === mp) player = null
                    startTone()
                    true
                }
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo reproducir $uri", e)
            player?.release()
            player = null
            false
        }
    }

    private fun startTone() {
        if (tone != null) return
        tone = try {
            ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME).also {
                it.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK)
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "ToneGenerator no disponible", e)
            null
        }
    }

    private fun startVibration() {
        val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (v == null || !v.hasVibrator()) return
        val effect = VibrationEffect.createWaveform(GuardNotifications.VIBRATION_PATTERN, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            v.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            @Suppress("DEPRECATION")
            v.vibrate(effect, GuardNotifications.alarmAudioAttributes())
        }
        vibrator = v
    }

    private companion object {
        const val TAG = "AlarmPlayer"
    }
}

/**
 * Volumen original del canal de alarma, guardado en disco ANTES de cambiarlo: si el proceso muere
 * con la alarma sonando, se restaura al volver a arrancar la app. Nunca se deja cambiado.
 */
@android.annotation.SuppressLint("ApplySharedPref") // commit() a propósito: debe estar en disco antes de tocar el volumen.
object VolumeRestore {
    private const val PREFS = "volume_restore"
    private const val KEY = "original_alarm_volume"

    fun save(context: Context, volume: Int) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Si ya hay uno pendiente, ese es el verdadero original: no se sobrescribe.
        if (!prefs.contains(KEY)) prefs.edit().putInt(KEY, volume).commit()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).commit()
    }

    fun restoreIfPending(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY)) return
        val original = prefs.getInt(KEY, -1)
        if (original >= 0) {
            try {
                context.getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, original, 0)
            } catch (e: SecurityException) {
                Log.w("VolumeRestore", "No se pudo restaurar el volumen", e)
            }
        }
        prefs.edit().remove(KEY).commit()
    }
}
