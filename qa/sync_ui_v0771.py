"""Regression: cash dashboard changes, task list and stale budget matrix on Chromium."""
from pathlib import Path
import http.server,threading,socketserver,os
from playwright.sync_api import sync_playwright
root=Path(__file__).resolve().parents[1]/'app/src/main/assets'
class Handler(http.server.SimpleHTTPRequestHandler):
 def log_message(self,*args): pass
class Server(socketserver.TCPServer):allow_reuse_address=True
os.chdir(root)
server=Server(('127.0.0.1',0),Handler)
threading.Thread(target=server.serve_forever,daemon=True).start()
errors=[]
try:
 with sync_playwright() as pw:
  browser=pw.chromium.launch(headless=True,args=['--no-sandbox'])
  page=browser.new_page(viewport={'width':390,'height':844},locale='fa-IR')
  page.add_init_script("""window.HamyarAndroid={ready(){},authStatus(){return '{"enabled":false}'},syncReminders(){}};""")
  page.on('pageerror',lambda e:errors.append(str(e)))
  page.on('dialog',lambda d:d.accept('حذف آزمایشی'))
  page.goto(f'http://127.0.0.1:{server.server_address[1]}/index.html')
  page.wait_for_function("window.HaselCashUI&&window.HaselAppExcelAccess&&window.HaselDataSync&&window.HaselActiveTab()==='home'",timeout=15000)
  label='بودجه همگام‌سازی آزمایشی'
  page.evaluate("""async label=>{
   const C=HamyarCore,K=HaselCashCore,d=C.today(),m=d.slice(0,7);
   const end=C.dateKey(+m.slice(0,4),+m.slice(5,7),C.monthDays(+m.slice(0,4),+m.slice(5,7)));
   let s=HaselCashUI.snapshot();
   s=K.addVersion(s,{label,year:m.slice(0,4),startDate:m+'/01',endDate:end});
   await HaselCashUI.commitExcel(s);
  }""",label)
  page.wait_for_function("x=>document.querySelector('#homeCashSummary').textContent.includes(x)",arg=label)
  print('PASS: dashboard auto-refreshes on committed cash change')
  page.locator('[data-tab="tasks"]').click()
  page.evaluate("""async()=>{const s=HaselAppExcelAccess.snapshot(),today=HamyarCore.today();s.tasks.unshift({
   id:'qa-task-1',title:'فعالیت آزمایشی همگام‌سازی',date:today,done:false,
   createdAt:new Date().toISOString(),completedAt:'',reminderAt:0,photo:''
  });await HaselAppExcelAccess.commit(s)}""")
  page.wait_for_function("document.querySelector('#recordList').textContent.includes('فعالیت آزمایشی همگام‌سازی')")
  print('PASS: activity list auto-refreshes after save')
  page.locator('[data-tab="cash"]').click()
  page.evaluate("document.querySelector('#cashDrawMatrix').closest('details').open=true")
  page.locator('#cashDrawMatrix').click()
  assert page.locator('#cashMatrixBody input.cash-matrix-cell').count()>0
  assert not page.locator('#cashSaveMatrix').is_disabled()
  page.locator('#cashVersionList .cash-record').filter(has_text=label).get_by_role('button',name='حذف').click()
  page.wait_for_function("HaselCashUI.snapshot().budgetVersions.length===0")
  page.wait_for_function("document.querySelector('#cashMatrixBody').childElementCount===0")
  assert page.locator('#cashSaveMatrix').is_disabled()
  assert page.evaluate("HaselCashUI.snapshot().budgetLines.length")==0
  assert label not in page.locator('#homeCashSummary').inner_text()
  print('PASS: budget deletion clears stale matrix and home summary')
  for width in (320,360,390,430):
   page.set_viewport_size({'width':width,'height':844})
   assert page.evaluate('document.documentElement.scrollWidth<=innerWidth+1'),width
  assert not errors,errors
  print('PASS: 4 mobile widths and no Javascript page errors')
  browser.close()
finally:
 server.shutdown();server.server_close()
