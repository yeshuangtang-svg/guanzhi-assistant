package cn.shiguang.qwenpocket;
public final class SearchPlannerTest {
 static int checks;
 static void check(boolean value){if(!value)throw new AssertionError("Search planner check "+checks);checks++;}
 public static void main(String[] args)throws Exception{
  check(SearchPlanner.choose("北京今天天气怎么样？","北京 今日 天气",1).query.equals("北京 今日 天气"));
  check(!SearchPlanner.choose("iPhone 16 Pro 芯片是什么？","检索词：iPhone 16 Pro 芯片",1).fallback);
  check(SearchPlanner.choose("iPhone 16 Pro 芯片是什么？","iPhone 15 Pro 芯片",1).fallback);
  check(SearchPlanner.choose("北京天气？","北京天气",3).fallback);
  check(!SearchPlanner.choose("北京天气？","北京天气\n解释如下",1).fallback);
  check(SearchPlanner.choose("苹果 iPhone 16 Pro 使用什么芯片？","苹果 iPhone 16 Pro 使用 Apple A系列芯片。",1).fallback);
  check(!SearchPlanner.choose("Qwen3.5 0.8B 的模型说明","Qwen3.5 0.8B 模型说明\n1. 模型架构解释",3).fallback);
  check(SearchPlanner.choose("北京天气？","天气 "+"字".repeat(170),1).fallback);
  check(SearchPlanner.choose("北京天气？","我不能联网查询北京天气",1).fallback);
  check(SearchPlanner.choose("北京天气？","红烧肉怎么做",1).fallback);
  check(SearchPlanner.choose("北京天气？","https://example.com/北京天气",1).fallback);
  check(SearchPlanner.choose("北京天气？","",1).query.equals("北京天气？"));
  check(!SearchPlanner.choose("今天北京天气","“北京 天气”",1).fallback);
  check(SearchPlanner.choose("北京天气？","- 北京 天气",1).fallback);
  String prompt=SearchPlanner.prompt("北京天气？","2026-11-11");check(prompt.contains("2026-11-11")&&prompt.contains("北京天气？")&&!prompt.contains("API Key"));
  org.json.JSONObject request=SearchClient.requestBody(SearchPlanner.choose("北京天气？","北京 天气",1).query);check(request.getString("query").equals("北京 天气")&&!request.getBoolean("include_answer"));
  System.out.println("PASS "+checks+" query planning and fallback checks");
 }
}
