package com.scenevibe.tvcompanionpoc;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.scenevibe.tvcompanionpoc.presentation.TvAccountSource;
import com.scenevibe.tvcompanionpoc.presentation.TvOffer;
import com.scenevibe.tvcompanionpoc.presentation.TvPreparation;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Activity-owned cache-only journey. One serial worker performs explicit 008 requests.
 * Epoch checks precede every callback-induced persistence and presentation mutation.
 * No service, legacy credential migration, runtime ports, scheduler or renderer is built.
 */
public final class TvFirst008Source implements TvAccountSource {
    private final Context context;
    private final TvFirst008State state;
    private final TvFirst008HttpPort http;
    private final String address;
    private final String multimediaOrigin;
    private final ExecutorService worker;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Object lock = new Object();
    private final Map<String, TvOffer> entries = new LinkedHashMap<>();
    private volatile AccountStage stage;
    private volatile String message, verification = "", next, selected;
    private volatile boolean busy, active;
    private long epoch, currentRevision;
    private long pollDeadline;
    private volatile long consentDeadline;
    private int polls;
    private Runnable listener;
    private TvPreparation preparation = empty();

    /** Production composition is default closed until a private Cloud origin is selected at build. */
    public TvFirst008Source(Context context) {
        this(context, new TvFirst008State(context), BuildConfig.CLOUD_ORIGIN.isEmpty() ? null : new TvFirst008Http(BuildConfig.CLOUD_ORIGIN),
                BuildConfig.CLOUD_ORIGIN, Executors.newSingleThreadExecutor());
    }
    /** Bind real state/cache and an injectable HTTP boundary for local software qualification. */
    TvFirst008Source(Context context, TvFirst008State state, TvFirst008HttpPort http, String origin, ExecutorService worker) {
        this.context = context.getApplicationContext(); this.state = state; this.http = http; this.worker = worker;
        address = http == null ? "" : origin + "/tv"; multimediaOrigin = origin;
        stage = http == null ? AccountStage.UNCONFIGURED : AccountStage.UNASSOCIATED;
        message = http == null ? "Le service de qualification n’est pas configuré pour cette APK. Association indisponible."
                : "Crée ton compte ou connecte-toi depuis un téléphone ou un ordinateur, puis associe cette TV.";
    }
    /** Return an immutable metadata-only snapshot; no fixture fallback. */
    @Override public List<TvOffer> offers() { synchronized (lock) { return Collections.unmodifiableList(new ArrayList<>(entries.values())); } }
    /** The source always represents actual 008 responses, never a demonstration. */
    @Override public boolean isLocalDemo() { return false; }
    /** Return closed source stage, independent of a credential merely being present. */
    @Override public AccountStage accountStage() {
        return stage == AccountStage.ASSOCIATED && android.os.SystemClock.elapsedRealtime() >= consentDeadline ? AccountStage.ERROR : stage;
    }
    /** Consumer-safe diagnosis deliberately omits credentials, ids, payloads and exception text. */
    @Override public String accountMessage() {
        return stage == AccountStage.ASSOCIATED && accountStage() == AccountStage.ERROR
                ? "Le consentement a expiré. Vérifie ton compte ; aucun nouvel ajout n’est autorisé." : message;
    }
    /** The only displayed challenge material is its public code and verification address. */
    @Override public String verification() { return verification; }
    /** Distinguish active work from a confirmed association. */
    @Override public boolean busy() { return busy; }
    /** Keep pagination explicit rather than an unbounded catalogue download. */
    @Override public boolean hasMore() { return next != null; }
    /** Retain a callback only for the Activity which owns this source. */
    @Override public void onChange(Runnable listener) { synchronized (lock) { this.listener = listener; } }
    /** Marshal notifications to the main thread and reject stale queued UI callbacks. */
    private void changed(long ticket) {
        main.post(() -> { synchronized (lock) { if (valid(ticket) && listener != null) listener.run(); } });
    }
    /** Check ownership under the same monitor as persistence; a late callback has no authority. */
    private boolean valid(long ticket) { return active && epoch == ticket; }
    /** Execute one operation without implicit fallback, reset or new request identifiers. */
    private void work(Operation operation) {
        work(operation,false);
    }
    /** Local verified-cache reads need Activity ownership, but never require a configured network transport. */
    private void work(Operation operation,boolean localOnly) {
        final long ticket;
        synchronized (lock) {
            if (!active || busy || http == null && !localOnly) return;
            ticket = epoch; busy = true;
        }
        changed(ticket);
        worker.execute(() -> {
            try { operation.run(ticket); }
            catch (Exception failed) { synchronized (lock) { if (valid(ticket)) failure(failed); } }
            finally { synchronized (lock) { if (valid(ticket)) busy = false; } changed(ticket); }
        });
    }
    /** Synchronous worker operation allows exceptions to stop before the next authority boundary. */
    private interface Operation { /** Execute only while this epoch owns side effects. */ void run(long ticket) throws Exception; }
    /** Expose only closed failure categories and retain retry identifiers on uncertain network results. */
    private void failure(Exception error) {
        stage = AccountStage.ERROR;
        if (error instanceof SecretStore.Unavailable) message = "Identifiants ou écriture locale indisponibles. Rien n’a été réinitialisé. Une récupération explicite est nécessaire.";
        else if (error instanceof TvFirst008Http.Failure) {
            int code = ((TvFirst008Http.Failure) error).status;
            message = code == 401 || code == 403 ? "Association ou consentement indisponible. Reconnecte ton compte et choisis explicitement une récupération."
                    : code == 409 ? "La sélection a changé. Actualise, puis confirme à nouveau ton choix."
                    : code == 410 ? "Ce code a expiré. Annule ce code avant d’en demander un nouveau."
                    : code == 422 || code == 404 ? "Cette Track n’est pas disponible dans le format accepté. Aucun cache n’est confirmé."
                    : "Service indisponible. Tu peux réessayer ; aucune réussite n’est supposée.";
        } else message = "Connexion ou preuve de cache indisponible. Réessaie sans réinitialiser la TV.";
        preparation = new TvPreparation(TvPreparation.Stage.FAILED, TvPreparation.Failure.PREPARATION_FAILED, false, null);
    }
    /** Qualify a response profile before accepting its fields. */
    private JSONObject profile(JSONObject response) {
        if (!TvFirst008Wire.PROFILE.equals(response.optString("profile"))) throw TvFirst008Wire.invalid();
        return response;
    }
    /** Construct only an additive 008 route; ordinary historical assignment/ACK remain untouched. */
    private static String route(String path) { return "/api/v1/" + path + "?profile=" + TvFirst008Wire.PROFILE; }
    /** Resolve a current durable identity without generating or migrating anything. */
    private JSONObject identity() {
        JSONObject identity = state.read("identity");
        if (identity == null) throw new SecretStore.Unavailable("IDENTITY_UNAVAILABLE");
        TvFirst008Wire.uuid(identity.optString("deviceId")); TvFirst008Wire.uuid(identity.optString("generation"));
        return identity;
    }

