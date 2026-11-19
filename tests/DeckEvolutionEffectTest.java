import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import java.util.*;

/**
 * “Search your deck for a card that evolves from this Pokémon and put it onto this Pokémon to evolve it. Then, shuffle
 * your deck.”: search_deck with destination EVOLVE_SELF. Candidates come from the server's evolution check on the
 * effect's own source; the evolved Pokémon keeps its identity, damage and attachments.
 */
public final class DeckEvolutionEffectTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static final String EVOLVE="{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,\"card_category\":\"POKEMON\",\"destination\":\"EVOLVE_SELF\"}";
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static int deck(ServerMatch m,UUID owner){return m.clientView(owner).players().stream().filter(p->p.id().equals(owner)).findFirst().orElseThrow().deckSize();}
    public static void main(String[] args){
        evolvesThisPokemon(); mayChooseNothing(); nothingEvolvesFurther(); onlyEvolutionEventFires(); abilityEvolvesItsOwnPokemon(); invalidData();
        System.out.println("DeckEvolutionEffectTest passed: 检索进化（候选由服务器按进化来源判断、保留伤害与身份、清除特殊状态、只触发进化事件、找不到也洗牌）");
    }
    static void evolvesThisPokemon(){
        var a=BeforeDamageAttackTest.attack("","",EVOLVE);
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"检索进化被攻击规则拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        UUID active=m.activePokemonView(A).pokemonId();
        m.placeDamageCountersByEffect(A,A,active,3);
        m.applySpecialConditionByEffect(A,A,SpecialCondition.POISONED);
        var handIds=m.privateView(A).hand().stream().map(c->c.instanceId()).toList();
        int deck=deck(m,A);
        m.attack(A,a.id());
        check(m.activePokemonView(B).damage()==80,"进化应在伤害之后，攻击伤害按原样造成");
        var prompt=m.effectCardPrompt(A);
        check(prompt!=null&&!prompt.candidates().isEmpty(),"牌库里有能进化的卡却没有候选");
        check(prompt.candidates().stream().allMatch(c->c.definitionId().equals("evolved")),"候选里混进了不能从这只宝可梦进化的卡");
        check(prompt.candidates().stream().noneMatch(c->handIds.contains(c.instanceId())),"候选里混进了手牌");
        m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),List.of(prompt.candidates().getFirst().instanceId()));
        var evolved=m.activePokemonView(A);
        check(evolved.pokemonId().equals(active)&&evolved.topCardId().equals("evolved"),"战斗宝可梦没有在原地进化成所选的卡");
        check(evolved.damage()==30&&evolved.hitPoints()==350,"进化后伤害指示物没有保留，或 HP 没有换成进化后的");
        check(evolved.conditions().isEmpty(),"进化后特殊状态没有清除");
        check(deck(m,A)==deck-1&&m.privateView(A).hand().size()==handIds.size(),"进化卡没有从牌库移到场上，或误进了手牌");
        check(m.currentPlayer().equals(B),"检索进化后攻击没有结束");
    }
    static void mayChooseNothing(){
        var a=BeforeDamageAttackTest.attack("","",EVOLVE);
        var m=BeforeDamageAttackTest.fixture(a);
        int deck=deck(m,A);
        m.attack(A,a.id());
        var prompt=m.effectCardPrompt(A);
        m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),List.of());
        check(m.activePokemonView(A).topCardId().equals("basic")&&deck(m,A)==deck&&m.currentPlayer().equals(B),"选择不进化时应只洗牌并结束攻击");
        check(m.replayTranscript().stream().anyMatch(e->e.operation().contains("没有找到进化卡")),"不进化时没有记录洗牌");
    }
    static void nothingEvolvesFurther(){
        var a=BeforeDamageAttackTest.attack("","",EVOLVE);
        var m=BeforeDamageAttackTest.fixture(a);
        UUID active=m.activePokemonView(A).pokemonId();
        m.evolve(A,active,DynamicPassiveModifierTest.hand(m,A,"evolved").instanceId());
        int deck=deck(m,A);
        m.attack(A,a.id());
        check(m.currentPlayer().equals(B)&&deck(m,A)==deck&&m.activePokemonView(A).topCardId().equals("evolved"),"没有能从这只宝可梦进化的卡时不应等待选牌，攻击照常结束");
    }
    /** Hand evolution fires the evolution event and the card-played event; deck evolution only the former. */
    static void onlyEvolutionEventFires(){
        var onEvolve=triggered("evolve-draw","{\"trigger\":\"ON_EVOLVE\",\"watch\":\"SELF\",\"optional\":false,\"usage\":\"NONE\"}","ON_EVOLVE","{\"type\":\"draw\",\"amount\":1}");
        var onPlay=triggered("play-draw","{\"trigger\":\"ON_CARD_PLAYED\",\"player\":\"SELF\",\"card\":\"POKEMON\",\"optional\":false,\"usage\":\"NONE\"}","ON_CARD_PLAYED","{\"type\":\"draw\",\"amount\":1}");
        var a=BeforeDamageAttackTest.attack("","",EVOLVE);
        // 手牌进化：两种事件都有（夹具本身有效）；从牌库进化：只有进化事件。
        check(handEvolutionDraws(a,onEvolve)==1&&handEvolutionDraws(a,onPlay)>=1,"夹具的进化／出牌触发特性没有生效");
        check(deckEvolutionDraws(a,onEvolve)==1,"从牌库进化没有触发进化事件");
        check(deckEvolutionDraws(a,onPlay)==0,"从牌库进化误触发了从手牌出牌事件");
    }
    static int handEvolutionDraws(AttackDefinition a,AbilityDefinition ability){
        var m=BeforeDamageAttackTest.fixture(a,ability);
        int before=m.privateView(A).hand().size();
        m.evolve(A,m.activePokemonView(A).pokemonId(),DynamicPassiveModifierTest.hand(m,A,"evolved").instanceId());
        resolveTriggers(m);
        return m.privateView(A).hand().size()-(before-1);
    }
    static int deckEvolutionDraws(AttackDefinition a,AbilityDefinition ability){
        var m=BeforeDamageAttackTest.fixture(a,ability);
        int before=m.privateView(A).hand().size();
        m.attack(A,a.id());
        var prompt=m.effectCardPrompt(A);
        m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),List.of(prompt.candidates().getFirst().instanceId()));
        resolveTriggers(m);
        return m.privateView(A).hand().size()-before;
    }
    /** Every own Pokémon carries the fixture Ability, so a card-played event may need an order. */
    static void resolveTriggers(ServerMatch m){
        for(int guard=0;guard<12&&m.triggerOrderPrompt()!=null;guard++){
            var prompt=m.triggerOrderPrompt();
            if(prompt.optionalDecision())m.decideTriggeredEffect(prompt.choosingPlayer(),prompt.id(),true);
            else m.chooseTriggeredEffect(prompt.choosingPlayer(),prompt.id(),prompt.entries().getFirst().id());
        }
        check(m.triggerOrderPrompt()==null,"触发排序没有在有限步骤内结束");
    }
    static void abilityEvolvesItsOwnPokemon(){
        var ability=ResumableAbilityTest.ability("ascend",EVOLVE);
        var plain=DynamicPassiveModifierTest.attack(40,false,false);
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),
            EnergyProfile.basic(EnergyType.COLORLESS),plain,List.of(ability));
        UUID bench=m.benchPokemonView(A).getFirst().pokemonId();
        m.useAbility(A,bench,ability.id());
        var prompt=m.effectCardPrompt(A);
        check(prompt!=null&&prompt.candidates().stream().allMatch(c->c.definitionId().equals("evolved")),"特性检索进化没有给出候选");
        m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),List.of(prompt.candidates().getFirst().instanceId()));
        check(m.benchPokemonView(A).stream().anyMatch(p->p.pokemonId().equals(bench)&&p.topCardId().equals("evolved")),"备战区使用特性的宝可梦没有进化");
        check(m.activePokemonView(A).topCardId().equals("basic"),"进化了别的宝可梦");
    }
    static void invalidData(){
        var trainer=new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_CARD_PLAYED,EVOLVE)));
        check(ResumableProgramRules.unsupportedReason(trainer,false).isPresent(),"训练家没有“这只宝可梦”，不能检索进化");
        check(AttackProgramRules.unsupportedReason(BeforeDamageAttackTest.attack(EVOLVE,"","")).isPresent(),"检索进化不能写在伤害前");
        for(String bad:List.of(
            EVOLVE.replace("\"amount\":1","\"amount\":2"),
            EVOLVE.replace("\"minimum\":0","\"minimum\":1"),
            EVOLVE.replace("\"card_category\":\"POKEMON\"","\"card_category\":\"TRAINER\""),
            EVOLVE.replace("\"destination\"","\"basic_pokemon_only\":true,\"destination\""),
            EVOLVE.replace("\"destination\"","\"reveal\":true,\"destination\""),
            EVOLVE.replace("EVOLVE_SELF","EVOLVE")))
            BeforeDamageAttackTest.reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack("","",bad)));
        var m=BeforeDamageAttackTest.fixture(BeforeDamageAttackTest.attack("","",EVOLVE));
        var effect=EffectRegistry.standard().parse(JsonParser.parseString(EVOLVE).getAsJsonObject());
        BeforeDamageAttackTest.reject(()->m.openEffectDeckSearch(A,effect,0));
    }
    static AbilityDefinition triggered(String id,String policy,String trigger,String effects){
        return dev.hua.ptcglive.rules.BundledCombatData.ability(JsonParser.parseString("{\"id\":\""+id+"\",\"nameZh\":\"触发夹具\",\"kind\":\"TRIGGERED\",\"effectSupport\":\"SUPPORTED\",\"eventPolicy\":"
            +policy+",\"triggers\":[{\"trigger\":\""+trigger+"\",\"effects\":["+effects+"]}]}").getAsJsonObject(),EffectRegistry.standard());
    }
}
