#!/usr/bin/env python3
"""System UI regression for Hasel360 v0.7.6 with synthetic ephemeral IndexedDB.
CAUTION: browser simulation, not a substitute for Android/device or true IndexedDB tests.
"""
from __future__ import annotations
import json,sys,traceback,datetime,os
from pathlib import Path
from playwright.sync_api import sync_playwright,TimeoutError as PWTimeout
ASSETS=Path(__file__).resolve().parents[1]/'app/src/main/assets'
OUT=Path(__file__).parent/'artifacts'
OUT.mkdir(parents=True,exist_ok=True)
results=[]
SHIM=r'''<script>
(() => {
  const cache={}; window.__qaCache=cache;
  Object.defineProperty(window,'localStorage',{configurable:true,value:{getItem(){return null},setItem(){},removeItem(){},clear(){}}});
  Object.defineProperty(window,'indexedDB',{configurable:true,value:{open(){
    const rq={result:{createObjectStore(){},transaction(){
      const tx={oncomplete:null,onabort:null,onerror:null};let pending=0,ended=false;
      function scheduleComplete(){if(ended)return;setTimeout(()=>{if(pending===0&&!ended){ended=true;tx.oncomplete&&tx.oncomplete()};},1)}
      tx.objectStore=()=>({
        get(key){pending++;const r={result:undefined};setTimeout(()=>{r.result=cache[key];pending--;r.onsuccess&&r.onsuccess();scheduleComplete()},0);return r},
        put(value,key){pending++;cache[key]=structuredClone(value);setTimeout(()=>{pending--;scheduleComplete()},0);return {}}
      });
      return tx;
    }}};
    setTimeout(()=>{rq.onupgradeneeded&&rq.onupgradeneeded();rq.onsuccess&&rq.onsuccess()},0);
    return rq;
  }}});
})();
</script>'''

def inline_html():
    content=(ASSETS/'index.html').read_text(encoding='utf-8')
    content=content.replace('<link href="style.css" rel="stylesheet"/>','<style>'+(ASSETS/'style.css').read_text(encoding='utf-8')+'</style>')
    for name in ['core.js','cash-core.js','settlement-core.js','security.js','period-analytics.js','app.js','cash-import.js','cash-ui.js']:
        content=content.replace('<script src="'+name+'"></script>','<script>'+ (ASSETS/name).read_text(encoding='utf-8')+'</script>')
    return content.replace('<script>'+ (ASSETS/'core.js').read_text(encoding='utf-8'),SHIM+'<script>'+ (ASSETS/'core.js').read_text(encoding='utf-8'),1)

html=inline_html()

def test(name,func):
  try:
    extra=func()
    print('PASS',name,str(extra)[:240])
    results.append({'name':name,'result':'PASS','detail':str(extra or '')})
  except Exception as e:
    print('FAIL',name,repr(e))
    results.append({'name':name,'result':'FAIL','detail':str(e)[:350]})

