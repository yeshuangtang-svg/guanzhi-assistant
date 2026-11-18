package cn.shiguang.qwenpocket;
import android.content.Context;
import android.graphics.*;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.*;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import java.util.*;
import java.util.concurrent.TimeUnit;
final class OcrTool {
    static class Line {String text;Rect box;Line(String text,Rect box){this.text=text;this.box=box;}}
    static List<Line> read(Bitmap bitmap)throws Exception{
        TextRecognizer client=TextRecognition.getClient(new ChineseTextRecognizerOptions.Builder().build());
        try{Text result=Tasks.await(client.process(InputImage.fromBitmap(bitmap,0)),45,TimeUnit.SECONDS);ArrayList<Line> lines=new ArrayList<>();for(Text.TextBlock block:result.getTextBlocks())for(Text.Line line:block.getLines()){Rect bounds=line.getBoundingBox();if(bounds!=null)lines.add(new Line(line.getText(),new Rect(bounds)));}return lines;}
        finally{client.close();}
    }
}
