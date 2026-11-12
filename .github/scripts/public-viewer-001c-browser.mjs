import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { readFile, writeFile } from 'node:fs/promises'
import { setTimeout as wait } from 'node:timers/promises'

/**
 * Clic réel dans Chromium sur le catalogue Preview, avec cookie Better Auth
 * émis avant l'essai et Companion Android initialisé par le seul parcours historique.
 */
const origin = 'https://scenevibe-public-viewer001c-qual.vercel.app'
const pkg = 'com.scenevibe.tvcompanionpoc'
const { chromium } = await import(process.env.SCENEVIBE_PLAYWRIGHT_MODULE)

/** Observe les fichiers de preuve du seul émulateur, sans accéder aux credentials. */
async function deviceEvidence(file, attempts) {
  for (let i=0;i<attempts;i++) {
    try {
      return JSON.parse(execFileSync('adb',['exec-out','run-as',pkg,'cat','files/'+file],{encoding:'utf8',stdio:['ignore','pipe','ignore']}))
    } catch { await wait(1000) }
  }
  throw new Error('ANDROID_EVIDENCE_UNAVAILABLE: '+file)
}

/** Obtient un ticket OIDC à audience exacte puis laisse Better Auth établir la session hôte. */
async function historicalSession(context) {
  const endpoint = new URL(process.env.ACTIONS_ID_TOKEN_REQUEST_URL)
  endpoint.searchParams.set('audience',origin+'/qualification/001c')
  const signed=await fetch(endpoint,{headers:{authorization:'Bearer '+process.env.ACTIONS_ID_TOKEN_REQUEST_TOKEN}})
  assert.equal(signed.status,200,'CI_IDENTITY_UNAVAILABLE')
  const {value}=await signed.json()
  const response=await context.request.post(origin+'/api/v1/qualification/001c-browser-session',{
    headers:{origin,authorization:'Bearer '+value},
  })
  assert.equal(response.status(),200,'HISTORICAL_SIGN_IN_REFUSED')
  assert.deepEqual((await context.cookies(origin)).filter(cookie=>cookie.name.startsWith('sv_autonomous')),[])
  assert.equal((await context.cookies(origin)).some(cookie=>cookie.name==='__Secure-scenevibe_auth.session_token'&&cookie.httpOnly&&cookie.secure),true)
}

/** Rejoue l'association initiale historique, avant le début du scénario A. */
async function prepare(context) {
  await historicalSession(context)
  const {userCode}=await deviceEvidence('001c-code.json',130)
  const claim=await context.request.post(origin+'/api/v1/device-activations/claim?profile=tvfirst008-cache-v1',{
    headers:{origin},data:{profile:'tvfirst008-cache-v1',userCode,consent:true},
  })
  assert.equal(claim.status(),200,'HISTORICAL_ASSOCIATION_REFUSED')
  const background=await deviceEvidence('001c-background.json',110)
  assert.equal(background.activityClosed,true)
  assert.equal(background.publicIdentityPresent,false)
  return background.deviceId
}

const browser=await chromium.launch({executablePath:process.env.SCENEVIBE_CHROMIUM_EXECUTABLE,headless:true,args:['--no-sandbox']})
try {
  const anonymous=await browser.newContext()
  const catalogue=await anonymous.request.get(origin+'/api/public/catalogue/tracks?limit=100')
  assert.equal(catalogue.status(),200)
  const {items}=await catalogue.json()
  const track=items.find(item=>item.track.title.startsWith('Qualification spectateur 001 — Columbo — Eaux troubles')&&item.multimedia?.audioCount===3)
  assert.ok(track,'PUBLISHED_TEXT_AUDIO_TRACK_REQUIRED')
  await anonymous.close()

  const context=await browser.newContext()
  const deviceId=await prepare(context)
  const page=await context.newPage()
  const sendPath='/api/autonomous/historical-viewer-send'
  const pairingCalls=[]
  page.on('request',request=>{if(/viewer-pair|device-activations/.test(request.url()))pairingCalls.push(request.url())})
  await page.goto(origin+'/track/'+encodeURIComponent(track.identity.slug),{waitUntil:'domcontentloaded'})
  const button=page.getByRole('button',{name:'Envoyer sur ma TV',exact:true})
  await button.waitFor({state:'visible',timeout:30_000})
  const destination=page.getByLabel('Télévision',{exact:true})
  if(await destination.count())await destination.selectOption(deviceId)
  const response=page.waitForResponse(r=>new URL(r.url()).pathname===sendPath&&r.request().method()==='POST',{timeout:45_000})
  await button.click({timeout:45_000})
  const sent=await response
  assert.equal(sent.status(),200,'BROWSER_HISTORICAL_SEND_REFUSED')
  const command=await sent.json()
  await page.locator('[data-viewer-state="ready"]').waitFor({timeout:170_000})
  assert.match(await page.locator('[data-viewer-state="ready"]').textContent(),/Track prête.*TEXT ON.*AUDIO ON/s)
  const native=await deviceEvidence('001c-native.json',30)
  assert.equal(native.commandId,command.commandId)
  assert.equal(native.packageSha256,track.multimedia.packageSha256)
  assert.equal(native.revisionId,track.multimedia.revisionId)
  assert.deepEqual(pairingCalls,[])
  assert.deepEqual((await context.cookies(origin)).filter(cookie=>cookie.name.startsWith('sv_autonomous')),[])
  await writeFile('viewer001c-evidence/test-a.json',JSON.stringify({origin,deviceId,trackId:track.identity.trackId,
    commandId:command.commandId,realBrowserClick:true,anonymousCatalogue:true,historicalCookie:true,
    publicCookie:false,newPairingCalls:0,native},null,2)+'\n')
} finally { await browser.close() }
