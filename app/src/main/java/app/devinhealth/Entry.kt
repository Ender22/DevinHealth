package app.devinhealth

import android.net.Uri
import androidx.health.connect.client.records.BloodGlucoseRecord
import java.time.LocalTime

/**
 * Anything the app can log, parsed from a link such as
 *   devinhealth://log?name=...&kcal=...                (meal, the default)
 *   devinhealth://log?type=bp&sys=128&dia=82&pulse=66   (blood pressure)
 *   devinhealth://log?type=glucose&glucose=5.6&when=fasting
 * The https://ender22.github.io/DevinHealth/?... form carries the same query.
 */
sealed class Entry {
    abstract val label: String
    abstract fun summary(): String

    data class Meal(val meal: MealLink) : Entry() {
        override val label get() = MealLink.mealLabel(meal.mealType).lowercase()
        override fun summary() = (meal.name ?: "Meal") + "\n" + meal.summary()
    }

    data class BloodPressure(val systolic: Int, val diastolic: Int, val pulse: Int?) : Entry() {
        override val label get() = "blood pressure"
        override fun summary() = "$systolic/$diastolic mmHg" + (pulse?.let { ", pulse $it bpm" } ?: "")
    }

    /** [mmolPerL] is always stored in mmol/L; [mgPerDl] remembers how it was entered for display. */
    data class Glucose(val mmolPerL: Double, val enteredAsMgDl: Boolean, val relation: Int) : Entry() {
        override val label get() = "blood glucose"
        val mgPerDl: Double get() = mmolPerL * MGDL_PER_MMOL
        override fun summary(): String {
            val value = if (enteredAsMgDl) "${mgPerDl.toInt()} mg/dL" else "%.1f mmol/L".format(mmolPerL)
            return value + " (" + relationLabel(relation) + ")"
        }
    }

    companion object {
        const val MGDL_PER_MMOL = 18.0182

        fun parse(uri: Uri): Entry? {
            fun num(vararg keys: String): Double? = keys.firstNotNullOfOrNull { k ->
                uri.getQueryParameter(k)?.trim()?.replace(",", ".")?.toDoubleOrNull()?.takeIf { it >= 0 }
            }
            return when (uri.getQueryParameter("type")?.lowercase()) {
                "bp", "bloodpressure", "pressure" -> {
                    val sys = num("sys", "systolic")?.toInt() ?: return null
                    val dia = num("dia", "diastolic")?.toInt() ?: return null
                    if (sys !in 50..260 || dia !in 30..200) return null
                    BloodPressure(sys, dia, num("pulse", "hr", "bpm")?.toInt()?.takeIf { it in 25..250 })
                }
                "glucose", "sugar", "bg" -> {
                    val raw = num("glucose", "level", "value", "bg") ?: return null
                    glucose(raw, uri.getQueryParameter("unit"), uri.getQueryParameter("when") ?: uri.getQueryParameter("relation"))
                }
                else -> MealLink.parse(uri).takeIf { it.hasData }?.let { Meal(it) }
            }
        }

        /** Unit: "mmol"/"mgdl" or null to guess from magnitude (readings under 30 are mmol/L). */
        fun glucose(value: Double, unit: String?, relation: String?, now: LocalTime = LocalTime.now()): Glucose? {
            val isMgDl = when (unit?.lowercase()?.replace("/", "")?.replace(" ", "")) {
                "mgdl", "mg" -> true
                "mmol", "mmoll" -> false
                else -> value >= 30
            }
            val mmol = if (isMgDl) value / MGDL_PER_MMOL else value
            if (mmol !in 0.5..60.0) return null
            return Glucose(mmol, isMgDl, relationFrom(relation, now))
        }

        fun relationFrom(raw: String?, now: LocalTime = LocalTime.now()): Int = when (raw?.lowercase()) {
            "fasting" -> BloodGlucoseRecord.RELATION_TO_MEAL_FASTING
            "before", "before_meal", "beforemeal", "pre" -> BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL
            "after", "after_meal", "aftermeal", "post" -> BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL
            "general", "random" -> BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL
            else -> if (now.hour in 4..8) BloodGlucoseRecord.RELATION_TO_MEAL_FASTING
            else BloodGlucoseRecord.RELATION_TO_MEAL_GENERAL
        }

        fun relationLabel(relation: Int): String = when (relation) {
            BloodGlucoseRecord.RELATION_TO_MEAL_FASTING -> "fasting"
            BloodGlucoseRecord.RELATION_TO_MEAL_BEFORE_MEAL -> "before meal"
            BloodGlucoseRecord.RELATION_TO_MEAL_AFTER_MEAL -> "after meal"
            else -> "general"
        }
    }
}
