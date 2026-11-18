package cn.shiguang.qwenpocket;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.*;
import android.text.*;
import android.text.style.ForegroundColorSpan;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** Full-height, recycled history list with search and recoverable removal. */
final class HistoryPanel {
    final MainActivity a; final Dialog dialog; final EditText query; final TextView count;TextView empty;
    final ArrayList<Object> rows=new ArrayList<>(); final BaseAdapter adapter;
    final Handler handler=new Handler(Looper.getMainLooper()); final Runnable refresh=this::reload;
    String filter="全部";
    HistoryPanel(MainActivity activity){
        a=activity;dialog=new Dialog(a,android.R.style.Theme_Material_Light_NoActionBar);
        LinearLayout page=a.column();page.setBackgroundColor(MainActivity.BG);page.setPadding(a.dp(20),a.dp(12),a.dp(20),a.dp(12));
        page.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(a.dp(20),i.getSystemWindowInsetTop()+a.dp(8),a.dp(20),i.getSystemWindowInsetBottom()+a.dp(10));return i;});
        LinearLayout head=a.row();Button close=a.button("‹",false);close.setTextSize(28);close.setBackgroundColor(0);close.setContentDescription("返回聊天");close.setOnClickListener(v->dialog.dismiss());head.addView(close,new LinearLayout.LayoutParams(a.dp(44),a.dp(48)));
        TextView title=a.text("对话记录",20,MainActivity.INK);title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.CENTER);head.addView(title,new LinearLayout.LayoutParams(0,a.dp(52),1));
        Button fresh=a.button("＋",false);fresh.setTextSize(26);fresh.setBackgroundColor(0);fresh.setContentDescription("新建对话");fresh.setOnClickListener(v->{dialog.dismiss();a.newChat();});head.addView(fresh,new LinearLayout.LayoutParams(a.dp(44),a.dp(48)));page.addView(head);
        query=new EditText(a);query.setSingleLine(true);query.setTextSize(15);query.setHint("搜索你聊过的问题");query.setPadding(a.dp(14),a.dp(12),a.dp(14),a.dp(12));query.setBackground(ChatDesign.surface(a,0xffffffff,18));
        LinearLayout.LayoutParams qlp=new LinearLayout.LayoutParams(-1,a.dp(50));qlp.topMargin=a.dp(16);page.addView(query,qlp);
        LinearLayout tabs=a.row();for(String name:new String[]{"全部","已置顶","回收站"}){Button tab=a.button(name,false);tab.setTag(name);tab.setOnClickListener(v->{filter=name;for(int i=0;i<tabs.getChildCount();i++){Button b=(Button)tabs.getChildAt(i);boolean selected=name.equals(b.getTag());b.setTextColor(selected?0xffffffff:MainActivity.GREEN);b.setBackground(a.shape(selected?MainActivity.GREEN:0xffedf4f0,19));}reload();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,a.dp(38),1);lp.setMargins(0,a.dp(12),a.dp(5),0);tabs.addView(tab,lp);}page.addView(tabs);((Button)tabs.getChildAt(0)).performClick();
        count=a.text("",12,MainActivity.MUTED);count.setPadding(0,a.dp(12),0,a.dp(6));page.addView(count);
        ListView list=new ListView(a);list.setDivider(null);list.setCacheColorHint(0);list.setClipToPadding(false);list.setPadding(0,0,0,a.dp(12));
        adapter=new BaseAdapter(){
            public int getCount(){return rows.size();}public Object getItem(int p){return rows.get(p);}public long getItemId(int p){return p;}
            public int getViewTypeCount(){return 2;}public int getItemViewType(int p){return rows.get(p) instanceof String?0:1;}
            public boolean isEnabled(int p){return rows.get(p) instanceof MainActivity.Session;}
            public View getView(int position,View old,ViewGroup parent){
                Object item=rows.get(position);
                if(item instanceof String){TextView label=old instanceof TextView?(TextView)old:a.text("",12,MainActivity.MUTED);label.setText((String)item);label.setPadding(a.dp(2),a.dp(18),0,a.dp(8));return label;}
                MainActivity.Session session=(MainActivity.Session)item;Holder h;
                if(old!=null&&old.getTag() instanceof Holder)h=(Holder)old.getTag();else h=new Holder();
                h.title.setText(highlight(session.title()));h.preview.setText(highlight(session.preview(query.getText().toString().trim())));
                int turns=0;boolean image=false,web=false;for(MainActivity.Message m:session.messages){if("user".equals(m.role))turns++;image|=!m.image.isEmpty();web|=m.searched;}
                String when=new SimpleDateFormat("MM-dd HH:mm",Locale.CHINA).format(new Date(session.updated));
                h.meta.setText((session==a.active?"当前对话 · ":"")+(session.pinned?"置顶 · ":"")+when+(!session.draft.isEmpty()||!session.draftImage.isEmpty()?" · 有草稿":""));h.kind.setText(image?"图":"聊");
                android.graphics.drawable.GradientDrawable background=ChatDesign.surface(a,0xffffffff,20);if(session==a.active)background.setStroke(a.dp(1),0xff83b49a);h.card.setBackground(background);
                h.body.setOnClickListener(v->{if(session.deleted){restore(session);return;}a.switchSession(session);dialog.dismiss();});
                h.body.setOnLongClickListener(v->{actions(session,h.more);return true;});
                h.more.setContentDescription("管理对话："+session.title());h.more.setOnClickListener(v->actions(session,h.more));return h.outer;
            }
        };
        list.setAdapter(adapter);page.addView(list,new LinearLayout.LayoutParams(-1,0,1));empty=a.text("",16,MainActivity.MUTED);empty.setGravity(Gravity.CENTER);empty.setLineSpacing(a.dp(10),1);page.addView(empty,new LinearLayout.LayoutParams(-1,0,1));list.setEmptyView(empty);
        LinearLayout foot=a.row();Button search=a.button("任务与资料",false),settings=a.button("模型与应用",false);search.setOnClickListener(v->{dialog.dismiss();a.tools.show();});settings.setOnClickListener(v->{dialog.dismiss();a.showSettings();});foot.addView(search,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(0,-2,1);fp.leftMargin=a.dp(8);foot.addView(settings,fp);page.addView(foot);
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int f){}public void onTextChanged(CharSequence s,int st,int before,int c){handler.removeCallbacks(refresh);handler.postDelayed(refresh,120);}public void afterTextChanged(Editable e){}});
        dialog.setContentView(page);dialog.setOnDismissListener(d->handler.removeCallbacks(refresh));dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE|WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    }
    void show(){dialog.show();dialog.getWindow().setLayout(-1,-1);reload();}
    class Holder {
        final LinearLayout outer=a.column(),card=a.row(),body=a.column();final TextView title=a.text("",16,MainActivity.INK),preview=a.text("",13,MainActivity.MUTED),meta=a.text("",11,MainActivity.MUTED);final Button more=a.button("⋯",false);final TextView kind=a.text("聊",15,MainActivity.GREEN);
        Holder(){outer.setPadding(0,0,0,a.dp(9));card.setPadding(a.dp(12),a.dp(16),a.dp(4),a.dp(16));kind.setGravity(Gravity.CENTER);kind.setBackground(a.shape(0xffedf4f0,12));LinearLayout.LayoutParams badge=new LinearLayout.LayoutParams(a.dp(38),a.dp(42));badge.rightMargin=a.dp(12);card.addView(kind,badge);title.setTypeface(null,Typeface.BOLD);title.setMaxLines(1);title.setEllipsize(TextUtils.TruncateAt.END);preview.setMaxLines(1);preview.setEllipsize(TextUtils.TruncateAt.END);preview.setPadding(0,a.dp(5),0,a.dp(8));body.addView(title);body.addView(preview);body.addView(meta);card.addView(body,new LinearLayout.LayoutParams(0,-2,1));more.setBackgroundColor(0);more.setTextSize(22);card.addView(more,new LinearLayout.LayoutParams(a.dp(44),a.dp(48)));outer.addView(card);outer.setTag(this);}
    }
    CharSequence highlight(String value){String q=query.getText().toString().trim();int start=value.toLowerCase(Locale.ROOT).indexOf(q.toLowerCase(Locale.ROOT));if(q.isEmpty()||start<0)return value;SpannableString text=new SpannableString(value);text.setSpan(new ForegroundColorSpan(MainActivity.GREEN),start,Math.min(value.length(),start+q.length()),0);return text;}
    void reload(){
        if(count==null||adapter==null)return;
        rows.clear();String q=query.getText().toString().trim();ArrayList<MainActivity.Session> matches=new ArrayList<>();
        for(MainActivity.Session s:a.sessions)if(s.deleted==filter.equals("回收站")&&(!filter.equals("已置顶")||s.pinned)&&s.hasContent()&&s.matches(q))matches.add(s);
        Collections.sort(matches,(x,y)->{if(!filter.equals("回收站")&&x.pinned!=y.pinned)return x.pinned?-1:1;return Long.compare(y.updated,x.updated);});
        String previous="";for(MainActivity.Session s:matches){String group=s.pinned&&!s.deleted?"置顶":group(s.updated);if(!group.equals(previous)){rows.add(group);previous=group;}rows.add(s);}
        count.setText(matches.isEmpty()?(q.isEmpty()?(filter.equals("回收站")?"回收站为空 · 删除的对话可在此恢复":"还没有对话，从一个问题开始吧"):"没有找到匹配内容，试试其他关键词"):matches.size()+" 个对话"+(filter.equals("回收站")?" · 点击即可恢复":" · 按最近更新排列"));if(empty!=null)empty.setText(!q.isEmpty()?"没有找到相关对话\n换个关键词试试":filter.equals("回收站")?"回收站为空\n删除的对话可以在这里恢复":filter.equals("已置顶")?"还没有置顶对话\n在对话右侧菜单中添加":"从一个问题开始\n你的对话会保存在这台手机上");adapter.notifyDataSetChanged();
    }
    String group(long time){Calendar today=Calendar.getInstance();today.set(Calendar.HOUR_OF_DAY,0);today.set(Calendar.MINUTE,0);today.set(Calendar.SECOND,0);today.set(Calendar.MILLISECOND,0);long start=today.getTimeInMillis();if(time>=start)return "今天";today.add(Calendar.DAY_OF_YEAR,-1);if(time>=today.getTimeInMillis())return "昨天";today.add(Calendar.DAY_OF_YEAR,-6);return time>=today.getTimeInMillis()?"近 7 天":"更早";}
    void actions(MainActivity.Session s,View anchor){
        PopupMenu menu=new PopupMenu(a,anchor);if(s.deleted)menu.getMenu().add("恢复对话");else for(String label:new String[]{s.pinned?"取消置顶":"置顶对话","重命名","分享文本","移入回收站"})menu.getMenu().add(label);
        menu.setOnMenuItemClickListener(item->{String op=item.getTitle().toString();if(op.equals("恢复对话"))restore(s);else if(op.contains("置顶")){s.pinned=!s.pinned;a.saveChat();reload();}else if(op.equals("重命名"))rename(s);else if(op.equals("分享文本")){StringBuilder text=new StringBuilder(s.title()+"\n\n");for(MainActivity.Message m:s.messages){text.append(m.role.equals("user")?"你：\n":"观知助手：\n").append(m.text).append("\n\n");for(SearchClient.Source source:m.sources)text.append(source.title).append(" ").append(source.url).append("\n");}try{a.startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,text.toString()),"分享对话"));}catch(Exception e){a.toast("没有可用的分享应用");}}else new AlertDialog.Builder(a).setTitle("移入回收站？").setMessage("“"+s.title()+"”可以从回收站恢复，图片和消息会保留。").setNegativeButton("取消",null).setPositiveButton("移入",(d,w)->{s.deleted=true;if(s==a.active){a.createSession();}a.saveChat();reload();}).show();return true;});menu.show();
    }
    void rename(MainActivity.Session s){EditText name=new EditText(a);name.setSingleLine(true);name.setText(s.title());name.setSelectAllOnFocus(true);name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(60)});new AlertDialog.Builder(a).setTitle("重命名对话").setView(name).setNegativeButton("取消",null).setPositiveButton("保存",(d,w)->{String value=name.getText().toString().trim();if(value.isEmpty()){a.toast("标题不能为空");return;}s.customTitle=value;a.saveChat();reload();}).show();}
    void restore(MainActivity.Session s){s.deleted=false;a.saveChat();reload();a.toast("对话已恢复");}
}
