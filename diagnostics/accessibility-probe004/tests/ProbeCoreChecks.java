package com.scenevibe.accessibilityprobe004;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Dependency-free executable software checks. All payloads and clocks are synthetic; no
 * platform application or physical device is contacted. Each check gets independent files.
 */
public final class ProbeCoreChecks {
    /** Run every named scenario and fail the process on the first failed assertion. */
    public static void main(String[] args) throws Exception {
        int passed = 0;
        Method[] methods = ProbeCoreChecks.class.getDeclaredMethods();
        Arrays.sort(methods, Comparator.comparing(Method::getName));
        for (Method method : methods) if (method.getName().startsWith("check")) {
            try { method.invoke(null); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(method.getName(), failure.getCause()); }
            System.out.println("PASS " + method.getName()); passed++;
        }
        System.out.println("CORE_CHECKS_PASS=" + passed);
    }

    /** Create a deterministic controller with a writable private synthetic directory. */
    private static Rig rig() throws Exception { return new Rig(); }

    /** Throw a meaningful software assertion without relying on the JVM -ea switch. */
    private static void require(boolean condition) { if (!condition) throw new AssertionError("Scenario assertion failed"); }

    /** Read the current synthetic campaign bytes, never any owner's capture. */
    private static String log(Rig rig) throws Exception { return Files.readString(rig.store.latest().toPath()); }

    /** Refuse collection before manual service connection. */
    public static void checkDisconnected() throws Exception {
        Rig r = rig(); require(!r.core.arm(ProbeProfile.NETFLIX)); require(!r.core.active()); require(r.store.latest() == null);
    }

    /** Connection alone must not collect or create a journal. */
    public static void checkConnectedDisarmed() throws Exception {
        Rig r = rig(); r.core.connect(); require(!r.core.active()); require(!r.core.accepts("com.netflix.ninja")); require(r.store.latest() == null);
    }

    /** Reject a profile mutation in an armed session. */
    public static void checkImmutableProfile() throws Exception {
        Rig r = rig(); r.arm(); require(!r.core.arm(ProbeProfile.DISNEY_PLUS)); require(r.core.profile() == ProbeProfile.NETFLIX); r.stop();
    }

    /** Package scoping never stores the rejected package string. */
    public static void checkPackageBoundary() throws Exception {
        Rig r = rig(); r.arm(); require(r.core.accepts("com.netflix.ninja")); require(!r.core.accepts("private.account.secret"));
        require(!r.core.accepts(null)); r.stop(); require(!log(r).contains("private.account")); require(log(r).contains("\"rejectedPackageCount\":2"));
    }

    /** Stop closes the stream and refuses all later events. */
    public static void checkOff() throws Exception {
        Rig r = rig(); r.arm(); r.stop(); long size = r.store.bytes(); require(!r.core.record(new SafeRow(SafeRow.Kind.EVENT)));
        require(size == r.store.bytes()); require(log(r).contains("OWNER_STOP"));
    }

    /** Reconnections stop a current run and never rearm it. */
    public static void checkReconnect() throws Exception {
        Rig r = rig(); r.arm(); r.core.connect(); require(r.core.connected()); require(!r.core.active()); require(log(r).contains("RECONNECTED"));
    }

    /** Interruptions/disconnection require a new explicit connection and arm. */
    public static void checkInterrupt() throws Exception {
        Rig r = rig(); r.arm(); r.core.disconnect(SafeRow.Reason.INTERRUPTED); require(!r.core.connected());
        require(!r.core.arm(ProbeProfile.NETFLIX)); require(log(r).contains("INTERRUPTED"));
    }

    /** A new controller cannot recover arm state from existing files. */
    public static void checkProcessRestart() throws Exception {
        Rig r = rig(); r.arm(); r.stop(); ProbeController fresh = new ProbeController(r.time, r.store);
        fresh.connect(); require(!fresh.active()); require(r.store.latest() != null);
    }

