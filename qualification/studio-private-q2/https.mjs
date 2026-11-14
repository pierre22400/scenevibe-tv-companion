import assert from "node:assert/strict"
import { createHash, randomUUID } from "node:crypto"
import { createRequire } from "node:module"
import { execFileSync, spawn } from "node:child_process"
import { mkdir, readFile, writeFile } from "node:fs/promises"
import { join } from "node:path"

/**
 * Recette HTTPS de la bibliothèque Q2-D. Les deux contextes Chrome authentifiés
 * utilisent le vrai Studio et le compte CI isolé. La preuve expurgée conserve
 * états, liaisons et empreintes de fichiers synthétiques.
 */
const origin = "https://scenevibe-product-consolidation001q2-studi-git-5fd51d-archc-ode.vercel.app"
const viewer = "https://scenevibe-product-consolidation001q2-viewe-git-6ef46d-archc-ode.vercel.app"
const root = origin + "/api/v1/studio-private"
const require = createRequire(import.meta.url)
const output = process.env.SCENEVIBE_STUDIO_EVIDENCE
const observations = []
const pkg = "com.scenevibe.tvcompanionpoc"
const instrumentation = pkg + ".test/androidx.test.runner.AndroidJUnitRunner"
const cloudCommit = process.env.SCENEVIBE_Q2_CLOUD_COMMIT
assert.ok(output && process.env.GITHUB_SHA && /^[a-f0-9]{40}$/.test(cloudCommit ?? ""), "PRIVATE_PROOF_SCOPE_REQUIRED")

/** Consigne seulement un fait démontré et son étape. */
function check(name, condition) {
  assert.ok(condition, name)
  observations.push(name)
  console.log("Q2D_PRIVATE " + name)
}

/** Attend que le Preview expose la session CI privée avant le lancement du vrai navigateur. */
async function waitPreview() {
  for (let attempt=0; attempt<45; attempt++) {
    const build = await fetch(origin+"/api/qualification/autonomous/studio-build", {
      cache:"no-store", signal:AbortSignal.timeout(8_000),
    }).then(reply=>reply.ok ? reply.json() : null).catch(()=>null)
    const result = await fetch(origin+"/api/qualification/autonomous/studio-private-ci-session", {
      method:"POST", headers:{origin}, signal:AbortSignal.timeout(8_000),
    }).catch(()=>null)
    if (build?.commit===cloudCommit && result?.status===401) return
    await new Promise(resolve=>setTimeout(resolve,10_000))
  }
  throw new Error("PRIVATE_CI_PREVIEW_UNAVAILABLE")
}

/** Échange une preuve OIDC neuve du workflow prévu contre une session du seul Preview Studio. */
async function authenticate(context) {
  const token = await fetch(process.env.ACTIONS_ID_TOKEN_REQUEST_URL + "&audience=" + encodeURIComponent(origin+"/studio-private-ci"), {
    headers: { authorization: "Bearer " + process.env.ACTIONS_ID_TOKEN_REQUEST_TOKEN },
  })
  assert.equal(token.status, 200)
  const reply = await context.request.post(origin + "/api/qualification/autonomous/studio-private-ci-session", {
    headers: { origin, authorization: "Bearer " + (await token.json()).value },
  })
  assert.equal(reply.status(), 200, "PRIVATE_CREATOR_SESSION")
}

/** Exécute le geste Studio normal avec un résultat HTTP observé. */
async function clickResponse(page, name, pathSuffix) {
  const button = page.getByRole("button", { name, exact: true })
  const response = page.waitForResponse(result => result.url().endsWith(pathSuffix) && result.request().method() === "POST", { timeout: 60_000 })
  const [received] = await Promise.all([response, button.click({ timeout: 60_000 })])
  const result = await received.json().catch(() => ({}))
  assert.equal(received.status(), 200, `${name} ${result.error ?? ""}`)
  return result
}

/** Ajoute un repère texte via le vrai formulaire de la Track. */
async function text(page, at, words) {
  await page.getByRole("button", { name: "Ajouter un texte", exact: true }).click()
  await page.getByLabel("Texte", { exact: true }).fill(words)
  await page.getByLabel("Début (s)", { exact: true }).fill(String(at))
  await page.getByLabel("Durée (1–30 s)", { exact: true }).fill("4")
}

