const {chromium}=require('../../build/slash-editor-test/node_modules/playwright-core');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',headless:true,args:['--enable-unsafe-swiftshader']});
 const page=await browser.newPage({viewport:{width:1600,height:1000}});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('http://127.0.0.1:8765/anchors');await page.waitForFunction(()=>document.getElementById('status').textContent.includes('已载入'));
 assert.equal(await page.locator('.point').count(),2);assert.equal(await page.locator('#model').evaluate(c=>!!c.getContext('webgl')),true);
 const canvas=page.locator('#texture'),box=await canvas.boundingBox();await page.mouse.click(box.x+box.width*.5,box.y+box.height*.5);assert.equal(await page.locator('.point').count(),3);
 await page.locator('[data-view="thirdPerson"]').click();assert.equal(await page.locator('.point').count(),2);
 await page.locator('[data-view="firstPerson"]').click();await page.locator('#undo').click();assert.equal(await page.locator('.point').count(),2);
 await page.locator('#animate').check();await page.waitForTimeout(500);await page.screenshot({path:'build/slash-anchor-editor.png',fullPage:true});
 await page.locator('#items').selectOption('rotten_ruins:domerite_sword');await page.waitForFunction(()=>document.getElementById('status').textContent.includes('domerite_sword'));
 assert.equal(await page.locator('.point').count(),2);assert.deepEqual(errors,[]);
 await browser.close();console.log('PASS: WebGL editor, texture add, independent views, undo, animation, model switch, no JS errors');
})().catch(e=>{console.error(e);process.exit(1);});
