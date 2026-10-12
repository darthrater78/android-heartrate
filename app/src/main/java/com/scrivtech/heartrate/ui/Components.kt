package com.scrivtech.heartrate.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scrivtech.heartrate.R
import com.scrivtech.heartrate.ui.theme.Action
import com.scrivtech.heartrate.ui.theme.Dimens
import com.scrivtech.heartrate.ui.theme.HrmType
import com.scrivtech.heartrate.ui.theme.OnAction
import com.scrivtech.heartrate.ui.theme.Outline
import com.scrivtech.heartrate.ui.theme.ShapeMd
import com.scrivtech.heartrate.ui.theme.TextDisabled
import com.scrivtech.heartrate.ui.theme.TextMuted
import com.scrivtech.heartrate.ui.theme.TextPrimary

/** The one filled button on a screen. [trailingIcon] turns it into a "go" row. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes trailingIcon: Int? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.PrimaryButton),
        shape = ShapeMd,
        colors = ButtonDefaults.buttonColors(containerColor = Action, contentColor = OnAction),
        contentPadding = PaddingValues(horizontal = 18.dp)
    ) {
        Text(
            text = text,
            style = HrmType.LabelLg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = if (trailingIcon != null) Modifier.weight(1f) else Modifier
        )
        if (trailingIcon != null) {
            Spacer(modifier = Modifier.width(Dimens.Sm))
            Icon(
                painter = painterResource(trailingIcon),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.MinTouch),
        shape = ShapeMd,
        border = BorderStroke(1.dp, Outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
    ) {
        Text(text = text, style = HrmType.LabelLg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A quiet action. Destructive actions use this too and confirm in a dialog, because colour
 * is reserved for zones and so cannot be what marks an action as dangerous.
 */
@Composable
fun MutedTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = Dimens.MinTouch),
        shape = ShapeMd,
        colors = ButtonDefaults.textButtonColors(
            contentColor = TextMuted,
            disabledContentColor = TextDisabled
        ),
        contentPadding = PaddingValues(horizontal = Dimens.Sm)
    ) {
        Text(text = text, style = HrmType.Body)
    }
}

/** A status or error line: an icon carries the alert, never a colour. */
@Composable
fun StatusLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.ic_alert),
            contentDescription = null,
            tint = TextPrimary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(Dimens.Sm))
        Text(text = text, style = HrmType.Body, color = TextPrimary)
    }
}
