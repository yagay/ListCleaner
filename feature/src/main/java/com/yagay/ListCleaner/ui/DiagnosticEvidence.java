package com.yagay.ListCleaner.ui;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded, deduplicated historical evidence. Counts are observations, not live health. */
public final class DiagnosticEvidence {
    private static final Pattern ID = Pattern.compile("pid=(\\d+) process=(\\S+)");
    private static final Pattern KIND = Pattern.compile("kind=(\\w+)");
    private final DiagnosticBuffer buffer = new DiagnosticBuffer(512 * 1024);
    private final Set<String> seen = new HashSet<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final MessageDigest digest;
    private int duplicates;
    private int omitted;

    public DiagnosticEvidence() {
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }

    public void accept(String source, String line) {
        if (!line.contains("com.yagay.ListCleaner,ListCleaner.Diagnostic,")) return;
        String hash = java.util.Base64.getEncoder().encodeToString(digest.digest(line.getBytes(StandardCharsets.UTF_8)));
        if (seen.contains(hash)) { duplicates++; return; }
        if (seen.size() >= 8192) { omitted++; return; }
        seen.add(hash);
        Matcher id = ID.matcher(line);
        if (id.find()) {
            String stage = line.contains(" MODULE_LOADED ") ? "loaded" :
                line.contains(" HOOK_INSTALLED ") ? "hookInstalled" :
                line.contains(" QUERY ") ? "queryObserved" :
                line.contains(" ORDER_APPLIED ") ? "orderObserved" :
                line.contains(" TILE_HOOK_INSTALLED ") ? "tileHookInstalled" :
                line.contains(" TILE_CONFIG ") ? "tileConfigRead" :
                line.contains(" TILE_EDITOR_SEEN ") ? "tileEditorSeen" :
                line.contains(" TILE_FILTERED ") ? "tileFilterObserved" :
                line.contains(" TILE_UNSUPPORTED ") ? "tileUnsupported" :
                line.contains(" TILE_FAILED ") || line.contains(" TILE_RESTORE_FAILED ") ? "tileFailed" :
                line.contains(" ORDER_RESULT ") ? (line.contains("changed=true") ? "orderChanged" : "orderUnchanged") :
                line.contains(" ORDER_DELIVERED ") ? "orderDeliveredNotUiVerified" :
                line.contains(" ORDER_HOOK_INSTALLED ") ? "orderHookInstalled" :
                line.contains(" ORDER_CAPABILITY ") ? "orderCapabilityObserved" :
                line.contains(" ORDER_SKIP ") ? "orderSkipped" :
                line.contains(" ORDER_FAILED ") || line.contains(" ORDER_HOOK_FAILED ") ? "orderFailed" :
                line.contains(" MANAGER_QUERY_BYPASS ") ? "managerBypass" :
                line.contains(" CONFIG_ACK ") ? "configAcknowledged" :
                line.contains(" HOT_RELOAD_READY ") ? "hotReloadReady" :
                line.contains(" HOT_RELOAD_FAILED ") ? "hotReloadFailed" :
                line.contains(" RULES_READ_FAILED ") ? "rulesReadFailed" :
                line.contains("FILTER_PAUSED") ? "identityUnknown" : null;
            if (stage != null) {
                Matcher kind = KIND.matcher(line);
                String key = id.group(2) + " pid=" + id.group(1) + " " + stage +
                    (kind.find() ? " kind=" + kind.group(1) : "");
                if (counts.containsKey(key) || counts.size() < 512) counts.merge(key, 1, Integer::sum);
            }
        }
        byte[] bytes = ("[" + source + "] " + line + "\n").getBytes(StandardCharsets.UTF_8);
        buffer.append(bytes, bytes.length);
    }

    /** Stable machine-oriented report body. Human explanations are added by the localized exporter. */
    public String reportBody() {
        StringBuilder result = new StringBuilder();
        result.append("duplicatesRemoved=").append(duplicates)
            .append(" omittedEvents=").append(omitted)
            .append(" textTruncated=").append(buffer.truncated()).append("\n\n");
        counts.forEach((key, value) -> result.append(key).append(" events=").append(value).append('\n'));
        return result.append("\nEvidence:\n").append(new String(buffer.snapshot(), StandardCharsets.UTF_8)).toString();
    }

    /** Kept for host-side regression checks and tooling compatibility. */
    public String report() {
        return "Historical observations, NOT live health.\n"
            + "Missing evidence is UNKNOWN. Counts are deduplicated event counts, not active hooks.\n"
            + "Check source timestamps/PIDs; old boots, truncation and rate limits can hide events.\n"
            + reportBody();
    }
}
