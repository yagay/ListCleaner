package com.yagay.ListCleaner.data

import java.io.Reader

/** Bound allocations even when the document provider cannot report a file size. */
internal fun Reader.readBackupText(
    limit: Int = 2_000_000,
    tooLargeMessage: String = "Backup file is too large"
): String {
    val text = StringBuilder()
    val buffer = CharArray(8192)
    while (true) {
        val count = read(buffer)
        if (count < 0) return text.toString()
        require(count <= limit - text.length) { tooLargeMessage }
        text.append(buffer, 0, count)
    }
}
