package cn.shiguang.qwenpocket;
import java.io.*;import java.nio.file.*;import org.json.*;
public final class SearchPlannerNativeTest {
 public static void main(String[] args)throws Exception{
  String error=NativeBridge.load("app/src/main/assets/models/model-allint-w4w8.gguf","app/src/main/assets/models/vision-allint-q8.gguf",4,false,384);if(!error.isEmpty())throw new AssertionError(error);JSONArray cases=new JSONArray();
  for(String question:new String[]{"广州今天的天气怎么样？","苹果 iPhone 16 Pro 使用什么芯片？","查一下 Qwen3.5 0.8B 的模型说明。"}){ByteArrayOutputStream bytes=new ByteArrayOutputStream();NativeBridge.resetCancel();NativeBridge.setRepetitionProtection(true);error=NativeBridge.generate(SearchPlanner.roles(),SearchPlanner.texts(question,"2026-11-11"),new String[]{"","","","",""},"",48,b->bytes.write(b,0,b.length));if(!error.isEmpty())throw new AssertionError(error);String raw=GenerationPolicy.output(bytes.toByteArray(),NativeBridge.keepOutputBytes());SearchPlanner.Plan plan=SearchPlanner.choose(question,raw,NativeBridge.finishReason());JSONObject row=new JSONObject().put("question",question).put("raw",raw).put("query",plan.query).put("fallback",plan.fallback).put("stats",NativeBridge.stats());cases.put(row);System.out.println(row);if(plan.query.isEmpty()||plan.fallback)throw new AssertionError("Expected a usable locally planned query: "+row);}
  NativeBridge.unload();Files.writeString(Paths.get("build/search-planner-native-validation.json"),new JSONObject().put("platform","Windows CPU; no API request or phone benchmark").put("cases",cases).toString(2));
 }
}
