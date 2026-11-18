package cn.shiguang.qwenpocket;
import java.nio.file.*;import java.io.*;import org.json.*;
public final class CpuOnlyNativeTest {
 public static void main(String[] args)throws Exception{
  NativeBridge.setDiagnosticPath("build/cpu-only-native.log");JSONArray records=new JSONArray();
  try{for(boolean legacyFlag:new boolean[]{false,true}){
   String error=NativeBridge.load("app/src/main/assets/models/model-allint-w4w8.gguf","app/src/main/assets/models/vision-allint-q8.gguf",4,legacyFlag,384);
   if(!error.isEmpty()||!NativeBridge.backend().equals("CPU"))throw new AssertionError(error);
   String diagnostics=NativeBridge.diagnostics();if(!diagnostics.contains("实际后端：CPU")||diagnostics.contains("Vulkan"))throw new AssertionError(diagnostics);
   String question=legacyFlag?TaskCatalog.prompt(10,"请提取图片中的文字和数字。只输出识别的文本。",""):TaskCatalog.prompt(1,TaskExamples.get(1)[0].prompt,"");
   ByteArrayOutputStream bytes=new ByteArrayOutputStream();NativeBridge.resetCancel();
   error=NativeBridge.generate(new String[]{"user"},new String[]{question},new String[]{legacyFlag?"build/local-ocr-check.png":""},"",200,b->bytes.write(b,0,b.length));
   String answer=GenerationPolicy.output(bytes.toByteArray(),NativeBridge.keepOutputBytes());
   if(!error.isEmpty()||answer.trim().isEmpty()||answer.contains("\uFFFD"))throw new AssertionError(error+answer);
   records.put(new JSONObject().put("legacy_flag_ignored",legacyFlag).put("actual",NativeBridge.backend()).put("image",legacyFlag).put("answer",answer).put("stats",NativeBridge.stats()));
  }}finally{NativeBridge.unload();}
  if(Files.size(Paths.get("build/cpu-only-native.log"))==0)throw new AssertionError("Missing diagnostics");
  JSONObject result=new JSONObject().put("platform","Windows CPU; actual quantized weights; not phone speed").put("cases",records);
  Files.writeString(Paths.get("build/cpu-only-validation.json"),result.toString(2));System.out.println(result.toString(2));
 }
}
