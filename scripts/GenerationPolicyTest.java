package cn.shiguang.qwenpocket;
import java.nio.charset.StandardCharsets;
public class GenerationPolicyTest {
 public static void main(String[] args){
  byte[] data="A你好🙂B".getBytes(StandardCharsets.UTF_8);
  for(int i=0;i<=data.length;i++){String s=GenerationPolicy.output(data,i);if(s.contains("\ufffd")||!"A你好🙂B".startsWith(s))throw new AssertionError("Broken UTF8 at "+i);}
  if(!GenerationPolicy.output(data,-1).equals("A你好🙂B"))throw new AssertionError("Untrimmed output");
  if(GenerationPolicy.note(2).isEmpty()||GenerationPolicy.note(3).isEmpty()||!GenerationPolicy.note(1).isEmpty())throw new AssertionError("Finish reasons");
  System.out.println("PASS: UTF-8 prefix trimming and finish reasons");
 }
}
