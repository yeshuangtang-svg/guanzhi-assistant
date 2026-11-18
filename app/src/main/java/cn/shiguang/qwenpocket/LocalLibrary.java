package cn.shiguang.qwenpocket;
import java.util.*;
import org.json.*;
/** Local CJK-bigram retrieval; no embedding service or upload. */
final class LocalLibrary {
    static Set<String> terms(String text){String t=text.toLowerCase(Locale.ROOT).replaceAll("\\s+","");Set<String> out=new HashSet<>();for(int i=0;i+1<t.length();i++)out.add(t.substring(i,i+2));return out;}
    static String retrieve(JSONArray docs,String question)throws JSONException{
        Set<String> query=terms(question);ArrayList<String[]> candidates=new ArrayList<>();
        for(int d=0;d<docs.length();d++){JSONObject doc=docs.getJSONObject(d);if(!doc.optBoolean("enabled",true))continue;String text=doc.getString("text");for(int start=0;start<text.length();start+=420){String part=text.substring(start,Math.min(text.length(),start+520));Set<String> words=terms(part);int hits=0;for(String q:query)if(words.contains(q))hits++;if(hits>=2)candidates.add(new String[]{String.valueOf(hits),doc.getString("name"),String.valueOf(start),part});}}
        candidates.sort((x,y)->Integer.compare(Integer.parseInt(y[0]),Integer.parseInt(x[0])));StringBuilder out=new StringBuilder();
        for(int i=0;i<Math.min(3,candidates.size());i++){String[] c=candidates.get(i);out.append("[资料").append(i+1).append("] ").append(c[1]).append(" · 字符位置 ").append(c[2]).append("\n").append(c[3]).append("\n\n");}
        return out.toString();
    }
}
