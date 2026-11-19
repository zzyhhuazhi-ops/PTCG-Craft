import dev.hua.ptcglive.rules.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import com.google.gson.*;

public final class ChineseSameNameLegalityTest {
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    public static void main(String[] args)throws Exception {
        verify(CurrentFormats.CHINESE);
        verify(CurrentFormats.INTERNATIONAL);
    }
    static void verify(String format)throws Exception {
        JsonObject raw;
        try(var stream=ChineseSameNameLegalityTest.class.getResourceAsStream("/data/ptcglive/rulesets/current/"+format+".json");var reader=new InputStreamReader(stream,StandardCharsets.UTF_8)) {
            raw=JsonParser.parseReader(reader).getAsJsonObject();
        }
        var before=BundledRules.ruleset(raw);var current=BundledRules.loadCurrentRulesets().get(format);
        var cards=BundledRules.loadCardDefinitions();var validNames=new HashSet<String>();
        for(var card:cards.all())if(!card.basicEnergy()&&card.effectSupport()==EffectSupport.SUPPORTED&&before.allows(card))validNames.add(card.category()+"\0"+card.nameZh());
        int admitted=0,pokemon=0,trainer=0;
        CardDefinition sample=null;
        for(var card:cards.all())if(!card.basicEnergy()&&!validNames.contains(card.category()+"\0"+card.nameZh()))
            check(current.allows(card)==before.allows(card),"Another format's legal names leaked into "+format+": "+card.id());
        for(var card:cards.all())if(!card.basicEnergy()&&!before.allows(card)&&validNames.contains(card.category()+"\0"+card.nameZh())&&!before.bannedCardIds().contains(card.id())) {
            check(current.allows(card),"Same-name printing remains blocked: "+card.id());admitted++;sample=card;
            if(card.category()==CardCategory.POKEMON)pokemon++;if(card.category()==CardCategory.TRAINER)trainer++;
        }
        check(admitted>0&&pokemon>0&&trainer>0,"No actual old Pokemon/Trainer reprints exercised");
        var bans=new JsonArray();bans.add(sample.id());raw.add("bannedCardIds",bans);
        var ids=new JsonArray();current.cardPool().legalCardIds().forEach(ids::add);raw.getAsJsonObject("cardPool").add("legalCardIds",ids);
        check(!BundledRules.ruleset(raw).allows(sample),"Same-name exception bypassed an explicit ban");
        var old=BundledRules.loadRulesets().get(format);
        check(old.cardPool().mode()!=CardPoolPolicy.Mode.REGULATION_MARKS_ANY_REGION,"Historical snapshot unexpectedly replaced");
        System.out.println("Same-name legality passed for "+format+": "+admitted+" additional printings; Pokemon="+pokemon+", Trainers="+trainer+"; bans and archived snapshots retained");
    }
}