/** Attend le reçu du brouillon local avant l'envoi explicite de la sauvegarde privée. */
async function save(page) {
  await page.getByRole("button", { name: "Enregistrer", exact: true }).click()
  await page.locator("header").getByText("Track enregistrée localement : document et fichiers AUDIO conservés", { exact: true }).waitFor({ timeout: 90_000 })
  return clickResponse(page, "Sauvegarder dans mon Cloud privé", "/revisions")
}

/** Parcourt les pages publiques réelles pour rechercher une Track synthétique. */
async function catalogueExcludes(title) {
  let after
  const seen = new Set()
  do {
    assert.ok(!seen.has(after))
    seen.add(after)
    const query = new URLSearchParams({ limit: "100", ...(after ? { after } : {}) })
    const response = await fetch(viewer + "/api/public/catalogue/tracks?" + query, { redirect: "error", cache: "no-store" })
    assert.equal(response.status, 200)
    const result = await response.json()
    if (result.items.some(row => row.track.title === title)) return false
    after = result.next
  } while (after)
  return true
}

/** Exécute une commande uniquement sur l'AVD jetable désigné par le workflow. */
function adb(...args) {
  return execFileSync("adb", args, { encoding: "utf8", timeout: 170_000 }).trim()
}

/** Demande un code natif, le consomme via Chrome puis exige l'association confirmée. */
async function pairAndroid(page) {
  assert.match(adb("get-serialno"), /^emulator-\d+$/)
  assert.equal(adb("shell", "getprop", "ro.kernel.qemu"), "1")
  const run = spawn("adb", ["shell", "am", "instrument", "-w", "-r", "-e", "class",
    pkg + ".StudioPrivateQ2HttpsTest#pair", instrumentation], { stdio: ["ignore", "pipe", "pipe"] })
  let log = ""
  let pairing
  let deviceId
  const result = await new Promise((resolve, reject) => {
    const timer = setTimeout(() => { run.kill(); reject(new Error("ANDROID_PAIR_TIMEOUT")) }, 150_000)
    run.stdout.on("data", bytes => {
      log += bytes.toString()
      const code = log.match(/Q2_PAIR_CODE=(\d{6})/)?.[1]
      if (code && !pairing) pairing = (async () => {
        await page.getByText("Associer une TV au Studio Q2", { exact: true }).first().click()
        await page.getByLabel("Code affiché sur la TV").fill(code)
        const result = await clickResponse(page,"Associer et autoriser le cache privé Q2","/devices/claim")
        assert.equal(result.status,"claimed")
        deviceId=result.deviceId
        await page.getByText("Code accepté. La TV doit confirmer son association.").waitFor()
      })().catch(error => { run.kill(); reject(error) })
    })
    run.on("error", error => { clearTimeout(timer); reject(error) })
    run.on("close", code => { clearTimeout(timer); resolve(code) })
  })
  await pairing
  assert.equal(result, 0, "ANDROID_PAIR_COMMAND")
  assert.match(log, /OK \(1 test\)/, log.replace(/Q2_PAIR_CODE=\d{6}/g, "Q2_PAIR_CODE=redacted"))
  check("android_https_pair_one_code", /^[a-f0-9-]{36}$/.test(deviceId ?? ""))
  return deviceId
}

/** Affecte exactement cette publication en utilisant le consentement Q2 fraîchement lu. */
async function assignAndroid(page, browserContext, deviceId, history, revision, release) {
  const devices = await (await browserContext.request.get(root + "/devices")).json()
  const selected = devices.items.find(item=>item.deviceId===deviceId)
  assert.ok(selected?.deviceId && selected.generation, "NO_AUTHORIZED_Q2_TV")
  await page.getByLabel("Appareil",{exact:true}).selectOption(deviceId,{timeout:25_000})
  const first = await clickResponse(page,"Envoyer cette révision à la TV","/revisions/"+revision+"/send")
  assert.equal(first.status,"REQUESTED")
  const data = { requestId: first.requestId, deviceId: selected.deviceId, generation: selected.generation, publicationId: release.publicationId }
  const repeat = await browserContext.request.post(history + "/" + revision + "/send", { headers: { origin }, data })
  assert.equal((await repeat.json()).requestId, data.requestId)
  const obsolete = { ...data, requestId: randomUUID(), generation: randomUUID() }
  assert.equal((await browserContext.request.post(history + "/" + revision + "/send", { headers: { origin }, data: obsolete })).status(), 403)
  return data
}

