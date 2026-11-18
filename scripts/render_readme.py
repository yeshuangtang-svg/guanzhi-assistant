"""Render README locally and generate its documentation illustrations."""
from pathlib import Path
import json
import markdown
from PIL import Image, ImageDraw, ImageFont
from playwright.sync_api import sync_playwright

root=Path(__file__).resolve().parents[1]
assets=root/'docs/images';assets.mkdir(parents=True,exist_ok=True)
build=root/'build';build.mkdir(exist_ok=True)
font='Microsoft YaHei,system-ui,sans-serif'
hero=f'''<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="410" viewBox="0 0 1200 410">
<defs><linearGradient id="bg" x2="1" y2="1"><stop stop-color="#123e35"/><stop offset="1" stop-color="#286b57"/></linearGradient></defs>
<rect width="1200" height="410" rx="28" fill="url(#bg)"/>
<g font-family="{font}"><text x="60" y="64" fill="#a9d9c7" font-size="16" letter-spacing="3">GUANZHI ASSISTANT</text>
<text x="56" y="147" fill="white" font-size="58" font-weight="700">观知助手</text>
<text x="60" y="203" fill="#e0eee7" font-size="25">让手机里的模型，看图、读文、推理。</text>
<text x="60" y="244" fill="#bcd9cc" font-size="18">微调 Qwen3.5-0.8B · 量化部署 · Android</text>
<g fill="#e3f1e9"><rect x="60" y="296" width="164" height="42" rx="21"/><rect x="237" y="296" width="164" height="42" rx="21"/><rect x="414" y="296" width="164" height="42" rx="21"/></g>
<g fill="#1c5142" font-size="17" text-anchor="middle"><text x="142" y="323">CPU 本地推理</text><text x="319" y="323">图文连续问答</text><text x="496" y="323">按需网页检索</text></g>
<rect x="805" y="63" width="310" height="284" rx="26" fill="#f1f7f3"/>
<rect x="836" y="90" width="72" height="72" rx="20" fill="#19594e"/>
<g transform="translate(836,90) scale(.667)"><path d="M30 35 Q42 31 53 39 Q64 31 77 35 L77 69 Q64 65 53 74 Q42 65 30 69 Z M53 39 L53 74" fill="none" stroke="white" stroke-width="4"/><circle cx="68" cy="37" r="10" fill="#c9f2df"/><path d="M64 37 L67 40 L73 33" fill="none" stroke="#19594e" stroke-width="2.6"/></g>
<text x="929" y="121" fill="#254c3c" font-size="24" font-weight="600">1.5.2</text><text x="929" y="151" fill="#658371" font-size="16">CPU 量化版</text>
<path d="M839 187 H1080" stroke="#cfdfd5"/><text x="837" y="224" fill="#315542" font-size="20">文字 × 图片</text>
<text x="837" y="263" fill="#315542" font-size="20">Markdown × 公式</text>
<text x="837" y="312" fill="#698477" font-size="14">源码与文档 · 本仓库不含模型权重</text></g></svg>'''
flow=f'''<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="410" viewBox="0 0 1200 410">
<defs><marker id="arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto"><path d="M0 0 L8 4 L0 8" fill="#7a9e8b"/></marker></defs>
<rect width="1200" height="410" rx="24" fill="#f2f7f4"/>
<g font-family="{font}"><text x="44" y="49" font-size="24" fill="#214c3b" font-weight="600">模型负责理解与生成，应用负责资料获取与展示</text>
<text x="44" y="91" font-size="16" fill="#607c6e">基础问答 · 在手机本地完成</text>
<g fill="white" stroke="#d8e6dc"><rect x="44" y="111" width="310" height="70" rx="16"/><rect x="445" y="111" width="310" height="70" rx="16"/><rect x="846" y="111" width="310" height="70" rx="16"/></g>
<g font-size="22" fill="#254c3c" text-anchor="middle"><text x="199" y="154">输入文字 / 图片</text><text x="600" y="154">CPU · 量化多模态模型</text><text x="1001" y="154">流式回答与公式渲染</text></g>
<g stroke="#7a9e8b" stroke-width="2" marker-end="url(#arrow)"><path d="M369 146 H429"/><path d="M770 146 H830"/></g>
<text x="44" y="236" font-size="16" fill="#607c6e">按需联网 · 用户打开“联网”时</text>
<g fill="#e0eee5"><rect x="44" y="256" width="310" height="70" rx="16"/><rect x="445" y="256" width="310" height="70" rx="16"/><rect x="846" y="256" width="310" height="70" rx="16"/></g>
<g font-size="21" fill="#254c3c" text-anchor="middle"><text x="199" y="299">本地模型提炼检索词</text><text x="600" y="299">应用获取公开网页</text><text x="1001" y="299">本地模型结合资料总结</text></g>
<g stroke="#7a9e8b" stroke-width="2" marker-end="url(#arrow)"><path d="M369 291 H429"/><path d="M770 291 H830"/></g>
<text x="44" y="374" font-size="16" fill="#607c6e">最多 3 条网页来源 · 展示来源链接 · 不依赖云端模型 API</text></g></svg>'''
(assets/'overview.svg').write_text(hero,encoding='utf8')
(assets/'workflow.svg').write_text(flow,encoding='utf8')

