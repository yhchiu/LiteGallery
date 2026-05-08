package org.iurl.litegallery

enum class FolderGroupBy(val preferenceValue: String) {
    NONE("none"),
    DATE("date"),
    NAME("name"),
    SIZE("size"),
    TYPE("type");

    companion object {
        fun fromPreference(value: String?): FolderGroupBy {
            return entries.firstOrNull { it.preferenceValue == value } ?: NONE
        }
    }
}
