package com.spop.poverlay

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.ActivityNotFoundException
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.os.Process
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.text.HtmlCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.spop.poverlay.overlay.OverlayService
import com.spop.poverlay.sensor.heartrate.HeartRateManager
import com.spop.poverlay.releases.ReleaseChecker
import com.spop.poverlay.ui.theme.PTONOverlayTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class MainActivity : ComponentActivity() {
    private lateinit var viewModel: ConfigurationViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if ((application as GrupettoApplication).emulatedModel != null) {
            requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        viewModel =
            ConfigurationViewModel(
                application, ConfigurationRepository(applicationContext, this,
                    (application as GrupettoApplication).configurationPreferencesName),
                ReleaseChecker()
            )
        viewModel.finishActivity.observe(this) {
            finish()
        }
        viewModel.requestOverlayPermission.observe(this) {
            requestScreenPermission()
        }
        viewModel.requestBluetoothPermissions.observe(this) { permissions ->
            requestBluetoothPermissions(permissions)
        }
        viewModel.requestRestart.observe(this) {
            restartGrupetto()
        }
        viewModel.requestDiscoveryReportExport.observe(this) {
            try { discoveryReportExport.launch("grupetto-bike-services.json") }
            catch (_: ActivityNotFoundException) {
                Toast.makeText(this, "No document picker installed. The report remains saved in Grupetto.", Toast.LENGTH_LONG).show()
            }
        }
        viewModel.requestQuit.observe(this) {
            quitGrupetto()
        }
        viewModel.requestIgnoreBatteryOptimizations.observe(this) {
            requestIgnoreBatteryOptimizations()
        }
        viewModel.requestBackgroundLocationPermission.observe(this) {
            requestBackgroundLocationPermission()
        }
        viewModel.infoPopup.observe(this) {
            Toast.makeText(
                this,
                it,
                Toast.LENGTH_LONG
            ).show()
        }
        viewModel.showCalibration.value = intent.getBooleanExtra("showCalibration", false)
        val upgradeNotice = (application as GrupettoApplication).serialNameUpgradeNotice
        setContent {
            PTONOverlayTheme {
                var showUpgradeNotice by remember { mutableStateOf(upgradeNotice.shouldShow) }
                if (showUpgradeNotice) {
                    SerialNameUpgradeDialog {
                        upgradeNotice.dismiss()
                        showUpgradeNotice = false
                    }
                }
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background,
                ) {
                    ConfigurationPage(
                        viewModel
                    )
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.onResume()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.onAppResumed()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("showCalibration", false)) viewModel.showCalibration.value = true
    }

    override fun onStop() {
        super.onStop()
        viewModel.onAppStopped()
    }

    private fun restartGrupetto() {
        Toast.makeText(
            this@MainActivity,
            HtmlCompat.fromHtml("<big>Restarting Grupetto</big>", HtmlCompat.FROM_HTML_MODE_LEGACY),
            Toast.LENGTH_LONG
        )
            .apply { setGravity(Gravity.CENTER, 0, 0) }
            .show()

        CoroutineScope(Dispatchers.IO).launch {
            delay(1500L)
            val pm: PackageManager = applicationContext.packageManager
            val intent = pm.getLaunchIntentForPackage(applicationContext.packageName)
            val mainIntent = Intent.makeRestartActivityTask(intent!!.component)
            applicationContext.startActivity(mainIntent)
            Runtime.getRuntime().exit(0)
        }
    }

    private fun quitGrupetto() {
        Toast.makeText(
            this@MainActivity,
            HtmlCompat.fromHtml("<big>Closing Grupetto</big>", HtmlCompat.FROM_HTML_MODE_LEGACY),
            Toast.LENGTH_LONG
        ).apply { setGravity(Gravity.CENTER, 0, 0) }.show()

        CoroutineScope(Dispatchers.Main).launch {
            // Explicitly stop long-running components before closing the task so Android won't revive it.
            stopService(Intent(this@MainActivity, OverlayService::class.java))
            HeartRateManager.stop()
            (application as GrupettoApplication).bikeCalibration.stop()
            // Radio teardown makes blocking IPC calls (ANT channel release, GATT close); keep it
            // off the main thread so a slow radio service can't trigger an ANR while quitting.
            withContext(Dispatchers.IO) {
                (application as GrupettoApplication).powerMeterManager.stop()
                (application as GrupettoApplication).bleServer.stop()
                (application as GrupettoApplication).antPlusServer.stop()
            }
            delay(750L)
            finishAffinity()
            finishAndRemoveTask()
        }
    }

    private val overlayPermissionRequest =
        registerForActivityResult(StartActivityForResult()) {
            if (Build.VERSION.SDK_INT >= 23) {
                viewModel.onOverlayPermissionRequestCompleted(
                    Settings.canDrawOverlays(this)
                )
            }
        }

    private val discoveryReportExport = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportBikeDiscoveryReport(it) } }

    private val bluetoothPermissionRequest =
        registerForActivityResult(RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            viewModel.onBluetoothPermissionsResult(allGranted)
            // The runtime dialog only offers "while using the app". Saved sensors can reconnect during
            // a ride only with "Allow all the time", so ask for that right after the first grant.
            val fineLocation = androidx.core.content.ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (fineLocation && !hasBackgroundLocation()) requestBackgroundLocationPermission()
        }

    private val batteryOptimizationRequest =
        registerForActivityResult(StartActivityForResult()) {
            viewModel.onBatteryOptimizationRequestCompleted()
        }

    private val backgroundLocationPermissionRequest =
        registerForActivityResult(RequestPermission()) { granted ->
            viewModel.onBackgroundLocationPermissionResult(granted)
        }

    private fun hasBackgroundLocation() = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * Opens this app's own location permission page, which has "Allow all the time". That page is
     * Android's internal permission screen, so not every build lets apps open it; if it refuses,
     * fall back to the app info page (Permissions, then Location).
     */
    private fun openAppSettings() {
        try {
            startActivity(
                Intent("android.intent.action.MANAGE_APP_PERMISSION")
                    .putExtra("android.intent.extra.PACKAGE_NAME", packageName)
                    .putExtra("android.intent.extra.PERMISSION_GROUP_NAME", "android.permission-group.LOCATION")
                    .putExtra("android.intent.extra.USER", android.os.Process.myUserHandle())
            )
        } catch (e: Exception) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }

    private fun requestScreenPermission() = Intent(
        "android.settings.action.MANAGE_OVERLAY_PERMISSION",
        Uri.parse("package:${packageName}")
    ).apply {
        overlayPermissionRequest.launch(this)
    }

    private fun requestBluetoothPermissions(permissions: Array<String>) {
        bluetoothPermissionRequest.launch(permissions)
    }

    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasBackgroundLocation()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ never offers "Allow all the time" in a pop-up; it is only in settings, so go
            // straight to the app's location permission page (like the overlay permission screen).
            android.widget.Toast.makeText(
                this,
                "Choose \"Allow all the time\" so heart-rate monitors and power meters reconnect during a ride.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            openAppSettings()
        } else {
            backgroundLocationPermissionRequest.launch(
                android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
            )
        }
    }

    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return
        }

        val packageUri = Uri.parse("package:$packageName")
        val requestIntent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri)

        try {
            batteryOptimizationRequest.launch(requestIntent)
        } catch (_: ActivityNotFoundException) {
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            batteryOptimizationRequest.launch(fallbackIntent)
        }
    }
}


@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    PTONOverlayTheme {
        // Preview placeholder - actual ConfigurationPage requires ViewModel
    }
}
