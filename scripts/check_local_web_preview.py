import json,pathlib,subprocess
from playwright.sync_api import sync_playwright
root=pathlib.Path(__file__).resolve().parents[1]
(root/'build').mkdir(exist_ok=True)
errors=[];layouts=[]
with sync_playwright() as p:
    browser=p.chromium.launch(headless=True)
    page=browser.new_page(viewport={'width':760,'height':1100})
    page.on('pageerror',lambda e:errors.append(str(e)))
    page.goto((root/'docs/previews/mobile.html').as_uri());f=page;f.locator('#gz-title').wait_for()
    for width in [760,390,320]:
        page.set_viewport_size({'width':width,'height':1200});f.locator('[aria-label="新建对话"]').click()
        f.locator('.gz-phone').screenshot(path=str(root/f'build/local-home-{width}.png'))
        for screen in ['home','history','chat']:
            if screen=='history':
                f.locator('[aria-label="打开对话记录"]').click();f.locator('[data-filter="全部"]').click()
                f.locator('#gz-query').fill('账单');assert f.locator('.gz-history').count()==1;f.locator('#gz-query').fill('')
                f.locator('.gz-phone').screenshot(path=str(root/f'build/local-history-{width}.png'))
                f.locator('[data-filter="回收站"]').click();assert '回收站为空' in f.locator('#gz-records').inner_text()
            if screen=='chat':
                f.locator('[aria-label="新建对话"]').click();f.locator('[data-task="数值计算"]').click();assert f.locator('.gz-example').count()==3
                f.locator('[data-example="0"]').click();assert '12.50' in f.locator('#gz-input').input_value()
                if f.locator('#gz-web').get_attribute('aria-pressed')=='false':f.locator('#gz-web').click()
                f.locator('#gz-send').click();assert '获取公开网页' in f.locator('.gz-answer').inner_text()
                f.locator('.gz-phone').screenshot(path=str(root/f'build/local-chat-{width}.png'))
            sizes=f.locator('#guanzhi-local-web').evaluate('(e)=>({width:innerWidth,scroll:document.documentElement.scrollWidth})')
            assert sizes['scroll']<=sizes['width']+1,sizes;layouts.append(dict(screen=screen,**sizes))
    assert not errors,errors
    (root/'build/local-web-preview-validation.json').write_text(json.dumps({'layouts':layouts,'errors':errors,'navigation_examples_search_toggle':True,'actual_android':False},ensure_ascii=False,indent=2),'utf-8')
    browser.close()
print('PASS: home/history/chat at 320/390/760 px, examples, search, web toggle')