    /** Enforce the exact ten-minute elapsed deadline without any event being required. */
    public static void checkDeadline() throws Exception {
        Rig r = rig(); r.arm(); r.time.elapsed += ProbeLimits.DURATION_MS - 1; require(r.core.active());
        r.time.elapsed++; require(!r.core.checkDeadline()); require(log(r).contains("TIME_LIMIT"));
    }

    /** Fail closed on an impossible monotonic rollback. */
    public static void checkClockRollback() throws Exception {
        Rig r = rig(); r.arm(); r.time.elapsed--; require(!r.core.active()); require(log(r).contains("CLOCK_INVALID"));
    }

    /** Judge event staleness on uptime, independent from deep-sleep elapsed differences. */
    public static void checkEventClockBases() throws Exception {
        Rig r = rig(); r.arm(); r.time.elapsed += 100_000;
        require(r.core.eventQuality(r.time.uptime) == SafeRow.Quality.OK);
        require(r.core.eventQuality(r.time.uptime - 5001) == SafeRow.Quality.STALE_EVENT);
        require(r.core.eventQuality(r.time.uptime + 1) == SafeRow.Quality.FUTURE_EVENT);
        require(r.core.eventQuality(-1) == SafeRow.Quality.FUTURE_EVENT); r.stop();
    }

    /** Check exploration boundaries at 249/250 ms and across separate windows. */
    public static void checkExploreRate() throws Exception {
        Rig r = rig(); r.arm(); require(r.core.allowExplore(1)); require(!r.core.allowExplore(1)); require(r.core.allowExplore(2));
        r.time.elapsed += 249; require(!r.core.allowExplore(1)); r.time.elapsed++; require(r.core.allowExplore(1)); r.stop();
    }

    /** Bound the number of tracked windows even if an app changes IDs continuously. */
    public static void checkWindowMapBudget() throws Exception {
        Rig r = rig(); r.arm(); for (int i = 0; i < 8; i++) require(r.core.allowExplore(i));
        require(!r.core.allowExplore(9)); r.stop(); require(log(r).contains("\"coalescedCount\":1"));
    }

    /** Ensure a full byte budget leaves a complete terminal row inside two decimal megabytes. */
    public static void checkByteLimit() throws Exception {
        Rig r = rig(); r.arm(); int accepted = 0;
        while (r.core.record(new SafeRow(SafeRow.Kind.EVENT).number("eventType", 2048))) accepted++;
        require(accepted > 100); require(!r.core.active()); require(r.store.latest().length() <= 2_000_000);
        String text = log(r); require(text.contains("BYTE_LIMIT")); require(text.endsWith("}\n"));
        require(text.contains("\"droppedEventCount\":1"));
    }

    /** Preserve UTF-8 without BOM, contiguous sequence numbers and all clock bases. */
    public static void checkSchemaAndClocks() throws Exception {
        Rig r = rig(); r.arm(); r.core.record(new SafeRow(SafeRow.Kind.EVENT).number("eventTimeUptimeMs", 42)); r.stop();
        byte[] bytes = Files.readAllBytes(r.store.latest().toPath()); require(bytes[0] == '{'); String text = log(r);
        require(text.contains("\"sequence\":1,")); require(text.contains("\"sequence\":2,")); require(text.contains("\"sequence\":3,"));
        require(text.contains("\"receivedUptimeMs\":7000")); require(text.contains("\"receivedElapsedRealtimeMs\":99000"));
        require(text.contains("\"wallTimeMs\":1700000000000"));
    }

    /** An unavailable directory fails arm rather than creating an apparently valid capture. */
    public static void checkStorageFailure() throws Exception {
        Path file = Files.createTempFile("probe004-not-directory", ".tmp");
        boolean failed = false; try { new JournalStore(file.toFile()); } catch (IOException expected) { failed = true; }
        require(failed);
    }

