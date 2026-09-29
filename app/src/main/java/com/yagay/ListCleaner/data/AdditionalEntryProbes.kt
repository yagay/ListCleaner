package com.yagay.ListCleaner.data

import android.content.Intent
import android.net.Uri

/** Resolver entry probes that are distinct from ordinary VIEW/SHARE handling. */
internal data class AdditionalEntryProbe(
    val intent: Intent,
    val broad: Boolean,
    val label: String
)

private data class UriProbeDefinition(
    val action: String,
    val sampleUri: String,
    val labelPrefix: String,
)

private data class ActionProbeDefinition(
    val action: String,
    val label: String,
)

private val URI_PROBES = listOf(
    UriProbeDefinition(Intent.ACTION_SENDTO, "mailto:test@example.com", "SENDTO"),
    UriProbeDefinition(Intent.ACTION_SENDTO, "sms:123456789", "SENDTO"),
    UriProbeDefinition(Intent.ACTION_SENDTO, "smsto:123456789", "SENDTO"),
    UriProbeDefinition(Intent.ACTION_SENDTO, "mms:123456789", "SENDTO"),
    UriProbeDefinition(Intent.ACTION_SENDTO, "mmsto:123456789", "SENDTO"),
    UriProbeDefinition(Intent.ACTION_DIAL, "tel:123456789", "DIAL"),
)

private val ACTION_PROBES = listOf(
    ActionProbeDefinition("android.media.action.IMAGE_CAPTURE", "CAPTURE_IMAGE"),
    ActionProbeDefinition("android.media.action.VIDEO_CAPTURE", "CAPTURE_VIDEO"),
    ActionProbeDefinition("android.provider.MediaStore.RECORD_SOUND", "RECORD_AUDIO"),
)

private val DOCUMENT_MIMES = listOf(
    "*/*",
    "image/*",
    "video/*",
    "audio/*",
    "text/plain",
    "application/pdf",
)

internal fun additionalEntryProbes(): List<AdditionalEntryProbe> = buildList {
    URI_PROBES.forEach { definition ->
        val scheme = definition.sampleUri.substringBefore(':')
        add(
            AdditionalEntryProbe(
                intent = Intent(definition.action, Uri.parse(definition.sampleUri)),
                broad = false,
                label = "${definition.labelPrefix} scheme=$scheme",
            )
        )
    }

    ACTION_PROBES.forEach { definition ->
        add(AdditionalEntryProbe(Intent(definition.action), false, definition.label))
    }

    DOCUMENT_MIMES.forEach { mime ->
        add(documentProbe(Intent.ACTION_GET_CONTENT, mime, "GET_CONTENT"))
        add(documentProbe(Intent.ACTION_OPEN_DOCUMENT, mime, "OPEN_DOCUMENT"))
        add(documentProbe(Intent.ACTION_CREATE_DOCUMENT, mime, "CREATE_DOCUMENT", create = true))
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
    add(AdditionalEntryProbe(Intent(Intent.ACTION_ASSIST), false, "ASSISTANT"))
}

private fun documentProbe(
    action: String,
    mime: String,
    label: String,
    create: Boolean = false,
): AdditionalEntryProbe {
    val intent = Intent(action)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(mime)
    if (create) intent.putExtra(Intent.EXTRA_TITLE, "ListCleaner-probe")
    return AdditionalEntryProbe(
        intent = intent,
        broad = mime == "*/*",
        label = "$label mime=$mime",
    )
}
