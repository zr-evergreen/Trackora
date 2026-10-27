package com.evergreen.trackora.feature.today

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.ui.isLargeTextScale
import com.evergreen.trackora.ui.text.forUserContent
import com.evergreen.trackora.ui.theme.TrackoraIcons
import com.evergreen.trackora.ui.theme.TrackoraMotion
import com.evergreen.trackora.ui.theme.cardContainer
import com.evergreen.trackora.util.PersianDigits

/**
 * One-line capture for a new job.
 *
 * ### Why this exists
 *
 * Recording a batch of finished work used to cost six interactions per item —
 * open the form, tap the title, type, dismiss the keyboard, scroll, save — with
 * the Save button below the fold on a small screen. For someone logging ten
 * pieces after finishing a batch, that is the difference between using the app
 * and going back to a paper notebook.
 *
 * ### Behaviour
 *
 * The field commits on the keyboard's Done action and immediately clears while
 * keeping focus, so a burst of entries is a burst of typing rather than a
 * sequence of round trips. The keyboard never closes between items.
 *
 * ### Why quantity is here and nothing else is
 *
 * Status, date, photo and the custom fields all take defaults, because asking
 * for them would rebuild the form this exists to avoid. Quantity is the
 * exception, and it earns the exception: work in Trackora is counted in units,
 * Reports leads with the total, and a pieceworker's billable number is the
 * count. Capturing a title without it produces a record that cannot answer the
 * question the user opened the app to answer.
 *
 * It is optional and visually secondary — narrow, after a divider, and skipped
 * entirely by pressing Done from the title.
 *
 * Deliberately not parsed out of the title. Reading a trailing number would
 * turn "سفارش Nike ۴۲" into a job called «سفارش Nike» with a quantity of 42,
 * and order numbers in titles are common.
 *
 * Blank input is ignored rather than rejected: pressing Done on an empty field
 * is a user changing their mind, not an error worth a message.
 *
 * ### Look
 *
 * Both fields share one card, so they read as one sentence — what, and how
 * many — rather than two forms. The card's edge turns indigo while either
 * field has focus, and the add button fills in once there is something to add.
 */
@Composable
fun QuickAddField(
    onAdd: (String, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    var titleFocused by remember { mutableStateOf(false) }
    var quantityFocused by remember { mutableStateOf(false) }

    fun commit() {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            // Normalised because the field accepts Persian digits, which is
            // what a Persian keyboard produces; the column stores a number.
            onAdd(trimmed, PersianDigits.toWestern(quantity).toIntOrNull())
            text = ""
            quantity = ""
        }
    }

    val stacked = isLargeTextScale()
    val borderColor by animateColorAsState(
        targetValue = if (titleFocused || quantityFocused) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = tween(TrackoraMotion.SHORT),
        label = "quickAddBorder"
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.cardContainer,
        border = BorderStroke(1.dp, borderColor)
    ) {
        val title: @Composable (Modifier) -> Unit = { fieldModifier ->
            TextField(
                value = text,
                onValueChange = { text = it },
                modifier = fieldModifier
                    .focusRequester(focusRequester)
                    .onFocusChanged { titleFocused = it.isFocused },
                placeholder = { Text(stringResource(id = R.string.today_quick_add_hint)) },
                singleLine = true,
                // User content: the title may be Persian, Latin or both, so it
                // resolves its own direction rather than inheriting the layout's.
                textStyle = LocalTextStyle.current.forUserContent(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                colors = borderlessFieldColors()
            )
        }
        val count: @Composable (Modifier) -> Unit = { fieldModifier ->
            TextField(
                value = quantity,
                // Digit-only, so a stray letter cannot make the field unparseable.
                // isDigit is Unicode-aware, which is what lets Persian digits through.
                onValueChange = { input -> quantity = input.filter { it.isDigit() }.take(6) },
                modifier = fieldModifier.onFocusChanged { quantityFocused = it.isFocused },
                placeholder = { Text(stringResource(id = R.string.today_quick_add_qty)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                colors = borderlessFieldColors()
            )
        }
        val add: @Composable () -> Unit = {
            // A visible commit affordance as well as the IME action: the Done
            // key is discoverable only once you have already found it.
            FilledIconButton(
                onClick = ::commit,
                enabled = text.isNotBlank(),
                modifier = Modifier.padding(end = 6.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    imageVector = TrackoraIcons.Add,
                    contentDescription = stringResource(id = R.string.today_quick_add_action)
                )
            }
        }

        // Side by side the quantity field is a fixed width, and a fixed width
        // with scaled text clips: at 1.5x «تعداد» broke across two lines inside
        // it. At large text the title takes the whole first line instead and
        // quantity and the button share the second.
        if (stacked) {
            Column(modifier = Modifier.fillMaxWidth()) {
                title(Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    count(Modifier.weight(1f))
                    add()
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                title(Modifier.weight(1f))
                VerticalDivider(
                    modifier = Modifier.height(28.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
                count(Modifier.width(84.dp))
                add()
            }
        }
    }
}

/** Fields with no fill and no underline: the card around them is the field. */
@Composable
private fun borderlessFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)