    /** Lease the existing encrypted generation only to the multimedia transport, never to presentation data. */
    MultimediaCloudClient.Session multimediaSession() {
        synchronized(lock) {
            if(!active || accountStage()!=AccountStage.ASSOCIATED)throw new IllegalStateException("ASSOCIATION_NOT_READY");
            final JSONObject captured=identity();
            final long capturedEpoch=epoch;
            final String device=captured.optString("deviceId"),generation=captured.optString("generation");
            return new MultimediaCloudClient.Session() {
                /** Preserve the selected stable device without accepting a caller account. */
                @Override public String deviceId(){return device;}
                /** Preserve the selected consent generation. */
                @Override public String generation(){return generation;}
                /** Resolve the existing keystore credential only while its generation remains current. */
                @Override public String token(){synchronized(lock){if(!current())throw new IllegalStateException("STALE_GENERATION");return state.token(identity());}}
                /** Cancel leases when association, consent or Activity task ownership ends. */
                @Override public boolean current(){synchronized(lock){try {JSONObject now=identity();return valid(capturedEpoch) && accountStage()==AccountStage.ASSOCIATED
                        && device.equals(now.optString("deviceId")) && generation.equals(now.optString("generation"));}catch(RuntimeException failed){return false;}}}
            };
        }
    }

    /** Read only the durable generation for inert offline cache restoration; this grants no consent or MEDIA. */
    String multimediaCachedGeneration() {synchronized(lock){return TvFirst008Wire.uuid(identity().optString("generation"));}}

    /** Persist a distinct multimedia retry identity before an explicit selection; historical Video pending state stays intact. */
    JSONObject pendingMultimedia(String publicationId,long osRevision) throws Exception {
        synchronized(lock) {
            MultimediaCloudClient.Session session=multimediaSession();TvFirst008Wire.uuid(publicationId);
            JSONObject previous=state.read("multimediaPending");
            if(previous!=null) {
                if(!publicationId.equals(previous.optString("publicationId")) || !session.generation().equals(previous.optString("generation")))throw new IllegalStateException("PENDING_MULTIMEDIA_INTENTION");
                return new JSONObject(previous.toString());
            }
            JSONObject command=new JSONObject().put("profile",MultimediaDeliveryProfile.PROFILE).put("generation",session.generation())
                    .put("intentionId",java.util.UUID.randomUUID().toString()).put("publicationId",publicationId).put("expectedRevision",osRevision).put("confirmed",true);
            state.write("multimediaPending",command);return new JSONObject(command.toString());
        }
    }

