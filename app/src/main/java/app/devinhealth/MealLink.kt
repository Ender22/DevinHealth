package app.devinhealth

import android.net.Uri
import androidx.health.connect.client.records.MealType
import java.time.LocalTime

/** Nutrition values parsed from a devinhealth://log?... or https://.../DevinHealth/?... link. */
data class MealLink(
    val name: String?,
    val kcal: Double?,
    val protein: Double?,
    val carbs: Double?,
    val fat: Double?,
    val fiber: Double?,
    val sugar: Double?,
    val sodiumMg: Double?,
    val mealType: Int,
) {
    val hasData: Boolean get() = kcal != null || protein != null || carbs != null || fat != null

    fun summary(): String = buildList {
        kcal?.let { add("${it.fmt()} kcal") }
        protein?.let { add("P ${it.fmt()}g") }
        carbs?.let { add("C ${it.fmt()}g") }
        fat?.let { add("F ${it.fmt()}g") }
        fiber?.let { add("fiber ${it.fmt()}g") }
        sugar?.let { add("sugar ${it.fmt()}g") }
        sodiumMg?.let { add("sodium ${it.fmt()}mg") }
    }.joinToString(" | ")

    companion object {
        fun parse(uri: Uri): MealLink {
            fun num(key: String): Double? = uri.getQueryParameter(key)
                ?.trim()?.replace(",", ".")?.toDoubleOrNull()?.takeIf { it >= 0 }
            return MealLink(
                name = uri.getQueryParameter("name")?.trim()?.takeIf { it.isNotEmpty() },
                kcal = num("kcal") ?: num("calories"),
                protein = num("protein"),
                carbs = num("carbs") ?: num("carbohydrates"),
                fat = num("fat"),
                fiber = num("fiber") ?: num("fibre"),
                sugar = num("sugar"),
                sodiumMg = num("sodium"),
                mealType = mealTypeFrom(uri.getQueryParameter("meal")),
            )
        }

        fun mealTypeFrom(raw: String?, now: LocalTime = LocalTime.now()): Int = when (raw?.lowercase()) {
            "breakfast" -> MealType.MEAL_TYPE_BREAKFAST
            "lunch" -> MealType.MEAL_TYPE_LUNCH
            "dinner" -> MealType.MEAL_TYPE_DINNER
            "snack" -> MealType.MEAL_TYPE_SNACK
            else -> when (now.hour) {
                in 5..9 -> MealType.MEAL_TYPE_BREAKFAST
                in 10..15 -> MealType.MEAL_TYPE_LUNCH
                in 17..21 -> MealType.MEAL_TYPE_DINNER
                else -> MealType.MEAL_TYPE_SNACK
            }
        }

        fun mealLabel(type: Int): String = when (type) {
            MealType.MEAL_TYPE_BREAKFAST -> "Breakfast"
            MealType.MEAL_TYPE_LUNCH -> "Lunch"
            MealType.MEAL_TYPE_DINNER -> "Dinner"
            MealType.MEAL_TYPE_SNACK -> "Snack"
            else -> "Meal"
        }
    }
}

private fun Double.fmt(): String = if (this == Math.rint(this)) toLong().toString() else "%.1f".format(this)
