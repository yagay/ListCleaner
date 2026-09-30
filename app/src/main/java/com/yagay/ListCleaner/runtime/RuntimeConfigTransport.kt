package com.yagay.ListCleaner.runtime

import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import com.yagay.ListCleaner.BuildConfig
import com.yagay.ListCleaner.R
import com.yagay.ListCleaner.domain.RuntimeAckCodec
import com.yagay.ListCleaner.domain.RuntimeProtocol
import java.util.UUID

/** UID-authenticated Runtime Probe v2 client. Wire format stays isolated from Application lifecycle. */
internal class RuntimeConfigTransport(
    private val context: Context,
) {
    fun queryAck(expectedDigest: String): RuntimeAckCodec.Ack? = parseAck(
        query(
            Intent(RuntimeProtocol.ACTION)
                .setPackage(context.packageName)
                .putExtra(RuntimeProtocol.EXTRA_PROTOCOL_VERSION, RuntimeProtocol.VERSION)
                .putExtra(RuntimeProtocol.EXTRA_EXPECTED_DIGEST, expectedDigest)
        ),
        expectedDigest,
    )

    fun pushConfig(encoded: String, digest: String, revision: Long): RuntimeAckCodec.Ack? {
        val chunks = encoded.chunked(RuntimeProtocol.CONFIG_CHUNK_CHARS)
        require(chunks.isNotEmpty() && chunks.size <= RuntimeProtocol.MAX_CONFIG_CHUNKS) {
            context.getString(R.string.runtime_config_transfer_too_large)
        }
        val transferId = UUID.randomUUID().toString()

        query(
            Intent(RuntimeProtocol.ACTION)
                .setPackage(context.packageName)
                .putExtra(RuntimeProtocol.EXTRA_PROTOCOL_VERSION, RuntimeProtocol.VERSION)
                .putExtra(RuntimeProtocol.EXTRA_OPERATION, RuntimeProtocol.OP_BEGIN)
                .putExtra(RuntimeProtocol.EXTRA_TRANSFER_ID, transferId)
                .putExtra(RuntimeProtocol.EXTRA_EXPECTED_DIGEST, digest)
                .putExtra(RuntimeProtocol.EXTRA_REVISION, revision)
                .putExtra(RuntimeProtocol.EXTRA_TOTAL_CHUNKS, chunks.size)
                .putExtra(RuntimeProtocol.EXTRA_TOTAL_CHARS, encoded.length)
        )

        chunks.forEachIndexed { index, chunk ->
            query(
                Intent(RuntimeProtocol.ACTION)
                    .setPackage(context.packageName)
                    .putExtra(RuntimeProtocol.EXTRA_PROTOCOL_VERSION, RuntimeProtocol.VERSION)
                    .putExtra(RuntimeProtocol.EXTRA_OPERATION, RuntimeProtocol.OP_CHUNK)
                    .putExtra(RuntimeProtocol.EXTRA_TRANSFER_ID, transferId)
                    .putExtra(RuntimeProtocol.EXTRA_CHUNK_INDEX, index)
                    .putExtra(RuntimeProtocol.EXTRA_CONFIG_CHUNK, chunk)
            )
        }

        return parseAck(
            query(
                Intent(RuntimeProtocol.ACTION)
                    .setPackage(context.packageName)
                    .putExtra(RuntimeProtocol.EXTRA_PROTOCOL_VERSION, RuntimeProtocol.VERSION)
                    .putExtra(RuntimeProtocol.EXTRA_OPERATION, RuntimeProtocol.OP_COMMIT)
                    .putExtra(RuntimeProtocol.EXTRA_TRANSFER_ID, transferId)
                    .putExtra(RuntimeProtocol.EXTRA_EXPECTED_DIGEST, digest)
                    .putExtra(RuntimeProtocol.EXTRA_REVISION, revision)
            ),
            digest,
        )
    }

    @Suppress("DEPRECATION")
    private fun query(intent: Intent): List<ResolveInfo> =
        context.packageManager.queryIntentActivities(intent, 0)

    private fun parseAck(results: List<ResolveInfo>, expectedDigest: String): RuntimeAckCodec.Ack? =
        results.asSequence()
            .filter { candidate ->
                candidate.activityInfo?.packageName == context.packageName &&
                    candidate.activityInfo?.name == RuntimeProtocol.COMPONENT
            }
            .mapNotNull { candidate ->
                RuntimeAckCodec.parse(
                    label = candidate.nonLocalizedLabel?.toString(),
                    hookCompatVersion = BuildConfig.HOOK_COMPAT_VERSION_CODE.toLong(),
                    expectedDigest = expectedDigest,
                )
            }
            .firstOrNull()
}
