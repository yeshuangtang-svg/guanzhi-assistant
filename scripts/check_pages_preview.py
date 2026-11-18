import json,pathlib
from playwright.sync_api import sync_playwright
root=pathlib.Path(__file__).resolve().parents[1]
with sync_playwright() as p:
    browser=p.chromium.launch(headless=True)
    page=browser.new_page(viewport={'width':760,'height':1400},device_scale_factor=1)
    errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
    page.goto((root/'build/pages-preview.html').as_uri())
    frame=page.frame_locator('iframe');frame.locator('#gp-title').wait_for()
    frame.locator('[data-root="tools"]').click()
    assert frame.locator('#gp-title').inner_text()=='工具与资料'
    assert frame.locator('#gp-content').get_by_text('备份与导入',exact=True).count()==0
    frame.locator('[data-go="library"]').click()
    frame.locator('input[data-setting="library"]').check()
    frame.locator('[data-back]').click()
    assert frame.locator('#gp-title').inner_text()=='工具与资料'
    frame.locator('.gp-phone').screenshot(path=str(root/'build/tools-page-preview.png'))
    frame.locator('[data-root="model"]').click()
    assert frame.locator('#gp-content').get_by_text('图片工作台',exact=True).count()==0
    frame.locator('[data-go="length"]').click();frame.locator('[data-go="length4096"]').click();frame.locator('[data-back]').click()
    assert '4096 tokens' in frame.locator('[data-go="length"]').inner_text()
    frame.locator('[data-go="speed"]').click();frame.locator('[data-go="setCPU"]').click();frame.locator('[data-back]').click()
    assert 'CPU' in frame.locator('[data-go="speed"]').inner_text()
    frame.locator('.gp-phone').screenshot(path=str(root/'build/model-page-preview.png'))
    frame.locator('[data-go="search"]').click()
    assert '本地模型负责两次推理' in frame.locator('#gp-content').inner_text()
    frame.locator('[data-back]').click();frame.locator('[data-go="about"]').click()
    assert '以微调后的 Qwen3.5-0.8B 为基模' in frame.locator('#gp-content').inner_text()
    frame.locator('.gp-phone').screenshot(path=str(root/'build/about-page-preview.png'))
    layouts=[]
    for width in [760,390,320]:
        page.set_viewport_size({'width':width,'height':1400})
        for screen in ['tools','model']:
            frame.locator('[data-root="'+screen+'"]').click()
            result=frame.locator('#guanzhi-pages-preview').evaluate('(el)=>({width:innerWidth,scrollWidth:document.documentElement.scrollWidth,overflow:[...el.querySelectorAll("button, .gp-copy, .gp-value")].filter(e=>e.scrollWidth>e.clientWidth+1).map(e=>e.textContent)})')
            assert result['scrollWidth']<=result['width']+1,result
            assert not result['overflow'],result
            layouts.append({'screen':screen,**result})
    assert not errors,errors
    (root/'build/pages-preview-validation.json').write_text(json.dumps({'layouts':layouts,'script_errors':errors,'interactive_back_and_settings':True,'android_device_test':False},ensure_ascii=False,indent=2),encoding='utf-8')
    print('PASS: navigation, sections, model description, search flow, and 320/390/760px preview layouts')
    browser.close()
