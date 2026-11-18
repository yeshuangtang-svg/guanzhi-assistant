/* Offline, untrusted Markdown: no raw HTML, no network, bounded KaTeX macros. */
(() => {
  'use strict';
  const esc = s => String(s).replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  function math(tex, display) {
    try { return katex.renderToString(tex, {displayMode:display, throwOnError:true, trust:false, strict:'ignore', maxExpand:500, maxSize:20, output:'htmlAndMathml'}); }
    catch (_) { return '<code class="math-error" title="此公式未能排版，保留原文">'+esc(tex)+'</code>'; }
  }
  function matchMath(src) {
    const pairs = [['$$','$$',true],['\\[','\\]',true],['\\(','\\)',false],['$','$',false]];
    for (const [open, close, display] of pairs) {
      if (!src.startsWith(open)) continue;
      if(open==='$' && (/\s/.test(src[1]||'') || src[1]==='$')) return;
      let end=open.length;
      while ((end=src.indexOf(close,end))>=0) {
        let slashes=0; for(let i=end-1;i>=0&&src[i]==='\\';i--)slashes++;
        if(slashes%2){end+=close.length;continue;}
        const body=src.slice(open.length,end);
        if(!body.trim() || (!display && body.includes('\n')))return;
        if(open==='$' && (/\s$/.test(body)||/^\d/.test(src.slice(end+1))))return;
        return {raw:src.slice(0,end+close.length),text:body,display};
      }
    }
    const env=/^\\begin\{(align\*?|aligned|equation\*?|gather\*?|cases|pmatrix|bmatrix|matrix)\}/.exec(src);
    if(env){const close='\\end{'+env[1]+'}',end=src.indexOf(close,env[0].length);if(end>=0)return {raw:src.slice(0,end+close.length),text:src.slice(0,end+close.length),display:true};}
  }
  const extension = level => ({name:'math'+level,level,
    start:src=>{const i=src.search(level==='block'?/(?:^|\n)[ \t]*(?:\$\$|\\\[|\\begin\{)/:/\$|\\\(|\\\[|\\begin\{/);return i<0?undefined:i;},
    tokenizer(src){const m=matchMath(src);if(m&&(level==='inline'||m.display))return {type:'math'+level,...m};},
    renderer(token){const value=math(token.text,token.display);return token.display?'<div class="math-block">'+value+'</div>':value;}
  });
  marked.use({gfm:true,breaks:true,extensions:[extension('block'),extension('inline')],renderer:{
    html(token){return esc(token.text);},
    image(token){return esc(token.text||'[图片]');}
  }});
  window.renderAnswer = source => {
    const root=document.getElementById('answer');
    try {
      const html=marked.parse(String(source).slice(0,100000));
      root.innerHTML=DOMPurify.sanitize(html,{USE_PROFILES:{html:true,svg:true,mathMl:true},FORBID_TAGS:['img','iframe','style','script','form','input','button','video','audio','object','embed'],FORBID_ATTR:['src','srcset']});
      root.querySelectorAll('a').forEach(a=>{if(!/^https?:\/\//i.test(a.getAttribute('href')||''))a.removeAttribute('href');a.rel='noopener noreferrer';});
      root.querySelectorAll('table').forEach(t=>{const w=document.createElement('div');w.className='table-wrap';t.replaceWith(w);w.append(t);});
    } catch (_) { root.textContent=String(source); }
    const report=()=>{if(window.ReaderSize)window.ReaderSize.resize(Math.ceil(root.getBoundingClientRect().height));};
    report();document.fonts.ready.then(report);requestAnimationFrame(report);
  };
  new ResizeObserver(()=>{if(window.ReaderSize)window.ReaderSize.resize(Math.ceil(document.getElementById('answer').getBoundingClientRect().height));}).observe(document.getElementById('answer'));
})();
