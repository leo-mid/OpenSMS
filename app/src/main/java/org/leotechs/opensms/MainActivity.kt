package org.leotechs.opensms

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Telephony
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import org.leotechs.opensms.ui.theme.OpenSMSTheme

class MainActivity : ComponentActivity() {
    private val REQUEST_DEFAULT_APP = 101
    private var isDefaultSmsApp by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isDefaultSmsApp = Telephony.Sms.getDefaultSmsPackage(this) == packageName

        if (!isDefaultSmsApp) {
            val setSmsAppIntent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            setSmsAppIntent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            startActivityForResult(setSmsAppIntent, REQUEST_DEFAULT_APP)
        }

        enableEdgeToEdge()
        setContent {
            OpenSMSTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        isDefault = isDefaultSmsApp,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DEFAULT_APP) {
            if (resultCode == Activity.RESULT_OK) {
                isDefaultSmsApp = true
                Toast.makeText(this, "OpenSMS is now your default SMS app!", Toast.LENGTH_SHORT).show()
            } else {
                isDefaultSmsApp = false
                Toast.makeText(this, "Permission denied. App may not function correctly.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun Greeting(name: String, isDefault: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(text = "Hello $name!")
        Text(
            text = if (isDefault) "Default SMS App: YES" else "Default SMS App: NO",
            color = if (isDefault) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    OpenSMSTheme {
        Greeting("Android", isDefault = true)
    }
}