package cn.shiguang.qwenpocket;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** One full-height surface with a real back stack for tools and app preferences. */
final class AppPages {
    final MainActivity a;
    final Dialog dialog;
    final ArrayList<Runnable> trail=new ArrayList<>();
    LinearLayout content;
    static final int SOFT=0xffedf4f0, LINE=0xffe1e9e4, DARK=0xff194f40;
    AppPages(MainActivity activity){
        a=activity;
        dialog=new Dialog(a,android.R.style.Theme_Material_Light_NoActionBar){
            @Override public void onBackPressed(){back();}
        };
    }
    void open(Runnable screen){trail.add(screen);screen.run();dialog.show();dialog.getWindow().setLayout(-1,-1);dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
    void go(Runnable screen){trail.add(screen);screen.run();}
    void back(){if(trail.size()<2){dialog.dismiss();return;}trail.remove(trail.size()-1);trail.get(trail.size()-1).run();}
    void refresh(){trail.get(trail.size()-1).run();}
    void page(String name){
        LinearLayout root=a.column();root.setBackgroundColor(MainActivity.BG);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(0,i.getSystemWindowInsetTop(),0,i.getSystemWindowInsetBottom());return i;});
        LinearLayout top=a.row();top.setPadding(a.dp(12),a.dp(6),a.dp(12),a.dp(6));
        Button back=a.button("‹",false);back.setTextSize(30);back.setBackgroundColor(Color.TRANSPARENT);back.setContentDescription("返回上一页");back.setOnClickListener(v->back());top.addView(back,new LinearLayout.LayoutParams(a.dp(48),a.dp(48)));
        TextView title=a.text(name,18,MainActivity.INK);title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.CENTER);top.addView(title,new LinearLayout.LayoutParams(0,a.dp(48),1));
        top.addView(new View(a),new LinearLayout.LayoutParams(a.dp(48),1));root.addView(top);
        ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setClipToPadding(false);content=a.column();content.setPadding(a.dp(20),a.dp(10),a.dp(20),a.dp(28));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));dialog.setContentView(root);
    }
    void section(String name,String detail){
        TextView title=a.text(name,16,MainActivity.INK);title.setTypeface(null,Typeface.BOLD);title.setPadding(a.dp(2),a.dp(20),0,a.dp(8));content.addView(title);
        if(!detail.isEmpty()){TextView note=a.text(detail,12,MainActivity.MUTED);note.setPadding(a.dp(2),0,a.dp(2),a.dp(12));note.setLineSpacing(a.dp(3),1);content.addView(note);}
    }
    void hero(String eyebrow,String title,String body){
        LinearLayout panel=a.column();panel.setPadding(a.dp(22),a.dp(20),a.dp(22),a.dp(22));panel.setBackground(a.shape(DARK,24));
        TextView label=a.text(eyebrow,11,0xffb9d9c9);panel.addView(label);
        TextView name=a.text(title,24,Color.WHITE);name.setTypeface(null,Typeface.BOLD);name.setPadding(0,a.dp(10),0,a.dp(10));panel.addView(name);
        TextView desc=a.text(body,14,0xffe1eee7);desc.setLineSpacing(a.dp(4),1);panel.addView(desc);content.addView(panel);
    }
    LinearLayout card(){LinearLayout card=a.column();card.setPadding(a.dp(16),a.dp(16),a.dp(16),a.dp(16));GradientDrawable bg=a.shape(Color.WHITE,20);bg.setStroke(a.dp(1),LINE);card.setBackground(bg);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=a.dp(10);content.addView(card,p);return card;}
    void row(String icon,String name,String description,String value,Runnable action){
        LinearLayout card=card(),line=a.row();Glyph glyph=new Glyph(icon);line.addView(glyph,new LinearLayout.LayoutParams(a.dp(40),a.dp(40)));
        LinearLayout words=a.column();words.setPadding(a.dp(12),0,a.dp(8),0);TextView title=a.text(name,15,MainActivity.INK);title.setTypeface(null,Typeface.BOLD);words.addView(title);
        if(!description.isEmpty()){TextView detail=a.text(description,12,MainActivity.MUTED);detail.setPadding(0,a.dp(5),0,0);detail.setLineSpacing(a.dp(2),1);words.addView(detail);}line.addView(words,new LinearLayout.LayoutParams(0,-2,1));
        if(!value.isEmpty()){TextView state=a.text(value,11,MainActivity.GREEN);state.setGravity(Gravity.CENTER);state.setPadding(a.dp(7),a.dp(5),a.dp(7),a.dp(5));state.setBackground(a.shape(SOFT,8));line.addView(state);}
        TextView arrow=a.text("›",24,MainActivity.MUTED);arrow.setPadding(a.dp(8),0,0,0);line.addView(arrow);card.addView(line);card.setFocusable(true);card.setContentDescription(name+"，"+description+"，"+value);card.setOnClickListener(v->{if(!a.busy)action.run();});
    }
    void paragraph(String title,String body){LinearLayout box=card();TextView label=a.text(title,16,MainActivity.INK);label.setTypeface(null,Typeface.BOLD);box.addView(label);TextView text=a.text(body,14,MainActivity.MUTED);text.setPadding(0,a.dp(10),0,0);text.setLineSpacing(a.dp(5),1);text.setTextIsSelectable(true);box.addView(text);}
    void tools(){
        page("任务与资料");hero("LOCAL MODEL","每个问题，都交给模型","计算、计数、读图与专业分析，\n由手机中的微调量化模型生成答案。");
        section("本地推理任务","每类任务配有 3 个实例，可直接试用或添加自己的图片。");
        row("image","看图问答","四类入口 · 十一种任务 · 33 个实例","本地模型",()->go(this::tasks));
        row("calc","数值计算","账单合计、表格求和、票据金额","模型推理",()->go(()->taskExamples(1)));
        row("cube","空间推理","展开图、相对面与图案朝向","模型推理",()->go(()->taskExamples(3)));
        row("book","资料问答","结合本机资料，由模型生成带来源的回答",a.tools.library.length()+" 份资料",()->go(this::library));
        section("输入与记录","");row("scan","图片工作台","裁剪、放大、标记位置，再交给模型","输入辅助",()->{if(a.pendingImage.isEmpty())go(this::imageEntry);else a.openWorkbench(a.pendingImage);});
        row("check","评测样例","保留模型回答与期望答案，检查推理效果","",()->a.tools.examples());
    }
    void tasks(){page("看图问答");hero("LOCAL MODEL","把问题交给模型","选择任务后回到聊天，添加图片并提问。\n模型会按任务核对图中信息和推理条件。");String[] desc={"","账单、表格中的数字与运算","计数、折叠、物理、专业题与图形规律","文字、局部细节与代码截图","成语、隐喻与画面线索"};row("book","自由提问","不限定任务 · 三个文字实例","",()->go(()->taskExamples(0)));for(int g=1;g<TaskCatalog.GROUPS.length;g++){final int group=g;row("image",TaskCatalog.GROUPS[g],desc[g],"本地模型",()->go(()->taskGroup(group)));}}
    void taskGroup(int group){page(TaskCatalog.GROUPS[group]);section("选择任务","回答由微调后的本地多模态模型生成。");for(int i=1;i<TaskCatalog.NAMES.length;i++)if(TaskCatalog.GROUP[i]==group){final int task=i;row("image",TaskCatalog.NAMES[i],TaskCatalog.GUIDE[i].split("。")[0],"",()->go(()->taskExamples(task)));}}
    void imageEntry(){page("图片工作台");hero("VISUAL WORKSPACE","先选择一张图片","保留图像细节，按需圈定要分析的区域。\n确认后的内容可以直接附到聊天问题中。");section("添加图片","");row("image","从相册选择","选择后点聊天输入框上方的缩略图进入工作台","",()->{dialog.dismiss();a.chooseImage(false);});row("scan","拍摄照片","适合纸质题目、票据和书页","",()->{dialog.dismiss();a.chooseImage(true);});}
    void taskExamples(int task){
        page(TaskCatalog.NAMES[task]);hero("QWEN3.5-0.8B",TaskCatalog.NAMES[task],"由本地模型分析并生成答案。\n可先试一个实例，也可换成自己的问题。");
        section("试试这些实例","文字实例可直接运行；拍图实例需要添加图片。这里没有预先写好的答案。");
        for(TaskExamples.Example example:TaskExamples.get(task))row(example.image?"image":"book",example.title,example.prompt,example.image?"拍图示例":"直接试用",()->{dialog.dismiss();a.useTask(task,example.prompt,example.image,true,null,"");});
        section("你的问题","");row("plus","自定义提问","添加图片，或描述条件和需要求解的内容","",()->{dialog.dismiss();a.useTask(task,"",false,false,null,"");});
    }
    void calculator(String recognized,java.util.function.Consumer<String> unused){
        page("数值计算");hero("LOCAL MODEL","让模型完成计算","填写算式、账单或表格数据，\n由本地模型给出计算过程与结果。");section("输入问题","模型生成结果可能出错，请核对重要数值。");LinearLayout box=card();EditText input=new EditText(a);input.setMinLines(3);input.setGravity(Gravity.TOP);input.setText(recognized);input.setHint("例如：12.50 + 8.30，优惠5元后是多少？");box.addView(input);row("calc","交给模型计算","返回对话中查看生成过程","本地模型",()->{String q=input.getText().toString().trim();if(q.isEmpty()){input.setError("请输入问题或数据");return;}dialog.dismiss();a.useTask(1,q,false,true,null,"");});row("book","查看三个实例","账单、表格与票据","",()->go(()->taskExamples(1)));
    }
    void cube(){taskExamples(3);}
    void library(){
        page("资料问答");hero("RETRIEVE + REASON","让资料参与回答","相关片段在手机上检索，本地模型结合资料\n回答问题，并标注引用的来源。");
        switchRow("结合资料回答","只检索下面启用的资料",a.tools.useLibrary,checked->{a.tools.useLibrary=checked;a.refreshAttachment();});
        row("plus","导入资料","TXT / Markdown / PDF · 保存在手机","",()->{dialog.dismiss();a.tools.openFile(20,"*/*",new String[]{"text/plain","text/markdown","application/pdf"});});
        section("我的资料",a.tools.library.length()==0?"导入第一份资料后，即可在聊天中提问。":"PDF 最多前 5 页由本地模型转写，请核对识别内容。");
        for(int i=0;i<a.tools.library.length();i++){final int index=i;JSONObject doc=a.tools.library.optJSONObject(i);if(doc==null)continue;row("book",doc.optString("name"),doc.optString("text").length()+" 字 · 点按预览和管理",doc.optBoolean("enabled",true)?"已启用":"已停用",()->go(()->document(index)));}
    }
    void document(int index){JSONObject doc=a.tools.library.optJSONObject(index);if(doc==null){back();return;}page("资料详情");switchRow("参与检索",doc.optString("name"),doc.optBoolean("enabled",true),checked->{try{doc.put("enabled",checked);a.tools.saveLibrary();}catch(Exception e){a.toast(MainActivity.friendly(e));}});String text=doc.optString("text");paragraph(doc.optString("name"),text.substring(0,Math.min(6000,text.length()))+(text.length()>6000?"\n\n仅预览前 6000 字，检索仍使用完整提取文本。":""));row("minus","移出资料库","原始文件保留","",()->new AlertDialog.Builder(a).setTitle("移出这份资料？").setMessage("只移除应用中的提取文本，原始文件不变。").setNegativeButton("返回",null).setPositiveButton("移出",(d,w)->{a.tools.library.remove(index);a.tools.saveLibrary();back();}).show());}
    void model(){
        page("模型与应用");String actual=a.loaded?"CPU 已就绪":"模型未就绪";
        hero("QWEN3.5-0.8B","微调模型 · 本地运行","W4/W8 量化版本  ·  "+actual+"\n图像理解与文本推理在手机完成。");
        section("模型运行","");row("chip","CPU 性能","线程设置与速度自检",a.threads+" 线程",()->go(this::speed));
        row("scan","看图精度","精细档保留更多视觉信息，处理时间更长",a.imageTokens+" tokens",()->go(this::imageBudget));
        row("sliders","回答长度","题目、图片与回答共享 8192 tokens 上下文",a.maxTokens+" tokens",()->go(this::length));
        switchRow("防重复生成","检测循环时停止，保留已经生成的有效内容",a.repetitionProtection,on->{a.repetitionProtection=on;a.getPreferences(0).edit().putBoolean("repetitionProtection",on).apply();});
        section("联网搜索","");row("globe","本地模型搜索网页","无需 API Key · 查看搜索流程与数据说明","",()->go(this::webInfo));
        section("本机数据","");
        row("book","备份与导入","迁移对话、图片、核对记录和资料","",()->a.tools.backupMenu());
        section("关于应用","");row("info","功能与设计说明","模型基础、功能分工与设计理念","",()->go(this::about));row("sliders","运行诊断","查看设备、推理后端和性能记录","",()->go(this::diagnostics));row("plus","应用更新","覆盖安装小型升级包，复用本机模型",BuildVariant.VERSION,()->go(this::update));
    }
    void switchRow(String name,String desc,boolean checked,java.util.function.Consumer<Boolean> change){LinearLayout box=card(),line=a.row();LinearLayout words=a.column();TextView t=a.text(name,15,MainActivity.INK);t.setTypeface(null,Typeface.BOLD);words.addView(t);TextView d=a.text(desc,12,MainActivity.MUTED);d.setPadding(0,a.dp(6),a.dp(10),0);words.addView(d);line.addView(words,new LinearLayout.LayoutParams(0,-2,1));Switch sw=new Switch(a);sw.setContentDescription(name);sw.setChecked(checked);sw.setOnCheckedChangeListener((v,on)->{if(a.busy){sw.setOnCheckedChangeListener(null);sw.setChecked(!on);sw.setEnabled(false);return;}change.accept(on);});line.addView(sw);box.addView(line);}
    void choose(String name,String desc,boolean selected,Runnable action){row("check",name,desc,selected?"已选择":"",action);}
    void reload(){dialog.dismiss();a.loaded=false;a.prepareModel();}
    void speed(){page("CPU 性能");section("本地 CPU 推理","图像理解和文本生成均在 CPU 上完成。当前后端："+a.backendInfo);row("chip","CPU 线程","调整后会重新加载模型",a.threads+" 线程",()->go(this::threads));row("sliders","速度自检","测量当前设置的首字和生成速度","",()->{dialog.dismiss();a.tools.benchmark();});row("info","运行诊断","查看引擎记录与近期退出原因","",()->go(this::diagnostics));}
    void threads(){page("CPU 线程");section("线程数量","更高的线程数不一定更快，可配合速度自检选择。");for(int n:new int[]{4,6,8})choose(n+" 线程",n==4?"预处理使用 6 线程":n==6?"预处理使用 8 线程":"推理与预处理均为 8 线程",a.threads==n,()->{a.threads=n;a.getPreferences(0).edit().putInt("threads",n).apply();reload();});}
    void length(){page("回答长度");section("最大生成长度","这是输出上限；模型可以提前结束。长输入可能减少可用输出预算。");String[] desc={"适合直接结论与简短问答","适合日常看图和分析","适合较完整的推导与说明","适合长回答，耗时与耗电可能增加"};int[] ns={512,1024,2048,4096};for(int i=0;i<ns.length;i++){final int n=ns[i];choose(n+" tokens",desc[i],a.maxTokens==n,()->{a.maxTokens=n;a.getPreferences(0).edit().putInt("maxTokens",n).apply();refresh();});}}
    void imageBudget(){page("看图精度");section("图像信息预算","局部小字可先在图片工作台裁剪、放大，再交给模型。");String[] names={"快速","均衡","精细"};int[] values={256,384,768};for(int i=0;i<values.length;i++){final int n=values[i];choose(names[i],n+" 图像 tokens",a.imageTokens==n,()->{a.imageTokens=n;a.getPreferences(0).edit().putInt("imageTokens",n).apply();reload();});}}
    void about(){page("功能与设计说明");hero("GUANZHI ASSISTANT","观知助手","以微调后的 Qwen3.5-0.8B 为基模，\n经量化后在手机本地运行的多模态助手。");section("模型基础","");paragraph("微调 → 量化 → 本地推理","应用使用经过微调的 Qwen3.5-0.8B，多模态模型结合图像和文字理解问题。量化降低权重体积与内存占用，使用 CPU 推理。无需云端模型 API Key；按需开启网页搜索，检索与总结由本地模型规划、生成。");section("任务与实例","");paragraph("答案由模型生成","数值计算、图形计数、空间推理、物理常识、细节判断、图文联想、代码分析、数学、化学、文字提取和图形规律，均由本地 LLM 生成答案。每类配有三个可试用实例，实例只提供问题，没有内置答案。");paragraph("资料问答","TXT / Markdown 在本机读取；PDF 最多前五页由同一本地模型转写，每页最多 1024 tokens，可能存在遗漏。相关资料片段交给模型回答，并保留来源。");section("设计理念","");paragraph("围绕提问展开","主页提供任务和实例；图片工作台只负责裁剪、放大与位置标记。识别、计算和分析统一回到对话，由模型生成结果，方便继续追问。历史记录保留输入、回答与反馈。");paragraph("结果如何理解","本版本不调用计算器或几何求解器来替代模型作答。模型也可能算错、漏数或识别错误，重要结果应核对。备份、图片裁剪、格式排版等应用操作由程序执行，不属于模型作答。");row("info","技术与数据说明","模型格式、开源来源与本地数据","",()->go(this::technical));}
    void technical(){page("技术与数据说明");paragraph("模型与引擎","微调 Qwen3.5-0.8B，语言权重混合 Q4_0/Q8_0、视觉 Q8_0；少量参数保留浮点格式。使用 llama.cpp CPU 引擎。上下文 8192 tokens，生成最多 4096 tokens。");paragraph("本地推理与网页搜索","应用不调用云端模型。开启联网搜索后，本地模型提炼检索词，应用读取必应公开搜索结果和网页，本地模型结合资料总结并标注来源。不需要搜索 API Key。仅检索词发送到搜索网站，图片、历史对话和本地资料不会上传；网页访问仍会向目标站点提供常规网络请求信息。网页需要登录、验证或无法读取时保留搜索摘要并明确标注。搜索网站改版或网络限制可能影响使用。");paragraph("模型任务与应用工具","已移除独立 OCR SDK、计算器和几何求解器，文字识别与任务答案由本地模型生成。图片处理、网页正文提取、资料检索、备份和界面排版由应用程序执行。");paragraph("技术来源","Qwen3.5：Apache-2.0\nllama.cpp：MIT\nKaTeX / Marked：MIT\nDOMPurify：Apache-2.0 / MPL-2.0\njsoup：MIT\n\n观知助手为独立应用，与千问官方无关联。");}
    void webInfo(){page("本地模型搜索网页");hero("LOCAL MODEL + WEB","让本地模型读网页","搜索关键词和最终总结由手机模型生成。\n应用负责访问网页，无需搜索 API Key。");paragraph("如何使用","在输入框下方打开“联网”，输入问题后发送。状态依次显示本地规划、获取网页、阅读网页和本地总结；回答下方可以查看检索词和原始来源。");paragraph("只获取必要的网页资料","只向必应发送本轮检索词，不上传图片、历史对话或本地资料。读取最多三条公开网页的正文节选；无法读取时使用已标注的搜索摘要。公开搜索入口可能受网络、验证和网站改版影响，失败时可以重试或选择仅本地回答。");}
    void diagnostics(){page("运行诊断");String report=RuntimeDiagnostics.report(a);paragraph("当前设备与模型",Build.MANUFACTURER+" "+Build.MODEL+"\nAndroid "+Build.VERSION.RELEASE+"\n实际后端："+a.backendInfo+"\n"+NativeBridge.stats());row("book","复制完整诊断","包含 CPU 引擎日志与近期退出记录","",()->{((ClipboardManager)a.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("运行诊断",report));a.toast("已复制完整诊断");});paragraph("最近一次加载",a.getPreferences(0).getString("lastEngineDiagnostic","暂无记录").split("引擎日志",2)[0]);paragraph("近期进程退出",RuntimeDiagnostics.lastExit(a));}
    void update(){page("应用更新");hero("GUANZHI ASSISTANT",BuildVariant.VERSION,"安装身份和签名保持一致，\n覆盖安装即可保留本机模型与历史。");section("更新方式","");paragraph("日常升级","下载不含千问权重的 Update APK 并覆盖安装。不要先卸载应用或清除数据；首次安装或模型缺失时，使用带量化模型的完整版。");paragraph("电脑直接传到手机","电脑与手机连接同一 Wi-Fi，在电脑上运行“启动手机更新.cmd”，手机扫码下载，再确认安装。下载服务只在你手动运行工具后开启。");}
    final class Glyph extends View {
        final String type;final Paint p=new Paint(3);
        Glyph(String type){super(a);this.type=type;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        protected void onDraw(Canvas c){float unit=getWidth()/40f;c.save();c.scale(unit,unit);p.setStyle(Paint.Style.FILL);p.setColor(SOFT);c.drawRoundRect(0,0,40,40,12,12,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.6f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setColor(MainActivity.GREEN);
            if(type.equals("scan")||type.equals("image")){c.drawRoundRect(10,10,30,30,4,4,p);c.drawLine(13,25,19,18,p);c.drawLine(19,18,27,25,p);c.drawCircle(25,15,1.5f,p);}
            else if(type.equals("book")){c.drawRoundRect(10,10,30,30,3,3,p);c.drawLine(16,10,16,30,p);c.drawLine(20,16,26,16,p);c.drawLine(20,21,26,21,p);}
            else if(type.equals("globe")){c.drawCircle(20,20,10,p);c.drawOval(15,10,25,30,p);c.drawLine(10,20,30,20,p);}
            else if(type.equals("cube")){Path path=new Path();path.moveTo(20,9);path.lineTo(30,15);path.lineTo(30,26);path.lineTo(20,32);path.lineTo(10,26);path.lineTo(10,15);path.close();c.drawPath(path,p);c.drawLine(10,15,20,21,p);c.drawLine(30,15,20,21,p);c.drawLine(20,21,20,32,p);}
            else if(type.equals("check")){c.drawCircle(20,20,10,p);c.drawLine(14,20,18,24,p);c.drawLine(18,24,26,16,p);}
            else if(type.equals("plus")||type.equals("minus")){c.drawLine(11,20,29,20,p);if(type.equals("plus"))c.drawLine(20,11,20,29,p);}
            else if(type.equals("info")){c.drawCircle(20,20,10,p);c.drawLine(20,18,20,26,p);c.drawPoint(20,14,p);}
            else if(type.equals("calc")){c.drawRoundRect(11,9,29,31,3,3,p);c.drawLine(15,15,25,15,p);for(int x=15;x<=25;x+=5)for(int y=21;y<=26;y+=5)c.drawPoint(x,y,p);}
            else if(type.equals("chip")){c.drawRoundRect(13,13,27,27,3,3,p);for(int n=16;n<=24;n+=8){c.drawLine(n,9,n,13,p);c.drawLine(n,27,n,31,p);c.drawLine(9,n,13,n,p);c.drawLine(27,n,31,n,p);}}
            else{for(int y=12;y<=28;y+=8){c.drawLine(10,y,30,y,p);c.drawCircle(y==20?24:16,y,2,p);}}c.restore();
        }
    }
}
