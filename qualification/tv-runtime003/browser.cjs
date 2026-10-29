/** Exact production phone assets and HTTP server, with an explicit recording owner and controlled lifetime clock. */
const fs=require('node:fs');
const path=require('node:path');
const assert=require('node:assert/strict');
const {spawn}=require('node:child_process');
const {chromium}=require('playwright');

/** Drive a Chromium phone viewport; this proves browser integration, never Android/Prime or two wall-clock hours. */
async function main(){
  const info=path.resolve(process.argv[2]),classpath=process.argv[3],assets=path.resolve(process.argv[4]),proof=process.argv[5];
  const child=spawn('java',['-cp',classpath,'com.scenevibe.tvcompanionpoc.LocalBrowserFixture',assets,info],{stdio:['pipe','ignore','inherit']});
  let browser;
  try{
    for(let i=0;!fs.existsSync(info)&&i<200;i++)await new Promise(r=>setTimeout(r,25));
    const data=JSON.parse(fs.readFileSync(info,'utf8')),code=data.instructions.match(/Code téléphone : ([0-9]{8})/)[1];
    browser=await chromium.launch({headless:true,args:['--no-sandbox']});
    const page=await browser.newPage({viewport:{width:390,height:844}}),errors=[],external=[];
    page.on('pageerror',error=>errors.push(error.message));
    page.on('request',request=>{if(!request.url().startsWith(`http://127.0.0.1:${data.port}/`))external.push(request.url());});
    await page.goto(`http://127.0.0.1:${data.port}/`);await page.locator('#code').fill(code);await page.locator('#pair').click();
    await page.locator('#text[aria-pressed="true"]').waitFor();await page.getByRole('status').filter({hasText:'120 min'}).waitFor();
    for(const toggle of ['text','audio','adsync']){const before=await page.locator(`#${toggle}`).getAttribute('aria-pressed');await page.locator(`#${toggle}`).click();await page.locator(`#${toggle}[aria-pressed="${before==='true'?'false':'true'}"]`).waitFor();}
    assert.equal(await page.locator('#suspend, #resume').count(),0);
    await page.locator('#hold').click();await page.locator('#hold[aria-pressed="true"]').waitFor();
    await page.getByRole('status').filter({hasText:'USER_SUSPENDED'}).waitFor();
    await page.locator('#hold').click();await page.locator('#hold[aria-pressed="false"]').waitFor();
    await page.getByRole('status').filter({hasText:'MEDIA_FIXTURE_ONLY'}).waitFor();
    await page.getByText('Recalage et relecture en cas d’incertitude MEDIA', {exact:true}).click();
    await page.locator('#time').fill('00:31');
    for(const [action,result] of [['calibrate','calibrate:00:31'],['replay','replay:'],['show','panel:show'],['close','panel:close']]){
      await page.locator(`#${action}`).click();await page.getByRole('status').filter({hasText:result}).waitFor();
    }
    await page.locator('#adguard').click();await page.locator('#adguard[aria-pressed="true"]').waitFor();
    assert.equal(await page.locator('#adprobe').getAttribute('aria-pressed'),'false');
    assert.equal(await page.locator('#hold').getAttribute('aria-pressed'),'false');
    await page.locator('#adguard').click();await page.locator('#adguard[aria-pressed="false"]').waitFor();
    await page.getByText('Investigation publicitaire · observation seule', {exact:true}).click();
    assert.equal(await page.locator('#ad-visible').isDisabled(),true);
    await page.locator('#adprobe').click();await page.locator('#adprobe[aria-pressed="true"]').waitFor();
    for(const [id,marker] of [['ad-visible','AD_VISIBLE'],['content-visible','CONTENT_VISIBLE'],['normal-seek','NORMAL_SEEK'],['normal-pause','NORMAL_PAUSE']]){
      await page.locator(`#${id}`).click();await page.getByRole('status').filter({hasText:`admark:${marker}`}).waitFor();
      assert.equal(await page.locator('#hold').getAttribute('aria-pressed'),'false');
      assert.match(await page.locator('#ad-observation').innerText(),/UNCERTAIN/);
    }
    await page.locator('#adprobe').click();await page.locator('#adprobe[aria-pressed="false"]').waitFor();
    assert.equal(await page.locator('#ad-visible').isDisabled(),true);
    child.stdin.write('6601000\n');await page.getByRole('status').filter({hasText:'10 min restantes'}).waitFor();
    await page.locator('#text').click();await page.locator('#text[aria-pressed="true"]').waitFor();
    assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),true);
    assert.equal(await page.evaluate(()=>localStorage.length+sessionStorage.length),0);
    child.stdin.write('7200999\n');await page.getByRole('status').filter({hasText:'1 min restantes'}).waitFor();
    child.stdin.write('7201000\n');await page.getByRole('status').filter({hasText:/Accès refusé|expiré|fetch|Failed to fetch/i}).waitFor();
    assert.deepEqual(errors,[]);assert.deepEqual(external,[]);
    fs.writeFileSync(proof,JSON.stringify({result:'PASS',scope:'CHROMIUM_390x844_REAL_HTTP_EXACT_ASSETS_RECORDING_OWNER_NOT_ANDROID_OR_PRIME',pairing:true,channelSwitches:true,manualSuspendResume:true,
      singlePauseActualState:true,optionalReplay:true,adProtectionIndependentSwitch:true,adObservationOptIn:true,ownerAnnotationsNotDetection:true,commandsAfter110Minutes:true,deadline120Minutes:true,clock:'INJECTED_MONOTONIC_NOT_TWO_WALL_CLOCK_HOURS',noExternalRequests:true,noPersistentToken:true,pageErrors:errors.length},null,2));
  }finally{if(browser)await browser.close();child.stdin.end();child.kill('SIGTERM');try{fs.unlinkSync(info);}catch{}}
}
main().catch(error=>{console.error(error.message);process.exitCode=1;});
