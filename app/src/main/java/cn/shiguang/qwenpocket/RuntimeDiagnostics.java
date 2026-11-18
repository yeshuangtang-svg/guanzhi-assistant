package cn.shiguang.qwenpocket;
import android.app.*;import android.os.Build;import java.io.*;import java.nio.charset.StandardCharsets;import java.text.SimpleDateFormat;import java.util.*;
final class RuntimeDiagnostics {
    static File log(MainActivity a){return new File(a.getFilesDir(),"engine-diagnostic.log");}
    static String time(){return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.CHINA).format(new Date());}
    static void saveLoad(MainActivity a,String error){
        a.getPreferences(0).edit().putString("lastEngineDiagnostic",time()+"\n版本 "+BuildVariant.VERSION+"\n"+(error.isEmpty()?"":"错误："+error+"\n")+NativeBridge.diagnostics()).commit();
    }
    static String lastExit(MainActivity a){
        if(Build.VERSION.SDK_INT<30)return "系统不提供进程退出记录。";
        try{List<ApplicationExitInfo> records=((ActivityManager)a.getSystemService(MainActivity.ACTIVITY_SERVICE)).getHistoricalProcessExitReasons(null,0,1);if(records.isEmpty())return "系统没有近期退出记录。";ApplicationExitInfo e=records.get(0);String reason;
            switch(e.getReason()){case ApplicationExitInfo.REASON_CRASH_NATIVE:reason="原生代码崩溃";break;case ApplicationExitInfo.REASON_CRASH:reason="Java 崩溃";break;case ApplicationExitInfo.REASON_LOW_MEMORY:reason="系统低内存回收";break;case ApplicationExitInfo.REASON_ANR:reason="应用无响应";break;case ApplicationExitInfo.REASON_USER_REQUESTED:reason="用户或系统请求退出";break;default:reason="原因代码 "+e.getReason();}
            return "最近退出："+new SimpleDateFormat("MM-dd HH:mm:ss",Locale.CHINA).format(new Date(e.getTimestamp()))+" · "+reason+"\n"+String.valueOf(e.getDescription());
        }catch(Exception e){return "系统退出记录不可用。";}
    }
    static String report(MainActivity a){return "观知助手 "+BuildVariant.VERSION+"\n"+Build.MANUFACTURER+" "+Build.MODEL+" / Android "+Build.VERSION.RELEASE+"\n请求："+"CPU"+"\n实际："+a.backendInfo+"\n"+NativeBridge.stats()+"\n\n"+NativeBridge.diagnostics()+"\n\n最近一次加载：\n"+a.getPreferences(0).getString("lastEngineDiagnostic","暂无记录")+"\n\n"+lastExit(a);}
}
