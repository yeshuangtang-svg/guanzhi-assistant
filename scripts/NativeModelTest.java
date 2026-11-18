package cn.shiguang.qwenpocket;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.json.*;

public class NativeModelTest {
    static JSONObject run(String question,String image,String evidence)throws Exception{
        NativeBridge.resetCancel();ByteArrayOutputStream bytes=new ByteArrayOutputStream();long start=System.nanoTime();long[] first={0};
        String error=NativeBridge.generate(new String[]{"user"},new String[]{question},new String[]{image},evidence,48,b->{
            if(first[0]==0)first[0]=System.nanoTime();bytes.write(b,0,b.length);
        });
        if(error==null||!error.isEmpty())throw new AssertionError("Inference failed: "+error);
        String output=bytes.toString("UTF-8");if(output.isEmpty())throw new AssertionError("Empty output");
        return new JSONObject().put("question",question).put("output",output).put("first_token_seconds",(first[0]-start)/1e9)
            .put("stats",NativeBridge.stats()).put("phase",NativeBridge.phase());
    }
    public static void main(String[] args)throws Exception{
        Path root=Paths.get(args[0]);String lm=root.resolve("app/src/main/assets/models/model-bf16.gguf").toString();
        String vision=root.resolve("app/src/main/assets/models/vision-f16.gguf").toString();
        String err=NativeBridge.load(lm,vision,4,false,384);if(!err.isEmpty())throw new AssertionError(err);
        JSONArray cases=new JSONArray();
        try{
            cases.put(run("17 加 25 等于多少？只回答数字。","",""));
            cases.put(run("根据提供的资料，测试展览在哪座城市举办？","","检索时间：2026-11-10\n[1] 测试资料：星河测试展览在苏州举办。"));
            String image=root.resolve("build/vision-check.png").toString();
            JSONObject cold=run("左边的图形是什么颜色？",image,"");cases.put(cold);
            JSONObject warm=run("左边的图形是什么颜色？",image,"");cases.put(warm);
            if(cold.getString("stats").contains("已复用图像特征"))throw new AssertionError("Unexpected cache hit");
            boolean baseline=System.getenv("QWEN_TEST_BASELINE")!=null;
            if(!baseline&&!warm.getString("stats").contains("已复用图像特征"))throw new AssertionError("Image cache not reused");
            if(!cold.getString("output").equals(warm.getString("output")))throw new AssertionError("Cache changed greedy output");
            NativeBridge.cancel();String stopped=NativeBridge.generate(new String[]{"user"},new String[]{"停止测试"},new String[]{""},"",32,b->{throw new AssertionError("Cancelled generation emitted tokens");});
            if(!stopped.isEmpty())throw new AssertionError(stopped);
            JSONObject report=new JSONObject().put("platform","Windows CPU; same JNI source, not an Android speed benchmark")
                .put("backend",NativeBridge.backend()).put("image_tokens",384).put("image_cache_output_identical",true)
                .put("cancel_before_generation_passed",true).put("arithmetic_correct",cases.getJSONObject(0).getString("output").trim().equals("42"))
                .put("evidence_answer_correct",cases.getJSONObject(1).getString("output").contains("苏州")).put("cases",cases);
            Files.write(root.resolve(baseline?"build/native-baseline-validation.json":"build/native-integration-validation.json"),report.toString(2).getBytes(StandardCharsets.UTF_8));
            System.out.println(report.toString(2));
        }finally{NativeBridge.unload();}
    }
}