    /** Recognize only closed whole-label grammar; generic menu/title words remain inconclusive. */
    public static void checkGrammar() {
        TextProjection p = new TextProjection();
        require(p.classify("Publicité", false) == SafeRow.Label.AD_LABEL_CANDIDATE);
        require(p.classify("Ad 1 of 2", false) == SafeRow.Label.AD_LABEL_CANDIDATE);
        require(p.classify("00:29", false) == SafeRow.Label.COUNTDOWN_CANDIDATE);
        require(p.classify("00:99", false) == SafeRow.Label.OTHER);
        require(p.classify("Mon film sans publicité", false) == SafeRow.Label.OTHER);
        require(p.classify("private@example.test", false) == SafeRow.Label.OTHER);
        require(p.classify(null, false) == SafeRow.Label.NONE);
    }

    /** Password projection never even asks the supplied object for its length or text. */
    public static void checkPasswordNoRead() {
        require(new TextProjection().classify(new PoisonText(), true) == SafeRow.Label.PASSWORD_SKIPPED);
    }

    /** Reject oversized values before converting them into strings or applying regexes. */
    public static void checkLongText() {
        require(new TextProjection().classify(new String(new char[161]), false) == SafeRow.Label.TOO_LONG);
    }

    /** Forget transient references and cap memory without stable hashes of unknown strings. */
    public static void checkEphemeralRefs() {
        TextProjection p = new TextProjection(); require(p.reference("id-a") == 1); require(p.reference("id-a") == 1);
        for (int i = 1; i < 256; i++) p.reference("id-" + i);
        require(p.reference("new-id") == 0); p.clear(); require(p.reference("new-id") == 1);
    }

    /** Never emit an arbitrary source class name as a known role. */
    public static void checkRoleAllowlist() {
        TextProjection p = new TextProjection(); require(p.role("android.widget.Button") == SafeRow.Role.BUTTON);
        require(p.role("private.title.class") == SafeRow.Role.UNKNOWN); require(p.role(null) == SafeRow.Role.UNKNOWN);
    }

    /** Only owner-reported public IDs receive readable categories and only in the correct profile. */
    public static void checkPublicIds() {
        TextProjection p = new TextProjection();
        require(p.viewKind("com.netflix.ninja:id/gibbonMain", ProbeProfile.NETFLIX) == SafeRow.ViewKind.GIBBON_MAIN);
        require(p.viewKind("com.netflix.ninja:id/gibbonMain", ProbeProfile.DISNEY_PLUS) == SafeRow.ViewKind.OTHER);
        require(p.viewKind("private@example.test", ProbeProfile.NETFLIX) == SafeRow.ViewKind.OTHER);
        require(p.viewKind(null, ProbeProfile.NETFLIX) == SafeRow.ViewKind.NONE);
    }

    /** A second platform campaign gets a fresh file/run and its own terminal profile. */
    public static void checkCampaignSeparation() throws Exception {
        Rig r = rig(); r.arm(); r.stop(); File netflix = r.store.latest();
        require(r.core.arm(ProbeProfile.DISNEY_PLUS)); r.core.record(new SafeRow(SafeRow.Kind.EVENT)); r.stop();
        File[] files = r.folder.toFile().listFiles(); require(files != null && files.length == 2);
        require(!Files.readString(netflix.toPath()).contains("DISNEY_PLUS"));
        for (File file : files) if (!file.equals(netflix)) require(!Files.readString(file.toPath()).contains("NETFLIX"));
    }

    /** A raw string value cannot enter a row; invalid/duplicate scalar field names are rejected. */
    public static void checkSafeSchema() {
        boolean rejected = false; try { new SafeRow(SafeRow.Kind.EVENT).number("private\"field", 1); }
        catch (IllegalArgumentException expected) { rejected = true; } require(rejected);
        rejected = false; try { new SafeRow(SafeRow.Kind.EVENT).number("sequence", 999); }
        catch (IllegalArgumentException expected) { rejected = true; } require(rejected);
        rejected = false; try { new SafeRow(SafeRow.Kind.EVENT).number("value", 1).number("value", 2); }
        catch (IllegalArgumentException expected) { rejected = true; } require(rejected);
    }

