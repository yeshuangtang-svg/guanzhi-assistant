from pathlib import Path
root=Path(__file__).resolve().parents[1]
p=root/'app/src/main/java/cn/shiguang/qwenpocket/MainActivity.java'
s=p.read_text(encoding='utf-8')
def sub(a,b):
 global s
 assert a in s,a[:120]
 s=s.replace(a,b)
sub('String pendingImage="";', 'String pendingImage="",pendingEvidence=""; int selectedTask=0; AppTools tools; Button taskButton;')
sub('maxTokens=512','maxTokens=1024')
sub('getInt("maxTokens",512)','getInt("maxTokens",1024)')
sub('boolean searched=false;', 'int task=0; String toolContext="",feedback="",expected="",comparison="";\n        boolean searched=false;')
sub('.put("mode",mode).put("stopNote",stopNote)', '.put("task",task).put("toolContext",toolContext).put("feedback",feedback).put("expected",expected).put("comparison",comparison).put("mode",mode).put("stopNote",stopNote)')
sub('m.stopNote=j.optString', 'm.task=TaskCatalog.safe(j.optInt("task",0));m.toolContext=j.optString("toolContext","");m.feedback=j.optString("feedback","");m.expected=j.optString("expected","");m.comparison=j.optString("comparison","");\n            m.stopNote=j.optString')
sub('boolean pinned=false,deleted=false;', 'String draftEvidence="";int draftTask=0;boolean pinned=false,deleted=false;')
sub('.put("draftImage",draftImage).put("messages",items)', '.put("draftImage",draftImage).put("draftEvidence",draftEvidence).put("draftTask",draftTask).put("messages",items)')
sub('s.draftImage=j.optString("draftImage","");', 's.draftImage=j.optString("draftImage","");s.draftEvidence=j.optString("draftEvidence","");s.draftTask=TaskCatalog.safe(j.optInt("draftTask",0));')
sub('buildUi();restoreChat();applyDraft();renderChat();prepareModel();','tools=new AppTools(this);buildUi();restoreChat();applyDraft();renderChat();prepareModel();handleShare(getIntent());')
sub('imageLabel=text("已添加图片",12,MUTED);','thumbnail.setOnClickListener(v->openWorkbench(pendingImage));\n        imageLabel=text("点击缩略图处理",12,MUTED);')
sub('pendingImage="";thumbnail.setImageDrawable(null);imageRow.setVisibility(View.GONE);});','pendingImage="";pendingEvidence="";refreshAttachment();saveChat();});')
sub('controls.addView(attach);','controls.addView(attach);taskButton=button("任务",false);taskButton.setBackgroundColor(Color.TRANSPARENT);taskButton.setOnClickListener(v->{if(!busy)tools.tasks();});controls.addView(taskButton);')
sub('String[] labels={"读懂图表","提取文字","分析题目","理解画面"};','String[] labels={"读图计算","看图解题","识别提取","趣味联想"};')
sub('b.setOnClickListener(v->input.setText(prompts[n]));','b.setOnClickListener(v->tools.taskGroup(n+1));')
sub('image.setScaleType(ImageView.ScaleType.FIT_CENTER);card.addView(image', 'image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setOnClickListener(v->openWorkbench(m.image));card.addView(image')
sub('card.addView(actions);','Button more=button("核对",false);more.setBackgroundColor(Color.TRANSPARENT);more.setOnClickListener(v->{if(!busy)tools.answerMenu(m);});actions.addView(more);card.addView(actions);')
sub('Message user=new Message("user",question,pendingImage);user.searched=webEnabled;messages.add(user);', 'Message user=new Message("user",question,pendingImage);user.task=selectedTask;user.toolContext=pendingEvidence;String refs=tools.references(question);if(!refs.isEmpty())user.toolContext+="\\n"+refs;user.searched=webEnabled;messages.add(user);')
sub('pendingImage="";thumbnail.setImageDrawable(null);imageRow.setVisibility(View.GONE);input.setText("");beginAnswer(webEnabled);','pendingImage="";pendingEvidence="";refreshAttachment();input.setText("");beginAnswer(webEnabled);')
sub('texts[i]=m.text;', 'texts[i]="user".equals(m.role)?TaskCatalog.prompt(m.task,m.text,m.toolContext):m.text;')
sub('void captureDraft(){if(active==null||input==null)return;', 'void captureDraft(){if(active==null||input==null)return;active.draftEvidence=pendingEvidence;active.draftTask=selectedTask;')
start=s.index('    void applyDraft()')
end=s.index('    void switchSession',start)
s=s[:start]+'''    void applyDraft(){input.setText(active.draft);input.setSelection(input.length());pendingImage=active.draftImage;pendingEvidence=active.draftEvidence;selectedTask=active.draftTask;refreshAttachment();}
    void refreshAttachment(){thumbnail.setImageDrawable(null);if(!pendingImage.isEmpty()&&new File(pendingImage).exists()){BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=8;thumbnail.setImageBitmap(BitmapFactory.decodeFile(pendingImage,o));imageRow.setVisibility(View.VISIBLE);imageLabel.setText("点图处理"+(pendingEvidence.isEmpty()?"":" · 已附核对记录"));}else{pendingImage="";imageRow.setVisibility(View.GONE);}if(taskButton!=null)taskButton.setText(selectedTask==0?"任务":TaskCatalog.NAMES[selectedTask]);}
    void openWorkbench(String path){if(busy||path.isEmpty())return;try{new VisionWorkbench(this,path).show();}catch(Exception e){toast(friendly(e));}}
''' +s[end:]
sub('"如何方便更新"},(d,n)->{','"如何方便更新","工具与资料","功能与设计说明"},(d,n)->{')
sub('new String[]{"256 tokens","512 tokens","1024 tokens"}', 'new String[]{"512 tokens · 简短","1024 tokens · 默认","2048 tokens · 详细","4096 tokens · 长回答"}')
sub('maxTokens=new int[]{256,512,1024}[b]', 'maxTokens=new int[]{512,1024,2048,4096}[b]')
sub('}else showUpdateHelp();','}else if(n==7)showUpdateHelp();else if(n==8)tools.show();else tools.about();')
sub('4096 tokens 上下文','8192 tokens 上下文，单次生成最多 4096 tokens（与题目、图片共享上下文）')
sub('.put("version",3)', '.put("version",4)')
start=s.index('    @Override protected void onActivityResult')
s=s[:start]+'''    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK)return;if(tools.activityResult(request,data))return;if(request!=10&&request!=11)return;Uri uri=request==10?(data==null?null:data.getData()):Uri.fromFile(new File(getCacheDir(),"capture.jpg"));if(uri!=null)importImage(uri,request==11);}
    void importImage(Uri uri,boolean capture){boolean wasLoading=busy;setWorking(true);send.setEnabled(false);status.setText("正在读取图片");worker.execute(()->{try{
        ImageDecoder.Source source=ImageDecoder.createSource(getContentResolver(),uri);
        Bitmap bitmap=ImageDecoder.decodeBitmap(source,(decoder,info,s)->{int w=info.getSize().getWidth(),h=info.getSize().getHeight();double scale=Math.min(1,3072.0/Math.max(w,h));decoder.setTargetSize(Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)));decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);});
        File dir=new File(getFilesDir(),"images");dir.mkdirs();File out=new File(dir,UUID.randomUUID()+".jpg");try(FileOutputStream os=new FileOutputStream(out)){bitmap.compress(Bitmap.CompressFormat.JPEG,95,os);}bitmap.recycle();if(capture)new File(getCacheDir(),"capture.jpg").delete();post(()->{pendingImage=out.getAbsolutePath();pendingEvidence="";refreshAttachment();setWorking(false);showReadyStatus();saveChat();});
    }catch(Throwable e){post(()->{setWorking(false);showReadyStatus();toast("图片读取失败："+friendly(e));});}});}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(busy){toast("请等当前任务结束后重新分享");return;}handleShare(intent);}
    void handleShare(Intent intent){if(intent==null||!Intent.ACTION_SEND.equals(intent.getAction()))return;intent.setAction(null);saveChat();if(active.hasContent()){active=new Session();sessions.add(active);messages=active.messages;applyDraft();renderChat();}CharSequence shared=intent.getCharSequenceExtra(Intent.EXTRA_TEXT);if(shared!=null){String t=shared.toString();input.setText(t.substring(0,Math.min(8000,t.length())));if(t.length()>8000)toast("分享文字超过 8000 字，已保留前段");}Uri image=intent.getParcelableExtra(Intent.EXTRA_STREAM);if(image!=null&&intent.getType()!=null&&intent.getType().startsWith("image/"))importImage(image,false);saveChat();}
}
'''
s=s.replace('千问·视界','观知助手').replace('千问视界诊断','观知助手诊断').replace('非千问官方 App','独立应用，与千问官方无关联')
p.write_text(s,encoding='utf-8')
p=root/'app/src/main/AndroidManifest.xml';s=p.read_text(encoding='utf-8').replace('千问·视界','观知助手').replace('android:exported="true" android:configChanges','android:exported="true" android:launchMode="singleTop" android:configChanges').replace('</activity>','''    <intent-filter><action android:name="android.intent.action.SEND" /><category android:name="android.intent.category.DEFAULT" /><data android:mimeType="image/*" /></intent-filter>
            <intent-filter><action android:name="android.intent.action.SEND" /><category android:name="android.intent.category.DEFAULT" /><data android:mimeType="text/plain" /></intent-filter>
        </activity>''');p.write_text(s,encoding='utf-8')
p=root/'app/src/main/java/cn/shiguang/qwenpocket/HistoryPanel.java';s=p.read_text(encoding='utf-8').replace('Button search=a.button("联网设置",false)','Button search=a.button("工具与资料",false)').replace('dialog.dismiss();a.showSearchSettings();','dialog.dismiss();a.tools.show();');p.write_text(s,encoding='utf-8')
