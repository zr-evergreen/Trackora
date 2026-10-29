package com.evergreen.trackora.theme

import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for the synchronous copy of the theme that lets the app apply it
 * before its first activity exists, so a theme other than the phone's own
 * no longer recreates the activity on every cold start.
 */
class ThemeManagerTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val launchPrefs = FakeSharedPreferences()
    private lateinit var scope: CoroutineScope
    private lateinit var store: DataStore<Preferences>
    private lateinit var manager: ThemeManager

    @Before
    fun setUp() {
        mockkStatic(AppCompatDelegate::class)
        every { AppCompatDelegate.setDefaultNightMode(any()) } just Runs
        scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        store = PreferenceDataStoreFactory.create(scope = scope) { File(folder.root, "theme.preferences_pb") }
        manager = ThemeManager(store, launchPrefs)
    }

    @After
    fun tearDown() {
        scope.cancel()
        unmockkStatic(AppCompatDelegate::class)
    }

    /** The next process: a new manager over the same copy. */
    private fun nextLaunch() = ThemeManager(mockk(relaxed = true), launchPrefs)

    @Test
    fun `a chosen theme is in place at the next launch before any activity`() = runTest {
        manager.setTheme(AppThemeMode.DARK)

        nextLaunch().applyLaunchTheme()

        verify(exactly = 2) { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES) }
    }

    @Test
    fun `with no copy yet the launch leaves the theme to the activity`() {
        manager.applyLaunchTheme()

        verify(exactly = 0) { AppCompatDelegate.setDefaultNightMode(any()) }
    }

    @Test
    fun `the first launch after the update makes the copy from the stored choice`() = runTest {
        // Chosen under the previous version, which kept no copy.
        store.edit { it[stringPreferencesKey("app_theme")] = AppThemeMode.LIGHT.name }

        manager.applySavedTheme()

        verify { AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO) }
        assertEquals(AppThemeMode.LIGHT.name, launchPrefs.getString("app_theme", null))
    }

    @Test
    fun `the copy is written only when the theme changes`() {
        manager.applyTheme(AppThemeMode.SYSTEM)
        manager.applyTheme(AppThemeMode.SYSTEM)
        manager.applyTheme(AppThemeMode.DARK)

        assertEquals(2, launchPrefs.writes)
    }

    @Test
    fun `a copy the app does not recognise is ignored`() {
        launchPrefs.edit().putString("app_theme", "SEPIA").apply()

        nextLaunch().applyLaunchTheme()

        verify(exactly = 0) { AppCompatDelegate.setDefaultNightMode(any()) }
    }
}

/** Just enough SharedPreferences for strings, counting the writes. */
private class FakeSharedPreferences : SharedPreferences {
    private val values = mutableMapOf<String, String?>()
    var writes = 0
        private set

    override fun getString(key: String, defValue: String?): String? = values[key] ?: defValue
    override fun contains(key: String): Boolean = key in values
    override fun getAll(): Map<String, *> = values.toMap()
    override fun edit(): SharedPreferences.Editor = Editor()

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? = defValues
    override fun getInt(key: String, defValue: Int): Int = defValue
    override fun getLong(key: String, defValue: Long): Long = defValue
    override fun getFloat(key: String, defValue: Float): Float = defValue
    override fun getBoolean(key: String, defValue: Boolean): Boolean = defValue
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) = Unit

    private inner class Editor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, String?>()
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun apply() {
            values.putAll(pending)
            writes++
        }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun putStringSet(key: String, values: Set<String>?) = this
        override fun putInt(key: String, value: Int) = this
        override fun putLong(key: String, value: Long) = this
        override fun putFloat(key: String, value: Float) = this
        override fun putBoolean(key: String, value: Boolean) = this
        override fun remove(key: String) = this
        override fun clear() = this
    }
}
