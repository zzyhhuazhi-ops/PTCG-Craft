package dev.hua.ptcglive;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Printed rarity and economy tier are separate: a promotional distribution is not a top rarity. */
public final class CardRarity {
    private record Reviewed(String printed,String economy,String source,String classification){}
    private static final Map<String,Reviewed> REVIEWED=load();
    private static Map<String,Reviewed> load(){
        try(var stream=CardRarity.class.getResourceAsStream("/data/ptcglive/rarity-pricing-review.json")){
            if(stream==null)throw new IllegalStateException("缺少罕度核对表");
            return Map.copyOf(new Gson().fromJson(new InputStreamReader(stream,StandardCharsets.UTF_8),new TypeToken<Map<String,Reviewed>>(){}.getType()));
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
    public static String printed(String id,String fallback){var r=REVIEWED.get(id);return r==null?fallback:r.printed();}
    public static String economy(String id,String printed){var r=REVIEWED.get(id);return r==null?economy(printed):r.economy();}
    public static String classification(String id){var r=REVIEWED.get(id);return r==null||r.classification()==null?"unknown":r.classification();}
    public static String classificationLabel(String id){return switch(classification(id)){
        case "cn_catalog"->"罕度依据：简中印刷记录";
        case "module_mapping"->"罕度依据：卡面类型映射（模组分类）";
        default->"罕度待核对：暂按基础档计价";
    };}
    public static String economy(String printed){
        String code=CardLabel.rarityCode(printed);
        return switch(code){
            case "C","U","R","RR","RRR","SR","AR","SAR","UR"->code;
            case "S","K","ACE","A","PR","CLASSIC"->"R";
            case "SSR","HR"->"SR";case "CHR"->"AR";case "CSR","MA"->"SAR";
            case "MUR","金卡","BWR"->"SPECIAL";
            case "PROMO"->"PROMO";
            default->"UNKNOWN";
        };
    }
    private CardRarity(){}
}
