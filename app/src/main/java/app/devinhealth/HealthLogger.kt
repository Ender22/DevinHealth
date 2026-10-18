package app.devinhealth

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

object HealthLogger {
    val permissions: Set<String> = setOf(
        HealthPermission.getWritePermission(NutritionRecord::class),
        HealthPermission.getReadPermission(NutritionRecord::class),
    )

    /** One of HealthConnectClient.SDK_AVAILABLE / SDK_UNAVAILABLE / SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED. */
    fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context)

    fun client(context: Context): HealthConnectClient = HealthConnectClient.getOrCreate(context)

    suspend fun hasPermissions(context: Context): Boolean =
        client(context).permissionController.getGrantedPermissions().containsAll(permissions)

    /** Writes the meal as a NutritionRecord ending now. Returns the new record id. */
    suspend fun log(context: Context, meal: MealLink, end: Instant = Instant.now()): String {
        val offset = ZoneId.systemDefault().rules.getOffset(end)
        val start = end.minus(Duration.ofMinutes(15))
        val record = NutritionRecord(
            startTime = start,
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
        val response = client(context).insertRecords(listOf(record))
        return response.recordIdsList.first()
    }
}
