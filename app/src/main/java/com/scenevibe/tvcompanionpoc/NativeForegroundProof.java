package com.scenevibe.tvcompanionpoc;

import java.util.HashSet;
import java.util.Set;

/**
 * Pure reduction of recent Activity-resume/pause evidence for the qualification target.
 * A single app must remain resumed. Empty history, HOME or concurrent apps refuse;
 * no app history is persisted and this is never a presentation/scheduler owner.
 */
final class NativeForegroundProof {
    private final Set<String> resumed = new HashSet<>();

    /** Apply only public activity lifecycle event codes; unknown events grant nothing. */
    void event(int type, String packageName) {
        if (packageName == null || packageName.isEmpty()) return;
        if (type == 1) resumed.add(packageName);
        else if (type == 2 || type == 23) resumed.remove(packageName);
    }

    /** Admit only an unambiguous recent Prime foreground lifecycle. */
    boolean primeAlone() {
        return resumed.size() == 1 && resumed.contains(NativeQualificationPolicy.PRIME_PACKAGE);
    }
}
