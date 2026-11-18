package cn.shiguang.qwenpocket;
import java.io.*;import java.nio.file.*;import org.json.*;
public final class LocalTasksNativeTest {
 static JSONObject run(String question,String image,String context,int max)throws Exception{
  NativeBridge.resetCancel();ByteArrayOutputStream bytes=new ByteArrayOutputStream();String err=NativeBridge.generate(new String[]{"user"},new String[]{question},new String[]{image},context,max,b->bytes.write(b,0,b.length));
  if(err==null||!err.isEmpty())throw new AssertionError(err);String answer=GenerationPolicy.output(bytes.toByteArray(),NativeBridge.keepOutputBytes());if(answer.trim().isEmpty())throw new AssertionError("No model output");
  return new JSONObject().put("question",question).put("answer",answer).put("finish",NativeBridge.finishReason()).put("stats",NativeBridge.stats());
 }
 public static void main(String[] args)throws Exception{
  String error=NativeBridge.load("app/src/main/assets/models/model-allint-w4w8.gguf","app/src/main/assets/models/vision-allint-q8.gguf",4,false,384);if(!error.isEmpty())throw new AssertionError(error);
  JSONArray cases=new JSONArray();NativeBridge.setRepetitionProtection(true);
  try{
   cases.put(run(TaskCatalog.prompt(1,TaskExamples.get(1)[0].prompt,""),"","",300));
   cases.put(run(TaskCatalog.prompt(10,"请提取图片中的文字和数字。只输出识别的文本。",""),"build/local-ocr-check.png","",160));
   JSONObject live=new JSONObject(Files.readString(Paths.get("build/live-public-search.json")));
   cases.put(run("根据网页资料，Qwen3.5-0.8B 默认使用哪种思考模式？请给出依据并用 [1] 等标注来源。如果没有相关证据，说明无法从这些资料确定。","",live.getString("context"),300));
   JSONObject result=new JSONObject().put("platform","Windows CPU, packaged quantized weights; not phone performance").put("task_answers_generated_by_native_model",true).put("cases",cases);Files.writeString(Paths.get("build/local-tasks-native-validation.json"),result.toString(2));System.out.println(result.toString(2));
  }finally{NativeBridge.unload();}
 }
}
