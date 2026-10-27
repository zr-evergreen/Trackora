package com.evergreen.trackora.feature.addedit

import android.net.Uri
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import coil.ImageLoader
import coil.fetch.Fetcher
import coil.request.Options
import com.evergreen.trackora.ui.theme.TrackoraTheme
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import kotlin.math.abs

/**
 * Tests for [PhotoSelector], the photo part of the entry form.
 *
 * Photos come from a stub Coil loader, so "still decoding" and "cannot be
 * read" are states the test holds on purpose rather than races it hopes to
 * catch.
 */
@RunWith(AndroidJUnit4::class)
class PhotoSelectorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val takePhoto = context.getString(R.string.action_take_photo)
    private val chooseFromGallery = context.getString(R.string.action_choose_from_gallery)
    private val importing = context.getString(R.string.photo_importing)
    private val cancel = context.getString(R.string.action_cancel)
    private val removePhoto = context.getString(R.string.action_remove_photo)

    private val isButton = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)
    private val isSpinner =
        SemanticsMatcher.expectValue(SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate)

    @After
    fun restoreImageLoader() {
        Coil.setImageLoader(ImageLoader(context))
    }

    @Test
    fun photoChoicesStayAvailableWhileAnImportRuns() {
        // A slow copy used to replace both choices with a spinner, leaving the
        // user no way to pick again.
        var gallery = 0
        var camera = 0
        setSelector(isImporting = true, onTakePhoto = { camera++ }, onChooseFromGallery = { gallery++ })

        composeRule.onNodeWithText(importing).assertIsDisplayed()
        composeRule.onNodeWithText(chooseFromGallery).assertIsDisplayed().performClick()
        composeRule.onNodeWithText(takePhoto).assertIsDisplayed().performClick()

        assertEquals(1, gallery)
        assertEquals(1, camera)
    }

    @Test
    fun anImportCanBeCancelled() {
        var cancelled = 0
        setSelector(isImporting = true, onCancelImport = { cancelled++ })

        composeRule.onNodeWithText(cancel).assertIsDisplayed().performClick()

        assertEquals(1, cancelled)
    }

    @Test
    fun photoChoicesAreAnnouncedAsButtons() {
        setSelector()

        composeRule.onNodeWithText(takePhoto).assertHasClickAction().assert(isButton)
        composeRule.onNodeWithText(chooseFromGallery).assertHasClickAction().assert(isButton)
    }

    @Test
    fun aPhotoStillDecodingShowsProgressInItsFrame() {
        useStubLoader(fail = false)
        setSelector(photoUri = "$STUB_SCHEME://never")

        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesMatching(isSpinner, hasAnyAncestor(hasTestTag(PhotoFrameTag))) > 0
        }
        composeRule.onNodeWithContentDescription(removePhoto).assertIsDisplayed()
    }

    @Test
    @SdkSuppress(minSdkVersion = 26) // captureToImage
    fun aPhotoThatCannotBeReadLeavesAVisibleFrame() {
        // The frame used to be transparent, so an undrawn photo read as an
        // empty gap with a lone remove button in it.
        useStubLoader(fail = true)
        val placeholder = setSelector(photoUri = "$STUB_SCHEME://unreadable")

        composeRule.waitUntil(TIMEOUT_MS) {
            composeRule.onAllNodesMatching(isSpinner, hasAnyAncestor(hasTestTag(PhotoFrameTag))) == 0
        }
        val frame = composeRule.onNodeWithTag(PhotoFrameTag).captureToImage().toPixelMap()
        // Lower start quarter: clear of the remove button and the spinner.
        val sample = frame[frame.width / 4, frame.height * 3 / 4]

        assertTrue("expected the placeholder $placeholder, found $sample", sample.isCloseTo(placeholder))
    }

    /** Shows the selector and returns the frame's expected placeholder colour. */
    private fun setSelector(
        photoUri: String? = null,
        isImporting: Boolean = false,
        onTakePhoto: () -> Unit = {},
        onChooseFromGallery: () -> Unit = {},
        onCancelImport: () -> Unit = {},
    ): Color {
        var placeholder = Color.Unspecified
        composeRule.setContent {
            TrackoraTheme(darkTheme = false) {
                placeholder = MaterialTheme.colorScheme.surfaceContainer
                PhotoSelector(
                    photoUri = photoUri,
                    isImporting = isImporting,
                    onTakePhoto = onTakePhoto,
                    onChooseFromGallery = onChooseFromGallery,
                    onRemovePhoto = {},
                    onCancelImport = onCancelImport
                )
            }
        }
        composeRule.waitForIdle()
        return placeholder
    }

    private fun useStubLoader(fail: Boolean) {
        Coil.setImageLoader(
            ImageLoader.Builder(context)
                .components { add(StubFetcher.Factory(fail)) }
                .build()
        )
    }

    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesMatching(
        matcher: SemanticsMatcher,
        within: SemanticsMatcher,
    ): Int = onAllNodes(matcher and within, useUnmergedTree = true).fetchSemanticsNodes().size

    private fun Color.isCloseTo(other: Color): Boolean =
        abs(red - other.red) < 0.02f && abs(green - other.green) < 0.02f && abs(blue - other.blue) < 0.02f

    /** Loads nothing: either never finishes, or fails at once. */
    private class StubFetcher(private val fail: Boolean) : Fetcher {
        override suspend fun fetch(): Nothing =
            if (fail) throw IOException("unreadable") else awaitCancellation()

        class Factory(private val fail: Boolean) : Fetcher.Factory<Uri> {
            override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
                if (data.scheme == STUB_SCHEME) StubFetcher(fail) else null
        }
    }

    private companion object {
        const val STUB_SCHEME = "trackora-test"
        const val TIMEOUT_MS = 5_000L
    }
}