/** Attend un état réellement émis par l'émulateur au lieu de déduire le résultat du seul clic. */
async function waitAndroidReceipt(browserContext, history, revision, requestId, expected = "CACHE_CONFIRMED") {
  for (let attempt = 0; attempt < 30; attempt++) {
    const status = await (await browserContext.request.get(history + "/" + revision + "/status")).json()
    if (status.requests?.find(request => request.id === requestId)?.status === expected) return
    await new Promise(resolve => setTimeout(resolve, 3000))
  }
  const services = adb("shell", "dumpsys", "activity", "services", pkg + "/.PublicCompanionService")
  let proc = ""
  try { proc = adb("shell", "pidof", pkg) } catch { /* Absence prévue en cas d’arrêt du service. */ }
  const logs = adb("logcat", "-d", "-s", "SceneVibePublic:I", "SceneVibeText009:I", "SceneVibeMultimediaHttp:I")
  await writeFile(join(output, "android-receiver-r" + revision + ".txt"),
    "foreground=" + services.includes("isForeground=true") + " pid=" + proc + "\n" + logs)
  throw new Error("ANDROID_" + expected + "_TIMEOUT_R" + revision)
}

/** Exerce les sorties de la vraie session Android et consigne des métriques expurgées. */
async function renderAndroid(revision, proof = "android-q2d-r" + revision) {
  const log = adb("shell", "am", "instrument", "-w", "-r", "-e", "revision", String(revision),
    "-e", "class", pkg + ".StudioPrivateQ2HttpsTest#render", instrumentation)
  assert.match(log, /OK \(1 test\)/, log)
  assert.match(log, new RegExp("Q2D_ANDROID_TEXT_AUDIO_PASS revision=" + revision))
  await writeFile(join(output, proof + ".txt"), log)
  check("android_native_text_audio_r" + revision, true)
}

/** Démarre le test natif qui garde la révision 2 active pendant la demande de révision 3. */
function renderWithPendingReplacement() {
  const run = spawn("adb", ["shell", "am", "instrument", "-w", "-r", "-e", "revision", "2",
    "-e", "replacement", "3", "-e", "class", pkg + ".StudioPrivateQ2HttpsTest#render", instrumentation])
  let log = ""
  let ended = false
  run.stdout.on("data", bytes=>{ log += bytes.toString() })
  run.stderr.on("data", bytes=>{ log += bytes.toString() })
  const done = new Promise(resolve=>{
    run.on("error", error=>{ log+="\n"+error.message; ended=true; resolve(-1) })
    run.on("close", code=>{ ended=true; resolve(code) })
  })
  return {
    /** Attend les observations natives avant de continuer les mutations dans Chromium. */
    async requireMarker(marker, seconds = 100) {
      for (let attempt=0; attempt<seconds; attempt++) {
        if(log.includes(marker))return
        if(ended)break
        await new Promise(resolve=>setTimeout(resolve,1_000))
      }
      throw new Error("ANDROID_NATIVE_MARKER_MISSING " + marker + "\n" + log)
    },
    /** Libère le processus de test et garde le journal que l'AVD a effectivement produit. */
    async close(keepExitCode) {
      if(!keepExitCode && !ended)run.kill()
      const code=await done
      await writeFile(join(output,"android-q2d-r2-r3-native.txt"),log)
      return { code, log }
    },
  }
}

/** Vérifie le passage veille puis réveil sur Android TV, avec une preuve issue de PowerManager. */
async function wakeAndroid() {
  for (const [key,state] of [["SLEEP","Asleep"],["WAKEUP","Awake"]]) {
    adb("shell","input","keyevent","KEYCODE_"+key)
    let power
    for (let attempt=0; attempt<15; attempt++) {
      power=adb("shell","dumpsys","power").match(/\bmWakefulness=(Asleep|Awake|Dreaming|Dozing)\b/)?.[1]
      if (power===state) break
      await new Promise(resolve=>setTimeout(resolve,1_000))
    }
    check("android_power_"+state.toLowerCase(),power===state)
  }
}

