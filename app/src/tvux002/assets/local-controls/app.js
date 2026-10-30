/**
 * Ephemeral, same-origin local control. A bearer stays only in this page's
 * memory; no Cloud request, storage, media transport, token URL or auto-retry.
 */
(() => {
  let token = "";
  let sequence = 0;
  let state = {};
  let busy = false;
  let mutationVersion = 0;
  let lastCommandResult = "";
  const status = document.getElementById("status");
  /** Send bounded local JSON with a same-origin custom header and no ambient cookies. */
  async function request(path, body) {
    const response = await fetch(path, {
      method: "POST",
      credentials: "omit",
      cache: "no-store",
      headers: {
        "Content-Type": "application/json",
        "X-SceneVibe-Local": "1",
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      body: JSON.stringify(body)
    });
    if (!response.ok) throw new Error("Accès refusé, expiré ou commande déjà reçue. Réactive les commandes locales dans SceneVibe TV si nécessaire.");
    return response.json();
  }
  /** Reflect requested switches separately from current playback availability and window focus. */
  function render(value, commandReply = false) {
    state = value;
    if (commandReply) lastCommandResult = value.result || "";
    for (const name of ["text", "audio", "adsync"]) {
      const button = document.getElementById(name);
      button.setAttribute("aria-pressed", String(Boolean(value[name])));
      button.textContent = `${name.toUpperCase()} : ${value[name] ? "ON" : "OFF"}`;
    }
    const hold = document.getElementById("hold");
    hold.setAttribute("aria-pressed", String(Boolean(value.suspended)));
    hold.textContent = value.suspended ? "Commentaires : en pause volontaire · reprendre" :
      value.commentState === "MEDIA_WAIT" ? "Commentaires : en attente MEDIA · mettre en pause" :
      value.commentState === "MEDIA_PAUSED" ? "Film en pause · mettre SceneVibe en pause" :
      value.commentState === "CHANNELS_OFF" ? "Canaux OFF · mettre en pause" :
      value.active ? "Commentaires : actifs · mettre en pause" : "Commentaires : service arrêté";
    const observation = value.adObservation || {};
    const protection = value.adProtection || {};
    const guard = document.getElementById("adguard");
    guard.setAttribute("aria-pressed", String(Boolean(protection.enabled)));
    guard.textContent = `Protection publicitaire expérimentale : ${protection.enabled ? "ON" : "OFF"}`;
    document.getElementById("ad-protection").textContent = protection.enabled ?
      `Protection : ${protection.state} · ${protection.reason || ""}. ${protection.allow ? "Calendrier soumis aux contrôles MEDIA habituels." : "Commentaires retenus ; reprise à vérifier. OFF retire explicitement la protection."}` :
      "Protection OFF · référence v25, commentaires possibles pendant une publicité. Réactivée uniquement sur demande ; OFF au redémarrage du service.";
    const probe = document.getElementById("adprobe");
    probe.setAttribute("aria-pressed", String(Boolean(observation.enabled)));
    probe.textContent = `Observation publicitaire : ${observation.enabled ? "ON" : "OFF"}`;
    document.getElementById("ad-observation").textContent = observation.enabled ?
      `Candidat : ${observation.candidate} · ${observation.reason || ""} · débuts ${observation.adStarts || 0}, fins ${observation.adEnds || 0}. Observation seule, aucun blocage des commentaires.` :
      "Observation passive désactivée · réglage indépendant de la protection.";
    const multi = value.multiObservation || {};
    const multiButton = document.getElementById("multi-observe");
    multiButton.setAttribute("aria-pressed", String(Boolean(multi.enabled)));
    multiButton.textContent = `Observation multi-plateforme : ${multi.enabled ? "ON" : "OFF"}`;
    const platform = document.getElementById("multi-platform");
    if (multi.enabled) platform.value = multi.platform;
    platform.disabled = busy || Boolean(multi.enabled);
    document.getElementById("multi-observation").textContent = multi.enabled ?
      `Campagne ${multi.platform} · plateforme observée : ${multi.observedPlatform || "NONE"} · ${multi.selection || ""} · annotations ${multi.annotations || 0} · dernier changement ${multi.lastTransition || "indéterminé"} · sessions cibles ${multi.targetSessionCount || 0}/${multi.activeSessionCount || 0} · ${multi.fresh ? "observation récente" : "observation absente ou périmée"}. ${(multi.samples || []).map(sample => `Session ${sample.session} : ${sample.state}, marqueur ${sample.markerStatus || sample.marker} (${sample.markerType}), âge ancre ${sample.anchorAgeMs} ms, identité ${sample.identityCompatibility}`).join(" ; ")} Lecture seule ; preuve physique à établir.` :
      "Observation désactivée · aucune protection Netflix/Disney+ et aucune conclusion physique.";
    availability();
    status.textContent = `${value.runtime || (value.active ? "Service multimédia actif" : "Service multimédia inactif · préférences modifiables")}\n${value.sync}\nFocus TV : ${value.focus}\nCommandes locales : ${Math.ceil(value.remainingMs / 60000)} min restantes${lastCommandResult ? `\n${lastCommandResult}` : ""}`;
  }
  /** Report failures without replaying a potentially applied command. */
  function failure(error) { status.textContent = error.message; }
  /** Disable only unavailable/pending actions; registered ON/OFF remains independent of MEDIA readiness. */
  function availability() {
    for (const button of document.querySelectorAll("#controls button")) {
      button.disabled = busy || (["hold", "calibrate", "replay", "adprobe", "adguard"].includes(button.id) && !state.active) ||
        (button.id === "adguard" && state.adProtection?.available === false) ||
        (["ad-visible", "content-visible", "normal-seek", "normal-pause"].includes(button.id) && !(state.active && state.adObservation?.enabled)) ||
        (["multi-ad", "multi-content", "multi-seek", "multi-pause"].includes(button.id) && !state.multiObservation?.enabled);
    }
    document.getElementById("multi-platform").disabled = busy || Boolean(state.multiObservation?.enabled);
  }
  /** Admit one browser from the explicitly displayed TV code. */
  async function pair() {
    try {
      const value = await request("/pair", { code: document.getElementById("code").value });
      token = value.token;
      document.getElementById("code").value = "";
      document.getElementById("pairing").hidden = true;
      document.getElementById("controls").hidden = false;
      render(await request("/status", {}));
    } catch (error) { failure(error); }
  }
  /** Absolute switch values and unique sequence numbers avoid double toggles or replayed recalibration. */
  async function command(name, value = "") {
    if (busy) return;
    busy = true;
    mutationVersion += 1;
    availability();
    try { render(await request("/command", { seq: ++sequence, command: name, value }), true); }
    catch (error) { failure(error); }
    finally { busy = false; availability(); }
  }
  document.getElementById("pair").addEventListener("click", pair);
  for (const name of ["text", "audio", "adsync"]) document.getElementById(name).addEventListener("click", () => command(name, String(!state[name])));
  document.getElementById("hold").addEventListener("click", () => command(state.suspended ? "resume" : "suspend"));
  document.getElementById("replay").addEventListener("click", () => command("replay"));
  document.getElementById("calibrate").addEventListener("click", () => command("calibrate", document.getElementById("time").value));
  document.getElementById("show").addEventListener("click", () => command("panel", "show"));
  document.getElementById("close").addEventListener("click", () => command("panel", "close"));
  document.getElementById("adprobe").addEventListener("click", () => command("adprobe", String(!state.adObservation?.enabled)));
  document.getElementById("adguard").addEventListener("click", () => command("adguard", String(!state.adProtection?.enabled)));
  document.getElementById("multi-observe").addEventListener("click", () => command("multiobserve", state.multiObservation?.enabled ? "OFF" : document.getElementById("multi-platform").value));
  for (const [id, marker] of [["multi-ad", "AD_VISIBLE"], ["multi-content", "CONTENT_VISIBLE"], ["multi-seek", "NORMAL_SEEK"], ["multi-pause", "NORMAL_PAUSE"]]) {
    document.getElementById(id).addEventListener("click", () => command("multimark", marker));
  }
  for (const [id, marker] of [["ad-visible", "AD_VISIBLE"], ["content-visible", "CONTENT_VISIBLE"], ["normal-seek", "NORMAL_SEEK"], ["normal-pause", "NORMAL_PAUSE"]]) {
    document.getElementById(id).addEventListener("click", () => command("admark", marker));
  }
  /** Read status only; polling cannot open a window, acquire focus or renew the two-hour lifetime. */
  async function poll() {
    if (token && !busy) {
      const version = mutationVersion;
      try {
        const value = await request("/status", {});
        if (!busy && version === mutationVersion) render(value);
      }
      catch (error) { if (!busy && version === mutationVersion) failure(error); return; }
    }
    setTimeout(poll, 2000);
  }
  poll();
})();
