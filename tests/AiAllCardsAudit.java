import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;
import java.nio.file.*;

/** Exhaustive static inventory; deliberately never labels static acceptance as executed coverage. */
public final class AiAllCardsAudit {
    public static void main(String[] args)throws Exception {
        var cards=BundledRules.loadCardDefinitions();var combat=BundledCombatData.load();
        var rules=BundledRules.loadRulesets().get("standard-international-2026");
        var rows=new ArrayList<Map<String,Object>>();var counts=new TreeMap<String,Integer>();
        for(var entry:CardCatalog.load()) {
            var definition=cards.find(entry.id());var row=new LinkedHashMap<String,Object>();
            row.put("id",entry.id());row.put("name",entry.label());
            String status;
            if(definition.isEmpty()){status="MISSING_RULES";row.put("reason","缺少战斗规则数据");}
            else if(definition.get().effectSupport()!=EffectSupport.SUPPORTED){status="ENGINE_UNSUPPORTED";row.put("reason",definition.get().unsupportedReasonZh());}
            else if(!engineProblems(definition.get(),cards,combat,rules).isEmpty()) {
                status="ENGINE_RULES_INVALID";row.put("reasons",engineProblems(definition.get(),cards,combat,rules));
            }
            else {
                var issues=AiSupport.problems(List.of(new CardInstance(new UUID(0,1),new UUID(0,2),entry.id())),cards,combat);
                status=issues.isEmpty()?"STATIC_PREFLIGHT_ACCEPTED":"AI_PREFLIGHT_BLOCKED";
                if(!issues.isEmpty()) {
                    var deck=List.of(new CardInstance(new UUID(0,1),new UUID(0,2),entry.id()));
                    if(AiSupport.matchProblems(deck,deck,cards,combat).isEmpty())status="REQUIRES_OPPONENT_PREFLIGHT";
                }
                row.put("reasons",issues);
            }
            row.put("status",status);row.put("executed",false);rows.add(row);counts.merge(status,1,Integer::sum);
        }
        var result=new LinkedHashMap<String,Object>();result.put("scope","所有目录卡逐卡静态审计，不等于所有卡已执行或完成对局");result.put("counts",counts);result.put("cards",rows);
        var output=Path.of("outputs/ai-all-cards-audit.json");Files.createDirectories(output.getParent());
        Files.writeString(output,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
        System.out.println("AiAllCardsAudit: "+rows.size()+" cards "+counts);
    }
    static List<String> engineProblems(CardDefinition card,CardDatabase cards,CombatCatalog combat,Ruleset rules){
        var deck=List.of(new CardInstance(new UUID(0,1),new UUID(0,2),card.id()));
        var construction=Set.of("牌组张数","缺少基础宝可梦","赛制不合法","卡牌合法性数据缺失","同名数量超限","棱镜之星数量超限","特殊构筑限制");
        var problems=new ArrayList<String>();
        new DeckValidator(cards,combat).validate(rules,deck).issues().stream()
            .filter(i->i.severity()==DeckIssue.Severity.ERROR&&!construction.contains(i.code()))
            .forEach(i->problems.add(i.messageZh()));
        // A quarter's printed moves alone are not the four-piece V-UNION summoning rule.
        combat.pokemon(card.id()).filter(p->p.pokemonName()!=null&&p.pokemonName().endsWith(" V-UNION"))
            .ifPresent(p->problems.add("V-UNION 四片组合上场规则尚未完成，不能把单片招式数据计为完整支持"));
        return List.copyOf(problems);
    }
}
