package org.leotechs.opensms

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.leotechs.opensms.ui.theme.OpenSMSTheme

class MainActivity : ComponentActivity() {
    private val REQUEST_DEFAULT_APP = 101
    private val MY_PERMISSIONS_REQUEST_SMS = 102
    private var isDefaultSmsApp by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isDefaultSmsApp = Telephony.Sms.getDefaultSmsPackage(this) == packageName

        if (!isDefaultSmsApp) {
            val setSmsAppIntent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
            setSmsAppIntent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
            startActivityForResult(setSmsAppIntent, REQUEST_DEFAULT_APP)
        } else {
            checkSmsPermissions()
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

    private fun checkSmsPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_MMS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_WAP_PUSH) != PackageManager.PERMISSION_GRANTED) {
            
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.READ_SMS,
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.SEND_SMS,
                    Manifest.permission.RECEIVE_MMS,
                    Manifest.permission.RECEIVE_WAP_PUSH),
                MY_PERMISSIONS_REQUEST_SMS)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DEFAULT_APP) {
            if (resultCode == Activity.RESULT_OK) {
                isDefaultSmsApp = true
                Toast.makeText(this, "OpenSMS is now your default SMS app!", Toast.LENGTH_SHORT).show()
                checkSmsPermissions()
            } else {
                isDefaultSmsApp = false
                Toast.makeText(this, "Permission denied. App may not function correctly.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = if(checkPermissionGranted(requestCode, permissions, grantResults)) "permission granted" else "permission not granted"
        Toast.makeText(this, granted, Toast.LENGTH_SHORT).show()
    }

    private fun checkPermissionGranted(requestCode: Int, permissions: Array<String>, grantResults: IntArray): Boolean{
        when (requestCode) {
            MY_PERMISSIONS_REQUEST_SMS -> {
                // Return true if all permissions are granted
                return grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            }
        }
        return false
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