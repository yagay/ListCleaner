package com.yagay.ListCleaner.domain

/**
 * Small declarative descriptions for apps that embed their own Direct Share surface instead of
 * delegating to Android's system Chooser. The filtering engine stays vendor-neutral; supporting a
 * new ROM/app should normally require only another profile here.
 */
data class EmbeddedDirectShareMethodSignature(
    val returnTypeName: String,
    val parameterTypeNames: List<String>,
)

data class EmbeddedDirectShareHostProfile(
    val id: String,
    val packages: Set<String>,
    val shareActivityClasses: Set<String>,
    val adapterClasses: Set<String>,
    val refreshMethods: Set<EmbeddedDirectShareMethodSignature>,
)

object EmbeddedDirectShareProfiles {
    val all: List<EmbeddedDirectShareHostProfile> = listOf(
        EmbeddedDirectShareHostProfile(
            id = "oplus-gallery",
            packages = setOf(
                "com.oneplus.gallery",
                "com.oplus.gallery",
                "com.coloros.gallery3d",
            ),
            shareActivityClasses = setOf(
                "com.oplus.gallery.sharepage.GalleryShareInnerActivity",
            ),
            adapterClasses = setOf(
                "com.oplus.gallery.sharepage.widget.HorizontalDirectShareRecyclerViewAdapter",
            ),
            // Method names are intentionally not used: OEM builds commonly obfuscate them. Match
            // the stable structural signature instead.
            refreshMethods = setOf(
                EmbeddedDirectShareMethodSignature(
                    returnTypeName = "void",
                    parameterTypeNames = listOf("android.content.Intent"),
                ),
            ),
        ),
    )

    val knownPackages: Set<String> = all.flatMapTo(linkedSetOf()) { it.packages }

    fun matching(packageName: String): List<EmbeddedDirectShareHostProfile> =
        all.filter { packageName in it.packages }

    fun isKnownHost(packageName: String): Boolean = packageName in knownPackages
}
