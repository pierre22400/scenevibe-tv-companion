package com.scenevibe.tvcompanionpoc;

import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Executes byte-exact BASE_TV_M5 sources in an isolated test class loader.
 * No scheduler algorithm is reproduced here: this wrapper constructs the actual
 * frozen models/snapshots, invokes entry points, and records listener callbacks.
 * The Android probe is compiled but never constructed or started. Its snapshot
 * value is used with the official AGP mockable framework in JVM tests only.
 */
final class M5FrozenLegacyOracle {
    private static final String PACKAGE = "com.scenevibe.tvcompanionpoc.";
    private static final Map<String, String> BLOBS = sourceBlobs();
    private static URLClassLoader frozen;

    /** Pin complete Git blobs, including all source comments and dependencies. */
    private static Map<String, String> sourceBlobs() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("ScheduledTrack", "38219f1130a0243272d3b1df5ad2e63461d7cc1e");
        result.put("MediaSyncedTrackScheduler", "441d983409d94ae6d5e31198ec739dec86166754");
        result.put("MediaIdentityMatcher", "f022a48c78151a11508d7feb12ba91239191a43a");
        result.put("MediaSessionProbe", "9c205b09ff5ffd85977250175eaab5019984b1b1");
        result.put("NotificationAccess", "b79a16d128c533169305e8a814b56488c3846559");
        result.put("MediaSessionAccessService", "c8728643be07f405a47b62a84f525302dbb73cab");
        return Collections.unmodifiableMap(result);
    }

    /** Compute a Git blob identity without normalizing or adapting source bytes. */
    private static String blob(byte[] content) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(("blob " + content.length + "\0").getBytes(StandardCharsets.US_ASCII));
        StringBuilder result = new StringBuilder();
        for (byte value : digest.digest(content)) {
            result.append(String.format("%02x", value & 255));
        }
        return result.toString();
    }

    /** Load a frozen resource completely; a missing reference fails the test. */
    static byte[] resource(String name) throws IOException {
        try (InputStream input = M5FrozenLegacyOracle.class.getResourceAsStream("/m5-phase-b/" + name)) {
            if (input == null) {
                throw new IOException("Missing frozen test resource");
            }
            return input.readAllBytes();
        }
    }

    /** Compile only pinned historical sources with the actual mockable Android jar. */
    private static synchronized URLClassLoader loader() throws Exception {
        if (frozen != null) {
            return frozen;
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Oracle qualification requires a JDK compiler");
        }
        Path directory = Files.createTempDirectory("scenevibe-m5-frozen-");
        directory.toFile().deleteOnExit();
        Path classes = Files.createDirectory(directory.resolve("classes"));
        List<java.io.File> sources = new ArrayList<>();
        for (Map.Entry<String, String> entry : BLOBS.entrySet()) {
            byte[] bytes = resource("oracle/" + entry.getKey() + ".java");
            if (!entry.getValue().equals(blob(bytes))) {
                throw new IllegalStateException("Frozen oracle Git blob mismatch");
            }
            Path source = directory.resolve(entry.getKey() + ".java");
            Files.write(source, bytes);
            source.toFile().deleteOnExit();
            sources.add(source.toFile());
        }
        URL android = Log.class.getProtectionDomain().getCodeSource().getLocation();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            boolean success = compiler.getTask(null, manager, diagnostics,
                    Arrays.asList("-encoding", "UTF-8", "-classpath", Path.of(android.toURI()).toString(),
                            "-sourcepath", directory.toString(), "-d", classes.toString()),
                    null, manager.getJavaFileObjectsFromFiles(sources)).call();
            if (!success) {
                throw new IllegalStateException("Pinned oracle compilation failed");
            }
        }
        frozen = new FrozenLoader(classes.toUri().toURL());
        return frozen;
    }

    /** Child-first only for the finite historical inventory, including nested values. */
    private static final class FrozenLoader extends URLClassLoader {
        /** Delegate framework/JDK/test classes while isolating the pinned six sources. */
        FrozenLoader(URL classes) {
            super(new URL[] {classes}, M5FrozenLegacyOracle.class.getClassLoader());
        }

        /** Never fall back to migrated production bytes for an oracle-owned class. */
        @Override
        protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            String shortName = name.startsWith(PACKAGE) ? name.substring(PACKAGE.length()) : "";
            String outer = shortName.split("\\$", 2)[0];
            if (!BLOBS.containsKey(outer)) {
                return super.loadClass(name, resolve);
            }
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                type = findClass(name);
            }
            if (resolve) {
                resolveClass(type);
            }
            return type;
        }
    }

    /** Resolve a historical type exclusively through the verified isolated loader. */
    private static Class<?> type(String name) throws Exception {
        return loader().loadClass(PACKAGE + name);
    }

    /** Invoke a package-private frozen value constructor, without altering its data. */
    private static Object construct(String name, Class<?>[] arguments, Object... values) throws Exception {
        Constructor<?> constructor = type(name).getDeclaredConstructor(arguments);
        constructor.setAccessible(true);
        return constructor.newInstance(values);
    }

    /** Read the opaque event identifier from a real frozen callback value. */
    private static String eventId(Object event) throws Exception {
        Field field = event.getClass().getDeclaredField("id");
        field.setAccessible(true);
        return (String) field.get(event);
    }

    /** Build the real legacy value; fixture payload never enters the new core. */
    private static Object track(JSONObject fixture, JSONObject action) throws Exception {
        JSONArray events = action.has("events") ? action.getJSONArray("events") : fixture.getJSONArray("events");
        List<Object> values = new ArrayList<>();
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.getJSONObject(i);
            values.add(construct("ScheduledTrack$Event",
                    new Class<?>[] {String.class, String.class, long.class, long.class, android.graphics.Bitmap.class},
                    event.getString("id"), "synthetic test payload", event.getLong("start"),
                    event.getLong("duration"), null));
        }
        JSONObject identity = fixture.optJSONObject("identity");
        if (identity == null) {
            identity = new JSONObject();
        }
        Object media = construct("ScheduledTrack$MediaIdentity",
                new Class<?>[] {String.class, String.class, String.class, long.class},
                identity.optString("platform", "prime_video"), identity.optString("id", "fixture-video"),
                identity.optString("title", "Columbo Murder by the Book"), identity.optLong("duration", 1_000_000L));
        return construct("ScheduledTrack",
                new Class<?>[] {String.class, String.class, type("ScheduledTrack$MediaIdentity"), List.class, boolean.class},
                "synthetic-track", identity.optString("package", "com.amazon.amazonvideo.livingroom"),
                media, values, action.optBoolean("freeze", fixture.optBoolean("freeze", true)));
    }

    /** Supply exact snapshot arguments, including independent raw/estimated positions. */
    private static Object snapshot(JSONObject action) throws Exception {
        long raw = action.optLong("position", 0L);
        return construct("MediaSessionProbe$Snapshot",
                new Class<?>[] {String.class, int.class, String.class, long.class, long.class, float.class,
                        long.class, String.class, String.class, String.class, long.class},
                action.optString("package", "com.amazon.amazonvideo.livingroom"), action.optInt("state", 3),
                "fixture-state", raw, action.optLong("estimated", raw), 1f, 0L,
                action.optString("mediaId", "fixture-video"),
                action.optString("title", "Columbo Murder by the Book"), action.optString("subtitle", ""),
                action.optLong("duration", 1_000_000L));
    }

    /** Translate only the listener vocabulary, preserving immediate callback order. */
    private static Object scheduler(M5TemporalJournal journal) throws Exception {
        Class<?> listener = type("MediaSyncedTrackScheduler$Listener");
        Object sink = Proxy.newProxyInstance(loader(), new Class<?>[] {listener}, (proxy, method, args) -> {
            switch (method.getName()) {
                case "onEligibility":
                    journal.append((boolean) args[0] ? M5TemporalJournal.Kind.ELIGIBLE
                            : M5TemporalJournal.Kind.INELIGIBLE, null, false, false);
                    return null;
                case "onPlayback":
                    journal.append(M5TemporalJournal.Kind.PLAYBACK, null, (boolean) args[0], (boolean) args[1]);
                    return null;
                case "onRender":
                    journal.append(M5TemporalJournal.Kind.DUE, eventId(args[0]), false, false);
                    return null;
                case "onExpire":
                    journal.append(M5TemporalJournal.Kind.EXPIRE, eventId(args[0]), false, false);
                    return null;
                default:
                    throw new IllegalStateException("Unexpected oracle callback");
            }
        });
        return type("MediaSyncedTrackScheduler").getConstructor(listener).newInstance(sink);
    }

    /** Execute fixture inputs against the real old engine; no candidate exists in B. */
    static M5TemporalJournal execute(JSONObject fixture) throws Exception {
        M5TemporalJournal journal = new M5TemporalJournal();
        Object engine = scheduler(journal);
        JSONArray actions = fixture.getJSONArray("actions");
        String token = null;
        for (int i = 0; i < actions.length(); i++) {
            JSONObject action = actions.getJSONObject(i);
            if (action.has("token")) {
                token = action.isNull("token") ? null : action.getString("token");
            }
            journal.begin(token);
            switch (action.getString("op")) {
                case "reconstruct":
                    engine = scheduler(journal);
                    // A fresh load deliberately has no persisted consumed cursor.
                case "load":
                    engine.getClass().getMethod("load", type("ScheduledTrack")).invoke(engine, track(fixture, action));
                    break;
                case "clear":
                    engine.getClass().getMethod("clear").invoke(engine);
                    break;
                case "unavailable":
                    engine.getClass().getMethod("onPlaybackUnavailable").invoke(engine);
                    break;
                case "snapshot":
                case "null":
                    engine.getClass().getMethod("onPlaybackSnapshot", type("MediaSessionProbe$Snapshot"))
                            .invoke(engine, action.getString("op").equals("null") ? null : snapshot(action));
                    break;
                default:
                    throw new IllegalArgumentException("Unknown corpus input");
            }
            journal.end();
        }
        return journal;
    }

    /** Prove the oracle's scheduler/model/matcher/probe cannot resolve to live classes. */
    static boolean isIsolated() throws Exception {
        for (String name : BLOBS.keySet()) {
            if (type(name).getClassLoader() != loader()
                    || type(name) == Class.forName(PACKAGE + name)) {
                return false;
            }
        }
        return true;
    }

    /** Prevent construction; every execution owns a fresh actual scheduler instance. */
    private M5FrozenLegacyOracle() {}
}
