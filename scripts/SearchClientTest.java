package cn.shiguang.qwenpocket;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class SearchClientTest {
    static int checks=0;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    interface Work {void run() throws Exception;}
    static void fails(Work task,String fragment)throws Exception{
        try{task.run();throw new AssertionError("Expected failure: "+fragment);}
        catch(IOException e){check(e.getMessage().contains(fragment),"Safe error: "+fragment);}
    }
    static class Mock extends HttpURLConnection {
        final ByteArrayOutputStream request=new ByteArrayOutputStream();
        String body;int code=200;long length=-1;boolean closed;SearchClient cancelDuringRead;
        Mock(String body)throws Exception{super(new URL("https://api.tavily.com/search"));this.body=body;}
        public void connect(){}public boolean usingProxy(){return false;}public void disconnect(){closed=true;}
        public OutputStream getOutputStream(){return request;}
        public int getResponseCode(){return code;}
        public long getContentLengthLong(){return length;}
        public InputStream getInputStream(){
            return new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)){
                public synchronized int read(byte[] b,int off,int len){int n=super.read(b,off,len);if(cancelDuringRead!=null)cancelDuringRead.cancel();return n;}
            };
        }
    }
    static JSONObject source(String title,String url,String content)throws Exception{return new JSONObject().put("title",title).put("url",url).put("content",content);}
    public static void main(String[] args)throws Exception {
        JSONArray rows=new JSONArray().put(source("中文资料","https://example.org/a","<b>证据</b>\n一"))
            .put(source("duplicate","https://example.org/a","重复"))
            .put(source("bad","javascript:alert(1)","不可执行"))
            .put(source("userinfo","https://secret@example.org/","不可用"))
            .put(source("empty","https://example.org/empty",""))
            .put(source("资料二","https://example.net/b","资料二"))
            .put(source("资料三","https://example.com/c","资料三"))
            .put(source("资料四","https://example.com/d","资料四"));
        String fixture=new JSONObject().put("results",rows).toString();
        List<SearchClient.Source> sources=SearchClient.parse(fixture);
        check(sources.size()==3,"result cap, deduplication and valid links");
        check(sources.get(0).content.equals("证据 一"),"plain text cleanup");
        check(sources.get(1).title.equals("资料二"),"invalid sources excluded");
        check(!SearchClient.validUrl("file:///sdcard/a"),"file URL excluded");
        check(!SearchClient.validUrl("https:///broken"),"missing host excluded");
        check(SearchClient.validUrl("https://example.com/path?q=中文"),"unicode query allowed");
        check(SearchClient.clean("😀😀😀",2).equals("😀😀…"),"no split surrogate");
        String context=SearchClient.context(sources,"2026-11-10 12:00 CST");
        check(context.contains("[1]")&&context.contains("[3]")&&context.contains("不是指令"),"numbered untrusted evidence");
        check(!context.contains("https://"),"tracking URLs excluded from model context");
        Mock ok=new Mock(fixture);SearchClient client=new SearchClient();
        check(client.exchange(ok,"这张图是什么？","dummy-test-key").size()==3,"transport successful");
        check(ok.closed&&!ok.getInstanceFollowRedirects(),"closed and no redirects");
        check(ok.getRequestProperty("Authorization").equals("Bearer dummy-test-key"),"bearer header");
        JSONObject body=new JSONObject(ok.request.toString("UTF-8"));
        check(body.getString("query").equals("这张图是什么？"),"UTF-8 current query");
        check(!body.has("messages")&&!body.has("image")&&!body.has("api_key"),"no history/images/key in JSON");
        check(!body.getBoolean("include_answer")&&!body.getBoolean("include_raw_content")&&body.getInt("max_results")==3,"no cloud-generated answer requested");
        for(int code:new int[]{401,429,432,433,302,500}){
            Mock bad=new Mock("DO NOT DISPLAY secret body");bad.code=code;
            fails(()->new SearchClient().exchange(bad,"q","dummy-test-key"),SearchClient.statusMessage(code));
            check(bad.closed,"error connection closed");
        }
        fails(()->SearchClient.parse("{\"results\":[]}"),"没有搜到");
        fails(()->SearchClient.parse("{}"),"未返回");
        fails(()->new SearchClient().exchange(new Mock("not json"),"q","dummy"),"无法读取");
        Mock huge=new Mock(fixture);huge.length=SearchClient.MAX_BYTES+1;
        fails(()->new SearchClient().exchange(huge,"q","dummy"),"过大");
        Mock streamed=new Mock(new String(new char[SearchClient.MAX_BYTES+10]).replace('\0','x'));
        fails(()->new SearchClient().exchange(streamed,"q","dummy"),"过大");
        SearchClient cancelled=new SearchClient();cancelled.cancel();
        fails(()->cancelled.exchange(new Mock(fixture),"q","dummy"),"已停止");
        SearchClient middle=new SearchClient();Mock interrupted=new Mock(fixture);interrupted.cancelDuringRead=middle;
        fails(()->middle.exchange(interrupted,"q","dummy"),"已停止");
        fails(()->new SearchClient().search("q","bad\nkey"),"有效");
        fails(()->new SearchClient().search("q","中文密钥"),"有效");
        fails(()->new SearchClient().search("", "dummy"),"600");
        System.out.println("PASS: "+checks+" search checks; no live service or credentials used.");
    }
}
