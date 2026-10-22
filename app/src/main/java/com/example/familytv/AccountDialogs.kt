package com.example.familytv

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private val wideProps = DialogProperties(usePlatformDefaultWidth = false)

@Composable
fun AccountDialog(vm: MainViewModel, onClose: () -> Unit) {
    val users by vm.users.collectAsStateWithLifecycle()
    val me = users.firstOrNull { it.name == vm.sessionUser }
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf("") }
    val tf = if (show) VisualTransformation.None else PasswordVisualTransformation()

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f),
        properties = wideProps,
        title = { Text("Account: ${vm.sessionUser ?: ""}${if (me?.owner == true) " (owner)" else ""}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Change password")
                AdaptiveFields(8.dp) { w ->
                    OutlinedTextField(old, { old = it }, label = { Text("Current") }, singleLine = true,
                        visualTransformation = tf, modifier = w(1f))
                    OutlinedTextField(new, { new = it }, label = { Text("New") }, singleLine = true,
                        visualTransformation = tf, modifier = w(1f))
                    OutlinedTextField(again, { again = it }, label = { Text("Repeat") }, singleLine = true,
                        visualTransformation = tf, modifier = w(1f))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(if (show) "Hide passwords" else "Show passwords", brush = null) { show = !show }
                    PillButton("Change password") {
                        if (new != again) result = "Passwords do not match"
                        else vm.changePassword(old, new) { msg ->
                            result = msg
                            if (msg == "Password changed") { old = ""; new = ""; again = "" }
                        }
                    }
                    Text(
                        result,
                        color = if (result == "Password changed") Color(0xFF7BE07B) else MaterialTheme.colorScheme.error,
                        fontSize = 13.sp, modifier = Modifier.weight(1f)
                    )
                }
                PillButton("Sign out", brush = null) { onClose(); vm.logout() }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}

@Composable
fun UsersDialog(vm: MainViewModel, onClose: () -> Unit) {
    val users by vm.users.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        modifier = Modifier.fillMaxWidth(0.85f),
        properties = wideProps,
        title = { Text("Manage Users") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LazyColumn(Modifier.heightIn(max = 130.dp)) {
                    items(users, key = { it.name }) { u ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(u.name + if (u.owner) "  (owner)" else "", modifier = Modifier.weight(1f))
                            if (!u.owner) {
                                IconButton(onClick = { vm.removeUser(u.name) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove ${u.name}")
                                }
                            }
                        }
                    }
                }
                Text("Add a user")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(name, { name = it; result = "" }, label = { Text("Username") }, singleLine = true,
                        modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        pass, { pass = it; result = "" }, label = { Text("Password") }, singleLine = true,
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") } },
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton("Add user") {
                        vm.addUser(name, pass) { msg ->
                            result = msg
                            if (msg == "User added") { name = ""; pass = "" }
                        }
                    }
                    Text(
                        result,
                        color = if (result == "User added") Color(0xFF7BE07B) else MaterialTheme.colorScheme.error,
                        fontSize = 13.sp, modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
    )
}
