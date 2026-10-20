package com.example.familytv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun LoginScreen(vm: MainViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    var stay by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val submit = {
        if (!busy && name.isNotBlank() && pass.isNotEmpty()) {
            busy = true
            error = null
            vm.login(name, pass, stay) { err -> busy = false; error = err }
        }
    }

    AppBackground {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Row(
                Modifier
                    .fillMaxWidth(0.82f)
                    .verticalScroll(rememberScrollState())
                    .glass(RoundedCornerShape(28.dp))
                    .padding(26.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    BrandMark(Modifier.size(60.dp))
                    Text("Sign in", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp))
                    Text("Enter your account to continue.", color = Pal.textDim, modifier = Modifier.padding(top = 4.dp))
                }
                Column(Modifier.weight(1.2f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        name, { name = it; error = null }, label = { Text("Username") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        pass, { pass = it; error = null }, label = { Text("Password") }, singleLine = true,
                        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") } },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = stay, onCheckedChange = { stay = it })
                        Text("Stay signed in", color = Color.White)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp) }
                    PillButton(if (busy) "Signing in..." else "Sign in", Modifier.fillMaxWidth(), enabled = !busy) { submit() }
                }
            }
        }
    }
}
