package com.yagay.ListCleaner.ui;

/**
 * Compatibility shim for older UI/tooling references.
 * New code should use com.yagay.ListCleaner.diagnostics.DiagnosticBuffer directly.
 */
@Deprecated
public final class DiagnosticBuffer extends com.yagay.ListCleaner.diagnostics.DiagnosticBuffer {
    public DiagnosticBuffer(int capacity) {
        super(capacity);
    }
}
