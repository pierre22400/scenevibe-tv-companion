/**
 * Ephemeral, same-origin local control. A bearer stays only in this page's
 * memory; no Cloud request, storage, media transport, token URL or auto-retry.
 */
(() => {
  let token = "";
  let sequence = 0;
  let state = {};
  let busy = false;
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
  function render(value) {
    state = value;
    for (const name of ["text", "audio", "adsync"]) {
      const button = document.getElementById(name);
      button.setAttribute("aria-pressed", String(Boolean(value[name])));
      button.textContent = `${name.toUpperCase()} : ${value[name] ? "ON" : "OFF"}`;
    }
    for (const name of ["suspend", "resume", "calibrate"]) document.getElementById(name).disabled = !value.active;
    status.textContent = `${value.active ? "Service multimédia actif" : "Service multimédia inactif · préférences modifiables"}\n${value.sync}\nFocus TV : ${value.focus}${value.result ? `\n${value.result}` : ""}`;
  }
  /** Report failures without replaying a potentially applied command. */
  function failure(error) { status.textContent = error.message; }
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
    try { render(await request("/command", { seq: ++sequence, command: name, value })); }
    catch (error) { failure(error); }
    finally { busy = false; }
  }
  document.getElementById("pair").addEventListener("click", pair);
  for (const name of ["text", "audio", "adsync"]) document.getElementById(name).addEventListener("click", () => command(name, String(!state[name])));
  for (const name of ["suspend", "resume"]) document.getElementById(name).addEventListener("click", () => command(name));
  document.getElementById("calibrate").addEventListener("click", () => command("calibrate", document.getElementById("time").value));
  document.getElementById("show").addEventListener("click", () => command("panel", "show"));
  document.getElementById("close").addEventListener("click", () => command("panel", "close"));
  /** Read status only; polling cannot open a window, acquire focus or renew the ten-minute lifetime. */
  async function poll() {
    if (token && !busy) {
      try { render(await request("/status", {})); }
      catch (error) { failure(error); return; }
    }
    setTimeout(poll, 2000);
  }
  poll();
})();
