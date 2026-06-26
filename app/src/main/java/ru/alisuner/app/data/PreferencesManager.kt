package ru.alisuner.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Менеджер сохранения настроек приложения.
 *
 * Обычные настройки (BPM, громкость, тональность, режим тюнера)
 * хранятся в стандартных SharedPreferences.
 *
 * Чувствительные данные (API-ключ Suno) хранятся в EncryptedSharedPreferences
 * (AES-256 через AndroidKeyStore).
 */
class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences = try {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            SECURE_PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (_: Exception) {
        // Фолбэк на обычные prefs, если шифрование недоступно
        context.getSharedPreferences(SECURE_PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ─── Метроном ───

    var metronomeBpm: Int
        get() = prefs.getInt(KEY_METRONOME_BPM, DEFAULT_BPM)
        set(value) = prefs.edit().putInt(KEY_METRONOME_BPM, value).apply()

    var metronomeVolume: Float
        get() = prefs.getFloat(KEY_METRONOME_VOLUME, DEFAULT_VOLUME)
        set(value) = prefs.edit().putFloat(KEY_METRONOME_VOLUME, value).apply()

    /** Размер такта: "2/4", "3/4", "4/4", "6/8" */
    var metronomeTimeSignature: String
        get() = prefs.getString(KEY_METRONOME_TIME_SIG, DEFAULT_TIME_SIG) ?: DEFAULT_TIME_SIG
        set(value) = prefs.edit().putString(KEY_METRONOME_TIME_SIG, value).apply()

    // ─── Тюнер ───

    var tunerMode: String
        get() = prefs.getString(KEY_TUNER_MODE, DEFAULT_TUNER_MODE) ?: DEFAULT_TUNER_MODE
        set(value) = prefs.edit().putString(KEY_TUNER_MODE, value).apply()

    var tunerCalibrationHz: Int
        get() = prefs.getInt(KEY_TUNER_CALIBRATION_HZ, DEFAULT_TUNER_CALIBRATION_HZ)
        set(value) = prefs.edit().putInt(KEY_TUNER_CALIBRATION_HZ, value.coerceIn(430, 450)).apply()

    var tunerHighAccuracy: Boolean
        get() = prefs.getBoolean(KEY_TUNER_HIGH_ACCURACY, DEFAULT_TUNER_HIGH_ACCURACY)
        set(value) = prefs.edit().putBoolean(KEY_TUNER_HIGH_ACCURACY, value).apply()

    var tunerTuningId: String
        get() = prefs.getString(KEY_TUNER_TUNING_ID, DEFAULT_TUNER_TUNING_ID) ?: DEFAULT_TUNER_TUNING_ID
        set(value) = prefs.edit().putString(KEY_TUNER_TUNING_ID, value).apply()

    // ─── Самоучитель ───

    var learnSelectedKey: String
        get() = prefs.getString(KEY_LEARN_SELECTED_KEY, DEFAULT_SELECTED_KEY) ?: DEFAULT_SELECTED_KEY
        set(value) = prefs.edit().putString(KEY_LEARN_SELECTED_KEY, value).apply()

    // ─── Аранжировщик (API-ключ — зашифрован) ───

    var sunoApiKey: String
        get() = securePrefs.getString(KEY_SUNO_API_KEY, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_SUNO_API_KEY, value).apply()

    companion object {
        private const val PREFS_NAME = "alisuner_prefs"
        private const val SECURE_PREFS_NAME = "alisuner_secure_prefs"

        // Ключи
        private const val KEY_METRONOME_BPM = "metronome_bpm"
        private const val KEY_METRONOME_VOLUME = "metronome_volume"
        private const val KEY_METRONOME_TIME_SIG = "metronome_time_sig"
        private const val KEY_TUNER_MODE = "tuner_mode"
        private const val KEY_TUNER_CALIBRATION_HZ = "tuner_calibration_hz"
        private const val KEY_TUNER_HIGH_ACCURACY = "tuner_high_accuracy"
        private const val KEY_TUNER_TUNING_ID = "tuner_tuning_id"
        private const val KEY_LEARN_SELECTED_KEY = "learn_selected_key"
        private const val KEY_SUNO_API_KEY = "suno_api_key"

        // Дефолтные значения
        const val DEFAULT_BPM = 120
        const val DEFAULT_VOLUME = 0.7f
        const val DEFAULT_TIME_SIG = "4/4"
        const val DEFAULT_TUNER_MODE = "AUTO"
        const val DEFAULT_TUNER_CALIBRATION_HZ = 440
        const val DEFAULT_TUNER_HIGH_ACCURACY = false
        const val DEFAULT_TUNER_TUNING_ID = "guitar_standard"
        const val DEFAULT_SELECTED_KEY = "C"
    }
}
