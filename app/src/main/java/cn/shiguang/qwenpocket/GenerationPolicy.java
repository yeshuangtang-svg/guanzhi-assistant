package cn.shiguang.qwenpocket;
import java.nio.charset.StandardCharsets;
final class GenerationPolicy {
    static String output(byte[] bytes,int keep){
        int n=keep<0?bytes.length:Math.min(keep,bytes.length);
        if(n>0){int start=n-1;while(start>0&&(bytes[start]&0xc0)==0x80)start--;int lead=bytes[start]&255;int need=lead<128?1:lead<224?2:lead<240?3:4;if(n-start<need)n=start;}
        return new String(bytes,0,n,StandardCharsets.UTF_8);
    }
    static String note(int reason){return reason==2?"检测到重复循环，已停止并去除重复尾段":reason==3?"已达到生成长度上限，回答可能未完成":"";}
}
