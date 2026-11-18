package cn.shiguang.qwenpocket;

import android.content.*;
import android.net.Uri;
import android.graphics.Color;
import android.webkit.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;

/** Renders only packaged scripts/fonts. Model text never becomes executable JavaScript. */
final class RichAnswerView extends LinearLayout {
    static final String BASE="https://appassets.androidplatform.net/render/";
    WebView web; final TextView fallback; boolean disposed=false,ready=false,rendering=false,queued=false; int height=-1;
    String latest="",rendered=null;Runnable heightChanged;
    final Runnable flush=this::flushRender;
    RichAnswerView(Context context,String source){
        super(context);setOrientation(VERTICAL);
        latest=source;fallback=new TextView(context);fallback.setText(source);fallback.setTextSize(16);fallback.setTextColor(MainActivity.INK);fallback.setTextIsSelectable(true);addView(fallback);
        try{
            web=new WebView(context);web.setBackgroundColor(Color.TRANSPARENT);web.setVisibility(INVISIBLE);
            WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
            settings.setDomStorageEnabled(false);settings.setDatabaseEnabled(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
            web.setVerticalScrollBarEnabled(false);web.setHorizontalScrollBarEnabled(false);web.setOverScrollMode(OVER_SCROLL_NEVER);
            web.addJavascriptInterface(new Object(){@JavascriptInterface public void resize(int cssPixels){
                post(()->{if(disposed||cssPixels<=0)return;int pixels=(int)Math.ceil(cssPixels*getResources().getDisplayMetrics().density);pixels=Math.min(pixels,200000);boolean changed=height!=pixels;if(changed){height=pixels;web.setLayoutParams(new LinearLayout.LayoutParams(-1,pixels));}fallback.setVisibility(GONE);web.setVisibility(VISIBLE);if(changed&&heightChanged!=null)post(heightChanged);});
            }},"ReaderSize");
            web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request){
                    Uri u=request.getUrl();String path=u.getPath();
                    if("https".equals(u.getScheme())&&"appassets.androidplatform.net".equals(u.getHost())&&path!=null&&path.startsWith("/render/")&&!path.contains("..")){
                        try{String mime=path.endsWith(".js")?"text/javascript":path.endsWith(".css")?"text/css":path.endsWith(".woff2")?"font/woff2":"text/html";return new WebResourceResponse(mime,"UTF-8",context.getAssets().open(path.substring(1)));}catch(IOException ignored){}
                    }
                    return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));
                }
                @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){
                    return true;
                }
                @Override public void onPageFinished(WebView view,String url){if(!disposed&&url.equals(BASE+"index.html")){ready=true;flushRender();}}
                @Override public boolean onRenderProcessGone(WebView view,RenderProcessGoneDetail detail){fallback.setVisibility(VISIBLE);dispose();return true;}
            });
            addView(web,new LinearLayout.LayoutParams(-1,1));web.loadUrl(BASE+"index.html");
        }catch(Throwable e){dispose();fallback.setVisibility(VISIBLE);}
    }
    void setSource(String source){
        latest=source==null?"":source;
        if(disposed||web==null){fallback.setText(latest);fallback.setVisibility(VISIBLE);return;}
        if(!ready){fallback.setText(latest);return;}
        if(!queued){queued=true;postDelayed(flush,140);}
    }
    void flushRender(){
        queued=false;if(disposed||!ready||rendering||latest.equals(rendered))return;
        final String snapshot=latest;rendering=true;fallback.setText(snapshot);
        try{web.evaluateJavascript("window.renderAnswer("+JSONObject.quote(snapshot).replace("\u2028","\\u2028").replace("\u2029","\\u2029")+")",ignored->{
            rendering=false;if(disposed)return;rendered=snapshot;if(!latest.equals(snapshot)&&!queued){queued=true;postDelayed(flush,140);}
        });}catch(Throwable e){rendering=false;dispose();fallback.setVisibility(VISIBLE);fallback.setText(latest);}
    }
    void dispose(){if(disposed)return;disposed=true;removeCallbacks(flush);heightChanged=null;if(web!=null){removeView(web);web.removeJavascriptInterface("ReaderSize");web.stopLoading();web.destroy();}}
    static void disposeTree(View view){if(view instanceof RichAnswerView){((RichAnswerView)view).dispose();return;}if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)disposeTree(g.getChildAt(i));}}
}