    /** Active export is refused, preserving the original open campaign. */
    public static void checkExportActiveRefused() throws Exception {
        Rig r = rig(); r.arm(); boolean rejected = false;
        try { r.store.exportLocal(Files.createTempDirectory("probe004-export").toFile()); }
        catch (IOException expected) { rejected = true; } require(rejected); require(r.core.active()); r.stop();
    }

    /** Stopped exports are byte-identical, non-destructive and repeatable. */
    public static void checkExportIntegrity() throws Exception {
        Rig r = rig(); r.arm(); r.stop(); File dest = Files.createTempDirectory("probe004-export").toFile();
        File out = r.store.exportLocal(dest); require(JournalStore.sha256(out).equals(JournalStore.sha256(r.store.latest())));
        require(r.store.exportLocal(dest).equals(out)); require(r.store.latest().exists());
    }

    /** Refuse an existing modified export instead of silently overwriting user data. */
    public static void checkExportConflict() throws Exception {
        Rig r = rig(); r.arm(); r.stop(); File dest = Files.createTempDirectory("probe004-export").toFile();
        File out = r.store.exportLocal(dest); Files.writeString(out.toPath(), "SYNTHETIC CONFLICT");
        boolean rejected = false; try { r.store.exportLocal(dest); } catch (IOException expected) { rejected = true; }
        require(rejected); require(Files.readString(out.toPath()).equals("SYNTHETIC CONFLICT"));
    }

    /** Delete only probe-owned files, never arbitrary files in a supplied app-owned directory. */
    public static void checkDeleteScope() throws Exception {
        Rig r = rig(); File other = r.folder.resolve("keep.txt").toFile(); Files.writeString(other.toPath(), "keep");
        r.arm(); require(!r.store.deleteStopped()); r.stop(); require(r.store.deleteStopped()); require(other.exists());
    }

    /** Apply retention only when called, only to stopped recognized files older than 24 hours. */
    public static void checkRetention() throws Exception {
        Rig r = rig(); r.arm(); r.stop(); File source = r.store.latest();
        require(source.setLastModified(r.time.wall - ProbeLimits.RETENTION_MS - 1));
        r.store.prune(r.time.wall); require(!source.exists());
    }

    /** Refuse path traversal and existing campaign names. */
    public static void checkGeneratedNames() throws Exception {
        Rig r = rig(); boolean rejected = false;
        try { r.store.open("../private"); } catch (IOException expected) { rejected = true; } require(rejected);
        String id = "11111111-1111-1111-1111-111111111111"; r.store.open(id); r.store.close();
        rejected = false; try { r.store.open(id); } catch (IOException expected) { rejected = true; } require(rejected);
    }

    /** An open orphan after process loss has no false terminal line. */
    public static void checkIncompleteCapture() throws Exception {
        Rig r = rig(); r.arm(); r.store.close(); require(!log(r).contains("SESSION_END"));
        require(new JournalStore(r.folder.toFile()).latest() != null);
    }

    /** Ordinary and dense cadences are campaign-only; dense mode expires after exactly 30 seconds. */
    public static void checkCadences() {
        ObservationCadence normal = new ObservationCadence(ObservationCadence.Mode.NORMAL, 100);
        require(normal.interval(100) == 2000);
        ObservationCadence dense = new ObservationCadence(ObservationCadence.Mode.DENSE_30_SECONDS, 100);
        require(dense.interval(30_099) == 1000); require(dense.interval(30_100) == 2000);
        require(new ObservationCadence(ObservationCadence.Mode.EVENT_ONLY_CONTROL, 0).interval(10) == 0);
    }

