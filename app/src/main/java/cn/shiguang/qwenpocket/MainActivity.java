package cn.shiguang.qwenpocket;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    static final int INK=0xff243b34, MUTED=0xff78877f, GREEN=0xff236b58, BG=0xfff7faf8;
    static final ExecutorService worker=Executors.newSingleThreadExecutor();
    final Handler ui=new Handler(Looper.getMainLooper());
    final ArrayList<Session> sessions=new ArrayList<>();
    ArrayList<Message> messages=new ArrayList<>();
    Session active;
    LinearLayout root,timeline,composer,imageRow;
    TextView status,detail,metrics,imageLabel,attachmentNote;
    ImageView thumbnail;
    EditText input;
    Button send,attach,camera,fresh,settings,webToggle;
    ScrollView scroll;
    ProgressBar progress;
    volatile boolean busy=false,loaded=false,destroyed=false,stopped=false;
    TextView streamingOutput;
    RichAnswerView streamingRich;
    boolean followOutput=true;
    volatile SearchClient searchClient;
    boolean webEnabled=false,storageWritable=true,importing=false;
    String pendingImage="",pendingEvidence=""; int selectedTask=0; AppTools tools; Button taskButton;
    File modelDir;
    int threads=4,maxTokens=1024,imageTokens=384;
    boolean generating=false,repetitionProtection=true;
    SpeedProfiles speed=SpeedProfiles.get(0);
    String backendInfo="CPU";
    long requestStarted;
    final Runnable progressTicker=this::watchProgress;
    
    static class Message {
        String role,text,image,state="complete",mode="本地回答",retrievedAt="",timing="",modelInfo="",stopNote="";
        int task=0; String toolContext="",feedback="",expected="",comparison="";
        String searchQuery="";boolean searchFallback=false;boolean libraryRequested=false,searched=false;
        List<SearchClient.Source> sources=new ArrayList<>();
        Message(String r,String t,String i){role=r;text=t;image=i;}
        JSONObject json()throws JSONException {
            JSONArray refs=new JSONArray();for(SearchClient.Source s:sources)refs.put(s.json());
            return new JSONObject().put("role",role).put("text",text).put("image",image).put("state",state)
                .put("searchQuery",searchQuery).put("searchFallback",searchFallback).put("libraryRequested",libraryRequested).put("task",task).put("toolContext",toolContext).put("feedback",feedback).put("expected",expected).put("comparison",comparison).put("mode",mode).put("stopNote",stopNote).put("timing",timing).put("modelInfo",modelInfo).put("searched",searched).put("retrievedAt",retrievedAt).put("sources",refs);
        }
        static Message from(JSONObject j)throws JSONException {
            Message m=new Message(j.getString("role"),j.getString("text"),j.optString("image",""));
            m.searchQuery=j.optString("searchQuery","");m.searchFallback=j.optBoolean("searchFallback",false);m.libraryRequested=j.optBoolean("libraryRequested",false);m.task=TaskCatalog.safe(j.optInt("task",0));m.toolContext=j.optString("toolContext","");m.feedback=j.optString("feedback","");m.expected=j.optString("expected","");m.comparison=j.optString("comparison","");
            m.stopNote=j.optString("stopNote","");m.modelInfo=j.optString("modelInfo","");m.timing=j.optString("timing","");m.state=j.optString("state","complete");m.mode=j.optString("mode","本地回答");
            m.searched=j.optBoolean("searched",false);m.retrievedAt=j.optString("retrievedAt","");
            JSONArray refs=j.optJSONArray("sources");if(refs!=null)for(int i=0;i<refs.length();i++){
                JSONObject s=refs.getJSONObject(i);String url=s.optString("url","");
                if(SearchClient.validUrl(url))m.sources.add(new SearchClient.Source(s.optString("title","网页资料"),url,s.optString("content","")));
            }
            if("pending".equals(m.state)){m.state="stopped";if(m.text.isEmpty())m.text="上次回答已中断，可点击重新回答。";}
            return m;
        }
    }
    static class Session {
        String id=UUID.randomUUID().toString(),customTitle="",draft="",draftImage="";long updated=System.currentTimeMillis();
        String draftEvidence="";int draftTask=0;boolean pinned=false,deleted=false;
        ArrayList<Message> messages=new ArrayList<>();
        String title(){if(!customTitle.isEmpty())return customTitle;for(Message m:messages)if("user".equals(m.role))return SearchClient.clean(m.text,48);return draft.isEmpty()?(draftEvidence.isEmpty()?"新对话":"核对记录"):SearchClient.clean(draft,48);}
        boolean hasContent(){return !messages.isEmpty()||!draft.isEmpty()||!draftImage.isEmpty()||!draftEvidence.isEmpty();}
        boolean matches(String query){String q=query.toLowerCase(Locale.ROOT);if(title().toLowerCase(Locale.ROOT).contains(q)||draft.toLowerCase(Locale.ROOT).contains(q))return true;for(Message m:messages)if(m.text.toLowerCase(Locale.ROOT).contains(q))return true;return false;}
        String preview(String query){String q=query.toLowerCase(Locale.ROOT);if(!q.isEmpty())for(Message m:messages){int pos=m.text.toLowerCase(Locale.ROOT).indexOf(q);if(pos>=0)return SearchClient.clean(m.text.substring(Math.max(0,pos-18)),110);}if(!draft.isEmpty())return "草稿："+SearchClient.clean(draft,100);for(int i=messages.size()-1;i>=0;i--)if(!messages.get(i).text.isEmpty())return SearchClient.clean(messages.get(i).text,110);return draftImage.isEmpty()?"暂无消息":"草稿：已添加图片";}
        JSONObject json()throws JSONException {JSONArray items=new JSONArray();for(Message m:messages)items.put(m.json());return new JSONObject().put("id",id).put("updated",updated).put("customTitle",customTitle).put("pinned",pinned).put("deleted",deleted).put("draft",draft).put("draftImage",draftImage).put("draftEvidence",draftEvidence).put("draftTask",draftTask).put("messages",items);}
        static Session from(JSONObject j)throws JSONException {Session s=new Session();s.id=j.getString("id");s.updated=j.optLong("updated",0);s.customTitle=j.optString("customTitle","");s.pinned=j.optBoolean("pinned",false);s.deleted=j.optBoolean("deleted",false);s.draft=j.optString("draft","");s.draftImage=j.optString("draftImage","");s.draftEvidence=j.optString("draftEvidence","");s.draftTask=TaskCatalog.safe(j.optInt("draftTask",0));JSONArray items=j.getJSONArray("messages");for(int k=0;k<items.length();k++)s.messages.add(Message.from(items.getJSONObject(k)));return s;}
    }
    int chatWindowEnd=Integer.MAX_VALUE;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);threads=getPreferences(0).getInt("threads",4);maxTokens=getPreferences(0).getInt("maxTokens",1024);
        if(!getPreferences(0).getBoolean("outputBudgetV14",false)){maxTokens=Math.max(1024,maxTokens);getPreferences(0).edit().putBoolean("outputBudgetV14",true).putInt("maxTokens",maxTokens).apply();}
        imageTokens=getPreferences(0).getInt("imageTokens",384);repetitionProtection=getPreferences(0).getBoolean("repetitionProtection",true);
        speed=SpeedProfiles.get(1);
        getPreferences(0).edit().putInt("speedProfile",1).remove("acceleration").remove("engineInFlight").remove("lastGpuDiagnostic").apply();
        NativeBridge.setDiagnosticPath(RuntimeDiagnostics.log(this).getAbsolutePath());
        modelDir=new File(getFilesDir(),"models-v1");
        tools=new AppTools(this);buildUi();restoreChat();applyDraft();renderChat();prepareModel();handleShare(getIntent());
    }
    void post(Runnable task){ui.post(()->{if(!destroyed&&!isFinishing())task.run();});}
    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    TextView text(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextSize(size);v.setTextColor(color);return v;}
    Button button(String label,boolean primary){
        Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(13);b.setTextColor(primary?Color.WHITE:GREEN);
        b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(dp(44));b.setMinimumHeight(dp(44));
        b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setBackground(shape(primary?GREEN:0xffedf4f0,14));return b;
    }
    LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    void buildUi(){ChatDesign.build(this);}
    void useTask(int task,String prompt,boolean needsImage,boolean run,String image,String evidence){
        if(busy)return;String selected=image==null?pendingImage:image;saveChat();createSession();selectedTask=TaskCatalog.safe(task);pendingImage=selected==null?"":selected;pendingEvidence=evidence==null?"":evidence;input.setText(prompt);input.setSelection(input.length());refreshAttachment();saveChat();renderChat();
        if(needsImage&&pendingImage.isEmpty()){toast("这是拍图示例，请添加图片后发送");chooseImage(false);return;}
        if(run&&loaded)startReply();else if(run)toast("示例已填入，模型就绪后点击发送");
    }
    void renderChat(){
        RichAnswerView.disposeTree(timeline);timeline.removeAllViews();streamingOutput=null;streamingRich=null;if(messages.isEmpty()){showWelcome();return;}
        int end=Math.min(chatWindowEnd,messages.size()),start=Math.max(0,end-20);
        if(start>0){Button older=button("查看更早的消息（还有 "+start+" 条）",false);older.setOnClickListener(v->{chatWindowEnd=start;renderChat();scroll.scrollTo(0,0);});timeline.addView(older);}
        for(int i=start;i<end;i++)addBubble(messages.get(i));
        if(end<messages.size()){Button newer=button("查看后续消息",false);newer.setOnClickListener(v->{chatWindowEnd=Math.min(messages.size(),end+20);renderChat();scroll.scrollTo(0,0);});timeline.addView(newer);}
    }
    void showWelcome(){ChatDesign.welcome(this);}
    TextView addBubble(Message m){return ChatDesign.bubble(this,m);}
    void openSource(SearchClient.Source source){new AlertDialog.Builder(this).setTitle(source.title).setMessage(source.content+"\n\n"+source.url).setNegativeButton("返回",null).setPositiveButton("打开原网页",(d,w)->{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(source.url)));}catch(Exception e){toast("无法打开浏览器");}}).show();}
    void refreshWeb(){if(webToggle!=null){webToggle.setText(webEnabled?"联网 ✓":"联网");webToggle.setTextColor(webEnabled?Color.WHITE:GREEN);webToggle.setBackground(shape(webEnabled?GREEN:0xffedf4f0,12));webToggle.setContentDescription(webEnabled?"关闭联网搜索":"开启联网搜索");}metrics.setText(webEnabled?"本地模型规划与总结 · 网页搜索无需 API Key":"本地模型推理 · 可按需开启联网搜索");}
    void toggleWeb(){if(busy)return;webEnabled=!webEnabled;refreshWeb();if(webEnabled)toast("由本地模型生成检索词并总结，检索词会发送到必应。图片与历史对话不上传。");}
    void setWorking(boolean value){
        busy=value;send.setText(value?"■":"↑");send.setContentDescription(value?"停止生成":"发送问题");send.setEnabled(loaded);attach.setEnabled(!value);camera.setEnabled(!value);fresh.setEnabled(!value);settings.setEnabled(!value);input.setEnabled(!value);if(webToggle!=null)webToggle.setEnabled(!value);
        if(value)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }
    void stopReply(){stopped=true;NativeBridge.cancel();SearchClient c=searchClient;if(c!=null)c.cancel();status.setText("正在停止…");}
    void startReply(){
        if(!loaded){toast("本地模型还未就绪");return;}
        String question=input.getText().toString().trim();if(question.isEmpty()&&pendingImage.isEmpty()&&pendingEvidence.isEmpty())return;
        
        if(question.isEmpty())question=selectedTask==0?"请分析所附图片或核对记录，并指出关键信息。":"请根据图片和核对记录完成"+TaskCatalog.NAMES[selectedTask]+"，说明依据。";
        if(question.length()>8000){toast("问题过长，请缩短后发送。");return;}
        
        Message user=new Message("user",question,pendingImage);user.task=selectedTask;user.toolContext=pendingEvidence;user.libraryRequested=tools.useLibrary;user.searched=webEnabled;messages.add(user);
        pendingImage="";pendingEvidence="";refreshAttachment();input.setText("");beginAnswer(webEnabled);
    }
    void beginAnswer(boolean useWeb){
        followOutput=true;chatWindowEnd=Integer.MAX_VALUE;active.updated=System.currentTimeMillis();
        ArrayList<Message> request=new ArrayList<>();for(Message m:messages)if("user".equals(m.role)||(("complete".equals(m.state)||"partial".equals(m.state))&&!m.text.isEmpty()))request.add(m);
        Message answer=new Message("assistant","","");answer.state="pending";answer.modelInfo=(speed.quantized?"W4/W8":"16位")+" / "+"CPU";answer.searched=useWeb;answer.mode=useWeb?"正在搜索":"本地回答";messages.add(answer);
        renderChat();TextView output=streamingOutput;
        output.setText(useWeb?"正在用本地模型组织搜索…":"正在阅读与思考…");stopped=false;NativeBridge.resetCancel();setWorking(true);
        requestStarted=SystemClock.elapsedRealtime();status.setText(useWeb?"本地模型正在规划搜索":"本地模型正在思考");saveChat();
        ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(input.getWindowToken(),0);scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));
        if(useWeb)performSearch(request,answer,output);else generate(request,answer,output,"");
    }
    void performSearch(ArrayList<Message> request,Message answer,TextView output){
        SearchClient client=new SearchClient();searchClient=client;
        worker.execute(()->{
            try{
                if(stopped){post(()->finishStopped(answer));return;}
                String question=SearchClient.clean(request.get(request.size()-1).text,500),date=new SimpleDateFormat("yyyy-MM-dd",Locale.CHINA).format(new Date());
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();getPreferences(0).edit().putBoolean("engineInFlight",false).commit();
                String error;
                try{NativeBridge.setRepetitionProtection(repetitionProtection);error=NativeBridge.generate(SearchPlanner.roles(),SearchPlanner.texts(question,date),new String[]{"","","","",""},"",48,b->bytes.write(b,0,b.length));}
                finally{getPreferences(0).edit().putBoolean("engineInFlight",false).commit();}
                if(stopped){post(()->finishStopped(answer));return;}
                SearchPlanner.Plan plan=SearchPlanner.choose(question,error==null||!error.isEmpty()?null:GenerationPolicy.output(bytes.toByteArray(),NativeBridge.keepOutputBytes()),NativeBridge.finishReason());
                post(()->{if(!stopped){status.setText("正在搜索网页");output.setText("检索词："+plan.query+"\n正在获取网页资料…");}});
                List<SearchClient.Source> sources=client.search(plan.query,value->post(()->{if(!stopped)status.setText(value);}));
                String time=new SimpleDateFormat("yyyy-MM-dd HH:mm z",Locale.CHINA).format(new Date()),context=SearchClient.context(sources,plan.query,time);
                post(()->{if(stopped){finishStopped(answer);return;}answer.searchQuery=plan.query;answer.searchFallback=plan.fallback;answer.sources=sources;answer.retrievedAt=time;answer.mode="本地模型 · 网页总结";output.setText("已取得 "+sources.size()+" 条来源，本地模型正在阅读与总结…");saveChat();generate(request,answer,output,context);});
            }catch(Exception e){post(()->{if(stopped){finishStopped(answer);return;}answer.state="error";answer.mode="检索未完成";answer.text="未取得可用网页资料，本次没有生成联网总结。\n"+friendly(e);setWorking(false);showReadyStatus();renderChat();saveChat();new AlertDialog.Builder(this).setTitle("搜索未完成").setMessage("可稍后重试，或仅用本地模型回答。切换到本地回答不会声称已经检索网页。").setNegativeButton("返回",null).setPositiveButton("重新搜索",(d,w)->retryLast()).setNeutralButton("仅本地回答",(d,w)->{if(busy)return;webEnabled=false;refreshWeb();messages.remove(messages.size()-1);beginAnswer(false);}).show();});}
            finally{if(searchClient==client)searchClient=null;}
        });
    }
    void generate(ArrayList<Message> request,Message answer,TextView output,String webContext){
        generating=true;ui.removeCallbacks(progressTicker);watchProgress();
        worker.execute(()->{
            ByteArrayOutputStream bytes=new ByteArrayOutputStream();String error="";long[] update={0},first={0};
            getPreferences(0).edit().putBoolean("engineInFlight",false).commit();
            try {
                Message latest=request.get(request.size()-1);if(latest.libraryRequested){String refs=tools.references(latest.text);if(!refs.isEmpty())latest.toolContext+="\n"+refs;latest.libraryRequested=false;}
                String[] roles=new String[request.size()],texts=new String[request.size()],images=new String[request.size()];
                for(int i=0;i<request.size();i++){Message m=request.get(i);roles[i]=m.role;texts[i]="user".equals(m.role)?TaskCatalog.prompt(m.task,m.text,m.toolContext):m.text;images[i]=m.image;}
                NativeBridge.setRepetitionProtection(repetitionProtection);
                error=NativeBridge.generate(roles,texts,images,webContext,maxTokens,token->{
                    if(first[0]==0)first[0]=SystemClock.elapsedRealtime()-requestStarted;bytes.write(token,0,token.length);long now=SystemClock.elapsedRealtime();if(now-update[0]>110){String value=GenerationPolicy.output(bytes.toByteArray(),-1);update[0]=now;post(()->{if(streamingRich!=null){output.setVisibility(View.GONE);streamingRich.setVisibility(View.VISIBLE);streamingRich.setSource(value);}else output.setText(value);if(!stopped)status.setText("本地模型正在回答");if(followOutput)scroll.post(()->{if(followOutput)scroll.fullScroll(View.FOCUS_DOWN);});});}
                });
            }catch(Throwable e){error=friendly(e);}
            getPreferences(0).edit().putBoolean("engineInFlight",false).commit();
            final String err=error==null?"推理回调中断":error;final String result=GenerationPolicy.output(bytes.toByteArray(),NativeBridge.keepOutputBytes());final int reason=NativeBridge.finishReason();
            post(()->{
                generating=false;answer.stopNote=GenerationPolicy.note(reason);answer.timing=first[0]>0?String.format(Locale.CHINA,"首字 %.1f 秒%s",first[0]/1000.0,answer.searched?"（含检索规划与联网）":""):"";
                answer.text=result.isEmpty()?(stopped?"已停止回答。":err.isEmpty()?"模型未输出内容，请重新回答。":"未生成回答："+err):result;
                answer.state=stopped?(result.isEmpty()?"stopped":"partial"):err.isEmpty()&&!result.isEmpty()?"complete":"error";
                setWorking(false);showReadyStatus();int oldY=scroll.getScrollY();boolean bottom=isNearBottom();renderChat();saveChat();scroll.post(()->{if(bottom)scroll.fullScroll(View.FOCUS_DOWN);else scroll.scrollTo(0,oldY);});
                if(!err.isEmpty()&&!stopped)new AlertDialog.Builder(this).setTitle("本次回答未完成").setMessage(err).setPositiveButton("知道了",null).show();
            });
        });
    }
    void watchProgress(){
        if(!generating||destroyed)return;
        if(!stopped){String value=NativeBridge.phase();if(!value.isEmpty())status.setText(value);}
        ui.postDelayed(progressTicker,500);
    }
    void finishStopped(Message answer){generating=false;answer.state="stopped";answer.mode="已停止";answer.text="已停止，本次未生成回答。";setWorking(false);showReadyStatus();renderChat();saveChat();}
    void retryLast(){
        if(busy||!loaded||messages.size()<2)return;Message last=messages.get(messages.size()-1);if(!"assistant".equals(last.role))return;
        boolean useWeb=last.searched;
        messages.remove(messages.size()-1);beginAnswer(useWeb);
    }
    boolean isNearBottom(){return timeline.getHeight()-scroll.getScrollY()-scroll.getHeight()<dp(220);}
    void chooseImage(boolean takePhoto){
        if(busy)return;
        try{if(!takePhoto){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.setType("image/*");intent.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(intent,10);}
        else{Uri uri=Uri.parse("content://cn.shiguang.qwenpocket.camera/capture.jpg");Intent intent=new Intent(MediaStore.ACTION_IMAGE_CAPTURE);intent.putExtra(MediaStore.EXTRA_OUTPUT,uri);intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION|Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("camera",uri));startActivityForResult(intent,11);}}
        catch(Exception e){toast("无法打开图片应用："+friendly(e));}
    }
    void showHistory(){if(busy)return;saveChat();new HistoryPanel(this).show();}
    void captureDraft(){if(active==null||input==null)return;String value=input.getText().toString();boolean changed=!active.draft.equals(value)||!active.draftImage.equals(pendingImage)||!active.draftEvidence.equals(pendingEvidence)||active.draftTask!=selectedTask;active.draft=value;active.draftImage=pendingImage;active.draftEvidence=pendingEvidence;active.draftTask=selectedTask;if(changed)active.updated=System.currentTimeMillis();}
    void applyDraft(){input.setText(active.draft);input.setSelection(input.length());pendingImage=active.draftImage;pendingEvidence=active.draftEvidence;selectedTask=active.draftTask;refreshAttachment();}
    void refreshAttachment(){if(attachmentNote!=null){attachmentNote.setVisibility(!pendingEvidence.isEmpty()||tools.useLibrary||selectedTask!=0?View.VISIBLE:View.GONE);attachmentNote.setText(!pendingEvidence.isEmpty()?"已附核对记录 · 点此查看 / 清除":tools.useLibrary?"本地资料问答已开启 · 管理资料":TaskCatalog.NAMES[selectedTask]+" · 查看实例");}thumbnail.setImageDrawable(null);if(!pendingImage.isEmpty()&&new File(pendingImage).exists()){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=8;thumbnail.setImageBitmap(BitmapFactory.decodeFile(pendingImage,o));imageRow.setVisibility(View.VISIBLE);imageLabel.setText("点图处理"+(pendingEvidence.isEmpty()?"":" · 已附核对记录"));}else{pendingImage="";imageRow.setVisibility(View.GONE);}if(taskButton!=null)taskButton.setText("任务");}
    void openWorkbench(String path){if(busy||path.isEmpty())return;try{new VisionWorkbench(this,path).show();}catch(Exception e){toast(friendly(e));}}
    void switchSession(Session target){if(busy||target.deleted)return;saveChat();active=target;messages=target.messages;chatWindowEnd=Integer.MAX_VALUE;applyDraft();webEnabled=false;refreshWeb();renderChat();saveChat();scroll.post(()->scroll.fullScroll(View.FOCUS_DOWN));}
    void newChat(){if(!busy)createSession();}
    void createSession(){
        saveChat();if(active==null||active.deleted||active.hasContent()){active=new Session();sessions.add(active);messages=active.messages;}
        chatWindowEnd=Integer.MAX_VALUE;applyDraft();webEnabled=false;refreshWeb();renderChat();saveChat();
    }
    void showSettings(){if(!busy){AppPages pages=new AppPages(this);pages.open(pages::model);}}
    void showUpdateHelp(){if(!busy){AppPages pages=new AppPages(this);pages.open(pages::update);}}
    void showReadyStatus(){
        String actual="CPU";
        status.setText("本地运行 · "+actual+" ▾");
    }
    void showSpeedProfiles(){if(!busy){AppPages pages=new AppPages(this);pages.open(pages::speed);}}
    void showModelInfo(){if(!busy){AppPages pages=new AppPages(this);pages.open(pages::about);}}
    void saveChat(){
        if(!storageWritable||active==null||(tools!=null&&tools.restoring))return;
        try{captureDraft();JSONArray all=new JSONArray();for(Session s:sessions)all.put(s.json());
            JSONObject data=new JSONObject().put("version",4).put("active",active.id).put("sessions",all);
            android.util.AtomicFile f=new android.util.AtomicFile(new File(getFilesDir(),"sessions.json"));FileOutputStream os=f.startWrite();try{os.write(data.toString().getBytes(StandardCharsets.UTF_8));f.finishWrite(os);}catch(Exception e){f.failWrite(os);throw e;}
        }catch(Exception e){toast("对话保存失败："+friendly(e));}
    }
    void restoreChat(){
        try{
            android.util.AtomicFile file=new android.util.AtomicFile(new File(getFilesDir(),"sessions.json"));
            if(file.getBaseFile().exists()||new File(getFilesDir(),"sessions.json.bak").exists()){
                String original=readAll(file.openRead());JSONObject data=new JSONObject(original);
                File backup=new File(getFilesDir(),"sessions-before-v3.json");if(data.optInt("version",1)<3&&!backup.exists()){try(FileOutputStream out=new FileOutputStream(backup)){out.write(original.getBytes(StandardCharsets.UTF_8));out.getFD().sync();}}
                JSONArray all=data.getJSONArray("sessions");String selected=data.optString("active","");
                for(int i=0;i<all.length();i++){Session s=Session.from(all.getJSONObject(i));sessions.add(s);if(s.id.equals(selected)&&!s.deleted)active=s;}
            }else{
                File legacy=new File(getFilesDir(),"chat.json");if(legacy.exists()){Session s=new Session();JSONArray items=new JSONArray(readAll(new FileInputStream(legacy)));for(int i=0;i<items.length();i++)s.messages.add(Message.from(items.getJSONObject(i)));sessions.add(s);active=s;}
            }
        }catch(Exception e){storageWritable=false;toast("历史记录读取失败，原文件已保留。本次对话暂不保存。");}
        if(active==null){active=new Session();sessions.add(active);}messages=active.messages;
    }
    static String readAll(InputStream in)throws IOException{try(InputStream stream=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=stream.read(b))!=-1)out.write(b,0,n);return out.toString("UTF-8");}}
    static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.US,"%02x",b&255));return s.toString();}
    static String friendly(Throwable e){String m=e.getMessage();return m==null?e.getClass().getSimpleName():m;}
    void toast(String t){Toast.makeText(this,t,Toast.LENGTH_LONG).show();}
    @Override protected void onPause(){saveChat();super.onPause();}
    @Override protected void onDestroy(){RichAnswerView.disposeTree(timeline);destroyed=true;stopped=true;SearchClient c=searchClient;if(c!=null)c.cancel();ui.removeCallbacks(progressTicker);NativeBridge.cancel();worker.execute(NativeBridge::unload);super.onDestroy();}
    void prepareModel(){
        setWorking(true);detail.setVisibility(View.VISIBLE);progress.setVisibility(View.VISIBLE);
        worker.execute(()->{try{
            modelDir.mkdirs();
            JSONObject manifest=new JSONObject(readAll(getAssets().open("models/manifest.json")));
            JSONArray available=manifest.getJSONArray("files"),files=new JSONArray();
            for(int i=0;i<available.length();i++){JSONObject f=available.getJSONObject(i);if(speed.contains(f.getString("name")))files.put(f);}
            if(files.length()!=2)throw new IOException("当前档位的模型清单不完整，请重新安装完整 APK。");
            long needed=0;
            for(int i=0;i<files.length();i++){JSONObject f=files.getJSONObject(i);File out=new File(modelDir,f.getString("name"));if(!out.exists()||out.length()!=f.getLong("size"))needed+=f.getLong("size");}
            if(new StatFs(modelDir.getAbsolutePath()).getAvailableBytes()<needed+256L*1024*1024)throw new IOException("空间不足，请至少预留 "+String.format(Locale.US,"%.1f",needed/1073741824.0+.3)+" GB 可用存储后重试。");
            long total=0;for(int i=0;i<files.length();i++)total+=files.getJSONObject(i).getLong("size");
            long done=0;byte[] buffer=new byte[1024*1024];
            for(int i=0;i<files.length();i++){
                JSONObject f=files.getJSONObject(i);String name=f.getString("name");long size=f.getLong("size");File out=new File(modelDir,name);File stamp=new File(modelDir,name+".verified");
                if(out.length()==size&&stamp.exists()&&readAll(new FileInputStream(stamp)).equals(f.getString("sha256"))){done+=size;continue;}
                if(out.isFile()&&out.length()==size){
                    MessageDigest existing=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(out)){int n;while((n=in.read(buffer))!=-1)existing.update(buffer,0,n);}
                    if(hex(existing.digest()).equals(f.getString("sha256"))){try(FileOutputStream os=new FileOutputStream(stamp)){os.write(f.getString("sha256").getBytes(StandardCharsets.UTF_8));}done+=size;continue;}
                }
                if(!manifest.optBoolean("weights_bundled",true))throw new IOException("这是小型升级包，尚未找到完整量化模型。请覆盖安装带模型的完整版并打开一次。不要卸载应用或清除数据。");
                File part=new File(modelDir,name+".part");MessageDigest digest=MessageDigest.getInstance("SHA-256");
                try(InputStream in=getAssets().open("models/"+name);FileOutputStream os=new FileOutputStream(part)){
                    int count;long last=0;while((count=in.read(buffer))!=-1){if(destroyed)throw new IOException("应用已关闭");os.write(buffer,0,count);digest.update(buffer,0,count);done+=count;long now=SystemClock.elapsedRealtime();if(now-last>200){int percent=(int)(done*100/total);post(()->{progress.setIndeterminate(false);progress.setProgress(percent);detail.setText("正在准备内置模型 "+percent+"% · 仅首次需要");});last=now;}}os.getFD().sync();
                }
                if(part.length()!=size||!hex(digest.digest()).equals(f.getString("sha256")))throw new IOException("模型文件校验失败，请重新安装完整 APK。");
                if(out.exists()&&!out.delete())throw new IOException("无法更新模型文件");if(!part.renameTo(out))throw new IOException("无法保存模型文件");
                try(FileOutputStream os=new FileOutputStream(stamp)){os.write(f.getString("sha256").getBytes(StandardCharsets.UTF_8));}
            }
            post(()->{status.setText("正在加载 · "+speed.label);detail.setText("正在载入 CPU 推理引擎。");progress.setIndeterminate(true);});
            getPreferences(0).edit().putBoolean("engineInFlight",false).commit();
            String error=NativeBridge.load(new File(modelDir,speed.languageFile).getPath(),new File(modelDir,speed.visionFile).getPath(),threads,false,imageTokens);
            getPreferences(0).edit().putBoolean("engineInFlight",false).commit();
            backendInfo=NativeBridge.backend();RuntimeDiagnostics.saveLoad(this,error);
            if(!error.isEmpty())throw new IOException(error);
            if(BuildVariant.LITE)removeLegacyModelFiles();
            loaded=true;post(()->{setWorking(importing);detail.setVisibility(View.GONE);showReadyStatus();detail.setText("Qwen3.5-0.8B · 自训练权重 · 飞行模式可用");progress.setVisibility(View.GONE);send.setEnabled(!importing);settings.setEnabled(!importing);fresh.setEnabled(!importing);attach.setEnabled(!importing);});
        }catch(Throwable e){post(()->{loaded=false;setWorking(false);status.setText("模型准备失败");detail.setText(friendly(e));progress.setVisibility(View.GONE);settings.setEnabled(true);fresh.setEnabled(true);new AlertDialog.Builder(this).setTitle("模型未能加载").setMessage(friendly(e)).setPositiveButton("重试",(d,w)->prepareModel()).setNeutralButton("切换档位",(d,w)->showSpeedProfiles()).setNegativeButton("关闭",null).show();});}});
    }
    void removeLegacyModelFiles(){
        // Only remove this app's obsolete extracted weights after the new model loads.
        try{
            File directory=modelDir.getCanonicalFile();
            for(String name:new String[]{"model-bf16.gguf","vision-f16.gguf"}){
                for(String suffix:new String[]{"",".verified",".part"}){
                    File old=new File(directory,name+suffix);
                    if(!old.getCanonicalFile().getParentFile().equals(directory))throw new IOException("Unexpected model path");
                    if(old.exists()&&(!old.isFile()||!old.delete()))throw new IOException("Cannot remove old model");
                }
            }
        }catch(IOException e){post(()->toast("量化模型已就绪，旧模型缓存未能完全清理。"));}
    }

    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;if(tools.activityResult(request,data))return;if(request!=10&&request!=11)return;Uri uri=request==10?(data==null?null:data.getData()):Uri.fromFile(new File(getCacheDir(),"capture.jpg"));if(uri!=null)importImage(uri,request==11);}
    void importImage(Uri uri,boolean capture){importing=true;setWorking(true);send.setEnabled(false);status.setText("正在读取图片");worker.execute(()->{try{
        ImageDecoder.Source source=ImageDecoder.createSource(getContentResolver(),uri);
        Bitmap bitmap=ImageDecoder.decodeBitmap(source,(decoder,info,s)->{int w=info.getSize().getWidth(),h=info.getSize().getHeight();double scale=Math.min(1,3072.0/Math.max(w,h));decoder.setTargetSize(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});
        File dir=new File(getFilesDir(),"images");dir.mkdirs();File out=new File(dir,UUID.randomUUID()+".jpg");try(FileOutputStream os=new FileOutputStream(out)){bitmap.compress(Bitmap.CompressFormat.JPEG,95,os);}bitmap.recycle();if(capture)new File(getCacheDir(),"capture.jpg").delete();post(()->{importing=false;pendingImage=out.getAbsolutePath();pendingEvidence="";refreshAttachment();setWorking(false);showReadyStatus();saveChat();});
    }catch(Throwable e){post(()->{setWorking(false);showReadyStatus();importing=false;toast("图片读取失败："+friendly(e));});}});}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(busy){toast("请等当前任务结束后重新分享");return;}handleShare(intent);}
    void handleShare(Intent intent){if(intent==null||!Intent.ACTION_SEND.equals(intent.getAction()))return;intent.setAction(null);saveChat();if(active.hasContent()){active=new Session();sessions.add(active);messages=active.messages;applyDraft();renderChat();}CharSequence shared=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);if(shared!=null){String t=shared.toString();input.setText(t.substring(0,Math.min(8000,t.length())));if(t.length()>8000)toast("分享文字超过 8000 字，已保留前段");}Uri image=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(image!=null&&intent.getType()!=null&&intent.getType().startsWith("image/"))importImage(image,false);saveChat();}
}
