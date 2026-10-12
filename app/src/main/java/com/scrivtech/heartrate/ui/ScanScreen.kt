package com.scrivtech.heartrate.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.scrivtech.heartrate.BleHeartRateManager
import com.scrivtech.heartrate.BuildConfig
import com.scrivtech.heartrate.ConnectionState
import com.scrivtech.heartrate.R
import com.scrivtech.heartrate.data.HrSession
import com.scrivtech.heartrate.data.Storage
import com.scrivtech.heartrate.data.maxHrForAge
import com.scrivtech.heartrate.data.timeInZones
import com.scrivtech.heartrate.ui.theme.Dimens
import com.scrivtech.heartrate.ui.theme.Ground
import com.scrivtech.heartrate.ui.theme.Hairline
import com.scrivtech.heartrate.ui.theme.HrmType
import com.scrivtech.heartrate.ui.theme.TextMuted
import com.scrivtech.heartrate.ui.theme.TextPrimary
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("MissingPermission")
@Composable
fun ScanScreen(
    bleManager: BleHeartRateManager,
    storage: Storage,
    age: Int?,
    onAgeChange: (Int) -> Unit,
    onShowHistory: () -> Unit
) {
    val state by bleManager.state.collectAsState()
    val devices by bleManager.devices.collectAsState()
    val errorMessage by bleManager.errorMessage.collectAsState()
    val recentDevices = remember { storage.getRecentDevices() }
    val sessions = remember { storage.getSessions() }
    var editingAge by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val maxHr = age?.let { maxHrForAge(it) }
    val scanning = state == ConnectionState.SCANNING

    val bluetoothAdapter = remember {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    }

    if (editingAge) {
        AgeDialog(
            initialAge = age,
            onDismiss = { editingAge = false },
            onSave = {
                onAgeChange(it)
                editingAge = false
            }
        )
    }

    LaunchedEffect(state) {
        if (state == ConnectionState.SCANNING) {
            delay(30_000)
            bleManager.stopScan()
        }
    }

    val toggleScan = { if (scanning) bleManager.stopScan() else bleManager.startScan() }
    // The device used last time is the likeliest one to be wanted now, so it gets the one
    // primary button and connecting is a single tap. Scanning steps down to secondary.
    val lastDevice = recentDevices.firstOrNull()
    val olderDevices = recentDevices.drop(1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ground)
            .safeDrawingPadding()
            .padding(horizontal = Dimens.ScreenEdge)
    ) {
        // weight(1f) lets the list take the remaining height so the version footer stays
        // pinned to the bottom instead of floating under a short list.
        LazyColumn(modifier = Modifier.weight(1f)) {
            item {
                Text(
                    text = "Heart Rate Mirror",
                    style = HrmType.Title,
                    color = TextPrimary,
                    modifier = Modifier.padding(top = Dimens.ScreenEdge, bottom = Dimens.Section)
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(Dimens.Md)) {
                    if (lastDevice != null) {
                        PrimaryButton(
                            text = "Connect ${lastDevice.name}",
                            onClick = {
                                bleManager.connect(
                                    bluetoothAdapter.getRemoteDevice(lastDevice.address)
                                )
                            },
                            trailingIcon = R.drawable.ic_arrow_forward
                        )
                        SecondaryButton(
                            text = if (scanning) "Stop scan" else "Scan for another device",
                            onClick = toggleScan
                        )
                    } else {
                        PrimaryButton(
                            text = if (scanning) "Stop scan" else "Scan for devices",
                            onClick = toggleScan
                        )
                        Text(
                            text = "Start HR Mirror on your watch, or put on your strap, then scan.",
                            style = HrmType.Body,
                            color = TextMuted
                        )
                    }

                    if (scanning) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                color = TextPrimary,
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(Dimens.Sm))
                            Text(text = "Scanning", style = HrmType.Body, color = TextMuted)
                        }
                    }
                    if (state == ConnectionState.DISCONNECTED) {
                        StatusLine(text = errorMessage ?: "Connection lost. Scan to reconnect.")
                    }
                    if (state == ConnectionState.FAILED && errorMessage != null) {
                        StatusLine(text = errorMessage!!)
                    }
                }
            }

            if (devices.isNotEmpty()) {
                item { SectionHeader(title = "Found devices") }
                items(devices, key = { it.address }) { device ->
                    DeviceRow(
                        name = device.name ?: "Unknown device",
                        detail = if (device.bondState == BluetoothDevice.BOND_BONDED) {
                            "Paired"
                        } else {
                            device.address
                        },
                        onClick = { bleManager.connect(device) }
                    )
                }
            }

            if (olderDevices.isNotEmpty()) {
                item { SectionHeader(title = "Recent devices") }
                items(olderDevices, key = { "recent_${it.address}" }) { device ->
                    DeviceRow(
                        name = device.name,
                        detail = "Previously connected",
                        onClick = {
                            bleManager.connect(bluetoothAdapter.getRemoteDevice(device.address))
                        }
                    )
                }
            }

            item { ZonesSection(age = age, onEdit = { editingAge = true }) }

            // Past sessions are the main reason to open the app when not about to train,
            // so the latest few are shown here rather than behind a link that only counts
            // them.
            if (sessions.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Sessions",
                        action = "All ${sessions.size}",
                        onAction = onShowHistory
                    )
                }
                items(sessions.take(HOME_SESSION_COUNT), key = { "session_${it.id}" }) { session ->
                    SessionSummaryRow(session = session, maxHr = maxHr, onClick = onShowHistory)
                }
            }
        }

        AppVersionFooter()
    }
}

