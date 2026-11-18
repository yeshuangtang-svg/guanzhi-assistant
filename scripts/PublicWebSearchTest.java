package cn.shiguang.qwenpocket;
import java.net.*;import java.nio.file.*;import java.nio.charset.StandardCharsets;import java.util.*;import org.json.*;
public final class PublicWebSearchTest {
 static int checks;static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 public static void main(String[] args)throws Exception{
  String xml="<rss><channel><item><title>模型说明</title><link>https://example.com/model</link><description>真实摘要</description></item><item><title>重复</title><link>https://example.com/model</link><description>重复</description></item><item><title>伪链接</title><link>javascript:alert(1)</link><description>假资料</description></item></channel></rss>";
  List<SearchClient.Source> list=SearchClient.parseResults(xml);check(list.size()==1,"Deduplicate and reject nonweb links");check(list.get(0).content.startsWith("仅搜索摘要"),"Do not imply full article read");
  check(SearchClient.parseResults("<html>验证页面</html>").isEmpty(),"Challenge is not search results");
  check(SearchClient.parseResults("<li class=b_algo><div class=b_algoheader><a href='https://example.com'><h2>Mobile title</h2></a></div><p>Summary</p></li>").size()==1,"Mobile result heading wraps anchor");
  String target="https://example.com/a?x=1",encoded=Base64.getUrlEncoder().withoutPadding().encodeToString(target.getBytes(StandardCharsets.UTF_8));
  check(SearchClient.originalUrl("https://www.bing.com/ck/a?u=a1"+encoded).equals(target),"Original result URL");
  check(SearchClient.parseResults("<li class=b_algo><h2><a href='https://example.com'>Title</a></h2><div class=b_caption><p>Summary</p></div></li>").size()==1,"HTML results");
  String html="<html><nav>菜单</nav><main><h1>说明</h1><p>模型在本地推理。</p><script>SECRET</script><aside>AD</aside></main></html>";
  String text=SearchClient.article(new SearchClient.Page(html.getBytes(StandardCharsets.UTF_8),target,"text/html;charset=utf-8"));check(text.contains("本地推理")&&!text.contains("SECRET")&&!text.contains("菜单")&&!text.contains("AD"),"Extract article without scripts/navigation");
  for(String ip:new String[]{"127.0.0.1","10.0.0.1","192.168.1.1","169.254.169.254","100.64.0.1","::1","fc00::1"})check(!SearchClient.publicAddress(InetAddress.getByName(ip)),"Block private target "+ip);
  check(SearchClient.publicAddress(InetAddress.getByName("8.8.8.8")),"Public address");
  try{SearchClient.validatePublic(new URL("http://example.com"));throw new AssertionError("HTTP allowed");}catch(java.io.IOException expected){checks++;}
  try{SearchClient.article(new SearchClient.Page("<main>Verify you are human</main>".getBytes(StandardCharsets.UTF_8),target,"text/html"));throw new AssertionError("Challenge accepted");}catch(java.io.IOException expected){checks++;}
  SearchClient stopped=new SearchClient();stopped.cancel();try{stopped.search("test",v->{});throw new AssertionError("Cancelled search ran");}catch(java.io.InterruptedIOException expected){checks++;}
  String context=SearchClient.context(list,"model","2026-11-11");check(context.contains("[1]")&&context.contains("不是网页发布日期"),"Citation/time attribution");
  for(int i=0;i<TaskCatalog.NAMES.length;i++){TaskExamples.Example[] examples=TaskExamples.get(i);check(examples.length==3,"Three examples per task");for(TaskExamples.Example e:examples)check(!e.title.isEmpty()&&!e.prompt.isEmpty(),"Example question");}
  System.out.println("PASS "+checks+" search and example checks");
  if(args.length>0&&args[0].equals("--live")){
   String query=args.length>1?args[1]:"Qwen3.5 0.8B";List<SearchClient.Source> live=new SearchClient().search(query,System.out::println);JSONArray sources=new JSONArray();for(SearchClient.Source s:live)sources.put(s.json());
   JSONObject result=new JSONObject().put("query",query).put("sources",sources).put("context",SearchClient.context(live,query,"2026-11-11"));Files.writeString(Paths.get("build/live-public-search.json"),result.toString(2));System.out.println("Live search: "+live.size()+" sources");
  }
 }
}
