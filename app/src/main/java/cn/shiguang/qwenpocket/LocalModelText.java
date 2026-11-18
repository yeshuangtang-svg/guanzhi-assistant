package cn.shiguang.qwenpocket;
import java.io.*;
/** Runs on MainActivity.worker; same local engine used for chat and PDF transcription. */
final class LocalModelText {
    static String transcribe(MainActivity a,String path)throws IOException{
        if(!a.loaded)throw new IOException("请先加载本地模型，再导入 PDF");
        if(a.stopped||a.destroyed)throw new IOException("资料导入已停止");
        ByteArrayOutputStream out=new ByteArrayOutputStream();NativeBridge.setRepetitionProtection(a.repetitionProtection);
        String err=NativeBridge.generate(new String[]{"user"},new String[]{TaskCatalog.prompt(10,"请转写这一页的文字，保留阅读顺序。只输出识别内容，不添加解释。不清楚的字用[?]标记。","")},new String[]{path},"",1024,b->out.write(b,0,b.length));
        if(a.stopped||a.destroyed)throw new IOException("资料导入已停止");if(err!=null&&!err.isEmpty())throw new IOException(err);
        String text=GenerationPolicy.output(out.toByteArray(),NativeBridge.keepOutputBytes());if(text.trim().isEmpty())throw new IOException("模型未识别出文字");
        return text+(NativeBridge.finishReason()==3?"\n[本页达到生成上限，转写可能不完整]":"");
    }
}
