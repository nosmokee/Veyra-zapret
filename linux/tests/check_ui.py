from playwright.sync_api import sync_playwright
import pathlib,json,re
import argparse,shutil
args=argparse.ArgumentParser();args.add_argument('url',help='running Veyra loopback URL');args.add_argument('--out',default='ui-test-output');opt=args.parse_args()
root=pathlib.Path(opt.out);root.mkdir(parents=True,exist_ok=True);url=opt.url.rstrip('/')+'/';(root/'screens').mkdir(exist_ok=True)
report=[]
with sync_playwright() as p:
 b=p.chromium.launch(executable_path=shutil.which('chromium') or shutil.which('chromium-browser'),headless=True,args=['--no-sandbox'])
 for js in (False,True):
  for width in (1280,390):
   ctx=b.new_context(viewport={'width':width,'height':900},java_script_enabled=js);page=ctx.new_page();errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
   for tab in ('home','strategies','settings','personal','telegram','about'):
    r=page.goto(url+'?tab='+tab);assert r.status==200
    assert page.locator('nav a').count()==6
    if js:assert page.evaluate('document.documentElement.scrollWidth <= window.innerWidth'),tab
    # evaluate forbidden with javascript disabled? Playwright can still evaluate but page scripts disabled
    overflow=page.evaluate('document.documentElement.scrollWidth > window.innerWidth');assert not overflow,(width,tab)
    assert 'Загрузка…' not in page.locator('body').inner_text()
    if not js:page.screenshot(path=str(root/'screens'/f'{tab}-{width}.png'),full_page=True)
    report.append({'js':js,'width':width,'tab':tab,'status':'pass'})
   page.goto(url);page.select_option('#profile','plain');page.click('#connect');assert 'Локальный прокси запущен' in page.locator('#status').inner_text();page.screenshot(path=str(root/'screens'/f'running-{width}.png'),full_page=True)
   page.click('nav a[href="/?tab=strategies"]');assert page.locator('button:disabled').count()==5
   page.click('nav a[href="/?tab=home"]');page.click('#connect');assert 'Готов к запуску' in page.locator('#status').inner_text()
   page.click('nav a[href="/?tab=personal"]');page.select_option('#theme','light');page.select_option('#color','blood');page.select_option('#background','cosmos');page.click('button:has-text("Сохранить")');assert page.locator('body').get_attribute('data-theme')=='light';page.reload();assert page.locator('#color').input_value()=='blood';page.screenshot(path=str(root/'screens'/f'light-{width}.png'),full_page=True)
   page.select_option('#theme','dark');page.select_option('#color','graphite');page.select_option('#background','none');page.click('button:has-text("Сохранить")');assert not errors,errors
   ctx.close()
 b.close()
(root/'browser-results.json').write_text(json.dumps({'routes':report,'flows':'start/stop, navigation, disabled strategy selection, appearance persistence passed in all four configurations'},indent=2));print('24 route checks + 4 interaction flows passed, zero page errors or horizontal overflow')
