package com.yagay.ListCleaner.data

/**
 * A batch is only usable if the *individual* shell command succeeded.
 * The trailing printf in a compound su script otherwise hides failures.
 * Keep this parser free of Android dependencies for JVM regression tests.
 */
internal object ProfileShellBatch {
    fun script(commands: List<String>, prefix: String): String = buildString {
        commands.forEachIndexed { index, command ->
            append("printf '\\n__${prefix}_START_${index}__\\n'; ")
            append(command)
            append("; lc_status=\$?; printf '\\n__${prefix}_END_${index}__:%s\\n' \"\$lc_status\"; ")
        }
    }

    fun entry(output: String, index: Int, prefix: String): String? {
        val start = "__${prefix}_START_${index}__\\n"
        val end = "\\n__${prefix}_END_${index}__:"
        val startPosition = output.indexOf(start)
        if (startPosition < 0) return null
        val bodyStart = startPosition + start.length
        val endPosition = output.indexOf(end, bodyStart)
        if (endPosition < 0) return null
        val statusStart = endPosition + end.length
        val status = output.substring(statusStart).lineSequence().firstOrNull()?.trim()?.toIntOrNull()
            ?: return null
        if (status != 0) return null
        val body = output.substring(bodyStart, endPosition)
        return body.takeIf(::clean)
    }

    fun clean(output: String): Boolean = output.lineSequence().none { raw ->
        val line = raw.trim()
        line.startsWith("Error:", ignoreCase = true) ||
            line.startsWith("Exception", ignoreCase = true) ||
            line.startsWith("SecurityException", ignoreCase = true) ||
            line.startsWith("java.lang.", ignoreCase = true) ||
            line.startsWith("Unknown command", ignoreCase = true) ||
            line.contains("[output truncated]") ||
            line.contains("[output incomplete]")
    }
}