    /** Retire only the exact successful retry record, preserving account and historical cache/ACK metadata. */
    void multimediaCached(MultimediaCloudClient.Binding binding) throws Exception {
        synchronized(lock) {
            JSONObject pending=state.read("multimediaPending");
            JSONObject current=identity();
            if(!active || accountStage()!=AccountStage.ASSOCIATED || pending==null || !binding.intention.equals(pending.optString("intentionId"))
                    || !binding.generation.equals(current.optString("generation")) || !binding.device.equals(current.optString("deviceId"))
                    || !binding.publication.equals(pending.optString("publicationId")))throw new IllegalStateException("STALE_MULTIMEDIA_INTENTION");
            state.write("multimediaPending",null);
            if (BuildConfig.CLOUD_MULTIMEDIA_SERVICE) {
                state.write("multimediaBinding",binding.json());
                state.write("multimediaReceipt",binding.json());
            }
        }
    }

    /** Serialize the inert runtime handoff with the exact captured lease and lifecycle cancellation. */
    void multimediaCached(MultimediaCloudClient.Binding binding,MultimediaCloudClient.Session lease,Runnable offer) throws Exception {
        synchronized(lock) {
            if(lease==null || !lease.current())throw new IllegalStateException("STALE_MULTIMEDIA_LEASE");
            multimediaCached(binding);offer.run();
        }
    }

    /** Explicit caller action only: run the HTTPS multimedia bridge on this existing owner's serial worker. */
    void prepareMultimedia(String publicationId,StudioMultimediaRuntime runtime,Runnable onPrepared) {
        prepareMultimedia(publicationId,runtime,onPrepared,new MultimediaHttpsPort(multimediaOrigin));
    }

    /** Inject only the network seam for native qualification; cache, decoder and lifecycle remain genuine. */
    void prepareMultimedia(String publicationId,StudioMultimediaRuntime runtime,Runnable onPrepared,MultimediaCloudClient.Port port) {
        TvFirst008Wire.uuid(publicationId);
        if(runtime==null || onPrepared==null || port==null)throw new IllegalArgumentException("MULTIMEDIA_OWNER_PORTS_REQUIRED");
        work(ticket -> {
            new MultimediaAndroidBridge(context,this,port).prepare(publicationId,runtime);
            main.post(() -> {synchronized(lock){if(valid(ticket) && accountStage()==AccountStage.ASSOCIATED)onPrepared.run();}});
        });
    }
    /** Discover only multimedia publications authorized for this encrypted account/device association. */
    void listMultimedia(java.util.function.Consumer<JSONObject> result) {
        multimediaWork(()->new MultimediaCloudClient(multimediaSession(),new MultimediaHttpsPort(multimediaOrigin)).publications(),result);
    }

    /** Run an explicit confirmed selection on the existing serial worker, preserving durable retry ids. */
    void downloadMultimedia(String publicationId,java.util.function.Consumer<MultimediaPrivateCache.Prepared> result) {
        downloadMultimedia(publicationId,result,new MultimediaHttpsPort(multimediaOrigin));
    }

    /** Exercise the exact TV selection callback with a test-local trusted TLS boundary on Android. */
    void downloadMultimedia(String publicationId,java.util.function.Consumer<MultimediaPrivateCache.Prepared> result,MultimediaCloudClient.Port port) {
        TvFirst008Wire.uuid(publicationId);
        multimediaWork(()->new MultimediaAndroidBridge(context,this,port).fetch(publicationId),result);
    }

    /** Restore verified inert files on the worker; process restart never restores playback permission. */
    void restoreMultimedia(java.util.function.Consumer<MultimediaPrivateCache.Prepared> result) {
        multimediaWork(()->MultimediaAndroidBridge.restoreCache(context,this),result,true);
    }

    /** Marshal immutable results to the current Activity epoch after work completes; no stale callbacks. */
    private <T> void multimediaWork(MultimediaOperation<T> operation,java.util.function.Consumer<T> result) {
        multimediaWork(operation,result,false);
    }