/** Relance le noyau Android en conservant les données et attend la reprise du service sans Activity. */
async function rebootAndroid() {
  adb("shell", "input", "keyevent", "KEYCODE_HOME")
  adb("reboot")
  adb("wait-for-device")
  for (let attempt = 0; attempt < 45; attempt++) {
    if (adb("shell", "getprop", "sys.boot_completed") === "1"
      && adb("shell", "dumpsys", "activity", "services", pkg + "/.PublicCompanionService").includes("isForeground=true")) return
    await new Promise(resolve => setTimeout(resolve, 2000))
  }
  throw new Error("Q2_ANDROID_BOOT_RECEIVER_TIMEOUT")
}

await waitPreview()
await mkdir(output, { recursive: true })
const wav = join(output, "q2d-synthetic.wav")
/** Génère un WAV PCM mono de deux secondes dont le flux ne dépend pas d'un paquet du runner. */
function testWav() {
  const sampleRate = 48_000, count = sampleRate * 2
  const bytes = Buffer.alloc(44 + count * 2)
  bytes.write("RIFF", 0)
  bytes.writeUInt32LE(bytes.length - 8, 4)
  bytes.write("WAVEfmt ", 8)
  bytes.writeUInt32LE(16, 16)
  bytes.writeUInt16LE(1, 20)
  bytes.writeUInt16LE(1, 22)
  bytes.writeUInt32LE(sampleRate, 24)
  bytes.writeUInt32LE(sampleRate * 2, 28)
  bytes.writeUInt16LE(2, 32)
  bytes.writeUInt16LE(16, 34)
  bytes.write("data", 36)
  bytes.writeUInt32LE(count * 2, 40)
  for (let index = 0; index < count; index++)
    bytes.writeInt16LE(Math.round(2800 * Math.sin(2 * Math.PI * 431 * index / sampleRate)), 44 + index * 2)
  return bytes
}
await writeFile(wav, testWav())
const originalSha = createHash("sha256").update(await readFile(wav)).digest("hex")
const { chromium } = require(process.env.SCENEVIBE_PLAYWRIGHT_MODULE)
const browser = await chromium.launch({ headless: true, executablePath: process.env.SCENEVIBE_CHROMIUM_EXECUTABLE || undefined,
  args: ["--no-sandbox", "--disable-dev-shm-usage"] })
