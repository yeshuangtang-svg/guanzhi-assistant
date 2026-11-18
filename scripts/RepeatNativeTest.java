package cn.shiguang.qwenpocket;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
public class RepeatNativeTest {
 public static void main(String[] args)throws Exception{
  String error=NativeBridge.load("app/src/main/assets/models/model-allint-w4w8.gguf","app/src/main/assets/models/vision-allint-q8.gguf",4,false,384);if(!error.isEmpty())throw new AssertionError(error);
  String[][] tests={{"What is 2 + 3? Answer with only the integer.","","5"},{"What is 17 multiplied by 23? Answer with only the integer.","","391"},{"请把 apple 翻译成中文。","","苹果"},{"What is the dominant color in this image? Answer with one color word.","build/dual-test-images/red.png","红|red"},{"How many red circles are in this image? Answer with only the number.","build/dual-test-images/circles.png","3|three|三"},{"请反复输出“春天来了，我们一起去公园看花。”直到长度用完，不要解释。","",""}};
  JSONArray rows=new JSONArray();
  for(boolean enabled:new boolean[]{false,true})for(int i=0;i<tests.length;i++){
   NativeBridge.setRepetitionProtection(enabled);NativeBridge.resetCancel();ByteArrayOutputStream bytes=new ByteArrayOutputStream();String[] t=tests[i];
   error=NativeBridge.generate(new String[]{"user"},new String[]{t[0]},new String[]{t[1]},"",i==5?256:64,b->bytes.write(b,0,b.length));if(!error.isEmpty())throw new AssertionError(error);
   String output=GenerationPolicy.output(bytes.toByteArray(),NativeBridge.keepOutputBytes());boolean match=t[2].isEmpty();for(String expected:t[2].split("\\|"))match|=output.toLowerCase().contains(expected);
   JSONObject row=new JSONObject().put("protection",enabled).put("question",t[0]).put("output",output).put("expected_match",match).put("stop_reason",NativeBridge.finishReason()).put("raw_bytes",bytes.size()).put("kept_bytes",NativeBridge.keepOutputBytes()).put("stats",NativeBridge.stats());rows.put(row);System.out.println(row);
   if(i<5&&!match)throw new AssertionError("Regression: "+row);
  }
  NativeBridge.unload();Files.write(Paths.get("build/repetition-native-validation.json"),new JSONObject().put("platform","Windows CPU, same JNI source; no phone test").put("cases",rows).toString(2).getBytes(StandardCharsets.UTF_8));
 }
}
