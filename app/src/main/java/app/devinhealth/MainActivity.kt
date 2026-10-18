package app.devinhealth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.MealType
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** The meal from the launching link, if any. Compose state so onNewIntent can swap it. */
    private var pendingMeal by mutableStateOf<MealLink?>(null)
    private var linkText by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { MaterialTheme { Screen() } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val uri: Uri = intent?.data ?: return
        linkText = uri.toString()
        pendingMeal = MealLink.parse(uri).takeIf { it.hasData }
    }

    @Composable
    private fun Screen() {
        val scope = rememberCoroutineScope()
        var status by remember { mutableStateOf("") }
        var granted by remember { mutableStateOf<Boolean?>(null) }
        val sdkStatus = remember { HealthLogger.availability(this) }

        val permissionLauncher = rememberLauncherForActivityResult(
            PermissionController.createRequestPermissionResultContract()
        ) { result ->
            granted = result.containsAll(HealthLogger.permissions)
            if (granted != true) status = "Health Connect permission not granted."
        }

        fun logMeal(meal: MealLink) {
            scope.launch {
                try {
                    if (!HealthLogger.hasPermissions(this@MainActivity)) {
                        granted = false
                        status = "Asking for Health Connect permission..."
                        permissionLauncher.launch(HealthLogger.permissions)
                        return@launch
                    }
                    granted = true
                    HealthLogger.log(this@MainActivity, meal)
                    status = "Logged " + MealLink.mealLabel(meal.mealType).lowercase() + ": " +
                        (meal.name ?: "Meal") + "\n" + meal.summary()
                    pendingMeal = null
                } catch (e: Exception) {
                    status = "Failed: " + e::class.simpleName + ": " + e.message
                }
            }
        }

        // Check permission on start.
        LaunchedEffect(sdkStatus) {
            if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
                granted = HealthLogger.hasPermissions(this@MainActivity)
            }
        }
        // Auto-log a meal that arrived via link once permission is known (no review step).
        LaunchedEffect(pendingMeal, granted) {
            val meal = pendingMeal ?: return@LaunchedEffect
            when (granted) {
                true -> logMeal(meal)
                false -> permissionLauncher.launch(HealthLogger.permissions)
                null -> Unit
            }
        }

        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("DevinHealth", style = MaterialTheme.typography.headlineMedium)

            when (sdkStatus) {
                HealthConnectClient.SDK_UNAVAILABLE ->
                    Text("Health Connect is not available on this device.", color = MaterialTheme.colorScheme.error)
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                    Text("Health Connect needs to be updated.", color = MaterialTheme.colorScheme.error)
                else -> Text(
                    when (granted) {
                        true -> "Health Connect: permission granted"
                        false -> "Health Connect: permission needed"
                        null -> "Health Connect: checking..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            pendingMeal?.let { meal ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(meal.name ?: "Meal", style = MaterialTheme.typography.titleMedium)
                        Text(MealLink.mealLabel(meal.mealType), style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(meal.summary())
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { logMeal(meal) }, Modifier.fillMaxWidth()) { Text("Log it") }
                    }
                }
            }

            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyLarge)

            Spacer(Modifier.height(8.dp))

            if (granted != true && sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
                Button(onClick = { permissionLauncher.launch(HealthLogger.permissions) }, Modifier.fillMaxWidth()) {
                    Text("Grant Health Connect permission")
                }
            }

            OutlinedButton(
                onClick = {
                    logMeal(
                        MealLink(
                            name = "Test meal (DevinHealth)", kcal = 123.0, protein = 10.0, carbs = 12.0,
                            fat = 5.0, fiber = 2.0, sugar = 3.0, sodiumMg = 200.0,
                            mealType = MealType.MEAL_TYPE_SNACK,
                        )
                    )
                },
                Modifier.fillMaxWidth(),
            ) { Text("Log test meal") }

            OutlinedButton(
                onClick = {
                    startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://ender22.github.io/DevinHealth/?name=Link%20test%20meal&kcal=321&protein=20&carbs=30&fat=10&meal=snack")
                        )
                    )
                },
                Modifier.fillMaxWidth(),
            ) { Text("Open test link via browser") }

            if (sdkStatus != HealthConnectClient.SDK_UNAVAILABLE) {
                OutlinedButton(
                    onClick = { startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS)) },
                    Modifier.fillMaxWidth(),
                ) { Text("Open Health Connect settings") }
            }

            linkText?.let { Text("Opened from: " + it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
