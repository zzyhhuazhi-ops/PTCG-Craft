import com.google.gson.JsonParser;
import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.economy.EconomyConfig;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.packs.*;
import dev.hua.ptcglive.rules.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;

public final class SupporterHighRarityPackTest {
    public static void main(String[] args) throws Exception {
        var cards=CardCatalog.load(); var definitions=BundledRules.loadCardDefinitions(); var combat=BundledCombatData.load();
        var packs=CustomPackCatalog.loadBundled(cards,definitions);
        var pack=packs.get(EconomyConfig.SUPPORTER_HIGH_RARITY_PACK);
        check(pack!=null && pack.custom() && pack.displayNameZh().equals("全支援者高罕卡包"),"新主题未注册");
        check(pack.cardsPerPack()==5,"改变了原有每包张数");
        var ids=pack.pools().get("supporter").keySet();
        check(ids.contains("sc_csv5c_157_sar") && ids.contains("sc_csv10c_276_sar"),"男支援者仍被旧主题过滤");
        check(!ids.contains("sc_csmlc_005_unmarked"),"无稀有度标记的礼盒卡不能被推断为高罕");
        var stream=SupporterHighRarityPackTest.class.getResourceAsStream("/data/ptcglive/packs/supporter-high-rarity.json");
        var manifest=JsonParser.parseReader(new java.io.InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
        check(ids.size()==manifest.getAsJsonArray("cards").size(),"实际卡池与核对表不同");
        var printings=new HashSet<String>();
        for (var value:manifest.getAsJsonArray("cards")) {
            var row=value.getAsJsonObject(); String id=row.get("cardId").getAsString();
            check(row.get("cardType").getAsString().equals("Supporter"),"混入非支援者卡");
            boolean reused=row.get("reuseExistingArtwork").getAsBoolean();
            if (reused) {
                check(CardCatalog.loadBase().stream().anyMatch(c->c.id().equals(id)),"复用卡不在旧目录");
                check(id.equals(row.get("artworkId").getAsString()),"插画身份不一致");
            } else if (row.get("ruleStatus").getAsString().equals("EXISTING_REUSED")) {
                String source=row.get("ruleSourceId").getAsString();
                var def=definitions.find(id).orElseThrow();
                check(def.trainerCategory()==TrainerCategory.SUPPORTER && def.effectSupport()==EffectSupport.SUPPORTED,"已有规则被破坏");
                check(def.regions().equals(Set.of("cn")),"简中印刷被当作外国印刷");
                check(def.nameZh().equals(definitions.find(source).orElseThrow().nameZh()),"同效果异画破坏同名四张限制");
                check(combat.trainer(id).equals(combat.trainer(source)),"已有完整效果程序发生变化");
            } else {
                var def=definitions.find(id).orElseThrow();var profile=combat.trainer(id).orElseThrow();
                check(def.trainerCategory()==TrainerCategory.SUPPORTER&&def.effectSupport()==EffectSupport.SUPPORTED&&def.regions().equals(Set.of("cn")),"已补全支援者的规则、类别或印刷地区丢失");
                if(profile.effect().opcode()==TrainerEffect.Opcode.COMPOSED)
                    dev.hua.ptcglive.engine.effect.TrainerProgramRules.validate(profile.effectProgram());
            }
            check(printings.add(row.get("setCode").getAsString()+":"+row.get("cardIndex").getAsString()),"同一实体印刷重复入池");
            for (String path:List.of("card_art/"+id+".jpg","card_thumb/"+id+".jpg","textures/item/cards/"+id+".png")) {
                try(var image=SupporterHighRarityPackTest.class.getResourceAsStream("/assets/ptcglive_mc/"+path)) {
                    check(image!=null,"缺少真实卡图："+path);
                    var bitmap=javax.imageio.ImageIO.read(image);
                    check(bitmap!=null,"无法解码卡图："+path);
                    if (!reused && path.startsWith("card_art/") && row.get("hdStatus").getAsString().equals("HD_VERIFIED"))
                        check(bitmap.getWidth()==736 && bitmap.getHeight()==1024,"高清规格不匹配："+path);
                }
            }
        }
        var view=PackUiSnapshot.preview(UUID.randomUUID(),pack,cards,definitions);
        for(var row:view.cards()) check(Math.abs(row.chances().getFirst().probability()-1d/ids.size())<1e-12,"展示与实际概率不一致");
        var boxes=new CardBoxService(Clock.systemUTC()); var opening=new PackOpeningService(boxes); var owner=UUID.randomUUID();
        for(int seed=0;seed<100;seed++) {
            var result=opening.open(owner,pack,new ServerRandom(seed));
            check(result.cards().size()==5 && result.cards().stream().allMatch(c->ids.contains(c.definitionId())),"服务端抽取了池外卡或张数错误");
        }
        check(boxes.unboxedForOwner(owner).size()==500,"抽卡没有进入原有资产流程");
        check(EconomyConfig.loadBundled().packPrice(pack.setId())==2000,"新卡包没有采用2000币价格");
        var union=new HashSet<String>();
        for(String era:List.of("sm","swsh","sv")) {
            var subset=packs.get("cn_supporter_"+era);
            check(subset!=null && subset.custom() && subset.cardsPerPack()==5,"世代卡包未注册");
            check(EconomyConfig.loadBundled().packPrice(subset.setId())==2000,"世代卡包价格错误");
            var expected=new HashSet<String>();
            for(var value:manifest.getAsJsonArray("cards")) {
                var row=value.getAsJsonObject();
                if(era.equals(row.get("era").getAsString()))expected.add(row.get("cardId").getAsString());
            }
            check(expected.equals(subset.pools().get("supporter").keySet()),"世代筛选串池");
            for(String id:expected)check(union.add(id),"相同插画跨世代重复导入");
            for(int seed=0;seed<20;seed++) {
                var result=opening.open(owner,subset,new ServerRandom(seed));
                check(result.cards().size()==5 && result.cards().stream().allMatch(c->expected.contains(c.definitionId())),"世代服务端抽卡错误");
            }
            for(String path:List.of("textures/item/booster_packs/"+subset.setId()+"/main.png","models/item/"+subset.setId()+".json"))
                check(SupporterHighRarityPackTest.class.getResource("/assets/ptcglive/"+path)!=null,"卡包封面或物品模型缺失");
        }
        check(union.equals(ids),"世代卡包并集与总池不一致");
        check(boxes.unboxedForOwner(owner).size()==800,"世代卡包未进入原有资产流程");
        check(EconomyConfig.loadBundled().cartPrice(Map.of("cn_supporter_sm",2,"cn_supporter_sv",1,"simplified_chinese_exclusive",1,"all_energy_pack",1))==8020,"混合购物车价格错误");
        check(!packs.containsKey("cn_supporter_me"),"不得创建未经核实的空Mega卡池");
        System.out.println("SupporterHighRarityPackTest passed: "+ids.size()+" unique illustrations, 3 era pools, original rules, exact odds, 800 owned cards and 2000-coin price");
    }
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
