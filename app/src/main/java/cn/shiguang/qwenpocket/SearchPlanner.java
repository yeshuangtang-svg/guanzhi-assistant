package cn.shiguang.qwenpocket;
import java.util.*;
/** Bounded local query generation: only the current question is supplied. */
final class SearchPlanner {
    static String prompt(String question,String date){return "请把下面的问题改写成用于网页搜索的关键词。保留地点、人物、产品型号、日期等关键条件。不要回答问题，不要编造答案。只输出一行检索词，不要解释或列表。今天是 "+date+"。\n问题："+question+"\n检索词：";}
    static String[] roles(){return new String[]{"user","assistant","user","assistant","user"};}
    static String[] texts(String question,String date){return new String[]{prompt("上海今天的天气怎么样？",date),"上海 今日 天气",prompt("手机 X9 Pro 使用什么处理器？",date),"手机 X9 Pro 处理器 型号",prompt(question,date)};}
    static final class Plan {final String query;final boolean fallback;Plan(String q,boolean f){query=q;fallback=f;}}
    static Plan choose(String question,String raw,int finishReason){
        if(raw==null)return new Plan(question.trim(),true);
        String[] lines=raw.split("\\r?\\n",-1);int inspected=0;
        for(int i=0;i<Math.min(6,lines.length)&&inspected<3;i++){
            String line=lines[i].trim();if(line.isEmpty())continue;inspected++;
            // A completed line is usable even if the model subsequently rambles to its limit.
            if(finishReason!=1&&(i==lines.length-1||finishReason!=3))continue;
            Plan candidate=line(question,line);
            if(!candidate.fallback&&!candidate.query.equals(question.trim()))return candidate;
        }
        return new Plan(question.trim(),true);
    }
    static Plan line(String question,String raw){
        String q=raw.trim();q=q.replaceFirst("^(检索词|搜索词|关键词|搜索关键词|query|Query)[：:]\\s*","").trim();
        if(q.length()>1&&((q.startsWith("\"")&&q.endsWith("\""))||(q.startsWith("“")&&q.endsWith("”"))))q=q.substring(1,q.length()-1).trim();
        boolean valid=!q.isEmpty()&&q.codePointCount(0,q.length())<=160&&!q.contains("。")&&!q.contains("！")&&!q.contains("：")&&!q.contains("<")&&!q.contains("```")&&!q.matches("^(?:[-*#]|\\d+[.)]\\s).*")&&!q.toLowerCase(Locale.ROOT).contains("http");
        for(String prefix:new String[]{"我","抱歉","答案","无法","不能","根据","I ","Sorry","The answer"})if(q.startsWith(prefix))valid=false;
        java.util.regex.Matcher numbers=java.util.regex.Pattern.compile("\\d+(?:\\.\\d+)?").matcher(question);
        while(numbers.find())if(!q.contains(numbers.group()))valid=false;
        String original=question.toLowerCase(Locale.ROOT),candidate=q.toLowerCase(Locale.ROOT);boolean related=false;
        for(int i=0;i+1<original.length();i++){char c=original.charAt(i),next=original.charAt(i+1);if(Character.isLetterOrDigit(c)&&Character.isLetterOrDigit(next)&&candidate.contains(original.substring(i,i+2))){related=true;break;}}
        return valid&&related?new Plan(q,false):new Plan(question.trim(),true);
    }
}
