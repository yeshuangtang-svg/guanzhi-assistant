import json,pathlib,mimetypes
from playwright.sync_api import sync_playwright
ROOT=pathlib.Path(__file__).resolve().parents[1]
ASSETS=ROOT/'app/src/main/assets/render'
SAMPLE=r'''## 二次方程的解
已知 $ax^2+bx+c=0$，且 \(a\ne 0\)，判别式为：
\[
\Delta=b^2-4ac
\]
当 $\Delta\ge 0$ 时，两个实数根为
$$x_{1,2}=\frac{-b\pm\sqrt{b^2-4ac}}{2a}.$$

### 积分与矩阵
$$\int_0^1 x^2\,dx=\frac13,\qquad A=\begin{pmatrix}1&2\\3&4\end{pmatrix}$$

| 情况 | 结论 |
| --- | --- |
| $\Delta>0$ | 两个不同实根 |
| $\Delta=0$ | 两个相同实根 |

- **核对条件**：先看 $a\ne0$。
- 公式过宽时，可在公式区域左右滑动。

```python
# 代码中的 $x$ 和 \(x\) 应保持原样
print("hello")
```
'''
with sync_playwright() as p:
    browser=p.chromium.launch(headless=True)
    page=browser.new_page(viewport={'width':350,'height':1100},device_scale_factor=2)
    errors=[];external=[]
    page.on('pageerror',lambda e:errors.append(str(e)))
    def route(req):
        prefix='https://appassets.androidplatform.net/render/'
        if req.request.url.startswith(prefix):
            target=ASSETS/req.request.url[len(prefix):]
            if target.is_file():req.fulfill(path=str(target),content_type=mimetypes.guess_type(target.name)[0] or 'application/octet-stream');return
        external.append(req.request.url);req.abort()
    page.route('**/*',route)
    page.goto('https://appassets.androidplatform.net/render/index.html')
    page.evaluate('source=>renderAnswer(source)',SAMPLE)
    page.evaluate('document.fonts.ready')
    if page.locator('.katex').count()!=9:
        print(page.locator('#answer').inner_text())
        print(page.locator('.katex annotation').all_text_contents())
    assert page.locator('.katex').count()==9,page.locator('.katex').count()
    assert page.locator('.math-error').count()==0
    assert '$x$' in page.locator('pre').inner_text()
    assert page.locator('table').count()==1
    sizes=[]
    for width in [280,350,430]:
        page.set_viewport_size({'width':width,'height':1100})
        sizes.append(page.evaluate('({width:innerWidth,scroll:document.documentElement.scrollWidth})'))
    assert all(x['scroll']<=x['width'] for x in sizes),sizes
    page.set_viewport_size({'width':350,'height':1100})
    page.locator('#answer').screenshot(path=str(ROOT/'build/formula-preview.png'))
    cases=[(r'行内 \(\frac{1}{2}\) 与 $x^2$。',2),(r'$$\sum_{n=1}^{\infty}\frac1{n^2}=\frac{\pi^2}{6}$$',1),(r'`$not math$`',0),(r'价格 $20 和 $30。',0),(r'\[\begin{cases}x&x>0\\-x&x\le0\end{cases}\]',1)]
    for source,count in cases:
        page.evaluate('source=>renderAnswer(source)',source)
        assert page.locator('.katex').count()==count,(source,page.locator('.katex').count())
    page.evaluate('source=>renderAnswer(source)',r'坏公式 $\notACommand{x}$')
    assert page.locator('.math-error').count()==1
    assert r'\notACommand' in page.locator('.math-error').inner_text()
    attack='<script>window.pwned=1</script>\n\n<img src="https://evil.invalid/x" onerror="window.pwned=1">\n\n[bad](javascript:alert(1))\n\n'+r'$\includegraphics{https://evil.invalid/x}$'
    page.evaluate('source=>renderAnswer(source)',attack)
    assert page.evaluate('window.pwned||0')==0
    assert page.locator('#answer script,#answer img,#answer iframe').count()==0
    assert not page.locator('#answer [href^="javascript:"]').count()
    assert not errors,errors
    assert not external,external
    result={'offline':True,'math_examples':9+sum(n for _,n in cases),'code_and_currency_preserved':True,'invalid_tex_preserved':True,'network_requests':external,'javascript_errors':errors,'widths':sizes,'android_device_tested':False}
    (ROOT/'build/rich-answer-validation.json').write_text(json.dumps(result,indent=2),encoding='utf-8')
    print(json.dumps(result))
    browser.close()