    /** Two consecutive long reads stop; a normal read resets that streak. */
    public static void checkOverloadProtection() {
        ObservationCadence mode = new ObservationCadence(ObservationCadence.Mode.NORMAL, 0);
        require(!mode.overloaded(501)); require(!mode.overloaded(500));
        require(!mode.overloaded(700)); require(mode.overloaded(501));
    }

    /** A stale worker can never append a row to a newly armed campaign. */
    public static void checkLateGenerationWrites() throws Exception {
        Rig r = rig(); r.arm(); long old = r.core.generation(); r.stop(); require(r.core.arm(ProbeProfile.DISNEY_PLUS));
        long bytes = r.store.bytes(); require(!r.core.recordFor(old, new SafeRow(SafeRow.Kind.NODE)));
        require(bytes == r.store.bytes()); require(r.core.recordFor(r.core.generation(), new SafeRow(SafeRow.Kind.CONTROL_MARK))); r.stop();
    }

    /** Empty root observations always leave a typed result, not a false negative ad decision. */
    public static void checkFreshRootUnavailable() throws Exception {
        Rig r = rig(); r.arm(); SurveyEngine e = engine(r, new SurveyEngine.History());
        e.root(null, 1, SafeRow.Origin.ACTIVE_ROOT); require(e.finish() == SafeRow.Quality.ROOT_UNAVAILABLE);
        r.stop(); require(log(r).contains("ROOT_UNAVAILABLE")); require(log(r).contains("\"nodeReadCount\":0"));
    }

    /** Every child is independently refreshed before any synthetic properties are interpreted. */
    public static void checkFreshChildren() throws Exception {
        Rig r = rig(); r.arm(); FakeNode root = new FakeNode(); FakeNode child = new FakeNode(); child.ad = true;
        root.children = new FakeNode[] { child }; SurveyEngine e = engine(r, new SurveyEngine.History());
        e.root(root, 1, SafeRow.Origin.ACTIVE_ROOT); require(e.finish() == SafeRow.Quality.FRESH_OK);
        require(root.refreshCount == 1 && child.refreshCount == 1); require(root.closed && child.closed);
        r.stop(); require(log(r).contains("AD_LABEL_CANDIDATE")); require(!log(r).contains("AD_START"));
    }

    /** An unrefreshable node's payload accessor must never run. */
    public static void checkRefreshFailureNoPayload() throws Exception {
        Rig r = rig(); r.arm(); FakeNode root = new FakeNode(); root.refreshable = false;
        SurveyEngine e = engine(r, new SurveyEngine.History()); e.root(root, 1, SafeRow.Origin.ACTIVE_ROOT);
        require(e.finish() == SafeRow.Quality.ROOT_UNAVAILABLE); require(root.factCount == 0 && root.closed);
        r.stop(); require(log(r).contains("REFRESH_FAILED"));
    }

    /** A known window is traversed once even when source, active root and windows overlap. */
    public static void checkWindowDeduplication() throws Exception {
        Rig r = rig(); r.arm(); FakeNode root = new FakeNode(), duplicate = new FakeNode();
        SurveyEngine e = engine(r, new SurveyEngine.History()); e.root(root, 1, SafeRow.Origin.ACTIVE_ROOT);
        e.root(duplicate, 1, SafeRow.Origin.WINDOW_ROOT); e.finish(); r.stop();
        require(duplicate.closed && duplicate.refreshCount == 0); require(log(r).contains("\"nodeReadCount\":1"));
        require(log(r).contains("\"countsMayDuplicate\":false"));
    }

    /** Window changes prevent false structural continuity; safe category changes remain facts only. */
    public static void checkShapeComparison() throws Exception {
        Rig r = rig(); r.arm(); SurveyEngine.History history = new SurveyEngine.History();
        SurveyEngine first = engine(r, history); first.root(new FakeNode(), 1, SafeRow.Origin.ACTIVE_ROOT); first.finish();
        FakeNode changed = new FakeNode(); changed.ad = true; SurveyEngine second = engine(r, history);
        second.root(changed, 1, SafeRow.Origin.ACTIVE_ROOT); second.finish();
        SurveyEngine third = engine(r, history); third.root(new FakeNode(), 2, SafeRow.Origin.ACTIVE_ROOT); third.finish();
        r.stop(); String text = log(r); require(text.contains("\"shapeChanged\":true"));
        require(text.contains("\"comparisonAvailable\":false")); require(!text.contains("AD_END"));
    }

