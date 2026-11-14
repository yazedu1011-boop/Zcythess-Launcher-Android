package com.launcher.mc

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.launcher.mc.core.AccountStore
import com.launcher.mc.core.AppState
import com.launcher.mc.core.MicrosoftAuth
import com.launcher.mc.ui.LauncherRoot

class MainActivity : ComponentActivity() {
override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
AppState.account = AccountStore.load()
handle(intent)
setContent { MaterialTheme(colorScheme = darkColorScheme()) { LauncherRoot() } }
}

override fun onNewIntent(intent: Intent) {
super.onNewIntent(intent)
handle(intent)
}

private fun handle(i: Intent?) {
i?.data?.getQueryParameter("code")?.let { MicrosoftAuth.pendingCode = it }
}

}