    /** Preserve the exact same epoch checks for explicit offline restoration and authenticated downloads. */
    private <T> void multimediaWork(MultimediaOperation<T> operation,java.util.function.Consumer<T> result,boolean localOnly) {
        if(result==null)throw new IllegalArgumentException("MULTIMEDIA_CALLBACK_REQUIRED");
        work(ticket->{
            T value=operation.run();
            main.post(()->{synchronized(lock){if(valid(ticket))result.accept(value);}});
        },localOnly);
    }

    /** Carry only a worker operation; all existing lifecycle checks remain source-owned. */
    private interface MultimediaOperation<T> {
        /** Perform bounded disk or authenticated network work away from the Android UI thread. */
        T run() throws Exception;
    }

    /** Use the current generation token for exactly one authenticated device operation. */
    private JSONObject deviceRequest(JSONObject id, String method, String suffix, JSONObject body) throws Exception {
        return http.request(method, route("devices/" + id.getString("deviceId") + "/" + suffix), state.token(id), body);
    }
    /** Verify grant ownership and actual current revision before any library or readiness claim. */
    private void associate(long ticket) throws Exception {
        JSONObject id = identity(), response = profile(deviceRequest(id, "GET", "association", null));
        if (!id.getString("deviceId").equals(response.optString("deviceId")) || !id.getString("generation").equals(response.optString("generation"))
                || !response.optBoolean("accountAssociated", false) || !"FORBIDDEN".equals(response.optString("rendering"))) throw TvFirst008Wire.invalid();
        long remaining = Instant.parse(response.getString("consentExpiresAt")).toEpochMilli() - System.currentTimeMillis();
        if (remaining <= 0) throw new TvFirst008Http.Failure(403);
        remaining = Math.min(remaining, 7L * 24 * 60 * 60 * 1000);
        synchronized (lock) {
            if (!valid(ticket)) return;
            currentRevision = TvFirst008Wire.integer(response, "currentRevision", 0); stage = AccountStage.ASSOCIATED;
            consentDeadline = android.os.SystemClock.elapsedRealtime() + remaining;
            message = "Compte associé. Consultation et ajout au cache uniquement ; aucun commentaire ni MEDIA."; verification = "";
        }
        main.postDelayed(() -> {
            final long expiredTicket;
            synchronized (lock) {
                if (!valid(ticket) || android.os.SystemClock.elapsedRealtime() < consentDeadline) return;
                expiredTicket = ++epoch; busy = false; preparation = empty(); stage = AccountStage.ERROR;
                message = "Le consentement a expiré. Vérifie ton compte ; rien n’a été réinitialisé.";
            }
            changed(expiredTicket);
        }, remaining);
        page(ticket, null);
        restoreReceipt(ticket, id);
    }
    /** Load one bounded page, retain unknown editorial facts, and never fetch executable artifacts here. */
    private void page(long ticket, String after) throws Exception {
        JSONObject id = identity();
        String path = route("devices/" + id.getString("deviceId") + "/library") + (after == null ? "" : "&after=" + TvFirst008Wire.uuid(after));
        JSONObject response = http.request("GET", path, state.token(id), null);
        JSONArray items = response.getJSONArray("items");
        if (items.length() > 20) throw TvFirst008Wire.invalid();
        List<TvOffer> rows = new ArrayList<>();
        for (int i = 0; i < items.length(); i++) rows.add(offer(items.getJSONObject(i)));
        String cursor = response.isNull("next") ? null : TvFirst008Wire.uuid(response.getString("next"));
        synchronized (lock) {
            if (!valid(ticket)) return;
            if (after == null) entries.clear();
            for (TvOffer row : rows) entries.put(row.id, row);
            next = cursor;
        }
    }
    /** Map a server FinalTrack reference to a distinct presentation key, without inventing metadata. */
    private static TvOffer offer(JSONObject row) {
        String reference = TvFirst008Wire.uuid(row.optString("id"));
        if (!"REQUIRES_VALIDATION".equals(row.optString("eligibility"))) throw TvFirst008Wire.invalid();
        String film = copy(row, "filmTitle"), title = copy(row, "title");
        return new TvOffer("entry-" + reference, title, "Auteur : " + copy(row, "author"),
                "La disponibilité sera vérifiée lors de l’ajout. Langue et édition décrivent la référence fournie ; aucune reconnaissance de l’œuvre n’est établie.",
                film, copy(row, "edition"), copy(row, "language"), null, "FinalTrack V1 à vérifier", "Bibliothèque privée du compte associé",
                "Révision non affectée", copy(row, "author"), true, Collections.emptyList());
    }
    /** Bound public metadata and display absence explicitly instead of guessing titles or authors. */
    private static String copy(JSONObject row, String key) {
        Object value = row.opt(key);
        return value instanceof String && !((String) value).trim().isEmpty() ? ((String) value).substring(0, Math.min(240, ((String) value).length())) : "Non renseigné";
    }
    /** Persist one challenge before contacting the server; retries reuse its possession secret. */
    @Override public void beginPairing(boolean recovery) {
        synchronized (lock) { if (!active || busy || http == null) return; ++epoch; main.removeCallbacksAndMessages(null); preparation = empty(); selected = null; entries.clear(); next = null; stage = AccountStage.CONNECTING; message = "Préparation de l’association explicite…"; }
        work(ticket -> {
            JSONObject pending;
            synchronized (lock) {
                if (!valid(ticket)) return;
                pending = state.challenge(recovery ? "recover" : "new");
            }
            JSONObject command = new JSONObject().put("profile", TvFirst008Wire.PROFILE)
                    .put("challengeId", pending.getString("challengeId")).put("installationId", pending.getString("installationId"))
                    .put("mode", pending.getString("mode")).put("challengeSecret", state.challengeSecret(pending));
            JSONObject response = profile(http.request("POST", route("device-activations"), null, command));
            if (!pending.getString("challengeId").equals(response.optString("challengeId")) || !pending.getString("mode").equals(response.optString("mode"))
                    || !response.optString("userCode").matches("[0-9]{6}") || !"/tv".equals(response.optString("verificationPath"))) throw TvFirst008Wire.invalid();
            long expires = Instant.parse(response.getString("expiresAt")).toEpochMilli();
            if (expires <= System.currentTimeMillis() || expires > System.currentTimeMillis() + 125_000L) throw TvFirst008Wire.invalid();
            synchronized (lock) {
                if (!valid(ticket)) return;
                pending.put("userCode", response.getString("userCode")).put("expiresAt", response.getString("expiresAt"));
                state.write("challenge", pending); stage = AccountStage.CODE;
                verification = address + "\nCode : " + response.getString("userCode");
                message = recovery ? "Sur le site, choisis la TV existante de ton compte et confirme sa récupération. Rien n’est réattribué automatiquement."
                        : "Sur le site, crée ton compte ou connecte-toi, puis confirme l’association et les autorisations de cache pour 7 jours.";
                pollDeadline = expires + 120_000L; polls = 0;
            }
            schedulePoll(ticket);
        });
    }
    /** Bound polling to one pending handshake; there is no recurring library or service poll. */
    private void schedulePoll(long ticket) {
        main.postDelayed(() -> {
            synchronized (lock) {
                if (!valid(ticket) || stage != AccountStage.CODE || ++polls > 60 || System.currentTimeMillis() > pollDeadline) return;
            }
            refresh();
        }, 2_000L);
    }
    /** Require possession, persist the claimed credential, then independently verify its grant. */
    private void poll(long ticket, JSONObject pending) throws Exception {
        JSONObject response = profile(http.request("GET", route("device-activations/" + TvFirst008Wire.uuid(pending.optString("challengeId"))), state.challengeSecret(pending), null));
        String status = response.optString("status");
        if ("expired".equals(status)) throw new TvFirst008Http.Failure(410);
        if ("open".equals(status)) {
            synchronized (lock) {
                if (!valid(ticket)) return;
                stage = AccountStage.CODE;
                if (pending.optString("userCode").matches("[0-9]{6}")) verification = address + "\nCode : " + pending.getString("userCode");
                if (pollDeadline == 0 && pending.has("expiresAt")) pollDeadline = Instant.parse(pending.getString("expiresAt")).toEpochMilli() + 120_000L;
            }
            schedulePoll(ticket); return;
        }
        if (!"claimed".equals(status)) throw TvFirst008Wire.invalid();
        synchronized (lock) { if (!valid(ticket)) return; state.claimed(response); }
        associate(ticket);
    }
    /** Abandon only a pending code after an explicit action; a Cloud identity is never erased. */
    @Override public void cancelPairing() {
        synchronized (lock) {
            ++epoch; busy = false; main.removeCallbacksAndMessages(null);
            try { state.write("challenge", null); stage = AccountStage.UNASSOCIATED; verification = ""; message = "Code abandonné localement. Choisis explicitement une nouvelle association ou une récupération."; }
            catch (Exception failed) { failure(failed); }
            changed(epoch);
        }
    }
    /** Resume an existing challenge/identity only; no activation or Track selection is invented. */
    @Override public void refresh() {
        synchronized (lock) {
            if (!active || busy || http == null) return;
            preparation = empty(); stage = AccountStage.CONNECTING;
            message = "Vérification du compte et du cache en cours…";
        }
        work(ticket -> {
            JSONObject pending = state.read("challenge");
            if (pending != null) poll(ticket, pending);
            else if (state.read("identity") != null) associate(ticket);
            else synchronized (lock) { if (valid(ticket)) { stage = AccountStage.UNASSOCIATED; message = "Associe cette TV après connexion à ton compte."; } }
        });
    }
    /** Fetch another bounded metadata page only on a user action. */
    @Override public void loadMore() { final String after = next; if (after != null) work(ticket -> page(ticket, after)); }
    /** Confirm ownership and detail when opening a card, without preparing or selecting it. */
    @Override public void inspect(String offerId) {
        String reference = reference(offerId);
        work(ticket -> {
            TvOffer detail = offer(deviceRequest(identity(), "GET", "library/" + reference, null));
            if (!offerId.equals(detail.id)) throw TvFirst008Wire.invalid();
            synchronized (lock) { if (valid(ticket)) entries.put(detail.id, detail); }
        });
    }
    /** Resolve only a currently offered key; no guessed executable identifier is admitted. */
    private String reference(String offerId) {
        synchronized (lock) {
            if (!entries.containsKey(offerId) || !offerId.startsWith("entry-")) throw new IllegalArgumentException("UNKNOWN_OFFER");
            return TvFirst008Wire.uuid(offerId.substring(6));
        }
    }
    /** Submit only after the shell's human confirmation; persist and reuse the intention id. */
    @Override public void select(String offerId) {
        final String reference = reference(offerId);
        synchronized (lock) {
            if (busy || accountStage() != AccountStage.ASSOCIATED) throw new IllegalStateException("ASSOCIATION_NOT_READY");
            selected = offerId; preparation = new TvPreparation(TvPreparation.Stage.REQUESTED, null, false, null);
        }
        work(ticket -> {
            JSONObject id = identity(), command;
            synchronized (lock) { if (!valid(ticket)) return; command = state.pending(reference, currentRevision, id.getString("generation")); }
            TvFirst008Wire.Binding binding;
            try { binding = new TvFirst008Wire.Binding(deviceRequest(id, "POST", "track-intentions", command)); }
            catch (TvFirst008Http.Failure refused) {
                if (refused.status == 409 || refused.status == 404 || refused.status == 422) synchronized (lock) { if (valid(ticket)) state.write("pending", null); }
                throw refused;
            }
            if (!binding.device.equals(id.getString("deviceId")) || !binding.generation.equals(id.getString("generation"))
                    || !binding.intention.equals(command.getString("intentionId")) || !binding.track.equals(reference)) throw TvFirst008Wire.invalid();
            synchronized (lock) {
                if (!valid(ticket)) return;
                state.write("binding", binding.json()); currentRevision = binding.revision;
                preparation = new TvPreparation(TvPreparation.Stage.PREPARING, null, false, evidence(binding, false, false));
            }
            changed(ticket);
            JSONObject response = http.request("GET", route("devices/" + binding.device + "/assignment") + "&intentionId=" + binding.intention, state.token(id), null);
            TvFirst008Wire.Envelope envelope = new TvFirst008Wire.Envelope(response, binding);
            synchronized (lock) {
                if (!valid(ticket)) return;
                if (!new TvFirst008Cache(context, binding.generation).prepare(envelope)) throw new IllegalStateException("CACHE_NOT_DURABLE");
                preparation = new TvPreparation(TvPreparation.Stage.PREPARING, null, false, evidence(binding, true, false));
            }
            changed(ticket);
            synchronized (lock) { if (!valid(ticket)) return; }
            JSONObject receipt = deviceRequest(id, "POST", "ack", binding.json());
            confirmed(ticket, binding, receipt);
        });
    }
    /** Persist success only after exact server confirmation and another durable local readback. */
    private void confirmed(long ticket, TvFirst008Wire.Binding binding, JSONObject response) {
        TvFirst008Wire.Binding received = new TvFirst008Wire.Binding(response);
        if (!binding.equals(received) || !"CACHE_CONFIRMED".equals(response.optString("status"))) throw TvFirst008Wire.invalid();
        synchronized (lock) {
            if (!valid(ticket)) return;
            if (!new TvFirst008Cache(context, binding.generation).matches(binding)) throw new IllegalStateException("CACHE_READBACK_FAILED");
            state.write("receipt", received.json()); state.write("pending", null);
            selected = "entry-" + binding.track;
            preparation = new TvPreparation(TvPreparation.Stage.READY, null, false, evidence(binding, true, true));
        }
    }
    /** Recheck current Cloud evidence and local bytes on resume; old or corrupt receipts prove nothing. */
    private void restoreReceipt(long ticket, JSONObject id) throws Exception {
        JSONObject saved = state.read("binding");
        if (saved == null) return;
        TvFirst008Wire.Binding binding = new TvFirst008Wire.Binding(saved);
        if (!binding.device.equals(id.optString("deviceId")) || !binding.generation.equals(id.optString("generation")) || binding.revision != currentRevision) return;
        JSONObject response = http.request("GET", route("devices/" + binding.device + "/assignment-status") + "&intentionId=" + binding.intention, state.token(id), null);
        if (!binding.equals(new TvFirst008Wire.Binding(response))) throw TvFirst008Wire.invalid();
        if ("CACHE_CONFIRMED".equals(response.optString("status"))) confirmed(ticket, binding, response);
        else synchronized (lock) {
            if (valid(ticket)) { selected = "entry-" + binding.track; preparation = new TvPreparation(TvPreparation.Stage.AWAITING_CONFIRMATION, null, false, evidence(binding, false, false)); }
        }
    }
    /** Build independent facts with an explicit cache receipt, never a historical ACK or MEDIA admission. */
    private static TvPreparation.Evidence evidence(TvFirst008Wire.Binding binding, boolean installed, boolean receipt) {
        TvPreparation.Binding value = new TvPreparation.Binding("entry-" + binding.track, binding.intention, binding.device, binding.track,
                binding.digest, binding.revision, binding.generation, binding.codec);
        return TvPreparation.Evidence.cacheOnly(value, value, installed ? value : null, receipt ? value : null);
    }
    /** Return readiness only for this exact offer after actual cache/server evidence. */
    @Override public TvPreparation preparation(String offerId) { synchronized (lock) { return offerId.equals(selected) && (preparation.stage != TvPreparation.Stage.READY || accountStage() == AccountStage.ASSOCIATED) ? preparation : empty(); } }
    /**
     * Export already-confirmed cache coordinates only, without starting any runtime, reading
     * a secret, requesting HTTP or granting MEDIA. Local playback requires separate explicit
     * qualification policy. The captured monotonic deadline cannot be extended by this view.
     */
    ConfirmedCacheReference confirmedCacheReference() {
        synchronized (lock) {
            if (!active || busy || selected == null || accountStage() != AccountStage.ASSOCIATED
                    || !preparation.readyFor(selected)) return null;
            try {
                TvFirst008Wire.Binding binding = new TvFirst008Wire.Binding(state.read("binding"));
                if (!selected.equals("entry-" + binding.track) || binding.revision != currentRevision) return null;
                long now = android.os.SystemClock.elapsedRealtime();
                ConfirmedCacheReference reference = new ConfirmedCacheReference(
                        lock, state, binding, now, consentDeadline,
                        () -> accountStage() == AccountStage.ASSOCIATED && ("entry-" + binding.track).equals(selected)
                                && currentRevision == binding.revision);
                return reference.isCurrent(now) ? reference : null;
            } catch (RuntimeException invalid) { return null; }
        }
    }