const context = await browser.newContext(), second = await browser.newContext()
try {
  const title = "Q2-D · Track synthétique privée " + process.env.GITHUB_RUN_ID + "-" + process.env.GITHUB_RUN_ATTEMPT
  await authenticate(context)
  const page = await context.newPage()
  await page.goto(origin + "/studio")
  await page.getByTestId("studio-storage-status").filter({ hasText: "Stockage local prêt" }).waitFor()
  await page.getByRole("button", { name: "Créer", exact: true }).click()
  await page.getByRole("button", { name: "Nouvelle expérience Video", exact: true }).click()
  await page.getByLabel("Titre de la Track", { exact: true }).fill(title)
  await page.getByLabel("Nature du programme", { exact: true }).selectOption("film")
  await page.getByLabel("Plateforme", { exact: true }).selectOption("prime_video")
  for (const [field, value] of [["Identifiant natif", "q2d-owned-media"], ["Titre du média cible", "Séquence de qualification Q2-D"],
    ["Édition précise", "q2d-fixture-2026"], ["Langue du média cible", "fr"]])
    await page.getByLabel(field, { exact: true }).fill(value)
  await text(page, 10, "Premier repère synthétique.")
  await text(page, 20, "Deuxième repère synthétique.")
  await page.getByRole("button", { name: "Ajouter un AUDIO", exact: true }).click()
  await page.getByLabel("Début AUDIO (s)", { exact: true }).fill("35")
  await page.locator("#audio-file").setInputFiles(wav)
  await page.getByTestId("audio-file-description").filter({ hasText: "q2d-synthetic.wav" }).waitFor()
  const first = await save(page)
  let deviceId, initialRelease
  {
    await clickResponse(page, "Préparer pour la TV", "/prepare")
    initialRelease = await clickResponse(page, "Rendre cette révision distribuable en privé", "/authorize")
    deviceId = await pairAndroid(page)
    const firstPath = `${root}/projects/${first.projectId}/revisions`
    // AndroidJUnitRunner stops the target process when instrumentation finishes.
    adb("shell", "am", "start", "-W", "-n", pkg + "/.MainActivity")
    const request = await assignAndroid(page, context, deviceId, firstPath, 1, initialRelease)
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    await waitAndroidReceipt(context, firstPath, 1, request.requestId)
    await renderAndroid(1)
    await wakeAndroid()
    await renderAndroid(1, "android-q2d-r1-after-wake-and-process-recreation")
    check("android_private_cache_read_after_wake_and_process_recreation",true)
    await rebootAndroid()
    check("android_boot_receiver_foreground", true)
  }
  check("first_private_save_2_text_1_audio", first.revision === 1 && first.textCount === 2 && first.audioCount === 1)
  await authenticate(second)
  const reopened = await second.newPage()
  await reopened.goto(origin + "/studio")
  await reopened.getByTestId("studio-storage-status").filter({ hasText: "Stockage local prêt" }).waitFor()
  await reopened.getByRole("button", { name: "Contenus", exact: true }).click()
  const library = reopened.getByRole("region", { name: "Mes Tracks privées", exact: true })
  await library.getByLabel("Titre", { exact: true }).fill(title)
  await library.getByRole("button", { name: "Rechercher", exact: true }).click()
  const entry = library.getByRole("listitem").filter({ hasText: title })
  await entry.getByRole("button", { name: "Ouvrir dans un nouveau brouillon local" }).click()
  check("restored_editable_second_chrome_context", await reopened.getByLabel("Titre de la Track", { exact: true }).inputValue() === title)
  await reopened.getByRole("button", { name: "AUDIO 1", exact: true }).click()
  check("restored_audio_editor", await reopened.getByTestId("audio-file-description").filter({ hasText: "q2d-synthetic.wav" }).isVisible())
  const history = `${root}/projects/${first.projectId}/revisions`
  const document = await (await second.request.get(history + "/1")).json()
  const parts = document.document.assets[0].parts
  const part = `${root}/projects/${first.projectId}/assets/1/${parts[0].sha256}`
  const audio = await second.request.get(part)
  check("restored_original_wav_integrity", audio.status() === 200 && document.document.assets[0].sha256 === originalSha
    && createHash("sha256").update(await audio.body()).digest("hex") === originalSha)
  check("anonymous_original_and_document_refused",
    (await fetch(part)).status === 401 && (await fetch(history + "/1")).status === 401)
  check("viewer_private_routes_disabled", (await fetch(viewer + new URL(part).pathname)).status === 404)
  const posted = { headers: { origin }, data: { expectedRevision: 0, document: document.document } }
  check("save_retry_returns_same_revision", (await (await second.request.post(history, posted)).json()).revision === 1)
  await text(reopened, 54, "Troisième repère synthétique.")
  const revised = await save(reopened)
  check("second_revision_same_track", revised.projectId === first.projectId && revised.revision === 2 && revised.textCount === 3 && revised.audioCount === 1)
  const stale = structuredClone(document.document)
  stale.draft.metadata.description = "Ancien brouillon concurrent"
  check("stale_update_refused", (await second.request.post(history, { headers: { origin }, data: { expectedRevision: 1, document: stale } })).status() === 409)
  const list = await (await second.request.get(history)).json()
  check("private_history_preserved", list.items.length === 2 && list.items.map(row => row.textCount).join() === "3,2")
  const prepared = await clickResponse(reopened, "Préparer pour la TV", "/prepare")
  await reopened.getByText("Révision privée préparée et vérifiée", { exact: true }).waitFor({ timeout: 30_000 })
  check("wav_mp3_preparation", prepared.status === "PREPARED_LOCAL" && prepared.resourceCount === 1
    && prepared.audioDerivatives[0].originalSha256 === originalSha && prepared.audioDerivatives[0].distributedSha256 !== originalSha)
  const release = await clickResponse(reopened, "Rendre cette révision distribuable en privé", "/authorize")
  check("private_distribution_authorized_separately", release.status === "PRIVATE_DISTRIBUTABLE" && release.deviceAssigned === false)
  const rejected = await second.request.post(history + "/2/send", { headers: { origin }, data: {
    requestId: randomUUID(), deviceId: randomUUID(), generation: randomUUID(), publicationId: release.publicationId,
  } })
  check("foreign_tv_refused", rejected.status() === 403)
  check("catalogue_never_contains_private_track", await catalogueExcludes(title))
  {
    const request = await assignAndroid(reopened, second, deviceId, history, 2, release)
    const incompatible = await second.request.post(history+"/2/send", { headers:{origin},
      data:{...request, requestId:randomUUID(),publicationId:initialRelease.publicationId} })
    check("unrelated_publication_revision_refused",incompatible.status()===422)
    await waitAndroidReceipt(second, history, 2, request.requestId)
    check("android_new_request_after_reboot_received", true)

    await text(reopened, 70, "Quatrième repère synthétique.")
    const third = await save(reopened)
    check("third_revision_same_track",third.revision===3 && third.projectId===first.projectId && third.textCount===4 && third.audioCount===1)
    await clickResponse(reopened,"Préparer pour la TV","/revisions/3/prepare")
    const queued = await clickResponse(reopened,"Rendre cette révision distribuable en privé","/revisions/3/authorize")
    const media = renderWithPendingReplacement()
    let native, completed = false
    try {
      await media.requireMarker("Q2D_ACTIVE_R2_READY",135)
      const next = await assignAndroid(reopened, second, deviceId, history, 3, queued)
      await media.requireMarker("Q2D_DEFERRED_R3_CACHE_R2_PRESERVED")
      await waitAndroidReceipt(second,history,3,next.requestId,"DEFERRED")
      check("android_active_r2_and_cache_preserved_while_r3_deferred",true)
      adb("shell","touch","/data/local/tmp/scenevibe-q2d-deferred-checked")
      await waitAndroidReceipt(second,history,3,next.requestId)
      await media.requireMarker("Q2D_REPLACEMENT_AND_TWO_CACHES_PASS")
      check("android_r3_replaced_after_voluntary_stop_and_public_cache_unchanged",true)
      const revoked = await second.request.post(root+"/devices/"+deviceId+"/revoke",{headers:{origin}})
      assert.equal(revoked.status(),200,await revoked.text())
      assert.equal((await revoked.json()).status,"REVOKED")
      const repeat = await second.request.post(history+"/3/send",{headers:{origin},data:{...next,requestId:randomUUID()}})
      check("owner_send_refused_after_revocation",repeat.status()===403)
      adb("shell","touch","/data/local/tmp/scenevibe-q2d-grant-revoked")
      await media.requireMarker("Q2D_REVOKED_DEVICE_REFUSED_CACHE_PRESERVED")
      await media.requireMarker("OK (1 test)",15)
      completed = true
    } finally { native=await media.close(completed) }
    assert.equal(native.code,0)
    assert.match(native.log,/OK \(1 test\)/)
    check("android_native_text_audio_r2", native.log.includes("Q2D_ANDROID_TEXT_AUDIO_PASS revision=2 text=3 audio=1"))
    check("android_corrupted_mp3_refused",native.log.includes("Q2D_CORRUPTED_MP3_REFUSED"))
    check("android_revoked_device_refused_and_cache_preserved",native.log.includes("Q2D_REVOKED_DEVICE_REFUSED_CACHE_PRESERVED"))
  }
  await reopened.screenshot({ path: join(output, "private-editor.png"), fullPage: true })
  await writeFile(join(output, "private-proof.json"), JSON.stringify({ sha: process.env.GITHUB_SHA, cloudCommit, observations,
    originalSha, derivedSha: prepared.audioDerivatives[0].distributedSha256, firstRevision: first.revision,
    currentRevision: revised.revision, tls: new URL(origin).protocol === "https:" }, null, 2))
} finally {
  await writeFile(join(output, "private-progress.json"), JSON.stringify({ sha: process.env.GITHUB_SHA, observations }, null, 2))
  await browser.close()
}
