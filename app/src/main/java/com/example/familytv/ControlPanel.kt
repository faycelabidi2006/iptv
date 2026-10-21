package com.example.familytv

import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.familytv.data.CodeGen
import com.example.familytv.data.Member
import com.example.familytv.data.SbConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val panelProps = DialogProperties(usePlatformDefaultWidth = false)
private val OK = Color(0xFF7BE07B)
private const val DAY = 86_400_000L

private fun date(ms: Long?): String =
    if (ms == null || ms <= 0L) "-" else SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(ms))

private fun ago(ms: Long): String {
    if (ms <= 0L) return "never"
    val m = (System.currentTimeMillis() - ms) / 60_000L
    return when {
        m < 1 -> "just now"
        m < 60 -> "$m min ago"
        m < 48 * 60 -> "${m / 60} h ago"
        else -> "${m / (24 * 60)} days ago"
    }
}

private fun daysLeft(ms: Long?): String {
    if (ms == null || ms <= 0L) return "no expiry"
    val d = (ms - System.currentTimeMillis()) / DAY
    return if (ms < System.currentTimeMillis()) "expired" else "$d days left"
}

/** لوحة تحكم المالك: توليد حسابات، وأعضاء الخادم، وإحصاءات اشتراك Xtream. لا تُعرض لغير المالك. */
@Composable
fun ControlPanelDialog(vm: MainViewModel, onClose: () -> Unit) {
    val users by vm.users.collectAsStateWithLifecycle()
    val owner = users.firstOrNull { it.name == vm.sessionUser }?.owner == true
    if (!owner) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val secret by vm.adminSecret.collectAsStateWithLifecycle()
    var secretDraft by remember(secret) { mutableStateOf(secret) }
    var uname by remember { mutableStateOf("") }
    var upass by remember { mutableStateOf("") }
    var days by remember { mutableIntStateOf(30) }
    var msg by remember { mutableStateOf("") }
    var msgOk by remember { mutableStateOf(true) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) { vm.loadXtreamStats() }
    LaunchedEffect(secret) { if (secret.isNotBlank()) vm.refreshMembers() }

    val report: (Boolean, String) -> Unit = { ok, m -> msgOk = ok; msg = m }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.94f),
        properties = panelProps,
        title = { Text("Control panel") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {

                // ---- 1) الخادم
                Section("Server (Supabase)")
                Text(
                    if (SbConfig.configured) "Connected to: ${SbConfig.URL.removePrefix("https://")}"
                    else "Not set. Edit URL and ANON_KEY in data/Server.kt, then rebuild the app.",
                    fontSize = 13.sp,
                    color = if (SbConfig.configured) Color.Unspecified else MaterialTheme.colorScheme.error
                )
                OutlinedTextField(
                    secretDraft, { secretDraft = it }, label = { Text("Admin secret (this device only)") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Save secret") { vm.setAdminSecret(secretDraft); msgOk = true; msg = "Secret saved on this device" }
                    PillButton("Refresh", brush = null) { vm.refreshMembers(); vm.loadXtreamStats() }
                }

                // ---- 2) توليد حساب
                Section("Generate a login")
                OutlinedTextField(uname, { uname = it; msg = "" }, label = { Text("Username") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(upass, { upass = it; msg = "" }, label = { Text("Password") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(7 to "7 days", 30 to "30 days", 90 to "90 days", 365 to "1 year", 0 to "No expiry").forEach { (d, label) ->
                        PillButton(label, brush = if (days == d) Pal.accentBrush else null) { days = d }
                    }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Generate", brush = null) { uname = CodeGen.username(); upass = CodeGen.password(); msg = "" }
                    PillButton("Save to server", enabled = !vm.membersBusy) { vm.addMember(uname, upass, days, report) }
                    PillButton("Copy", brush = null, enabled = uname.isNotBlank() && upass.isNotBlank()) {
                        val until = if (days > 0) date(System.currentTimeMillis() + days * DAY) else "no expiry"
                        clip.setText(AnnotatedString("Username: $uname\nPassword: $upass\nValid until: $until"))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                    }
                }
                Text("Copy the password now. Only a hash is stored, so it cannot be shown again. The account links to the first device that signs in.", fontSize = 12.sp)
                if (msg.isNotEmpty()) Text(msg, color = if (msgOk) OK else MaterialTheme.colorScheme.error, fontSize = 13.sp)

                // ---- 3) الأعضاء
                Section("Members (${vm.members.size})  -  online now: ${vm.members.count { it.online }}")
                if (vm.membersBusy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                if (vm.membersError.isNotEmpty()) Text(vm.membersError, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                if (vm.members.isEmpty() && !vm.membersBusy && vm.membersError.isEmpty()) Text("No members yet.", fontSize = 13.sp)
                vm.members.forEach { m ->
                    MemberCard(
                        m, busy = vm.membersBusy, confirmDelete = pendingDelete == m.name,
                        onExtend = { vm.extendMember(m.name, 30, report) },
                        onToggle = { vm.setMemberDisabled(m.name, !m.disabled, report) },
                        onResetDevice = { vm.resetDevice(m.name, report) },
                        onDelete = {
                            if (pendingDelete == m.name) { pendingDelete = null; vm.deleteMember(m.name, report) }
                            else pendingDelete = m.name
                        }
                    )
                }

                // ---- 4) اشتراك Xtream
                Section("Xtream subscriptions")
                if (vm.statsLoading) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                if (vm.xtreamStats.isEmpty() && !vm.statsLoading) Text("No Xtream account is saved.", fontSize = 13.sp)
                vm.xtreamStats.forEach { st ->
                    val shape = RoundedCornerShape(14.dp)
                    Column(Modifier.fillMaxWidth().glass(shape).padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(st.playlist.name + "  (" + st.playlist.user + ")", fontWeight = FontWeight.SemiBold)
                        val i = st.info
                        if (i != null) {
                            Text("Status: ${i.status.ifBlank { "-" }}${if (i.trial) "  (trial)" else ""}", fontSize = 13.sp)
                            Text("Connected now: ${i.active} of ${i.max}", fontSize = 13.sp)
                            Text("Started: ${date(i.created)}", fontSize = 13.sp)
                            Text("Ends: ${date(i.expires)}  (${daysLeft(i.expires)})", fontSize = 13.sp)
                        } else {
                            Text(st.error ?: "Error", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}

@Composable
private fun Section(title: String) {
    Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun MemberCard(
    m: Member, busy: Boolean, confirmDelete: Boolean,
    onExtend: () -> Unit, onToggle: () -> Unit, onResetDevice: () -> Unit, onDelete: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val status = when {
        m.disabled -> "Disabled"
        m.expired() -> "Expired"
        else -> "Active"
    }
    Column(Modifier.fillMaxWidth().glass(shape).padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(m.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(status, color = if (status == "Active") OK else MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Text("Started: ${date(m.created)}   Ends: ${date(m.expires)}  (${daysLeft(m.expires)})", fontSize = 13.sp)
        Text(
            if (m.online) "Online now" + (if (m.watching.isNotBlank()) "  -  watching: ${m.watching}" else "")
            else "Last seen: ${ago(m.lastSeen)}",
            color = if (m.online) OK else Color.Unspecified, fontSize = 13.sp
        )
        Text(
            (if (m.deviceBound) "Device: linked since ${date(m.boundAt)}" else "Device: not linked yet") +
                "   Watched: ${m.minutes / 60} h ${m.minutes % 60} min",
            fontSize = 13.sp
        )
        if (m.by.isNotBlank()) Text("Created by: ${m.by}", fontSize = 12.sp)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("+30 days", brush = null, enabled = !busy && m.expires > 0L, onClick = onExtend)
            PillButton("Reset device", brush = null, enabled = !busy && m.deviceBound, onClick = onResetDevice)
            PillButton(if (m.disabled) "Enable" else "Disable", brush = null, enabled = !busy, onClick = onToggle)
            PillButton(if (confirmDelete) "Tap again to delete" else "Delete", brush = null, enabled = !busy, onClick = onDelete)
        }
    }
}