    /**
     * Read-only, memory-only cache proof. Leaving the Activity does not erase its confirmed
     * bytes; a generation/binding/receipt change or expiry invalidates this reference. No
     * credential accessor, installation operation, ACK API or render authority is exposed.
     */
    static final class ConfirmedCacheReference {
        final TvFirst008Wire.Binding binding;
        private final Object monitor;
        private final TvFirst008State metadata;
        private final long issuedAt, expiresAt;
        private final java.util.function.BooleanSupplier cacheStillSelected;
        /** Retain a read-only selection/known-consent signal, independent of Activity pause. */
        private ConfirmedCacheReference(Object monitor, TvFirst008State metadata,
                TvFirst008Wire.Binding binding, long issuedAt, long expiresAt,
                java.util.function.BooleanSupplier cacheStillSelected) {
            this.monitor = monitor; this.metadata = metadata; this.binding = binding;
            this.issuedAt = issuedAt; this.expiresAt = expiresAt;
            this.cacheStillSelected = cacheStillSelected;
        }
        /** Check the same complete coordinates without renewing, migrating or writing them. */
        boolean isCurrent(long now) { return whileCurrent(now, () -> {}); }
        /** Serialize a local effect with 008's actual metadata mutation boundary. */
        boolean whileCurrent(long now, Runnable action) {
            synchronized (monitor) {
                try {
                    if (now < issuedAt || now >= expiresAt || !cacheStillSelected.getAsBoolean()) return false;
                    JSONObject id = metadata.read("identity");
                    if (id == null || !binding.device.equals(id.optString("deviceId"))
                            || !binding.generation.equals(id.optString("generation"))
                            || !binding.equals(new TvFirst008Wire.Binding(metadata.read("binding")))
                            || !binding.equals(new TvFirst008Wire.Binding(metadata.read("receipt")))) return false;
                } catch (RuntimeException invalid) { return false; }
                action.run(); return true;
            }
        }
    }
    /** Capture a current verified multimedia receipt; Activity pause never renews this consent. */
    MultimediaReference confirmedMultimediaReference(MultimediaPrivateCache.Prepared prepared) {
        synchronized(lock) {
            if(!BuildConfig.CLOUD_MULTIMEDIA_SERVICE || !active || busy || prepared==null
                    || accountStage()!=AccountStage.ASSOCIATED)return null;
            try {
                MultimediaReference ref=new MultimediaReference(lock,state,prepared,consentDeadline,
                        ()->accountStage()==AccountStage.ASSOCIATED);
                return ref.current(android.os.SystemClock.elapsedRealtime())?ref:null;
            }catch(RuntimeException refused){return null;}
        }
    }

