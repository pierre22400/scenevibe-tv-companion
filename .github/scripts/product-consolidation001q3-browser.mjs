import assert from "node:assert/strict"
import { execFileSync } from "node:child_process"
import { writeFile } from "node:fs/promises"
import { randomUUID } from "node:crypto"
import { setTimeout as wait } from "node:timers/promises"

/**
 * Clic Chromium réel et cache Android du même envoi sur le Viewer isolé.
 * Le code et le cookie de la TV jetable restent hors des preuves publiées.
 */
const origin = "https://scenevibe-product-consolidation001q2-viewe-git-6ef46d-archc-ode.vercel.app"
const pkg = "com.scenevibe.tvcompanionpoc"
const track = process.env.SCENEVIBE_VIEWER_TRACK_ID
const { chromium } = await import(process.env.SCENEVIBE_PLAYWRIGHT_MODULE)
assert.equal(process.env.SCENEVIBE_PUBLIC_VIEWER_ORIGIN, origin)

/** Lit une observation dans les seuls fichiers privés de l’émulateur du job. */
async function read(name, attempts = 180) {
  for (let index=0; index<attempts; index++) {
    try {
      return JSON.parse(execFileSync("adb",["exec-out","run-as",pkg,"cat","files/"+name],
        { encoding:"utf8",stdio:["ignore","pipe","ignore"] }))
    } catch { await wait(1000) }
  }
  throw new Error("Q3_ANDROID_EVIDENCE_UNAVAILABLE: "+name)
}

/** Publie atomiquement au test Android, sans identifiant ni secret dans la ligne de commande. */
function write(name,value) {
  execFileSync("adb",["shell","-T","run-as",pkg,"sh","-c",
    "'cat > files/"+name+".tmp && mv files/"+name+".tmp files/"+name+"'"],
    { input:JSON.stringify(value),stdio:["pipe","ignore","ignore"] })
}

const browser=await chromium.launch({ executablePath:process.env.SCENEVIBE_CHROMIUM_EXECUTABLE, headless:true, args:["--no-sandbox"] })
try {
  const context=await browser.newContext()
  const page=await context.newPage()
  const publication=await context.request.get(origin+"/api/public/catalogue/tracks/"+encodeURIComponent(track))
  assert.equal(publication.status(),200)
  const { multimedia }=await publication.json()
  const { code }=await read("q3-code.json")
  await page.goto(origin+"/track/"+encodeURIComponent(track))
  const button=page.getByRole("button",{ name:"Envoyer sur ma TV",exact:true })
  await button.click()
  const pair=await context.request.post(origin+"/api/autonomous/viewer-pair",{ headers:{origin},data:{userCode:code,consent:true} })
  assert.equal(pair.status(),200)
  const { deviceId }=await pair.json()
  const cookies=(await context.cookies(origin+"/api/autonomous/viewer-status")).filter(item=>item.name.startsWith("sv_autonomous_tv_"))
  assert.equal(cookies.length,1)
  write("q3-paired.json",{deviceId,cookie:cookies[0].name+"="+cookies[0].value})
  const background=await read("q3-background.json")
  assert.equal(background.activityClosed,true)
  assert.equal(background.privateActive,true)
  await page.reload()
  const sendPath="/api/autonomous/viewer-send"
  const pending=page.waitForResponse(response=>new URL(response.url()).pathname===sendPath &&
    response.request().method()==="POST",{timeout:60000})
  await button.click()
  const response=await pending
  assert.equal(response.status(),200)
  const command=await response.json()
  write("q3-command.json",{commandId:command.commandId})
  const waiting=page.locator('[data-viewer-state="waiting"]')
  await waiting.waitFor({timeout:150000})
  const waitText=await waiting.innerText()
  assert.match(waitText,/fin de la session SceneVibe en cours/i)
  assert.equal(await page.locator('[data-viewer-state="ready"]').count(),0)
  write("q3-stop-private.json",{stop:true})
  assert.equal((await read("q3-first-resource-bytes.json",180)).received,true)
  execFileSync("adb",["shell","cmd","connectivity","airplane-mode","enable"],{stdio:"ignore"})
  try {
    const downloading=await context.request.post(origin+"/api/autonomous/viewer-status",
      {headers:{origin},data:{trackId:track,deviceId}})
    assert.equal(downloading.status(),200)
    assert.equal((await downloading.json()).command.state,"downloading")
    write("q3-network-cut.json",{cut:true})
    await wait(4000)
    const interrupted=await context.request.post(origin+"/api/autonomous/viewer-status",
      {headers:{origin},data:{trackId:track,deviceId}})
    assert.equal((await interrupted.json()).command.state,"downloading")
  } finally {
    execFileSync("adb",["shell","cmd","connectivity","airplane-mode","disable"],{stdio:"ignore"})
  }
  const ready=page.locator('[data-viewer-state="ready"]')
  await ready.waitFor({timeout:180000})
  assert.match(await ready.innerText(),/TEXT ON.*AUDIO ON/)
  const cache=await read("q3-cache.json",30)
  assert.equal(cache.commandId,command.commandId)
  assert.equal(cache.revision,multimedia.revisionId)
  assert.equal(cache.packageSha256,multimedia.packageSha256)
  const payload=response.request().postDataJSON()
  const replay=await context.request.post(origin+sendPath,{headers:{origin},data:payload})
  assert.equal(replay.status(),200)
  assert.equal((await replay.json()).commandId,command.commandId)
  const foreign=await context.request.post(origin+sendPath,{headers:{origin},data:{...payload,deviceId:randomUUID()}})
  assert.equal(foreign.status(),401)
  await writeFile("public-viewer-native/q3-browser.json",JSON.stringify({origin,track,...cache,
    browserClick:true,activityClosed:true,webReady:true,privateDeferred:true,waitText,voluntaryStop:true,resourceBytesBeforeCut:true,resumedSameCommand:true,replaySameCommand:true,foreignDeviceStatus:401},null,2))
  await page.screenshot({path:"public-viewer-native/q3-browser-ready.png",fullPage:true})
} finally {
  await browser.close()
}
