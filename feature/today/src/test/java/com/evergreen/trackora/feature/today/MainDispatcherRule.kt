package com.evergreen.trackora.feature.today

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps `Dispatchers.Main` for a test dispatcher for the duration of a test.
 *
 * `viewModelScope` is hard-wired to `Dispatchers.Main`, which has no
 * implementation on a plain JVM test JVM — without this rule every ViewModel
 * test fails at construction with "Module with the Main dispatcher had failed
 * to initialize".
 *
 * The default is [UnconfinedTestDispatcher] so that work launched in a
 * ViewModel's `init` block has already run by the time the constructor
 * returns. That keeps the tests free of `advanceUntilIdle()` bookkeeping when
 * all they care about is the resulting state. Pass a `StandardTestDispatcher`
 * when a test needs to observe an intermediate state, such as the loading flag
 * before data arrives.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