with sync_playwright() as pl:
  chrome=os.environ.get('HASEL_QA_CHROME') or ('/usr/bin/chromium' if Path('/usr/bin/chromium').exists() else None)
  browser=pl.chromium.launch(headless=True,args=['--no-sandbox'],**({'executable_path':chrome} if chrome else {}))
  context=browser.new_context(viewport={'width':390,'height':844},locale='fa-IR',accept_downloads=True)
  page=context.new_page();errors=[];page.on('pageerror',lambda ex: errors.append(str(ex)))
  page.on('dialog',lambda dlg: dlg.accept('اصلاح ثبت آزمایشی') if dlg.type=='prompt' else dlg.accept())
  page.set_content(html,wait_until='load');page.wait_for_timeout(600)
  def click_tab(tab):
    page.locator('[data-tab="'+tab+'"]').click()
    page.wait_for_timeout(60)
  def snapshot(name):
    page.screenshot(path=str(OUT/name),full_page=False)
  def basic():
    assert page.locator('#home').is_visible()
    assert not page.locator('body').get_attribute('class')=='auth-pending'
    assert page.locator('#homeAll').get_attribute('aria-pressed')=='true'
    assert not errors,errors
    return 'صفحه اصلی، آماده‌سازی برنامه و بارگذاری اسکریپت‌ها'
  test('UI-BOOT-01: launch and script errors',basic)
  def responsive():
    measurements=[];bad=[]
    for width in [320,360,390,430,540,768]:
      page.set_viewport_size({'width':width,'height':810})
      for tab in ['home','tasks','calendar','reports','cash']:
        click_tab(tab)
        if tab=='cash':
          for id in ['cashCategoryForm','cashAccountForm','cashUnitForm','cashVersionForm','cashTxnForm']:
            page.locator('#'+id).evaluate('(el)=>{const d=el.closest("details");if(d)d.open=true;}')
        sample=page.evaluate('''() => {
         const vw=document.documentElement.clientWidth,scroll=document.documentElement.scrollWidth;
         const root=document.querySelector('#'+(document.querySelector('[data-tab].active')?.dataset.tab||'home'));
         const controls=[...root?.querySelectorAll('input,select,textarea,button')||[]];
         const outside=controls.filter(el=>{
           const s=getComputedStyle(el),r=el.getBoundingClientRect();
           if(!r.width||!r.height||s.visibility==='hidden')return false;
           if(el.closest('.cash-scroll,.cash-table,.cash-matrix-scroll,#monthCalendar,.main-calendar,nav'))return false;
           return r.left< -1.5 || r.right>vw+1.5;
         }).map(el=>({id:el.id||el.outerHTML.slice(0,60),left:Math.round(el.getBoundingClientRect().left),right:Math.round(el.getBoundingClientRect().right)})).slice(0,8);
         return {vw,scroll,outside};}''')
        if sample['scroll']>sample['vw']+2 or sample['outside']:
          bad.append({'width':width,'tab':tab,**sample})
        measurements.append({'w':width,'tab':tab,**sample})
        if width==390 and tab=='cash':snapshot('qa-cash-390.png')
        if width==390 and tab=='reports':snapshot('qa-reports-390.png')
    (OUT/'responsive.json').write_text(json.dumps(measurements,ensure_ascii=False,indent=2))
    assert not bad,'سرریز افقی: '+json.dumps(bad[:9],ensure_ascii=False)
    return '۳۰ حالت عرض/صفحه بدون خروج کنترل‌ها؛ استثنا: جداول عمداً اسکرول‌پذیر'
  test('UI-RESP-02: responsive 6x5 matrix',responsive)
  page.set_viewport_size({'width':390,'height':844})
  def calendar():
    click_tab('reports')
    wrap=page.locator('#reportFrom').locator('xpath=..')
    wrap.locator('button.date-pick-button').click()
    assert page.locator('#datePicker').is_visible()
    assert page.locator('#pickerModeSwitch [data-mode="jalali"]').count()==1
    # Today should fill currently targeted input with no second submit
    page.locator('#pickerToday').click()
    assert not page.locator('#datePicker').is_visible()
    val=page.locator('#reportFrom').input_value()
    assert page.evaluate('(v)=>window.HamyarCore.dateNorm(v)===window.HamyarCore.today()',val)
    wrap.locator('button.date-pick-button').click()
    page.locator('#pickerModeSwitch [data-mode="gregorian"]').click()
    selected_before=page.locator('#pickerSelectedText').inner_text()
    page.locator('#pickerConfirm').click()
    result=page.locator('#reportFrom').input_value()
    assert page.evaluate('(v)=>window.HamyarCore.dateNorm(v)===window.HamyarCore.today()',result)
    assert '٬' not in page.locator('#pickerHeading').inner_text()
    return {'today':val,'converted':result,'selected':selected_before[:65]}
  test('DATE-03: picker today, dual calendar, canonical day',calendar)
  def dashboard():
    click_tab('home')
    for id in ['homeAll','homeWeek','homeMonth','homeAll']:
      page.locator('#'+id).click()
      assert page.locator('#'+id).get_attribute('aria-pressed')=='true'
      assert page.locator('#homePeriodMetrics .period-metric-card').count()>=4
    # drill-through
    page.locator('#homePeriodMetrics .period-metric-card').first.click()
    assert page.locator('#homeDrilldown').is_visible()
    page.locator('#homeDrillClose').click()
    assert not page.locator('#homeDrilldown').is_visible()
    return 'نمای کلی / هفتگی / ماهانه و فهرست جزئیات'
  test('DASH-04: period modes and clickable drilldown',dashboard)
  def controls():
    click_tab('cash')
    widths=page.evaluate('''() => Object.fromEntries(['cashCategoryCode','cashUnitCode','cashAccountCode','cashCategoryName','cashUnitName','cashAccountName','cashAccountDate','cashTxnDate'].map(id=>[id,Math.round(document.getElementById(id).getBoundingClientRect().width)]))''')
    for id in ['cashCategoryCode','cashUnitCode','cashAccountCode']:
      assert widths[id]<=100,(id,widths)
    for id in ['cashCategoryName','cashUnitName','cashAccountName']:
      assert widths[id]>widths[id.replace('Name','Code')],(id,widths)
    # Contract: input length four, date read-only and has picker button.
    assert page.locator('#cashAccountCode').get_attribute('maxlength')=='4'
    assert page.locator('#cashAccountDate').get_attribute('readonly') is not None
    assert page.locator('#cashAccountDate').locator('xpath=..').locator('button').count()==1
    return widths
  test('UI-FIELDS-05: four-character codes and compact dates',controls)
  def create_record():
    click_tab('tasks');page.locator('#add').click()
    assert page.locator('#editor').is_visible()
    page.locator('#title').fill('تست سیستمی کار بدون اطلاعات واقعی')
    page.locator('#dueDate').locator('xpath=..').locator('button').click()
    page.locator('#pickerToday').click()
    assert page.locator('#dueDate').input_value()
    page.locator('#saveRecord').click();page.wait_for_timeout(180)
    assert not page.locator('#editor').is_visible()
    assert 'تست سیستمی کار' in page.locator('#recordList').inner_text()
    # Mark done; verify dashboard
    page.locator('#recordList button').filter(has_text='انجام شد').first.click();page.wait_for_timeout(140)
    assert 'انجام‌شده' in page.locator('#recordList').inner_text()
    return 'افزودن، انتخاب تاریخ، ذخیره و تکمیل کار در رابط'
  test('E2E-TASK-06: create and complete task',create_record)
  def create_payable():
    click_tab('pay');page.locator('#add').click()
    page.locator('#title').fill('تست سیستمی پرداختنی آزمایشی')
    page.locator('#dueDate').locator('xpath=..').locator('button').click();page.locator('#pickerToday').click()
    page.locator('#amount').fill('250000000');page.locator('#saveRecord').click();page.wait_for_timeout(180)
    assert 'تست سیستمی پرداختنی' in page.locator('#recordList').inner_text()
    return 'تعهد پرداخت ۲۵۰ میلیون ریال'
  test('E2E-DEBT-07: create payable',create_payable)
  def create_cash_masters():
    click_tab('cash')
    page.locator('#cashAccountForm').evaluate('(el)=>el.closest("details").open=true')
    page.locator('#cashAccountCode').fill('1890')
    page.locator('#cashAccountName').fill('بانک تست خودکار')
    # Opening date already populated (today), do not modify
    page.locator('#cashAccountOpening').fill('1000000000')
    page.locator('#cashAccountForm button[type=submit]').click();page.wait_for_timeout(180)
    assert 'بانک تست خودکار' in page.locator('#cashAccountList').inner_text(),page.locator('#notice').inner_text()
    page.locator('#cashUnitForm').evaluate('(el)=>el.closest("details").open=true')
    page.locator('#cashUnitCode').fill('2890');page.locator('#cashUnitName').fill('واحد تست خودکار')
    page.locator('#cashUnitForm button[type=submit]').click();page.wait_for_timeout(180)
    assert 'واحد تست خودکار' in page.locator('#cashUnitList').inner_text(),page.locator('#notice').inner_text()
    return 'حساب و واحد چهاررقمی آزمایشی ایجاد شدند'
  test('E2E-MASTER-08: account and unit entry',create_cash_masters)
  def settlement_flow():
    click_tab('pay')
    page.locator('#recordList button').filter(has_text='ثبت تسویه').first.click()
    assert page.locator('#settlementDialog').is_visible()
    page.locator('#settlementAccount').select_option(label='1890 • بانک تست خودکار')
    page.locator('#settlementUnit').select_option(label='2890 • واحد تست خودکار')
    options=page.locator('#settlementCategory option').evaluate_all('(arr)=>arr.map(x=>({v:x.value,t:x.textContent}))')
    category=next((x for x in options if x['v']),None)
    assert category,options
    page.locator('#settlementCategory').select_option(category['v'])
    page.locator('#settlementAmount').fill('120000000')
    page.locator('#settlementDate').locator('xpath=..').locator('button').click()
    page.locator('#pickerToday').click()
    page.wait_for_timeout(140)
    warning=page.locator('#settlementBudgetWarning').inner_text()
    assert page.locator('#settlementBefore').inner_text()
    page.locator('#settlementConfirm').click();page.wait_for_timeout(200)
    assert not page.locator('#settlementDialog').is_visible(),page.locator('#notice').inner_text()
    assert '۱۳۰٬۰۰۰٬۰۰۰' in page.locator('#recordList').inner_text() or '130,000,000' in page.locator('#recordList').inner_text(),page.locator('#recordList').inner_text()[:400]
    cash=page.evaluate('window.HaselCashUI.snapshot()')
    linked=[x for x in cash['transactions'] if x.get('sourceDebtId')]
    assert len(linked)==1,linked
    assert linked[0]['amount']=='120000000'
    return {'linked':len(linked),'amount':linked[0]['amount'],'budgetWarning':warning}
  test('E2E-SETTLE-09: payable partial settlement with one linked cash entry',settlement_flow)
  def persistence_and_errors():
    click_tab('cash')
    snap=page.evaluate('''() => ({legacy:window.__qaCache.data, cash:window.__qaCache['cash-v1']})''')
    assert len(snap['legacy']['debts'])==1
    assert len(snap['cash']['transactions'])==1
    assert len(snap['legacy']['debts'][0]['settlements'])==1
    assert not errors,errors
    return {'tasks':len(snap['legacy']['tasks']),'debts':len(snap['legacy']['debts']),'linkedPayments':len(snap['cash']['transactions']),'javascriptErrors':len(errors)}
  test('DATA-10: mock store coherence & browser exceptions',persistence_and_errors)
  def duplicate_account():
    click_tab('cash')
    before=len(page.evaluate('window.HaselCashUI.snapshot().accounts'))
    page.locator('#cashAccountCode').fill('1890')
    page.locator('#cashAccountName').fill('بانک تکراری')
    page.locator('#cashAccountForm button[type=submit]').click();page.wait_for_timeout(160)
    after=len(page.evaluate('window.HaselCashUI.snapshot().accounts'))
    assert after==before,(before,after)
    assert 'تکرار' in page.locator('#notice').inner_text() or 'وجود دارد' in page.locator('#notice').inner_text(),page.locator('#notice').inner_text()
    return 'کد چهاررقمی تکراری رد شد؛ شمار حساب‌ها ثابت ماند'
  test('FIN-11: duplicate account code rejected without side effects',duplicate_account)
  def void_settlement():
    click_tab('pay')
    assert page.locator('#recordList').get_by_text('ابطال تسویه').count()==1
    page.locator('#recordList').get_by_text('ابطال تسویه').click()
    page.wait_for_timeout(230)
    cash=page.evaluate('window.HaselCashUI.snapshot()')
    legacy=page.evaluate('window.__qaCache.data')
    assert cash['transactions'][0].get('voidedAt'),cash['transactions'][0]
    assert legacy['debts'][0]['settlements'][0].get('voidedAt')
    assert len(cash['transactions'])==1
    assert sum(1 for x in cash['transactions'] if not x.get('voidedAt'))==0
    assert page.evaluate('String(window.HaselSettlements.outstanding(window.__qaCache.data.debts[0]))')=='250000000'
    return 'تسویه و گردش هر دو باطل شدند؛ سابقه حفظ شد و مانده تعهد بازیابی شد'
  test('FIN-12: synchronized settlement reversal and audit preservation',void_settlement)
  def backup_validate():
    click_tab('settings')
    with page.expect_download(timeout=8000) as d:
      page.locator('#exportBackup').click()
    download=d.value;dest=OUT/'qa-synthetic-backup.json';download.save_as(str(dest))
    data=json.loads(dest.read_text(encoding='utf8'))
    assert len(data['tasks'])==1 and len(data['debts'])==1
    assert data['cash']['transactions'][0].get('voidedAt')
    assert data['debts'][0]['settlements'][0].get('voidedAt')
    before=page.evaluate('JSON.stringify({legacy:window.__qaCache.data,cash:window.__qaCache["cash-v1"]})')
    page.locator('#importBackup').set_input_files(str(dest));page.wait_for_timeout(200)
    after=page.evaluate('JSON.stringify({legacy:window.__qaCache.data,cash:window.__qaCache["cash-v1"]})')
    assert before==after,'Backup round-trip changed synthetic records'
    return {'backupBytes':dest.stat().st_size,'debts':len(data['debts']),'auditTransactions':len(data['cash']['transactions'])}
  test('DATA-13: JSON backup and restore preserve financial links',backup_validate)
  page.screenshot(path=str(OUT/'qa-final-390.png'),full_page=False)
  browser.close()
report={'date':datetime.datetime.now(datetime.timezone.utc).isoformat(),'sourceVersion':'0.7.6','testTool':'Playwright + Chromium inline assets + synthetic IndexedDB mock','scope':'browser user interface and mocked persistence, NOT Android or native IndexedDB','tests':results,'pageErrors':errors,'pass':sum(x['result']=='PASS' for x in results),'fail':sum(x['result']=='FAIL' for x in results)}
(OUT/'ui-results.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf8')
print('SUMMARY',report['pass'],'passed',report['fail'],'failed');sys.exit(0 if report['fail']==0 else 1)