package org.iurl.litegallery

import android.content.Context
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.ListPreference
import androidx.preference.PreferenceViewHolder

class ColorThemePreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle
) : ListPreference(context, attrs, defStyleAttr) {

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        
        // Find the summary TextView
        val summaryView = holder.findViewById(android.R.id.summary) as? TextView
        
        if (summaryView != null && value != null) {
            // Get the current color theme
            val colorTheme = value ?: ThemeHelper.COLOR_BLUE
            val color = ThemeHelper.getColorThemePreviewColor(context, colorTheme)
            
            // Create a colored circle drawable for the summary
            val drawable = context.resources.getDrawable(R.drawable.color_preview_circle, null)
            drawable?.setTint(color)
            drawable?.setBounds(0, 0, 32, 32)
            
            // Set the drawable as compound drawable
            summaryView.setCompoundDrawablesRelative(drawable, null, null, null)
            summaryView.compoundDrawablePadding = 16
        }
    }
}