package com.ipvio.stream

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DebridStreamDialog(title: String, onClose: () -> Unit, onPlay: (String) -> Unit) {
    val ctx = LocalContext.current
    DebridPrefs.init(ctx)
    val services = remember { DebridPrefs.services() }
    var service by remember { mutableStateOf(services.firstOrNull()) }
    var link by remember { mutableStateOf(clipboardLink(ctx)) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val first = remember { FocusRequester() }
    val shape = RoundedCornerShape(DS.RadiusL)

    LaunchedEffect(Unit) {
        delay(150)
        runCatching { first.requestFocus() }
    }

    Dialog(
        onDismissRequest = { if (!busy) onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            Modifier.width(760.dp).clip(shape).background(Tok.GlassStrong)
                .border(1.dp, DS.Overlay3, shape).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Stream über Debrid", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "Füge einen Magnet-Link oder eine .torrent-Adresse für Inhalte ein, für die du eine Berechtigung hast. Real-Debrid oder TorBox erzeugt daraus einen direkten Stream.",
                color = Muted, fontSize = 15.sp, lineHeight = 21.sp
            )
            if (services.isEmpty()) {
                Text(
                    "Kein Debrid-Schlüssel eingerichtet. Trage ihn unter Einstellungen > API-Schlüssel ein.",
                    color = DS.Gold, fontSize = 16.sp, lineHeight = 22.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton("Schließen", light = true) { onClose() }
                }
            } else {
                if (services.size > 1) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        services.forEach { s ->
                            ChoiceChip(s.label, service == s) { service = s }
                        }
                    }
                }
                Field(link, { link = it; status = "" }, "Magnet- oder .torrent-Link")
                if (status.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (busy) Spinner(Accent2, Modifier.size(18.dp))
                        Text(status, color = if (busy) Muted else DS.Gold, fontSize = 15.sp, lineHeight = 20.sp)
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(
                        if (busy) "Bitte warten …" else "Abspielen",
                        Modifier.focusRequester(first), icon = AIcon.Play
                    ) {
                        if (busy) return@PillButton
                        val s = service ?: return@PillButton
                        if (!Debrid.isLink(link)) {
                            status = "Ungültiger Link. Magnet- oder .torrent-Adresse einfügen."
                            return@PillButton
                        }
                        busy = true
                        status = "Stream wird vorbereitet …"
                        scope.launch {
                            when (val r = Debrid.resolve(ctx, s, link) { status = it }) {
                                is DebridResult.Ok -> {
                                    busy = false
                                    onPlay(r.url)
                                    onClose()
                                }
                                is DebridResult.Error -> {
                                    busy = false
                                    status = r.message
                                }
                            }
                        }
                    }
                    PillButton("Abbrechen", light = true) { if (!busy) onClose() }
                }
                Text(title, color = Muted, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

private fun clipboardLink(ctx: Context): String {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
    val text = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString().orEmpty().trim()
    return if (Debrid.isLink(text)) text else ""
}
