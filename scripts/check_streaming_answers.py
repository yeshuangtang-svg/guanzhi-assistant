import pathlib,json,mimetypes
from playwright.sync_api import sync_playwright
root=pathlib.Path(__file__).resolve().parents[1];assets=root/'app/src/main/assets/render'
source=r'''## 流式排版
这是 **逐步生成的回答**，包括列表和公式：

- 第一项：$x^2$。
- 第二项：$y=2x+1$。

$$x_{1,2}=\frac{-b\pm\sqrt{b^2-4ac}}{2a}$$

| 项目 | 数值 |
| --- | --- |
| 小计 | 40.00 |
| 优惠 | 5.00 |

```python
print("hello")
# $x$ 保持为代码
```
结果为 **35.00 元**。
'''
with sync_playwright() as p:
    browser=p.chromium.launch(headless=True);page=browser.new_page(viewport={'width':350,'height':1000});errors=[];external=[]
    page.on('pageerror',lambda e:errors.append(str(e)))
    def route(r):
        prefix='https://appassets.androidplatform.net/render/'
        if r.request.url.startswith(prefix):
            path=assets/r.request.url[len(prefix):]
            if path.is_file():r.fulfill(path=str(path),content_type=mimetypes.guess_type(path.name)[0] or 'application/octet-stream');return
        external.append(r.request.url);r.abort()
    page.route('**/*',route);page.goto('https://appassets.androidplatform.net/render/index.html')
    appeared=False;updates=0
    for i in range(1,len(source)+1,3):
        page.evaluate('s=>renderAnswer(s)',source[:i]);updates+=1
        if i<len(source)-50 and page.locator('.katex').count():appeared=True
        assert page.locator('#answer script,#answer iframe,#answer img').count()==0
    page.evaluate('s=>renderAnswer(s)',source);page.evaluate('document.fonts.ready')
    assert appeared,'Math must render before generation ends'
    assert page.locator('.katex').count()==3 and page.locator('table').count()==1 and page.locator('pre').count()==1
    assert '$x$' in page.locator('pre').inner_text()
    expected=page.locator('#answer').inner_html();page.evaluate('()=>renderAnswer("")');page.evaluate('s=>renderAnswer(s)',source);assert expected==page.locator('#answer').inner_html()
    for width in [280,350,430]:
        page.set_viewport_size({'width':width,'height':1000});assert page.evaluate('document.documentElement.scrollWidth<=innerWidth')
    for attack in ['<script>window.pwned=1</script>','[x](javascript:alert(1))','<img src="https://evil.invalid/x" onerror="window.pwned=1">']:
        for i in range(1,len(attack)+1):page.evaluate('s=>renderAnswer(s)',attack[:i])
    assert page.evaluate('window.pwned||0')==0 and not external and not errors
    page.evaluate('s=>renderAnswer(s)',source);page.set_viewport_size({'width':350,'height':1000});page.locator('#answer').screenshot(path=str(root/'build/streaming-markdown-preview.png'))
    report={'updates':updates,'math_visible_before_completion':True,'final_matches_one_shot':True,'external_requests':external,'errors':errors,'android_webview_tested':False};(root/'build/streaming-answer-validation.json').write_text(json.dumps(report,indent=2),'utf-8');print(json.dumps(report));browser.close()
