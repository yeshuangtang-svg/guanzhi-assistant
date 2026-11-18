package cn.shiguang.qwenpocket;
import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.zip.*;
/** Whitelisted portable archive; no settings, credentials or model files. */
final class AppArchive {
    static final long LIMIT=256L*1024*1024;
    static void imagePaths(JSONObject data,PathMapper mapper)throws Exception{JSONArray sessions=data.getJSONArray("sessions");for(int i=0;i<sessions.length();i++){JSONObject s=sessions.getJSONObject(i);s.put("draftImage",mapper.map(s.optString("draftImage","")));JSONArray ms=s.getJSONArray("messages");for(int k=0;k<ms.length();k++){JSONObject m=ms.getJSONObject(k);m.put("image",mapper.map(m.optString("image","")));}}}
    interface PathMapper {String map(String path)throws Exception;}
    static void write(OutputStream out,JSONObject original,JSONArray library,File images)throws Exception{
        JSONObject data=new JSONObject(original.toString());Map<String,File> files=new LinkedHashMap<>();File base=images.getCanonicalFile();
        imagePaths(data,p->{if(p.isEmpty())return "";File f=new File(p).getCanonicalFile();if(!base.equals(f.getParentFile())||!f.isFile())throw new IOException("图片缺失或不在应用图片目录："+f.getName());String name="images/"+f.getName();if(!name.matches("images/[a-zA-Z0-9_-]+\\.jpg"))throw new IOException("图片文件名无效");files.put(name,f);return name;});
        byte[] metadata=new JSONObject().put("format",1).put("data",data).put("library",library).toString().getBytes(StandardCharsets.UTF_8);
        if(metadata.length>16L*1024*1024||files.size()>1999)throw new IOException("备份文字或文件数过多，请按当前对话分别导出");
        long bytes=metadata.length;for(File f:files.values()){if(f.length()>32L*1024*1024)throw new IOException("单张图片超过 32 MB");bytes+=f.length();}if(bytes>LIMIT)throw new IOException("内容超过 256 MB，请按当前对话分别导出");
        try(ZipOutputStream z=new ZipOutputStream(out)){put(z,"archive.json",metadata);byte[] b=new byte[65536];for(Map.Entry<String,File> f:files.entrySet()){z.putNextEntry(new ZipEntry(f.getKey()));try(InputStream in=new FileInputStream(f.getValue())){int n;while((n=in.read(b))!=-1)z.write(b,0,n);}z.closeEntry();}}
    }
    static void put(ZipOutputStream z,String name,byte[] bytes)throws IOException{z.putNextEntry(new ZipEntry(name));z.write(bytes);z.closeEntry();}
    static final class Restored {JSONObject data;JSONArray library;File staging;}
    static Restored read(InputStream input,File staging)throws Exception{
        if(!staging.mkdirs()&&!staging.isDirectory())throw new IOException("无法准备导入目录");Set<String> names=new HashSet<>();long total=0;String metadata=null;
        try(ZipInputStream z=new ZipInputStream(input)){ZipEntry e;byte[] b=new byte[65536];while((e=z.getNextEntry())!=null){String name=e.getName();if(!names.add(name)||names.size()>2000||(!name.equals("archive.json")&&!name.matches("images/[a-zA-Z0-9_-]+\\.jpg")))throw new IOException("备份含重复或不支持的文件路径");File f=new File(staging,name);if(!f.getCanonicalPath().startsWith(staging.getCanonicalPath()+File.separator))throw new IOException("无效路径");f.getParentFile().mkdirs();long entry=0;try(OutputStream o=new FileOutputStream(f)){int n;while((n=z.read(b))!=-1){total+=n;entry+=n;if(total>LIMIT||entry>(name.equals("archive.json")?16L*1024*1024:32L*1024*1024))throw new IOException("备份内容超过安全导入上限");o.write(b,0,n);}}if(name.equals("archive.json"))metadata=new String(java.nio.file.Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);}}
        if(metadata==null)throw new IOException("缺少备份清单");JSONObject root=new JSONObject(metadata);if(root.getInt("format")!=1)throw new IOException("不支持的备份版本");Restored r=new Restored();r.data=root.getJSONObject("data");r.library=root.optJSONArray("library");if(r.library==null)r.library=new JSONArray();r.staging=staging;
        imagePaths(r.data,p->{if(p.isEmpty())return "";if(!p.matches("images/[a-zA-Z0-9_-]+\\.jpg")||!names.contains(p))throw new IOException("缺失或无效的图片引用");return p;});return r;
    }
}
