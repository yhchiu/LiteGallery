package org.iurl.litegallery

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.text.DateFormat
import java.util.Date

object SearchFilterUi {
    private const val KB = 1024L
    private const val MB = 1024L * 1024L
    private const val GB = 1024L * 1024L * 1024L

    private val RANGE_LT_1_MB = 1L until MB
    private val RANGE_1_TO_10_MB = MB until (10L * MB)
    private val RANGE_10_TO_100_MB = (10L * MB) until (100L * MB)
    private val RANGE_GT_100_MB = (100L * MB)..Long.MAX_VALUE

    fun formatDateRange(context: Context, range: TimeRange?): String {
        if (range == null) return context.getString(R.string.search_chip_date_any)
        val formatter = DateFormat.getDateInstance(DateFormat.SHORT)
        val start = formatter.format(Date(range.startMsInclusive))
        val end = formatter.format(Date(range.endMsExclusive - 1L))
        return context.getString(R.string.search_chip_date_format, start, end)
    }

    fun formatSizeRange(context: Context, range: LongRange?): String {
        return when (range) {
            null -> context.getString(R.string.search_chip_size_any)
            RANGE_LT_1_MB -> context.getString(R.string.search_chip_size_lt_1mb)
            RANGE_1_TO_10_MB -> context.getString(R.string.search_chip_size_1_10mb)
            RANGE_10_TO_100_MB -> context.getString(R.string.search_chip_size_10_100mb)
            RANGE_GT_100_MB -> context.getString(R.string.search_chip_size_gt_100mb)
            else -> {
                val min = FormatterCompat.formatShortFileSize(context, range.first)
                val max = FormatterCompat.formatShortFileSize(context, range.last)
                "$min - $max"
            }
        }
    }

    fun showSizeRangeDialog(
        activity: AppCompatActivity,
        currentRange: LongRange?,
        onRangeSelected: (LongRange?) -> Unit
    ) {
        val labels = arrayOf(
            activity.getString(R.string.search_chip_size_any),
            activity.getString(R.string.search_chip_size_lt_1mb),
            activity.getString(R.string.search_chip_size_1_10mb),
            activity.getString(R.string.search_chip_size_10_100mb),
            activity.getString(R.string.search_chip_size_gt_100mb),
            activity.getString(R.string.search_chip_size_custom)
        )
        val checked = when (currentRange) {
            null -> 0
            RANGE_LT_1_MB -> 1
            RANGE_1_TO_10_MB -> 2
            RANGE_10_TO_100_MB -> 3
            RANGE_GT_100_MB -> 4
            else -> 5
        }

        AlertDialog.Builder(activity)
            .setTitle(R.string.search_size_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                when (which) {
                    0 -> {
                        onRangeSelected(null)
                        dialog.dismiss()
                    }
                    1 -> {
                        onRangeSelected(RANGE_LT_1_MB)
                        dialog.dismiss()
                    }
                    2 -> {
                        onRangeSelected(RANGE_1_TO_10_MB)
                        dialog.dismiss()
                    }
                    3 -> {
                        onRangeSelected(RANGE_10_TO_100_MB)
                        dialog.dismiss()
                    }
                    4 -> {
                        onRangeSelected(RANGE_GT_100_MB)
                        dialog.dismiss()
                    }
                    5 -> {
                        dialog.dismiss()
                        showCustomSizeDialog(activity, onRangeSelected)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .showThemed()
    }

    private fun showCustomSizeDialog(
        activity: AppCompatActivity,
        onRangeSelected: (LongRange?) -> Unit
    ) {
        val minEditText = EditText(activity).apply {
            hint = activity.getString(R.string.search_size_min)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val maxEditText = EditText(activity).apply {
            hint = activity.getString(R.string.search_size_max)
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        val unitSpinner = Spinner(activity).apply {
            adapter = ArrayAdapter(
                activity,
                android.R.layout.simple_spinner_dropdown_item,
                arrayOf("KB", "MB", "GB")
            )
            setSelection(1)
        }

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padding = (20 * resources.displayMetrics.density).toInt()
            setPadding(padding, padding / 2, padding, 0)
            addView(minEditText, matchWidthParams())
            addView(maxEditText, matchWidthParams())
            addView(unitSpinner, matchWidthParams())
        }

        val dialog = AlertDialog.Builder(activity)
            .setTitle(R.string.search_size_custom_title)
            .setView(content)
            .setPositiveButton(R.string.ok, null)
            .setNegativeButton(R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            ThemeHelper.applyRuntimeCustomColors(dialog)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val multiplier = when (unitSpinner.selectedItemPosition) {
                    0 -> KB
                    1 -> MB
                    else -> GB
                }
                val range = parseCustomRange(minEditText.text?.toString(), maxEditText.text?.toString(), multiplier)
                if (range == null) {
                    Toast.makeText(activity, R.string.search_size_invalid, Toast.LENGTH_SHORT).show()
                } else {
                    onRangeSelected(range)
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun parseCustomRange(minText: String?, maxText: String?, multiplier: Long): LongRange? {
        val minValue = minText?.trim()?.toLongOrNull() ?: return null
        val maxValue = maxText?.trim()?.toLongOrNull() ?: return null
        if (minValue < 0L || maxValue < 0L || maxValue < minValue) return null
        if (minValue > Long.MAX_VALUE / multiplier || maxValue > Long.MAX_VALUE / multiplier) return null
        val minBytes = minValue * multiplier
        val maxBytes = maxValue * multiplier
        if (maxBytes < minBytes) return null
        return minBytes..maxBytes
    }

    private fun matchWidthParams(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

    private fun AlertDialog.Builder.showThemed(): AlertDialog {
        val dialog = create()
        dialog.show()
        ThemeHelper.applyRuntimeCustomColors(dialog)
        return dialog
    }

    private object FormatterCompat {
        fun formatShortFileSize(context: Context, bytes: Long): String =
            android.text.format.Formatter.formatShortFileSize(context, bytes)
    }
}
