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
    add(
        AdditionalEntryProbe(
            Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:test@example.com")),
            broad = false,
            label = "SENDTO scheme=mailto"
        )
    )
    add(
        AdditionalEntryProbe(
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:123456789")),
            broad = false,
            label = "SENDTO scheme=smsto"
        )
    )
    add(
        AdditionalEntryProbe(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:123456789")),
            broad = false,
            label = "DIAL scheme=tel"
        )
    )

    add(
        AdditionalEntryProbe(
            Intent("android.media.action.IMAGE_CAPTURE"),
            broad = false,
            label = "CAPTURE_IMAGE"
        )
    )
    add(
        AdditionalEntryProbe(
            Intent("android.media.action.VIDEO_CAPTURE"),
            broad = false,
            label = "CAPTURE_VIDEO"
        )
    )
    add(
        AdditionalEntryProbe(
            Intent("android.provider.MediaStore.RECORD_SOUND"),
            broad = false,
            label = "RECORD_AUDIO"
        )
    )

    listOf("*/*", "image/*", "video/*", "audio/*", "text/plain", "application/pdf").forEach { mime ->
        add(
            AdditionalEntryProbe(
                Intent(Intent.ACTION_GET_CONTENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType(mime),
                broad = mime == "*/*",
                label = "GET_CONTENT mime=$mime"
            )
        )
    }
}