/** A section title with an optional quiet action on the right, closed by a hairline. */
@Composable
private fun SectionHeader(
    title: String,
    action: String? = null,
    onAction: () -> Unit = {}
) {
    Column(modifier = Modifier.padding(top = Dimens.Section)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.MinTouch),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = HrmType.Body,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (action != null) {
                MutedTextButton(text = action, onClick = onAction)
            }
        }
        HorizontalDivider(color = Hairline)
    }
}

/**
 * The one setting the zone model needs, and the legend for every zone colour in the app.
 *
 * With no age set this is a prompt rather than a status line, because until it is answered
 * every zone feature in the app is silently absent and nothing else would explain why.
 * Once set it shows the zones themselves with their boundaries, so the numbers behind the
 * colours are inspectable rather than magic.
 */
@Composable
private fun ZonesSection(age: Int?, onEdit: () -> Unit) {
    if (age == null) {
        SecondaryButton(
            text = "Set your age to see heart rate zones",
            onClick = onEdit,
            modifier = Modifier.padding(top = Dimens.Section)
        )
        return
    }
    val maxHr = maxHrForAge(age)
    Column {
        SectionHeader(
            title = "Your zones",
            action = "Age $age · max $maxHr · Change",
            onAction = onEdit
        )
        Spacer(modifier = Modifier.height(Dimens.Sm))
        ZoneScale(maxHr = maxHr)
    }
}

@Composable
private fun DeviceRow(name: String, detail: String, onClick: () -> Unit) {
    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.ListRow),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = HrmType.Body,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(text = detail, style = HrmType.BodySm, color = TextMuted)
            }
            Icon(
                painter = painterResource(R.drawable.ic_arrow_forward),
                contentDescription = "Connect",
                tint = TextMuted,
                modifier = Modifier.size(24.dp)
            )
        }
        HorizontalDivider(color = Hairline)
    }
}

@Composable
private fun SessionSummaryRow(session: HrSession, maxHr: Int?, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("MMM d", Locale.getDefault()) }
    val date = remember(session.startTime) { dateFormat.format(Date(session.startTime)) }
    val zoneTimes = remember(session.id, maxHr) {
        if (maxHr != null) timeInZones(session.readings, maxHr) else emptyMap()
    }

    Column(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = Dimens.ListRow),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.sessionName.ifEmpty { date },
                    style = HrmType.Body,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (session.sessionName.isEmpty()) {
                        formatDuration(session.durationMs)
                    } else {
                        "$date · ${formatDuration(session.durationMs)}"
                    },
                    style = HrmType.BodySm,
                    color = TextMuted
                )
            }
            Spacer(modifier = Modifier.width(Dimens.Md))
            ZoneBar(timeInZones = zoneTimes, modifier = Modifier.width(84.dp))
            Spacer(modifier = Modifier.width(Dimens.Md))
            Text(text = "${session.avgBpm}", style = HrmType.NumeralLg, color = TextPrimary)
            Spacer(modifier = Modifier.width(Dimens.Xs))
            Text(text = "avg", style = HrmType.BodySm, color = TextMuted)
        }
        HorizontalDivider(color = Hairline)
    }
}

/**
 * Version line and outbound links, shown at the foot of the home screen.
 *
 * The version comes from [BuildConfig.VERSION_NAME] rather than a literal, so it and the
 * release-notes URL both follow versionName in app/build.gradle.kts and cannot go stale on
 * the next bump. The release-notes link 404s until that version's release is published,
 * which is the intended behaviour: the link is correct for the build it ships in.
 */
@Composable
private fun AppVersionFooter() {
    val uriHandler = LocalUriHandler.current
    val version = BuildConfig.VERSION_NAME

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The version is the one part worth being able to read off a screenshot when
        // someone reports a problem.
        Text(text = "v$version", style = HrmType.BodySm, color = TextMuted)
        Spacer(modifier = Modifier.width(Dimens.Md))
        MutedTextButton(
            text = "Release notes",
            onClick = { uriHandler.openUri("$REPO_URL/releases/tag/v$version") }
        )
        MutedTextButton(text = "GitHub", onClick = { uriHandler.openUri(REPO_URL) })
    }
}

private const val REPO_URL = "https://github.com/darthrater78/android-heartrate"

/** How many of the latest sessions the home screen previews before "All". */
private const val HOME_SESSION_COUNT = 3