    /** One immutable, read-only cache/consent reference; neither a bearer nor a persisted ARM token. */
    static final class MultimediaReference {
        final MultimediaPrivateCache.Prepared prepared;
        private final Object monitor;
        private final TvFirst008State metadata;
        private final long issuedAt=android.os.SystemClock.elapsedRealtime(),expiresAt;
        private final java.util.function.BooleanSupplier associated;
        /** Retain the exact receipt and the original monotonic consent deadline. */
        private MultimediaReference(Object monitor,TvFirst008State metadata,MultimediaPrivateCache.Prepared prepared,
                long expiresAt,java.util.function.BooleanSupplier associated) {
            this.monitor=monitor;this.metadata=metadata;this.prepared=prepared;this.expiresAt=expiresAt;this.associated=associated;
        }
        /** Recheck identity, receipt and current selection before every local effect. */
        boolean current(long now) {
            return whileCurrent(now,()->{});
        }
        /** Serialize a local effect with the source's exact metadata mutation boundary. */
        boolean whileCurrent(long now,Runnable action) {
            synchronized(monitor) {
                try {
                    MultimediaCloudClient.Binding binding=prepared.binding;
                    JSONObject identity=metadata.read("identity");
                    boolean valid=now>=issuedAt && now<expiresAt && associated.getAsBoolean() && identity!=null
                            && binding.device.equals(identity.optString("deviceId")) && binding.generation.equals(identity.optString("generation"))
                            && binding.same(new MultimediaCloudClient.Binding(metadata.read("multimediaBinding")))
                            && binding.same(new MultimediaCloudClient.Binding(metadata.read("multimediaReceipt")));
                    if(!valid)return false;
                }catch(Exception refused){return false;}
                action.run();return true;
            }
        }
    }