    /** Soft time budget is explicit; no child lookup begins after it is exhausted. */
    public static void checkSurveyDelay() throws Exception {
        Rig r = rig(); r.arm(); FakeNode root = new FakeNode(); root.slowClock = r.time;
        root.children = new FakeNode[] { new FakeNode() }; SurveyEngine e = engine(r, new SurveyEngine.History());
        e.root(root, 1, SafeRow.Origin.ACTIVE_ROOT); require(e.finish() == SafeRow.Quality.TIMEOUT_OR_DELAY);
        require(root.children[0].refreshCount == 0); r.stop(); require(log(r).contains("TIMEOUT_OR_DELAY"));
    }

    /** Null children are incomplete and cannot update a fresh structural baseline. */
    public static void checkNullChildren() throws Exception {
        Rig r = rig(); r.arm(); FakeNode root = new FakeNode(); root.children = new FakeNode[] { null };
        SurveyEngine e = engine(r, new SurveyEngine.History()); e.root(root, 1, SafeRow.Origin.ACTIVE_ROOT);
        require(e.finish() == SafeRow.Quality.INCOMPLETE); r.stop(); require(log(r).contains("STALE_NODE"));
    }

    /** A wide tree cannot exceed the shared 200-read budget. */
    public static void checkFreshNodeBudget() throws Exception {
        Rig r = rig(); r.arm(); FakeNode root = new FakeNode(); root.children = new FakeNode[230];
        for (int i = 0; i < 230; i++) root.children[i] = new FakeNode();
        SurveyEngine e = engine(r, new SurveyEngine.History()); e.root(root, 1, SafeRow.Origin.ACTIVE_ROOT);
        require(e.finish() == SafeRow.Quality.TRUNCATED); r.stop(); require(log(r).contains("\"nodeReadCount\":200"));
    }

    /** Depth is bounded independently of node count and no deep child is refreshed. */
    public static void checkFreshDepthBudget() throws Exception {
        Rig r = rig(); r.arm(); FakeNode root = new FakeNode(), cursor = root;
        for (int i = 0; i < 20; i++) { FakeNode next = new FakeNode(); cursor.children = new FakeNode[] { next }; cursor = next; }
        SurveyEngine e = engine(r, new SurveyEngine.History()); e.root(root, 1, SafeRow.Origin.ACTIVE_ROOT);
        require(e.finish() == SafeRow.Quality.TRUNCATED); require(cursor.refreshCount == 0); r.stop();
    }

    /** Range numbers are finite and ordered, password-suppressed and never assigned time units. */
    public static void checkRangeProjection() {
        require(facts(false, true, 0, 10, 100).range == SafeRow.RangeState.VALID);
        require(facts(false, true, 0, 101, 100).range == SafeRow.RangeState.INVALID);
        require(facts(false, true, 0, Double.NaN, 100).range == SafeRow.RangeState.INVALID);
        require(facts(true, true, 0, 10, 100).range == SafeRow.RangeState.PASSWORD_SKIPPED);
        require(facts(false, false, 0, 0, 0).range == SafeRow.RangeState.ABSENT);
        boolean rejected = false; try { new SafeRow(SafeRow.Kind.NODE).decimal("rangeCurrent", Double.POSITIVE_INFINITY); }
        catch (IllegalArgumentException expected) { rejected = true; } require(rejected);
    }

