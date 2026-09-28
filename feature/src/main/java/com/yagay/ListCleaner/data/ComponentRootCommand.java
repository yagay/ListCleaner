package com.yagay.ListCleaner.data;

import com.yagay.ListCleaner.ui.DiagnosticBuffer;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** One explicit user action per invocation; bounded output/time, no persistent root daemon. */
public final class ComponentRootCommand {
    private ComponentRootCommand() {}

    public enum RootFailureReason {
        UNAVAILABLE,
        TIMEOUT,
        DENIED
    }

    public static final class RootAccessException extends IllegalStateException {
        public final RootFailureReason reason;

        RootAccessException(RootFailureReason reason) {
            super(reason.name());
            this.reason = reason;
        }

        RootAccessException(RootFailureReason reason, Throwable cause) {
            super(reason.name(), cause);
            this.reason = reason;
        }
    }

    /** Read-only check for each explicit batch; do not cache authorization across actions. */
    public static void requireRoot() {
        final Result result;
        try {
            result = run("test \"$(id -u)\" = 0");
        } catch (Exception failure) {
            throw new RootAccessException(RootFailureReason.UNAVAILABLE, failure);
        }
        verifyRoot(result);
    }

    static void verifyRoot(Result result) {
        if (result.timedOut) {
            throw new RootAccessException(RootFailureReason.TIMEOUT);
        }
        if (result.exitCode != 0) {
            throw new RootAccessException(RootFailureReason.DENIED);
        }
    }

    public static final class Result {
        public final int exitCode;
        public final boolean timedOut;
        public final String output;
        Result(int code, boolean timeout, String text) { exitCode = code; timedOut = timeout; output = text; }
    }

    public static Result run(String script) throws Exception {
        return capture(new ProcessBuilder("su", "-c", script), 25);
    }

    public static Result capture(ProcessBuilder builder, int seconds) throws Exception {
        DiagnosticBuffer buffer = new DiagnosticBuffer(16 * 1024);
        AtomicReference<Exception> error = new AtomicReference<>();
        Process process = builder.redirectErrorStream(true).start();
        Thread reader = new Thread(() -> {
            try (java.io.InputStream in = process.getInputStream()) {
                byte[] bytes = new byte[2048]; int size;
                while ((size = in.read(bytes)) >= 0) buffer.append(bytes, size);
            } catch (Exception failure) { error.set(failure); }
        }, "component-root-output");
        reader.setDaemon(true);
        reader.start();
        try {
            boolean finished = process.waitFor(seconds, TimeUnit.SECONDS);
            if (!finished) process.destroyForcibly();
            reader.join(1000);
            String text = new String(buffer.snapshot(), StandardCharsets.UTF_8);
            if (buffer.truncated()) text += "\n[output truncated]";
            if (reader.isAlive() || error.get() != null) text += "\n[output incomplete]";
            return new Result(finished ? process.exitValue() : -1, !finished, text);
        } finally {
            process.destroy();
            try { process.getInputStream().close(); } catch (Exception ignored) { }
            try { process.getOutputStream().close(); } catch (Exception ignored) { }
            try { process.getErrorStream().close(); } catch (Exception ignored) { }
        }
    }
}