    /** Cancel an intention locally without clearing identity or any committed cache. */
    @Override public void cancel(String offerId) {
        synchronized (lock) {
            ++epoch; busy = false; preparation = empty(); selected = null;
            try { state.write("pending", null); } catch (Exception failed) { failure(failed); }
            changed(epoch);
        }
    }
    /** No synthetic scenarios are included in this variant. */
    @Override public List<String> scenarioNames() { return Collections.emptyList(); }
    /** Reject demo scenario controls in a real adapter. */
    @Override public void chooseScenario(int index) { throw new IllegalArgumentException("NO_DEMO_SCENARIO"); }
    /** This variant does not save a development scenario. */
    @Override public int scenarioIndex() { return 0; }
    /** Resume current identity verification only, without creating any new association. */
    @Override public void resume() { synchronized (lock) { active = true; ++epoch; busy = false; preparation = empty(); } refresh(); }
    /** Revoke callback ownership before any further cache/receipt mutation. */
    @Override public void pause() {
        synchronized (lock) { active = false; ++epoch; busy = false; preparation = empty(); main.removeCallbacksAndMessages(null); }
    }
    /** Release Activity-owned tasks; no foreground service is involved. */
    @Override public void close() { pause(); synchronized (lock) { listener = null; } worker.shutdownNow(); }
    /** Construct an evidence-free presentation state. */
    private static TvPreparation empty() { return new TvPreparation(TvPreparation.Stage.NONE, null, false, null); }
}
