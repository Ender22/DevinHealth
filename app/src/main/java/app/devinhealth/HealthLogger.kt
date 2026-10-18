package app.devinhealth

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.BloodGlucose
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.health.connect.client.units.Pressure
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

object HealthLogger {
    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
        HealthPermission.getWritePermission(BloodPressureRecord::class),
        HealthPermission.getWritePermission(HeartRateRecord::class),
        HealthPermission.getWritePermission(BloodGlucoseRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(BloodGlucoseRecord::class),
    )

    data class Reading(val time: Instant, val text: String)
    data class History(
        val bloodPressure: List<Reading>,
        val glucose: List<Reading>,
        val bpAverage: String?,
        val glucoseAverageMmol: Double?,
    )

    /** Last [days] days of blood pressure and glucose readings, newest first. */
    suspend fun history(context: Context, days: Long = 30): History {
        val now = Instant.now()
        val filter = TimeRangeFilter.between(now.minus(Duration.ofDays(days)), now)
        val bp = client(context).readRecords(ReadRecordsRequest(BloodPressureRecord::class, filter)).records
            .sortedByDescending { it.time }
        val bg = client(context).readRecords(ReadRecordsRequest(BloodGlucoseRecord::class, filter)).records
            .sortedByDescending { it.time }
        val bpAvg = if (bp.isEmpty()) null else
            "${bp.map { it.systolic.inMillimetersOfMercury }.average().toInt()}/" +
                "${bp.map { it.diastolic.inMillimetersOfMercury }.average().toInt()} mmHg"
        return History(
            bloodPressure = bp.map {
                Reading(it.time, "${it.systolic.inMillimetersOfMercury.toInt()}/${it.diastolic.inMillimetersOfMercury.toInt()} mmHg")
            },
            glucose = bg.map {
                val mmol = it.level.inMillimolesPerLiter
                Reading(it.time, "%.1f mmol/L (%d mg/dL, %s)".format(mmol, (mmol * Entry.MGDL_PER_MMOL).toInt(), Entry.relationLabel(it.relationToMeal)))
            },
            bpAverage = bpAvg,
            glucoseAverageMmol = bg.map { it.level.inMillimolesPerLiter }.average().takeIf { bg.isNotEmpty() },
        )
    }

    /** One of HealthConnectClient.SDK_AVAILABLE / SDK_UNAVAILABLE / SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED. */
    fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    fun client(context: Context): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(context: Context): Boolean =
        client(context).permissionController.getGrantedPermissions().containsAll(permissions)

    /** Writes the entry to Health Connect, timestamped now. */
    suspend fun log(context: Context, entry: Entry, at: Instant = Instant.now()) {
        val offset = ZoneId.systemDefault().rules.getOffset(at)
        val records: List<Record> = when (entry) {
            is Entry.Meal -> listOf(nutrition(entry.meal, at))
            is Entry.BloodPressure -> buildList {
                add(
                    BloodPressureRecord(
                        time = at,
                        zoneOffset = offset,
                        systolic = Pressure.millimetersOfMercury(entry.systolic.toDouble()),
                        diastolic = Pressure.millimetersOfMercury(entry.diastolic.toDouble()),
                        bodyPosition = BloodPressureRecord.BODY_POSITION_SITTING_DOWN,
                        measurementLocation = BloodPressureRecord.MEASUREMENT_LOCATION_LEFT_UPPER_ARM,
                        metadata = Metadata.manualEntry(),
                    )
                )
                entry.pulse?.let { bpm ->
                    add(
                        HeartRateRecord(
                            startTime = at,
                            startZoneOffset = offset,
                            endTime = at.plusSeconds(1),
                            endZoneOffset = offset,
                            samples = listOf(HeartRateRecord.Sample(time = at, beatsPerMinute = bpm.toLong())),
                            metadata = Metadata.manualEntry(),
                        )
                    )
                }
            }
            is Entry.Glucose -> listOf(
                BloodGlucoseRecord(
                    time = at,
                    zoneOffset = offset,
                    level = BloodGlucose.millimolesPerLiter(entry.mmolPerL),
                    specimenSource = BloodGlucoseRecord.SPECIMEN_SOURCE_CAPILLARY_BLOOD,
                    relationToMeal = entry.relation,
                    metadata = Metadata.manualEntry(),
                )
            )
        }
        client(context).insertRecords(records)
    }

    private fun nutrition(meal: MealLink, end: Instant): NutritionRecord {
        val offset = ZoneId.systemDefault().rules.getOffset(end)
        return NutritionRecord(
            startTime = end.minus(Duration.ofMinutes(15)),
            startZoneOffset = offset,
            endTime = end,
            endZoneOffset = offset,
            metadata = Metadata.manualEntry(),
            energy = meal.kcal?.let { Energy.kilocalories(it) },
            protein = meal.protein?.let { Mass.grams(it) },
            totalCarbohydrate = meal.carbs?.let { Mass.grams(it) },
            totalFat = meal.fat?.let { Mass.grams(it) },
            dietaryFiber = meal.fiber?.let { Mass.grams(it) },
            sugar = meal.sugar?.let { Mass.grams(it) },
            sodium = meal.sodiumMg?.let { Mass.milligrams(it) },
            name = meal.name,
            mealType = meal.mealType,
        )
    }
}
