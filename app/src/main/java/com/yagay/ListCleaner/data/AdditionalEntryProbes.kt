package com.yagay.ListCleaner.data

import android.content.Intent
import android.net.Uri

/** Resolver entry probes that are distinct from ordinary VIEW/SHARE handling. */
internal data class AdditionalEntryProbe(
    val intent: Intent,
    val broad: Boolean,
    val label: String
)

internal fun additionalEntryProbes(): List<AdditionalEntryProbe> = buildList {
    val sendToSamples = listOf(
        "mailto:test@example.com",
        "sms:123456789",
        "smsto:123456789",
        "mms:123456789",
        "mmsto:123456789",
    )
    sendToSamples.forEach { sample ->
        val scheme = sample.substringBefore(':')
        add(
            AdditionalEntryProbe(
                Intent(Intent.ACTION_SENDTO, Uri.parse(sample)),
                false,
                "SENDTO scheme=$scheme",
            )
        )
    }
    add(AdditionalEntryProbe(Intent(Intent.ACTION_DIAL, Uri.parse("tel:123456789")), false, "DIAL scheme=tel"))

    add(AdditionalEntryProbe(Intent("android.media.action.IMAGE_CAPTURE"), false, "CAPTURE_IMAGE"))
    add(AdditionalEntryProbe(Intent("android.media.action.VIDEO_CAPTURE"), false, "CAPTURE_VIDEO"))
    add(AdditionalEntryProbe(Intent("android.provider.MediaStore.RECORD_SOUND"), false, "RECORD_AUDIO"))

    val documentMimes = listOf("*/*", "image/*", "video/*", "audio/*", "text/plain", "application/pdf")
    documentMimes.forEach { mime ->
        add(
            AdditionalEntryProbe(
                Intent(Intent.ACTION_GET_CONTENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(mime),
                broad = mime == "*/*",
                label = "GET_CONTENT mime=$mime"
            )
        )
        add(
            AdditionalEntryProbe(
                Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(mime),
                broad = mime == "*/*",
                label = "OPEN_DOCUMENT mime=$mime"
            )
        )
        add(
            AdditionalEntryProbe(
                Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(mime)
                    .putExtra(Intent.EXTRA_TITLE, "ListCleaner-probe"),
                broad = mime == "*/*",
                label = "CREATE_DOCUMENT mime=$mime"
            )
        )
    }

    add(
        AdditionalEntryProbe(
            Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME)
                .addCategory(Intent.CATEGORY_DEFAULT),
            broad = false,
            label = "DEFAULT_HOME"
        )
    )
    add(
        AdditionalEntryProbe(
            Intent(Intent.ACTION_ASSIST),
            broad = false,
            label = "ASSISTANT"
        )
    )
}
