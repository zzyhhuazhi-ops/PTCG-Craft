import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Generated hand-entry/evolution events retain optionality, ownership and private selections. */
public final class GeneratedRuleBatch25Test {
    static final UUID A=GeneratedRuleBatch19Test.A,B=GeneratedRuleBatch19Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static AbilityDefinition ability(String id){return GeneratedRuleBatch8Test.ability(id);}
    static ServerMatch game(String id){return game(id,0);}
    static ServerMatch game(String id,int seed){return GeneratedRuleBatch24Test.fixture(List.of(ability(id)),seed,EnergyType.GRASS);}
    static void trigger(ServerMatch m,String id){if(ability(id).eventPolicy().trigger()==EffectTrigger.ON_ENTER_PLAY)m.playBasicPokemonToBench(A,hand(m,A,"basic").instanceId());else m.evolve(A,bench(m,A),hand(m,A,"evolved").instanceId());}
    static void optional(ServerMatch m,boolean use){GeneratedRuleBatch10Test.optional(m,use);}
    static void cards(ServerMatch m,List<UUID> ids){GeneratedRuleBatch10Test.select(m,ids);}
    static void target(ServerMatch m,UUID id){GeneratedRuleBatch10Test.target(m,id);}
    static boolean heads(ServerMatch m,int start){return GeneratedRuleBatch23Test.coinResult(m,start)==0;}
    public static void main(String[] args){recover();searches();energy();stadium();vanishedStadium();randomShuffle();coins();gustAndCounters();boundaries();invalid();System.out.println("GeneratedRuleBatch25Test passed: real optional hand entry/evolution, recovery/search privacy, Energy ownership, Stadium removal, random hand shuffle/reveal, coin outcomes, selection and source boundaries");}
    static void recover(){for(String id:List.of("sm1_en_104","swsh2_en_019"))for(boolean use:List.of(false,true)){
        var m=game(id);CardInstance wanted=hand(m,A,id.equals("sm1_en_104")?"item":"supporter"),wrong=hand(m,A,"energy");m.discardHandCardsByEffect(A,A,List.of(wanted.instanceId(),wrong.instanceId()));trigger(m,id);optional(m,use);
        if(use){var p=m.effectCardPrompt(A);check(p.minimum()==1&&p.candidates().equals(List.of(wanted)),"Event recovery wrong category");cards(m,List.of(wanted.instanceId()));}check(m.privateView(A).hand().contains(wanted)==use&&m.privateView(A).discard().contains(wrong),"Optional recovery did not honor decision");
    }}
    static void searches(){for(String id:List.of("sm2_en_055","sm11_en_144","sv4_en_053")){
        var m=game(id);GeneratedRuleBatch24Test.seedDeck(m,List.of("energy","wrong","special","tool","supporter","basic"));trigger(m,id);optional(m,true);var p=m.effectCardPrompt(A);Set<String> actual=new HashSet<>(p.candidates().stream().map(CardInstance::definitionId).toList());var expected=id.equals("sm2_en_055")?Set.of("energy","wrong"):id.equals("sv4_en_053")?Set.of("tool"):Set.of("energy","wrong","special","tool","supporter","basic");check(actual.equals(expected)&&p.minimum()==(id.equals("sm11_en_144")?1:0)&&m.clientView(B).effectSelection()==null,"Event search filter/minimum/privacy");var chosen=p.candidates().getFirst();cards(m,List.of(chosen.instanceId()));check(m.privateView(A).hand().contains(chosen)&&!m.hasPendingEffectChoice(),"Event search failed to resume");
    }}
    static void energy(){for(String id:List.of("sm4_en_070","sm9_en_082","sm11_en_086")){
        var m=game(id);UUID victim=id.equals("sm4_en_070")?bench(m,B):active(m,B);CardInstance special=hand(m,B,"special"),basic=hand(m,B,"energy");m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,special.instanceId(),B,victim);m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,basic.instanceId(),B,victim);trigger(m,id);optional(m,true);var p=m.effectCardPrompt(A);check(p.candidates().contains(special)&&p.candidates().contains(basic)==id.equals("sm9_en_082"),"Event discard Energy kind/host");cards(m,List.of(special.instanceId()));check(m.privateView(B).discard().contains(special)&&!m.privateView(A).discard().contains(special),"Event discarded Energy to wrong owner");
    }}
    static void stadium(){String id="sv8_en_056";for(boolean exists:List.of(false,true))for(boolean use:List.of(false,true)){
        var m=game(id);var card=hand(m,B,"stadium");if(exists){m.endTurn(A);m.playTrainer(B,card.instanceId(),null);m.endTurn(B);}trigger(m,id);if(exists)optional(m,use);else check(m.triggerOrderPrompt()==null,"Missing Stadium offered ineffective trigger");check(m.privateView(B).discard().contains(card)==(exists&&use)&&(m.clientView(null).stadium()==null)==(!exists||use),"Stadium event ignored owner/optional decision");
    }}
    static void vanishedStadium(){
        String id="sv8_en_056";var data=GeneratedRuleBatch8Test.entry(id).getAsJsonArray("abilities").get(0).getAsJsonObject().deepCopy();data.addProperty("id","second-snow-sink");var second=BundledCombatData.ability(data,EffectRegistry.standard());var m=GeneratedRuleBatch24Test.fixture(List.of(ability(id),second),0,EnergyType.GRASS);m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);trigger(m,id);
        for(int guard=0;guard<8&&m.triggerOrderPrompt()!=null;guard++){var p=m.triggerOrderPrompt();if(p.optionalDecision())m.decideTriggeredEffect(A,p.id(),true);else m.chooseTriggeredEffect(A,p.id(),p.entries().getFirst().id());}
        check(m.clientView(null).stadium()==null&&!m.hasPendingEffectChoice()&&m.triggerOrderPrompt()==null,"Stadium disappearing before another captured event blocked continuation");
        var manual=ResumableAbilityTest.ability("remove-stadium","{\"type\":\"discard_stadium\"}");var n=GeneratedRuleBatch24Test.fixture(List.of(manual),0,EnergyType.GRASS);check(!n.canUseAbility(A,bench(n,A),manual.id()),"Missing Stadium allowed an ineffective manual Ability");reject(()->n.useAbility(A,bench(n,A),manual.id()));
    }
    static void randomShuffle(){for(String id:List.of("sm12_en_151","sm12_en_152"))for(int count:List.of(0,1,3))for(boolean use:List.of(false,true)){
        var m=game(id);m.discardHandCardsByEffect(A,B,m.privateView(B).hand().stream().skip(count).map(CardInstance::instanceId).toList());var old=List.copyOf(m.privateView(B).hand());int deck=m.privateView(B).deck().size(),own=m.privateView(A).deck().size();trigger(m,id);if(count>0)optional(m,use);else check(m.triggerOrderPrompt()==null,"Empty hand offered no-effect shuffle");int moved=use?Math.min(count,id.equals("sm12_en_151")?1:2):0;var removed=old.stream().filter(c->!m.privateView(B).hand().contains(c)).toList();check(removed.size()==moved&&m.privateView(B).deck().size()==deck+moved&&m.privateView(B).deck().containsAll(removed)&&m.privateView(A).deck().size()==own,"Random hand shuffle wrong owner/count/identity");if(moved>0)check(m.replayTranscript().stream().anyMatch(e->e.operation().startsWith("已审核效果随机选手牌公开后洗回牌库：")&&removed.stream().allMatch(c->e.operation().contains(c.definitionId()))),"Selected shuffled cards not publicly recorded");check(!m.hasPendingEffectChoice(),"Immediate shuffle left pending choice");
    }}
    static void coins(){for(String id:List.of("swsh2_en_142","swsh11_en_060","sv6_en_138")){boolean h=false,t=false;for(int seed=0;seed<60&&!(h&&t);seed++){
        var m=game(id,seed);var supporter=hand(m,A,"supporter");m.discardHandCardsByEffect(A,A,List.of(supporter.instanceId()));int opponent=m.privateView(B).hand().size();trigger(m,id);int before=m.randomTranscript().size();optional(m,true);long heads=m.randomTranscript().subList(before,m.randomTranscript().size()).stream().filter(e->e.operation().startsWith("effect-coin:")&&e.result()==0).count();h|=heads>0;t|=heads==0;
        if(id.equals("swsh2_en_142")&&heads>0){var p=m.effectCardPrompt(A);check(p.candidates().equals(List.of(supporter)),"Coin event recovered wrong category");cards(m,List.of(supporter.instanceId()));}
        if(id.equals("swsh2_en_142"))check(m.privateView(A).hand().contains(supporter)==(heads>0),"Coin recovery branch");if(id.equals("swsh11_en_060"))check(m.activePokemonView(B).conditions().contains(SpecialCondition.PARALYZED)==(heads>0)&&m.activePokemonView(B).damage()==0,"Coin status wrong branch or ran checkup");if(id.equals("sv6_en_138"))check(m.privateView(B).hand().size()==opponent-heads,"Per-head random shuffle mismatch");check(!m.hasPendingEffectChoice(),"Event coin did not complete");
    }check(h&&t,"Both event coin outcomes not covered "+id);}}
    static void gustAndCounters(){var m=game("sv9_en_136");UUID switched=bench(m,B);trigger(m,"sv9_en_136");optional(m,true);target(m,switched);check(active(m,B).equals(switched),"Hand evolution gust target");var n=game("sv10_en_122");trigger(n,"sv10_en_122");optional(n,true);UUID a=active(n,B),b=bench(n,B);target(n,a);var p=n.effectPokemonPrompt(A);reject(()->n.chooseEffectPokemon(A,p.resolutionId(),p.step(),a));target(n,b);check(n.activePokemonView(B).damage()==20&&n.benchPokemonView(B).getFirst().damage()==20,"Distinct counter event recipients");}
    static void boundaries(){var a=ability("swsh2_en_019");var m=game("swsh2_en_019");check(m.triggerOrderPrompt()==null,"Opening triggered hand-entry ability");reject(()->m.useAbility(A,bench(m,A),a.id()));var supporter=hand(m,A,"supporter");m.discardHandCardsByEffect(A,A,List.of(supporter.instanceId()));var deck=m.privateView(A).deck().stream().filter(c->c.definitionId().equals("basic")).findFirst().orElseThrow();m.putBasicPokemonFromOwnDeckToBenchByEffect(A,deck.instanceId());check(m.triggerOrderPrompt()==null&&m.privateView(A).discard().contains(supporter),"Deck entry triggered HAND-only recovery");
        String id="sv8_en_004";var n=game(id);var before=n.privateView(B).deck();var top=before.getLast();trigger(n,id);optional(n,true);check(n.privateView(B).discard().contains(top)&&n.privateView(B).deck().size()==before.size()-1,"Hand entry mill wrong top/owner");
    }
    static void invalid(){for(String bad:List.of("{\"type\":\"shuffle_random_hand\",\"target\":\"TRIGGER_POKEMON\",\"amount\":1}","{\"type\":\"shuffle_random_hand\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":61}","{\"type\":\"shuffle_random_hand\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1,\"bind\":\"x\"}","{\"type\":\"discard_stadium\",\"target\":\"OPPONENT_ACTIVE\"}")){
        var data=GeneratedRuleBatch8Test.entry("sm12_en_151").getAsJsonArray("abilities").get(0).getAsJsonObject().deepCopy();data.getAsJsonArray("triggers").get(0).getAsJsonObject().getAsJsonArray("effects").set(0,JsonParser.parseString(bad));reject(()->TriggeredAbilityRules.validate(BundledCombatData.ability(data,EffectRegistry.standard())));
    }}
}
