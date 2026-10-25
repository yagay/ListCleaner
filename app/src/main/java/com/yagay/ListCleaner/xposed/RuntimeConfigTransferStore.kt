package com.yagay.ListCleaner.xposed

import android.content.Intent
import android.os.SystemClock
import com.yagay.ListCleaner.data.RuleRepository
import com.yagay.ListCleaner.domain.RuntimeProtocol

/** Owns manager BEGIN/CHUNK/COMMIT transport state and returns only verified payloads. */
internal class RuntimeConfigTransferStore(
    private val record: (String) -> Unit,
    private val now: () -> Long = SystemClock::elapsedRealtime,
) {
    data class Completed(
        val id: String,
        val digest: String,
        val revision: Long,
        val encoded: String,
    )

    private data class Pending(
        val callerUid: Int,
        val managerAppId: Int,
        val id: String,
        val digest: String,
        val revision: Long,
        val totalChars: Int,
        val startedAt: Long,
        val chunks: Array<String?>,
    )

    private var pending: Pending? = null

    @Synchronized
    fun begin(intent: Intent, callerUid: Int, managerAppId: Int): Boolean {
        val transferId = intent.getStringExtra(RuntimeProtocol.EXTRA_TRANSFER_ID)
        val digest = intent.getStringExtra(RuntimeProtocol.EXTRA_EXPECTED_DIGEST)
        val revision = intent.getLongExtra(RuntimeProtocol.EXTRA_REVISION, -1L)
        val totalChunks = intent.getIntExtra(RuntimeProtocol.EXTRA_TOTAL_CHUNKS, -1)
        val totalChars = intent.getIntExtra(RuntimeProtocol.EXTRA_TOTAL_CHARS, -1)
        if (!RuntimeProtocol.validTransferId(transferId) ||
            !RuntimeProtocol.validDigest(digest) ||
            revision < 0L ||
            totalChunks !in 1..RuntimeProtocol.MAX_CONFIG_CHUNKS ||
            totalChars !in 1..RuleRepository.MAX_BACKUP_CHARS
        ) {
            record(
                "CONFIG_PUSH_REJECT stage=begin uid=$callerUid transfer=${transferId ?: "none"} " +
                    "chunks=$totalChunks chars=$totalChars revision=$revision"
            )
            return false
        }
        pending = Pending(
            callerUid = callerUid,
            managerAppId = managerAppId,
            id = requireNotNull(transferId),
            digest = requireNotNull(digest),
            revision = revision,
            totalChars = totalChars,
            startedAt = now(),
            chunks = arrayOfNulls(totalChunks),
        )
        record(
            "CONFIG_PUSH_BEGIN uid=$callerUid transfer=$transferId chunks=$totalChunks " +
                "chars=$totalChars revision=$revision digest=$digest"
        )
        return true
    }

    @Synchronized
    fun append(intent: Intent, callerUid: Int): Boolean {
        val transfer = pending ?: return false
        if (transfer.callerUid != callerUid || now() - transfer.startedAt > TIMEOUT_MS) {
            pending = null
            record("CONFIG_PUSH_REJECT stage=chunk uid=$callerUid reason=owner_or_timeout")
            return false
        }
        val transferId = intent.getStringExtra(RuntimeProtocol.EXTRA_TRANSFER_ID)
        val index = intent.getIntExtra(RuntimeProtocol.EXTRA_CHUNK_INDEX, -1)
        val chunk = intent.getStringExtra(RuntimeProtocol.EXTRA_CONFIG_CHUNK)
        if (transferId != transfer.id ||
            index !in transfer.chunks.indices ||
            chunk == null ||
            chunk.length > RuntimeProtocol.CONFIG_CHUNK_CHARS
        ) {
            record(
                "CONFIG_PUSH_REJECT stage=chunk uid=$callerUid transfer=${transferId ?: "none"} index=$index"
            )
            return false
        }
        transfer.chunks[index] = chunk
        return true
    }

    @Synchronized
    fun commit(intent: Intent, callerUid: Int, managerAppId: Int): Completed? {
        val transfer = pending ?: return null
        pending = null
        val transferId = intent.getStringExtra(RuntimeProtocol.EXTRA_TRANSFER_ID)
        val expectedDigest = intent.getStringExtra(RuntimeProtocol.EXTRA_EXPECTED_DIGEST)
        val revision = intent.getLongExtra(RuntimeProtocol.EXTRA_REVISION, -1L)
        if (transfer.callerUid != callerUid ||
            transfer.managerAppId != managerAppId ||
            transferId != transfer.id ||
            expectedDigest != transfer.digest ||
            revision != transfer.revision ||
            now() - transfer.startedAt > TIMEOUT_MS ||
            transfer.chunks.any { it == null }
        ) {
            record(
                "CONFIG_PUSH_REJECT stage=commit uid=$callerUid transfer=${transferId ?: "none"} " +
                    "revision=$revision reason=metadata_or_chunks"
            )
            return null
        }
        val encoded = buildString(transfer.totalChars) {
            transfer.chunks.forEach { append(requireNotNull(it)) }
        }
        if (encoded.length != transfer.totalChars) {
            record(
                "CONFIG_PUSH_REJECT stage=commit uid=$callerUid transfer=${transfer.id} " +
                    "reason=length expected=${transfer.totalChars} actual=${encoded.length}"
            )
            return null
        }
        val actualDigest = RuntimeProtocol.digest(encoded)
        if (actualDigest != transfer.digest) {
            record(
                "CONFIG_PUSH_REJECT stage=commit uid=$callerUid transfer=${transfer.id} " +
                    "reason=digest expected=${transfer.digest} actual=$actualDigest"
            )
            return null
        }
        return Completed(transfer.id, actualDigest, transfer.revision, encoded)
    }

    private companion object {
        const val TIMEOUT_MS = 10_000L
    }
}
