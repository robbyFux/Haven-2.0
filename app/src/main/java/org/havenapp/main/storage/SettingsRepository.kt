package org.havenapp.main.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.havenapp.main.detection.DetectionMode
import org.havenapp.main.detection.DetectionZone
import org.havenapp.main.sensor.CameraPosition
import org.havenapp.main.sensor.Sensitivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val KEY_SENSITIVITY = stringPreferencesKey("sensitivity")
        private val KEY_CAMERA_POSITION = stringPreferencesKey("camera_position")
        private val KEY_COUNTDOWN_SECONDS = intPreferencesKey("countdown_seconds")
        private val KEY_CALIBRATION_SECONDS = intPreferencesKey("calibration_seconds")
        private val KEY_DETECTION_MODE = stringPreferencesKey("detection_mode")
        private val KEY_DETECTION_ZONE = stringPreferencesKey("detection_zone")
        private val KEY_MOTION_ENABLED = booleanPreferencesKey("sensor_motion_enabled")
        private val KEY_LIGHT_ENABLED = booleanPreferencesKey("sensor_light_enabled")
        private val KEY_MIC_ENABLED = booleanPreferencesKey("sensor_mic_enabled")
        private val KEY_CAMERA_ENABLED = booleanPreferencesKey("sensor_camera_enabled")
        private val KEY_CLIP_DURATION_SECONDS = intPreferencesKey("clip_duration_seconds")
        private val KEY_MEDIA_ENCRYPTED_V1 = booleanPreferencesKey("media_encrypted_v1")
    }

    val sensitivity: Flow<Sensitivity> = dataStore.data.map { prefs ->
        prefs[KEY_SENSITIVITY]
            ?.let { runCatching { Sensitivity.valueOf(it) }.getOrNull() }
            ?: Sensitivity.MEDIUM
    }

    val cameraPosition: Flow<CameraPosition> = dataStore.data.map { prefs ->
        prefs[KEY_CAMERA_POSITION]
            ?.let { runCatching { CameraPosition.valueOf(it) }.getOrNull() }
            ?: CameraPosition.BACK
    }

    suspend fun setSensitivity(sensitivity: Sensitivity) {
        dataStore.edit { it[KEY_SENSITIVITY] = sensitivity.name }
    }

    suspend fun setCameraPosition(position: CameraPosition) {
        dataStore.edit { it[KEY_CAMERA_POSITION] = position.name }
    }

    val countdownSeconds: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_COUNTDOWN_SECONDS] ?: 60
    }

    suspend fun setCountdownSeconds(seconds: Int) {
        dataStore.edit { it[KEY_COUNTDOWN_SECONDS] = seconds }
    }

    val calibrationSeconds: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_CALIBRATION_SECONDS] ?: 10
    }

    suspend fun setCalibrationSeconds(seconds: Int) {
        dataStore.edit { it[KEY_CALIBRATION_SECONDS] = seconds }
    }

    val detectionMode: Flow<DetectionMode> = dataStore.data.map { prefs ->
        prefs[KEY_DETECTION_MODE]
            ?.let { runCatching { DetectionMode.valueOf(it) }.getOrNull() }
            ?: DetectionMode.MOTION_ONLY
    }

    suspend fun setDetectionMode(mode: DetectionMode) {
        dataStore.edit { it[KEY_DETECTION_MODE] = mode.name }
    }

    val detectionZone: Flow<DetectionZone?> = dataStore.data.map { prefs ->
        prefs[KEY_DETECTION_ZONE]?.let { DetectionZone.fromString(it) }
    }

    suspend fun setDetectionZone(zone: DetectionZone?) {
        dataStore.edit { prefs ->
            if (zone != null) prefs[KEY_DETECTION_ZONE] = zone.serialize()
            else prefs.remove(KEY_DETECTION_ZONE)
        }
    }

    val motionEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_MOTION_ENABLED] ?: true }
    val lightEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_LIGHT_ENABLED] ?: true }
    val micEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_MIC_ENABLED] ?: true }
    val cameraEnabled: Flow<Boolean> = dataStore.data.map { it[KEY_CAMERA_ENABLED] ?: true }

    suspend fun setMotionEnabled(enabled: Boolean) { dataStore.edit { it[KEY_MOTION_ENABLED] = enabled } }
    suspend fun setLightEnabled(enabled: Boolean) { dataStore.edit { it[KEY_LIGHT_ENABLED] = enabled } }
    suspend fun setMicEnabled(enabled: Boolean) { dataStore.edit { it[KEY_MIC_ENABLED] = enabled } }
    suspend fun setCameraEnabled(enabled: Boolean) { dataStore.edit { it[KEY_CAMERA_ENABLED] = enabled } }

    /** Clip duration in seconds for video recording on sensor trigger. Default: 30 s. */
    val clipDurationSeconds: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_CLIP_DURATION_SECONDS] ?: 30
    }

    suspend fun setClipDurationSeconds(seconds: Int) {
        dataStore.edit { it[KEY_CLIP_DURATION_SECONDS] = seconds }
    }

    /** Whether the one-shot media encryption migration (SEC-02) has already been run. */
    val mediaEncryptedV1: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_MEDIA_ENCRYPTED_V1] ?: false
    }

    suspend fun setMediaEncryptedV1(done: Boolean) {
        dataStore.edit { it[KEY_MEDIA_ENCRYPTED_V1] = done }
    }
}
