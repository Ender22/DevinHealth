package app.devinhealth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.MealType
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    /** The entry from the launching link, if any. Compose state so onNewIntent can swap it. */
    private var pendingEntry by mutableStateOf<Entry?>(null)
    /** False when the pending entry came from a link we already logged; then it needs a manual tap. */
    private var autoLog by mutableStateOf(true)
    private var linkText by mutableStateOf<String?>(null)
    private var linkNote by mutableStateOf<String?>(null)
    private var pendingUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only act on the link when the activity is first created, not on rotation or restore.
        if (savedInstanceState == null) handleIntent(intent)
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
        pendingUri = uri
        pendingEntry = Entry.parse(uri)
        val loggedAt = LinkLedger.loggedAt(this, uri)
        autoLog = loggedAt == null
        linkNote = loggedAt?.let { "This link was already logged at " + it + ". Tap Log it to log it again." }
    }

    /** Called after a successful log so a re-delivered intent cannot log the same link twice. */
    private fun markLinkLogged() {
        pendingUri?.let { LinkLedger.markLogged(this, it) }
        pendingUri = null
        linkNote = null
        intent?.data = null
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

        fun logEntry(entry: Entry) {
            scope.launch {
                try {
                    if (!HealthLogger.hasPermissions(this@MainActivity)) {
                        granted = false
                        status = "Asking for Health Connect permission..."
                        permissionLauncher.launch(HealthLogger.permissions)
                        return@launch
                    }
                    granted = true
                    HealthLogger.log(this@MainActivity, entry)
                    status = "Logged " + entry.label + ": " + entry.summary()
                    if (entry == pendingEntry) markLinkLogged()
                    pendingEntry = null
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
        // Auto-log an entry that arrived via link once permission is known (no review step).
        LaunchedEffect(pendingEntry, granted) {
            val entry = pendingEntry ?: return@LaunchedEffect
            if (!autoLog) return@LaunchedEffect
            when (granted) {
                true -> logEntry(entry)
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

            pendingEntry?.let { entry ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(entry.label.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
                        Text(entry.summary(), style = MaterialTheme.typography.titleMedium)
                        linkNote?.let { Spacer(Modifier.height(8.dp)); Text(it, style = MaterialTheme.typography.bodyMedium) }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = { logEntry(entry) }, Modifier.fillMaxWidth()) { Text("Log it") }
                    }
                }
            }

            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyLarge)

            if (granted != true && sdkStatus == HealthConnectClient.SDK_AVAILABLE) {
                Button(onClick = { permissionLauncher.launch(HealthLogger.permissions) }, Modifier.fillMaxWidth()) {
                    Text("Grant Health Connect permission")
                }
            }

            BloodPressureForm(onLog = ::logEntry)
            GlucoseForm(onLog = ::logEntry)
            if (granted == true) HistoryCard(status)

            Spacer(Modifier.height(8.dp))
            Text("Testing", style = MaterialTheme.typography.labelLarge)

            OutlinedButton(
                onClick = {
                    logEntry(
                        Entry.Meal(
                            MealLink(
                                name = "Test meal (DevinHealth)", kcal = 123.0, protein = 10.0, carbs = 12.0,
                                fat = 5.0, fiber = 2.0, sugar = 3.0, sodiumMg = 200.0,
                                mealType = MealType.MEAL_TYPE_SNACK,
                            )
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

@Composable
private fun HistoryCard(refreshKey: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var history by remember { mutableStateOf<HealthLogger.History?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    // Re-read whenever the status line changes, i.e. after every log.
    LaunchedEffect(refreshKey) {
        try { history = HealthLogger.history(context); error = null } catch (e: Exception) { error = e.message }
    }
    val fmt = remember { java.time.format.DateTimeFormatter.ofPattern("EEE d MMM HH:mm").withZone(java.time.ZoneId.systemDefault()) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Last 30 days", style = MaterialTheme.typography.titleMedium)
            error?.let { Text("Could not read history: $it", color = MaterialTheme.colorScheme.error) }
            val h = history ?: return@Column
            if (h.bloodPressure.isEmpty() && h.glucose.isEmpty()) Text("No readings yet.")
            if (h.bloodPressure.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Blood pressure, average " + h.bpAverage + " over " + h.bloodPressure.size + " readings",
                    style = MaterialTheme.typography.labelLarge)
                h.bloodPressure.take(7).forEach { Text(fmt.format(it.time) + "  " + it.text, style = MaterialTheme.typography.bodyMedium) }
            }
            if (h.glucose.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Glucose, average %.1f mmol/L over %d readings".format(h.glucoseAverageMmol, h.glucose.size),
                    style = MaterialTheme.typography.labelLarge)
                h.glucose.take(7).forEach { Text(fmt.format(it.time) + "  " + it.text, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
private fun BloodPressureForm(onLog: (Entry) -> Unit) {
    var sys by remember { mutableStateOf("") }
    var dia by remember { mutableStateOf("") }
    var pulse by remember { mutableStateOf("") }
    val entry = sys.toIntOrNull()?.let { s ->
        dia.toIntOrNull()?.let { d ->
            if (s in 50..260 && d in 30..200) Entry.BloodPressure(s, d, pulse.toIntOrNull()?.takeIf { it in 25..250 }) else null
        }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Blood pressure", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(sys, { sys = it }, "Systolic", Modifier.weight(1f))
                NumberField(dia, { dia = it }, "Diastolic", Modifier.weight(1f))
                NumberField(pulse, { pulse = it }, "Pulse", Modifier.weight(1f))
            }
            Button(
                onClick = { entry?.let { onLog(it); sys = ""; dia = ""; pulse = "" } },
                enabled = entry != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Log blood pressure") }
        }
    }
}

@Composable
private fun GlucoseForm(onLog: (Entry) -> Unit) {
    var value by remember { mutableStateOf("") }
    var mgDl by remember { mutableStateOf(false) }
    var relation by remember { mutableStateOf<String?>(null) }
    val entry = value.replace(",", ".").toDoubleOrNull()?.let { v ->
        Entry.glucose(v, if (mgDl) "mgdl" else "mmol", relation)
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Blood glucose", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(value, { value = it }, if (mgDl) "mg/dL" else "mmol/L", Modifier.weight(1f), decimal = !mgDl)
                Column(Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !mgDl, onClick = { mgDl = false }, label = { Text("mmol/L") })
                        FilterChip(selected = mgDl, onClick = { mgDl = true }, label = { Text("mg/dL") })
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("fasting" to "Fasting", "before" to "Before meal", "after" to "After meal").forEach { (key, label) ->
                    FilterChip(
                        selected = relation == key,
                        onClick = { relation = if (relation == key) null else key },
                        label = { Text(label) },
                    )
                }
            }
            Button(
                onClick = { entry?.let { onLog(it); value = "" } },
                enabled = entry != null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Log glucose") }
        }
    }
}

@Composable
private fun NumberField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || (decimal && (c == '.' || c == ',')) }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
