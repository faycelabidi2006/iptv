package com.example.familytv

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object Pal {
    val bg1 = Color(0xFF1B0C33)
    val bg2 = Color(0xFF0A0512)
    val panel = Color(0xCC2B2536)
    val accent = Color(0xFFF2EA4F)
    val button = Color(0xFF7A5BB0)
    val tile = Color(0xCC7A1F2B)
}

@Composable
fun AppBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Pal.bg1, Pal.bg2)))) { content() }
}

/** إطار أبيض حول العنصر المحدد بالريموت (مهم لصناديق Android TV). */
fun Modifier.focusBorder(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .onFocusChanged { focused = it.hasFocus }
        .border(
            width = if (focused) 3.dp else 0.dp,
            color = if (focused) Color.White else Color.Transparent,
            shape = RoundedCornerShape(6.dp)
        )
}

@Composable
fun ListRow(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .background(Pal.panel, RoundedCornerShape(4.dp))
            .focusBorder()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) { content() }
}

fun openExternal(ctx: Context, url: String) {
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(url), "video/*"))
    } catch (e: Exception) {
        Toast.makeText(ctx, "No external player found", Toast.LENGTH_SHORT).show()
    }
}
