import json,pathlib
from playwright.sync_api import sync_playwright
root=pathlib.Path(__file__).resolve().parents[1]
with sync_playwright() as p:
    browser=p.chromium.launch(headless=True)
    page=browser.new_page(viewport={'width':760,'height':1200},device_scale_factor=1)
    errors=[]
    page.on('pageerror',lambda e:errors.append(str(e)))
    page.goto((root/'preview-check.html').as_uri())
    frame=page.frame_locator('iframe')
    frame.locator('#q-new-phone').wait_for()
    page.wait_for_timeout(1200)
    frame.locator('[data-new="gallery"]').click()
    frame.locator('[data-new="use-image"]').click()
    assert frame.locator('#q-attachment').is_visible()
    frame.locator('#q-new-input').fill('请解释这张图。')
    frame.locator('[data-new="send"]').click()
    frame.locator('[data-new="retry"]').wait_for()
    assert '示例回答' in frame.locator('#q-new-main').inner_text()
    frame.locator('[data-new="new"]').first.click()
    frame.locator('[data-new="history"]').click()
    assert '请解释这张图。' in frame.locator('#q-new-overlay').inner_text()
    frame.locator('[data-new="settings"]').click()
    assert frame.locator('select[aria-label="回答长度"]').is_visible()
    frame.locator('[data-new="close"]').click()
    frame.locator('#q-new-phone').screenshot(path=str(root/'build/new-ui-preview.png'))
    result=[]
    for width in [760,390,320]:
        page.set_viewport_size({'width':width,'height':1300})
        page.wait_for_timeout(200)
        result.append(frame.locator('#qwen-vision-preview').evaluate('(el)=>({width:innerWidth,scrollWidth:document.documentElement.scrollWidth,phoneWidth:el.querySelector("#q-new-phone").getBoundingClientRect().width})'))
    page.set_viewport_size({'width':760,'height':1200})
    frames=[f for f in page.frames if f!=page.main_frame]
    frames[0].evaluate('document.querySelectorAll("[data-variant]").forEach((el,i)=>el.hidden=i===0)')
    frame.locator('#q-old-phone').screenshot(path=str(root/'build/current-ui-preview.png'))
    assert not errors,errors
    assert all(x['scrollWidth']<=x['width']+1 for x in result),result
    (root/'build/preview-validation.json').write_text(json.dumps({'javascript_errors':errors,'widths':result,'flows':['choose image','send demo','preserve previous conversation','open settings']},indent=2),encoding='utf-8')
    print(json.dumps(result))
    browser.close()
