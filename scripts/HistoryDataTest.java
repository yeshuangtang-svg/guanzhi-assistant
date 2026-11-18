package cn.shiguang.qwenpocket;
import org.json.*;

public final class HistoryDataTest {
    static int checks;
    static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception {
        JSONObject legacy=new JSONObject().put("id","old-chat").put("updated",1234L).put("messages",new JSONArray()
            .put(new JSONObject().put("role","user").put("text","如何计算二次方程？").put("image","/local/image.jpg"))
            .put(new JSONObject().put("role","assistant").put("text","判别式 $b^2-4ac$ 决定根的数量").put("image","")));
        MainActivity.Session s=MainActivity.Session.from(legacy);
        check(s.id.equals("old-chat")&&s.updated==1234,"Legacy identity and date");
        check(!s.deleted&&!s.pinned&&s.draft.isEmpty(),"Migration defaults");
        check(s.messages.size()==2&&s.messages.get(0).image.equals("/local/image.jpg"),"Preserve images and messages");
        check(s.matches("判别式")&&s.preview("判别式").contains("判别式"),"Search assistant body");
        check(s.matches("二次方程")&&!s.matches("不存在"),"Search title");
        s.customTitle="代数复习";s.pinned=true;s.draft="下一题怎么做？";s.draftImage="/local/draft.jpg";s.deleted=true;
        MainActivity.Session restored=MainActivity.Session.from(s.json());
        check(restored.title().equals("代数复习")&&restored.pinned&&restored.deleted,"Metadata round-trip");
        check(restored.draft.equals(s.draft)&&restored.draftImage.equals(s.draftImage),"Preserve draft");
        check(restored.messages.get(1).text.equals(s.messages.get(1).text),"Preserve raw LaTeX");
        check(restored.updated==1234,"Serialization must not bump history order");
        restored.deleted=false;check(restored.messages.size()==2,"Recycle bin restoration");
        MainActivity.Session draft=new MainActivity.Session();draft.draft="仅保存草稿";check(draft.hasContent()&&draft.title().equals(draft.draft),"Draft-only history");
        draft.draft="";draft.draftImage="photo.jpg";check(draft.hasContent(),"Image-only draft");
        MainActivity.Message pending=MainActivity.Message.from(new JSONObject().put("role","assistant").put("text","").put("state","pending"));
        check(pending.state.equals("stopped")&&!pending.text.isEmpty(),"Interrupted answer recovery");
        s.draftTask=8;s.draftEvidence="核算：0.1+0.2=0.3";s.messages.get(0).task=1;s.messages.get(0).toolContext="[资料1] 单据";s.messages.get(1).feedback="OCR 文字错误";s.messages.get(1).expected="20.8";s.messages.get(1).comparison="直答/辅助对照";
        MainActivity.Session v4=MainActivity.Session.from(s.json());check(v4.draftTask==8&&v4.draftEvidence.equals(s.draftEvidence),"Task draft round trip");check(v4.messages.get(0).task==1&&v4.messages.get(0).toolContext.contains("资料1"),"Evidence persistence");check(v4.messages.get(1).expected.equals("20.8")&&v4.messages.get(1).comparison.equals("直答/辅助对照"),"Feedback round trip");MainActivity.Session evidenceOnly=new MainActivity.Session();evidenceOnly.draftEvidence="1+1=2";check(evidenceOnly.hasContent(),"Evidence-only draft survives");
        s.messages.get(1).searchQuery="iPhone 16 Pro 芯片";s.messages.get(1).searchFallback=true;MainActivity.Session searchRestored=MainActivity.Session.from(s.json());check(searchRestored.messages.get(1).searchFallback&&searchRestored.messages.get(1).searchQuery.equals("iPhone 16 Pro 芯片"),"Search provenance round trip");
        System.out.println("PASS "+checks+" history persistence and migration checks");
    }
}
