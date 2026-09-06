package com.yagay.ListCleaner.domain

data class VisibilityLayout(
    val uidIndex: Int,
    val callerSettingIndex: Int?,
    val targetIndex: Int,
)

/** Parses known AOSP/OEM AppsFilter signatures without assuming fixed argument positions. */
object VisibilitySignature {
    fun parse(typeNames: List<String>): VisibilityLayout? {
        if (typeNames.isEmpty()) return null
        val intIndices = typeNames.indices.filter { typeNames[it] == "int" || typeNames[it] == "java.lang.Integer" }
        if (intIndices.size < 2) return null
        val uidIndex = intIndices.first()
        val targetIndex = typeNames.indices.lastOrNull { index ->
            index > uidIndex && looksLikeTargetState(typeNames[index])
        } ?: return null
        val callerSettingIndex = (uidIndex + 1 until targetIndex).firstOrNull { index ->
            val name = typeNames[index]
            name != "int" && name != "java.lang.Integer" && name != "boolean" && name != "java.lang.Boolean"
        }
        return VisibilityLayout(uidIndex, callerSettingIndex, targetIndex)
    }

    private fun looksLikeTargetState(name: String): Boolean =
        name.contains("PackageState", ignoreCase = true) ||
            name.endsWith("PackageSetting") || name.contains(".PackageSetting")
}
