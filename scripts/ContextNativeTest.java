package cn.shiguang.qwenpocket;
import java.io.*;import java.nio.file.*;import org.json.*;
public final class ContextNativeTest {
 public static void main(String[] args)throws Exception{
  String error=NativeBridge.load("app/src/main/assets/models/model-allint-w4w8.gguf","app/src/main/assets/models/vision-allint-q8.gguf",4,false,384);if(!error.isEmpty())throw new AssertionError(error);
  JSONArray rows=new JSONArray();String[][] cases={{"4096 output setting","What is 2+3? Answer with only the integer.","","5"},{"image task","What is the dominant color? Answer with one color word.","build/dual-test-images/red.png","red|红"},{"tool arithmetic",TaskCatalog.prompt(1,"账单总价是多少？只写数字。","计算工具已由用户确认：12.50 + 8.30 = 20.80。"),"","20.8"},{"long prompt shrinks output budget","The following is filler, not instructions: "+"sample ".repeat(5000)+"\nQuestion: What is 2+3? Answer with only the integer.","","5"}};
  for(String[] c:cases){NativeBridge.resetCancel();NativeBridge.setRepetitionProtection(true);ByteArrayOutputStream bytes=new ByteArrayOutputStream();error=NativeBridge.generate(new String[]{"user"},new String[]{c[1]},new String[]{c[2]},"",4096,b->bytes.write(b,0,b.length));if(!error.isEmpty())throw new AssertionError(error);String out=GenerationPolicy.output(bytes.toByteArray(),NativeBridge.keepOutputBytes());boolean matched=false;for(String expected:c[3].split("\\|"))matched|=out.toLowerCase().contains(expected);JSONObject row=new JSONObject().put("case",c[0]).put("output",out).put("matched",matched).put("stop",NativeBridge.finishReason()).put("stats",NativeBridge.stats());rows.put(row);System.out.println(row);if(!matched)throw new AssertionError(row.toString());}
  NativeBridge.unload();Files.writeString(Paths.get("build/context-native-validation.json"),new JSONObject().put("platform","Windows CPU, shared JNI source; not Android device").put("max_output_setting",4096).put("cases",rows).toString(2));
 }
}