with sync_playwright() as p:
    browser=p.chromium.launch(headless=True)
    page=browser.new_page(viewport={'width':1200,'height':410},device_scale_factor=1)
    for name in ['overview','workflow']:
        page.goto((assets/(name+'.svg')).as_uri());page.locator('svg').screenshot(path=str(assets/(name+'.png')))
    page.set_viewport_size({'width':390,'height':1200})
    errors=[];page.on('pageerror',lambda e:errors.append(str(e)))
    page.goto((root/'docs/previews/mobile.html').as_uri())
    page.add_style_tag(content='.gz-phone{min-height:790px;display:flex;flex-direction:column}.gz-main{flex:1}')
    shots=[]
    for name in ['home','examples','history']:
        page.locator('[aria-label="新建对话"]').click()
        if name=='examples':page.locator('[data-task="数值计算"]').first.click()
        if name=='history':page.locator('[aria-label="打开对话记录"]').click()
        target=build/f'readme-{name}.png';page.locator('.gz-phone').screenshot(path=str(target));shots.append(target)
    assert not errors,errors
    height=max(Image.open(p).height for p in shots)
    sheet=Image.new('RGB',(1260,height+142),'#edf3ef');draw=ImageDraw.Draw(sheet)
    title=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',25);small=ImageFont.truetype('C:/Windows/Fonts/msyh.ttc',16)
    for i,(path,label) in enumerate(zip(shots,['主页 · 从问题开始','实例 · 一键带入问题','历史 · 查找和继续对话'])):
        draw.text((25+i*415,24),label,font=title,fill='#294b3b');sheet.paste(Image.open(path).convert('RGB'),(20+i*415,73))
    draw.text((25,height+106),'交互设计预览 / 演示记录，非手机实机截图；具体界面以 APK 为准。',font=small,fill='#607a6b')
    sheet.save(assets/'interface-preview.png',optimize=True)
    renderer=build/'streaming-markdown-preview.png'
    if renderer.exists():
        image=Image.open(renderer).convert('RGB');image.save(assets/'markdown-math-preview.png',optimize=True)

    css='''*{box-sizing:border-box}body{margin:0;background:#f6f8fa;color:#1f2328;font:16px/1.7 -apple-system,BlinkMacSystemFont,"Segoe UI","Microsoft YaHei",sans-serif}header{background:#fff;border-bottom:1px solid #d1d9e0;padding:18px 32px;color:#59636e;font-size:14px}article{max-width:1020px;margin:26px auto;background:white;border:1px solid #d1d9e0;border-radius:10px;padding:38px 48px}h1,h2,h3{line-height:1.35;color:#183f31}h1{font-size:32px;border-bottom:1px solid #d8dee4;padding-bottom:12px}h2{font-size:24px;border-bottom:1px solid #d8dee4;padding-bottom:8px;margin-top:36px}h3{font-size:19px;margin-top:25px}a{color:#0969da;text-decoration:none}p{margin:14px 0}img{display:block;max-width:100%;height:auto;margin:22px auto;border-radius:8px}table{border-collapse:collapse;width:100%;font-size:14px;table-layout:fixed}th,td{border:1px solid #d1d9e0;padding:8px 12px;text-align:left;overflow-wrap:anywhere}th{background:#eef5f1}tbody tr:nth-child(even){background:#f8faf9}code{font:13px/1.65 Consolas,monospace;background:#eff1f3;padding:2px 4px;border-radius:4px;overflow-wrap:anywhere}pre{background:#f6f8fa;padding:18px;border-radius:6px;overflow:auto}pre code{padding:0;background:none}blockquote{margin-left:0;padding-left:18px;border-left:4px solid #d0ddd5;color:#66756c}li{margin:5px 0}@media(max-width:650px){header{padding:12px}article{margin:0;border:0;border-radius:0;padding:20px}h1{font-size:26px}h2{font-size:21px}th,td{padding:6px;font-size:12px}}'''
    rendered=markdown.markdown((root/'README.md').read_text(encoding='utf8'),extensions=['tables','fenced_code','toc'])
    html='<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><base href="'+root.as_uri()+'/"><title>观知助手 README 本地预览</title><style>'+css+'</style><body><header>yeshuangtang-svg / guanzhi-assistant　·　README 本地排版预览</header><article>'+rendered+'</article></body></html>'
    preview=build/'readme-preview.html';preview.write_text(html,encoding='utf8')
    page.set_viewport_size({'width':1440,'height':1100});page.goto(preview.as_uri());page.evaluate('document.fonts.ready')
    assert page.locator('img').count()>=3
    assert page.locator('img').evaluate_all('(xs)=>xs.every(x=>x.complete&&x.naturalWidth>0)')
    page.screenshot(path=str(build/'readme-preview-desktop.png'),full_page=True)
    page.screenshot(path=str(build/'readme-preview-top.png'))
    page.set_viewport_size({'width':390,'height':1100})
    assert page.evaluate('document.documentElement.scrollWidth<=innerWidth'), 'Mobile overflow'
    page.screenshot(path=str(build/'readme-preview-mobile.png'))
    browser.close()
(build/'readme-preview-validation.json').write_text(json.dumps({'images_loaded':True,'desktop_width':1440,'mobile_width':390,'mobile_no_horizontal_overflow':True,'interface_is_mockup':True,'page_errors':errors},indent=2),encoding='utf8')
print('PASS: illustrations, README image links, desktop/mobile layouts; screenshots saved to build/')
