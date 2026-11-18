package cn.shiguang.qwenpocket;
public final class LiteProfileTest {
 public static void main(String[] args){
  for(int old:new int[]{-1,0,1,2,3,99}){
   SpeedProfiles p=SpeedProfiles.get(old);
   if(p.id!=1||!p.quantized||!p.label.equals("CPU"))throw new AssertionError("CPU migration: "+old);
   if(!p.languageFile.equals("model-allint-w4w8.gguf")||!p.visionFile.equals("vision-allint-q8.gguf"))throw new AssertionError("Wrong weights");
  }
  System.out.println("PASS: all legacy and invalid profiles migrate to CPU with the same quantized weights");
 }
}
