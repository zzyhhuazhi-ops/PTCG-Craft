import dev.hua.ptcglive.*;
import dev.hua.ptcglive.collection.*;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Exact printing types, provenance, full coverage and display/search/economy agreement. */
public final class RarityClassificationTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        var cards=CardCatalog.load();
        var byId=new HashMap<String,CardCatalog.Entry>();cards.forEach(c->byId.put(c.id(),c));
        try(var stream=CardRarity.class.getResourceAsStream("/data/ptcglive/rarity-pricing-review.json")){
            var reviewed=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            check(reviewed.keySet().equals(byId.keySet()),"Every active printing must be classified exactly once");
            for(var card:cards){
                var row=reviewed.getAsJsonObject(card.id());
                check(!row.get("source").getAsString().isBlank(),"Missing provenance: "+card.id());
                check(Set.of("cn_catalog","module_mapping","unknown").contains(CardRarity.classification(card.id())),"Unlabelled origin");
                check(!CardLabel.rarityCode(card.rarityZh()).equals("OTHER"),"Unfilterable rarity: "+card.id());
                check(CollectionSearch.matches(card,0,0,CollectionFilters.ALL.toggle("rarity",card.rarityZh()),List.of()),"Filter disagrees: "+card.id());
                String tier=CardRarity.economy(card.id(),card.rarityZh());
                check(tier.equals(CardRarity.economy(card.rarityZh())),"ID-free consumer tier disagrees: "+card.id());
                if(CardRarity.classification(card.id()).equals("unknown"))check(tier.equals("UNKNOWN"),"Unknown promoted to highest rarity");
            }
        }
        // Promo distribution must not hide the actual printing type or promote ordinary cards.
        expect(byId,"bwbsp_en_001","R","R");
        expect(byId,"swshbsp_en_004","RR","RR");
        expect(byId,"swshbsp_en_005","RRR","RRR");
        expect(byId,"svbsp_en_053","SAR","SAR");
        expect(byId,"sv1_en_253","UR","UR");
        expect(byId,"bw1_en_115","待核对","UNKNOWN");
        expect(byId,"sc_cs1bc_175_sr","SR","SR");
        check(CardRarity.classification("svbsp_en_053").equals("module_mapping"),"Foreign promo claimed official CN rarity");
        check(CardRarity.classification("sc_cs1bc_175_sr").equals("cn_catalog"),"CN source lost");
        System.out.println("RarityClassificationTest passed: "+cards.size()+" printings, provenance, filters, unified economy and promo/gold/unknown regressions.");
    }
    static void expect(Map<String,CardCatalog.Entry> cards,String id,String printed,String tier){
        var card=Objects.requireNonNull(cards.get(id),id);
        check(card.rarityZh().equals(printed),id+" display "+card.rarityZh());
        check(CardRarity.economy(id,card.rarityZh()).equals(tier),id+" economy");
    }
}
