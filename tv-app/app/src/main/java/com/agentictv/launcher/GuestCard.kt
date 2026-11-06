package com.agentictv.launcher

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/**
 * The welcome card for guests: a friendly line plus the Wi-Fi details and a
 * QR code they can scan with their phone camera to join the Wi-Fi.
 * The Wi-Fi details come from the TV (scripts/set-guest-info.sh), never from GitHub.
 */
@Composable
fun GuestCard(info: GuestInfo?, modifier: Modifier = Modifier) {
    val hasWifi = info != null && info.wifiName.isNotBlank()
    val qr = remember(info) { if (hasWifi) wifiQr(info!!.wifiName, info.wifiPassword) else null }

    Row(
        modifier = modifier.glassCard().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (qr != null) {
            Image(qr, contentDescription = "Wi-Fi QR code", modifier = Modifier.size(120.dp).clip(RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(14.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Welcome!", color = TextMain, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                info?.welcome ?: "Make yourself at home. Pick something to watch below.",
                color = TextDim, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (hasWifi) {
                Text("Wi-Fi  ${info!!.wifiName}", color = TextMain, fontSize = 12.sp, maxLines = 1)
                if (info.wifiPassword.isNotBlank()) {
                    Text("Password  ${info.wifiPassword}", color = TextMain, fontSize = 12.sp, maxLines = 1)
                }
                Text("Scan the code with your phone to join", color = TextDim, fontSize = 10.sp)
            }
        }
    }
}

/** Full-screen pop-up with the guest card. Back or OK closes it. */
@Composable
fun GuestPanel(info: GuestInfo?, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xCC000000))
            .focusRequester(focus)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            GuestCard(info, Modifier.width(520.dp))
            Spacer(Modifier.height(16.dp))
            Text(
                if (info?.wifiName.isNullOrBlank()) "Ask your host for the Wi-Fi details." else "Press OK or Back to close",
                color = TextDim, fontSize = 12.sp,
            )
        }
    }
}

/** Pop-up with the QR code that opens the phone remote. Back or OK closes it. */
@Composable
fun PhonePanel(link: String?, onClose: () -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val qr = remember(link) { link?.let { qrCode(it) } }
    Box(
        Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color(0xCC000000))
            .focusRequester(focus)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.width(560.dp).glassCard().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (qr != null) {
                Image(qr, "QR code for the phone remote", Modifier.size(150.dp).clip(RoundedCornerShape(10.dp)))
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Talk to the TV from your phone", color = TextMain, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    if (link == null) "The TV isn't on Wi-Fi right now."
                    else "Scan with your phone camera (same Wi-Fi as the TV). Then tap the box and use the 🎤 on your keyboard.",
                    color = TextDim, fontSize = 14.sp,
                )
                if (link != null) Text(link, color = TextDim, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** A see-through rounded card, used for the top cards. */
fun Modifier.glassCard(): Modifier = this
    .clip(RoundedCornerShape(16.dp))
    .background(Frosted)

/** Builds the standard "join this Wi-Fi" QR code that phone cameras understand. */
private fun wifiQr(name: String, password: String): ImageBitmap {
    fun esc(s: String) = s.replace("""([\;,:"])""".toRegex(), """\\$1""")
    val text = if (password.isBlank()) "WIFI:T:nopass;S:${esc(name)};;"
    else "WIFI:T:WPA;S:${esc(name)};P:${esc(password)};;"
    return qrCode(text)
}

/** Turns any text (a link, Wi-Fi details...) into a QR code picture. */
fun qrCode(text: String): ImageBitmap {
    val size = 168
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i ->
        if (matrix[i % size, i / size]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
}

