package com.evergreen.trackora.feature.today

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.evergreen.trackora.util.PersianDigits
import com.evergreen.trackora.ui.text.forUserContent

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
 * It is optional and visually secondary — narrow, unlabelled until focused,
 * and skipped entirely by pressing Done from the title.
 *
 * Deliberately not parsed out of the title. Reading a trailing number would
 * turn "سفارش Nike ۴۲" into a job called «سفارش Nike» with a quantity of 42,
 * and order numbers in titles are common.
 *
 * Blank input is ignored rather than rejected: pressing Done on an empty field
 * is a user changing their mind, not an error worth a message.
 */
@Composable
fun QuickAddField(
    onAdd: (String, Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }

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

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        modifier = Modifier
            .weight(1f)
            .focusRequester(focusRequester),
        placeholder = { Text(stringResource(id = R.string.today_quick_add_hint)) },
        singleLine = true,
        // User content: the title may be Persian, Latin or both, so it resolves
        // its own direction rather than inheriting the layout's.
        textStyle = LocalTextStyle.current.forUserContent(),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { commit() }),
        trailingIcon = {
            // A visible commit affordance as well as the IME action: the Done
            // key is discoverable only once you have already found it.
            IconButton(
                onClick = ::commit,
                enabled = text.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(id = R.string.today_quick_add_action)
                )
            }
        },
        shape = MaterialTheme.shapes.medium,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )

        OutlinedTextField(
            value = quantity,
            // Digit-only, so a stray letter cannot make the field unparseable.
            // isDigit is Unicode-aware, which is what lets Persian digits through.
            onValueChange = { input -> quantity = input.filter { it.isDigit() }.take(6) },
            modifier = Modifier.width(76.dp),
            placeholder = { Text(stringResource(id = R.string.today_quick_add_qty)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { commit() }),
            shape = MaterialTheme.shapes.medium,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}