    /** Build only synthetic closed facts for portable range validation. */
    private static NodeFacts facts(boolean password, boolean range, double min, double value, double max) {
        return new NodeFacts(0, SafeRow.Role.TEXT, SafeRow.ViewKind.NONE, true, false, false, true, false,
            password, true, true, SafeRow.Label.OTHER, SafeRow.Label.OTHER, range, 1, min, value, max);
    }

    /** Create the actual shared traversal with deterministic synthetic clocks. */
    private static SurveyEngine engine(Rig r, SurveyEngine.History history) {
        return new SurveyEngine(r.core, r.time, new TextProjection(), r.core.generation(), 1, SafeRow.Trigger.PERIODIC_TICK, history);
    }

    /** Synthetic public-node adapter: property access requires a successful refresh. */
    private static final class FakeNode implements SurveyNode {
        FakeNode[] children = new FakeNode[0]; boolean refreshable = true, ad, closed;
        int refreshCount, factCount; FakeClock slowClock;
        /** Create a synthetic node without contacting Android. */
        FakeNode() { }
        /** Return the one admitted synthetic platform package. */
        @Override public CharSequence packageName() { return "com.netflix.ninja"; }
        /** Count refreshes and optionally emulate one costly non-preemptible call. */
        @Override public boolean refresh() { refreshCount++; if (slowClock != null) slowClock.elapsed += 600; return refreshable; }
        /** Refuse stale payload reads, exposing only closed synthetic scalars. */
        @Override public NodeFacts facts(ProbeProfile profile, TextProjection projection) {
            require(refreshable && refreshCount > 0); factCount++;
            return new NodeFacts(children.length, SafeRow.Role.TEXT, SafeRow.ViewKind.NONE, true, false, false, true, false,
                false, true, false, ad ? SafeRow.Label.AD_LABEL_CANDIDATE : SafeRow.Label.OTHER, SafeRow.Label.NONE, false, -1, 0, 0, 0);
        }
        /** Return one synthetic child or an explicit null. */
        @Override public SurveyNode child(int index) { return children[index]; }
        /** Mark the synthetic handle released. */
        @Override public void close() { closed = true; }
    }

    /** Synthetic fixture dependencies are explicit and never persisted as owner preferences. */
    private static final class Rig {
        final Path folder;
        final FakeClock time = new FakeClock();
        final JournalStore store;
        final ProbeController core;
        /** Create an isolated temporary store and disconnected controller. */
        Rig() throws Exception {
            folder = Files.createTempDirectory("probe004-synthetic-"); store = new JournalStore(folder.toFile());
            core = new ProbeController(time, store);
        }
        /** Simulate the explicit connection plus owner arm, not a platform advertisement. */
        void arm() { core.connect(); require(core.arm(ProbeProfile.NETFLIX)); }
        /** Finish this synthetic campaign with its owner-stop category. */
        void stop() { core.stop(SafeRow.Reason.OWNER_STOP); }
    }

    /** Deterministic clocks with deliberately different uptime and elapsed bases. */
    private static final class FakeClock implements ProbeClock {
        long elapsed = 99_000;
        long uptime = 7_000;
        long wall = 1_700_000_000_000L;
        /** Return synthetic elapsed time. */
        @Override public long elapsed() { return elapsed; }
        /** Return synthetic uptime. */
        @Override public long uptime() { return uptime; }
        /** Return synthetic wall time. */
        @Override public long wall() { return wall; }
    }

    /** Any password text access would fail this test immediately. */
    private static final class PoisonText implements CharSequence {
        /** Fail on forbidden inspection. */
        @Override public int length() { throw new AssertionError("Password inspected"); }
        /** Fail on forbidden inspection. */
        @Override public char charAt(int index) { throw new AssertionError("Password inspected"); }
        /** Fail on forbidden inspection. */
        @Override public CharSequence subSequence(int start, int end) { throw new AssertionError("Password inspected"); }
        /** Fail on forbidden conversion. */
        @Override public String toString() { throw new AssertionError("Password inspected"); }
    }
}
