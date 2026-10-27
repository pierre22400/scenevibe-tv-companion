import { createServer } from "node:https"
import { readFile, writeFile, mkdir } from "node:fs/promises"
import { resolve, join } from "node:path"
import { pathToFileURL } from "node:url"
import { once } from "node:events"

/**
 * API de qualification locale : charge les services Cloud exacts du checkout 003-I.
 * Les repositories sont en mémoire et l'ObjectStore est HTTP local. Aucun client
 * Neon/R2, identifiant fournisseur, déploiement ni route publique n'est composé.
 * TLS sert exclusivement l'émulateur via adb reverse et vérifie son vrai bearer.
 */

/** Run the qualified Cloud composition behind a local HTTPS socket until the test owner stops it. */
async function main() {
  const cloud = resolve(process.env.SCENEVIBE_CLOUD_CHECKOUT ?? ""), out = resolve(process.env.SCENEVIBE_ANDROID_EVIDENCE ?? "")
  if (!process.env.SCENEVIBE_CLOUD_CHECKOUT || !process.env.SCENEVIBE_ANDROID_EVIDENCE) throw Error("EXPLICIT_LOCAL_PATHS_REQUIRED")
  process.chdir(cloud)
  const { localEnvironment } = await import(pathToFileURL(join(cloud, "qualification/cloud-bridge003d/environment.ts")).href)
  const { handleMultimediaDelivery } = await import(pathToFileURL(join(cloud, "packages/cloud/src/multimedia-delivery/http.ts")).href)
  const environment = await localEnvironment(), handle = handleMultimediaDelivery(() => environment.service)
  const calls: Array<{ action: string; method: string; status: number }> = []
  let fault = "normal"
  const server = createServer({ key: await readFile(join(out, "tls/key.pem")), cert: await readFile(join(out, "tls/cert.pem")) }, async (incoming, outgoing) => {
    try {
      const url = new URL(incoming.url ?? "/", "https://localhost")
      // Loopback-only fault control is never a Cloud product route and grants no authority.
      if (url.pathname === "/qualification-fault") {
        fault = url.searchParams.get("mode") ?? "normal"
        if (!["normal", "truncate", "corrupt", "mime", "redirect"].includes(fault)) { outgoing.writeHead(400).end(); return }
        outgoing.writeHead(200, { "content-type": "application/json" }).end("{}"); return
      }
      const chunks: Buffer[] = []
      for await (const chunk of incoming) chunks.push(Buffer.from(chunk))
      const headers = new Headers()
      if (incoming.headers.authorization) headers.set("authorization", incoming.headers.authorization)
      if (incoming.headers["content-type"]) headers.set("content-type", incoming.headers["content-type"])
      const response = await handle(new Request(url, { method: incoming.method, headers,
        ...(incoming.method === "POST" ? { body: Buffer.concat(chunks) } : {}) }))
      const action = url.pathname.split("/").pop() ?? "unknown"
      calls.push({ action, method: incoming.method ?? "GET", status: response.status })
      await writeFile(join(out, "http-events.json"), JSON.stringify(calls, null, 2))
      if (action === "resource" && response.status === 200 && fault !== "normal") {
        if (fault === "redirect") { outgoing.writeHead(302, { location: "https://example.invalid/refused" }).end(); return }
        const data = Buffer.from(await response.arrayBuffer())
        if (fault === "corrupt") data[Math.min(100, data.length - 1)] ^= 255
        outgoing.writeHead(200, { ...Object.fromEntries(response.headers), ...(fault === "mime" ? { "content-type": "image/jpeg" } : {}) })
        if (fault === "truncate") { outgoing.write(data.subarray(0, 100)); outgoing.destroy(); return }
        outgoing.end(data); return
      }
      outgoing.writeHead(response.status, Object.fromEntries(response.headers))
      if (response.body) {
        const reader = response.body.getReader()
        while (true) { const next = await reader.read(); if (next.done) break; if (!outgoing.write(next.value)) await once(outgoing, "drain") }
      }
      outgoing.end()
    } catch { if (!outgoing.headersSent) outgoing.writeHead(503); outgoing.end() }
  })
  await mkdir(out, { recursive: true })
  await new Promise<void>((done,reject) => { server.once("error",reject);server.listen(8443,"127.0.0.1",done) })
  // Synthetic short-lived test identities go only to the instrumentation asset, never the target APK.
  await writeFile(join(out, "runtime-private.json"), JSON.stringify({ ...environment.tv,
    foreignToken: environment.foreign.token, publicationId: environment.publicationId, origin: "https://localhost:8443" }))
  await writeFile(join(out, "server-ready.json"), JSON.stringify({ tls: true, port: 8443, host: "127.0.0.1", cloudCommit: "42c2b94aa436a2dbacf2bb082dc6737457602cee",
    repositories: "MEMORY", objectStore: "LOCAL_HTTP", providersContacted: false, publiclyDeployed: false }))
  await new Promise<void>(done => { process.once("SIGTERM", done); process.once("SIGINT", done) })
  server.closeAllConnections(); await new Promise<void>(done => server.close(() => done())); await environment.close()
}

main().catch(error => { process.stderr.write("LOCAL_ANDROID_HTTPS_QUALIFICATION_FAILED: "+String(error)+"\n"); process.exitCode = 1 })
