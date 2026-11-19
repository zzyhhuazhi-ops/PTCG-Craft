import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.view.MatchActionMatcher;
import java.util.*;

/** Native Dimensional Hand is a prerequisite for Memory Helix's official Bench exception. */
public final class GeneratedRuleBatch50Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B,WATCH=new UUID(0,5000);
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static AttackDefinition attack(String id){return BundledCombatData.attack(GeneratedRuleBatch8Test.entry(id).getAsJsonArray("attacks").get(1).getAsJsonObject(),EffectRegistry.standard());}
    static AttackDefinition attack(){return attack("sv3-5_en_065");}
    static ServerMatch fixture(AttackDefinition a){return GeneratedRuleBatch48Test.fixture(a,GeneratedRuleBatch48Test.hit(),false,false,List.of());}
    static UUID source(ServerMatch m){return m.benchPokemonView(A).getFirst().pokemonId();}
    static UUID top(ServerMatch m,UUID id){return EventTargetBindingTest.field(m).topCard(A,id).instanceId();}
    static long entry(ServerMatch m,UUID id){return EventTargetBindingTest.field(m).pokemonEntrySequence(A,id);}
    static void pay(ServerMatch m){GeneratedRuleBatch48Test.pay(m,A,source(m),2);}
    static void fire(ServerMatch m,AttackDefinition a){UUID id=source(m);m.attackFromBench(A,id,entry(m,id),top(m,id),a.id());}
    static void atomic(ServerMatch m,Runnable r){var a=m.clientView(A);var b=m.privateView(B);var random=m.randomTranscript();reject(r);check(a.equals(m.clientView(A))&&b.equals(m.privateView(B))&&random.equals(m.randomTranscript()),"Rejected Bench declaration changed board, zones or RNG");}
    public static void main(String[] args){printsAndCosts();sourceAuthority();sourceState();selfAndReaction();session();schema();System.out.println("GeneratedRuleBatch50Test passed: five actual Dimensional Hand prints and Psychic-2 cost, own Bench permission and source authority, exact entry/top-card revalidation, actual source SELF and damage reaction, Active/Bench status separation, issued card-face session actions with cost masking/replay/stale source protection, native Active and old constructor compatibility");}
    static void printsAndCosts(){var cards=BundledRules.loadCardDefinitions();var catalog=BundledCombatData.load();
        for(String id:List.of("sv3-5_en_065","sv3-5_en_188","sv3-5_en_201","sv4-5_en_215","svbsp_en_050")){
            check(cards.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"Actual Alakazam print not fully supported");var a=attack(id);check(a.mayUseFromBench()&&a.energyCost().equals(Map.of(EnergyType.PSYCHIC,2))&&a.baseDamage()==120&&a.damageType()==EnergyType.PSYCHIC,"Printed source permission, cost or damage changed");AttackProgramRules.validate(a);
            var j=GeneratedRuleBatch8Test.entry(id);check(j.get("hitPoints").getAsInt()==310&&j.get("prizeCards").getAsInt()==2&&!BundledCombatData.attack(j.getAsJsonArray("attacks").get(0).getAsJsonObject(),EffectRegistry.standard()).mayUseFromBench(),"Permission leaked to Mind Jack or printed profile changed");
            var m=fixture(a);atomic(m,()->fire(m,a));pay(m);int turn=m.turnNumber(),rng=m.randomTranscript().size();var activeEnergy=m.activePokemonView(A).attachedEnergyIds();fire(m,a);check(m.lastAttack().finalDamage()==120&&m.activePokemonView(B).damage()==120&&m.currentPlayer().equals(B)&&m.turnNumber()==turn+1&&m.randomTranscript().size()==rng,"Bench print did not resolve once");check(m.activePokemonView(A).attachedEnergyIds().equals(activeEnergy)&&m.benchPokemonView(A).getFirst().attachedEnergyIds().size()==2,"Used or discarded unrelated Active energy");
            var nativeActive=fixture(a);nativeActive.attack(A,a.id());check(nativeActive.lastAttack().finalDamage()==120,"Permission removed ordinary Active use");
        }
    }
    static void sourceAuthority(){var a=attack();var m=fixture(a);pay(m);UUID id=source(m),top=top(m,id);long entry=entry(m,id);
        for(Runnable r:List.<Runnable>of(()->m.attackFromBench(B,id,entry,top,a.id()),()->m.attackFromBench(A,m.activePokemonView(A).pokemonId(),entry,top,a.id()),()->m.attackFromBench(A,m.benchPokemonView(B).getFirst().pokemonId(),entry,top,a.id()),()->m.attackFromBench(A,id,entry+1,top,a.id()),()->m.attackFromBench(A,id,entry,UUID.randomUUID(),a.id()),()->m.attackFromBench(A,id,entry,top,"hit")))atomic(m,r);
        for(String mode:List.of("evolve","switch","reentry")){var n=fixture(a);pay(n);UUID old=source(n),face=top(n,old);long life=entry(n,old);
            if(mode.equals("evolve"))n.evolveByEffect(A,old,GeneratedRuleBatch48Test.hand(n,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            else if(mode.equals("switch"))n.switchActiveByEffect(A,A,old);
            else{n.movePokemonByEffect(A,A,old,LeavePlayDestination.HAND);n.playBasicPokemonToBench(A,face);check(!PokemonIdentityTestSupport.logical(n,A,face).equals(old),"Old physical card reentry reused old field identity");pay(n);}
            atomic(n,()->n.attackFromBench(A,old,life,face,a.id()));
        }
        fire(m,a);atomic(m,()->m.attackFromBench(A,id,entry,top,a.id()));
    }
    static void sourceState(){for(String mode:List.of("sleep","paralyze","confusion","lock","named")){var a=attack();var m=fixture(a);pay(m);UUID active=m.activePokemonView(A).pokemonId();var f=EventTargetBindingTest.field(m);
        if(mode.equals("lock"))m.preventAttackByEffect(A,A,active);else if(mode.equals("named"))f.lockAttackUntilAfterOwnerNextTurn(A,active,a);else m.applySpecialConditionByEffect(A,A,mode.equals("sleep")?SpecialCondition.ASLEEP:mode.equals("paralyze")?SpecialCondition.PARALYZED:SpecialCondition.CONFUSED);
        int rng=m.randomTranscript().size();fire(m,a);check(m.activePokemonView(B).damage()==120&&m.randomTranscript().subList(rng,m.randomTranscript().size()).stream().noneMatch(e->e.operation().startsWith("attack-confusion:")),"Unrelated Active's status or lock blocked Bench or consumed Confusion RNG");
    }
        var a=attack();var locked=fixture(a);pay(locked);locked.preventAttackByEffect(A,A,source(locked));check(locked.benchAttackOptions(A).isEmpty(),"Locked actual source still offered");atomic(locked,()->fire(locked,a));
    }
    static void selfAndReaction(){var j=GeneratedRuleBatch8Test.entry("sv3-5_en_065").getAsJsonArray("attacks").get(1).getAsJsonObject().deepCopy();j.addProperty("selfDamage",30);j.addProperty("selfHealAfterAttack",10);j.add("triggers",JsonParser.parseString("[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":30}]}]"));var a=BundledCombatData.attack(j,EffectRegistry.standard());var m=fixture(a);pay(m);m.placeDamageCountersByEffect(A,A,source(m),5);m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),2);fire(m,a);check(m.benchPokemonView(A).getFirst().damage()==40&&m.activePokemonView(A).damage()==20&&m.lastAttack().selfDamage()==30,"Bench SELF heal/self damage hit unrelated Active");
        var n=fixture(attack());pay(n);EventTargetBindingTest.field(n).retaliateUntilOwnerNextTurn(B,A,B,n.activePokemonView(B).pokemonId(),3);fire(n,attack());check(n.benchPokemonView(A).getFirst().damage()==30&&n.activePokemonView(A).damage()==0,"Damage reaction used mutable Active instead of actual Bench attacker");
    }
    static void session(){var a=attack();var m=fixture(a);m.preventAttackByEffect(A,A,m.activePokemonView(A).pokemonId());var s=MatchSession.multiplayer(m,A,B);s.addSpectator(WATCH);String key="field:"+source(m);var unpaid=s.view(A,A);var option=unpaid.options().stream().filter(o->o.labelZh().startsWith("攻击：备战")).findFirst().orElseThrow();check(option.cardKeys().getFirst().equals(key)&&!MatchActionMatcher.isAvailableAttackOption(option)&&MatchActionMatcher.cardFaceActions(unpaid.options(),key,true).isEmpty(),"Unpaid Bench action exposed or wrong source anchor");atomic(m,()->s.submit(A,A,unpaid.revision(),option.index()));pay(m);var paid=s.view(A,A);var actions=MatchActionMatcher.cardFaceActions(paid.options(),key,true);check(actions.size()==1&&actions.getFirst().labelZh().startsWith("攻击：备战")&&!paid.attackBlockZh().isBlank(),"Active block hid independently issued Bench action");
        check(MatchActionMatcher.cardFaceActions(paid.options(),"field:"+m.activePokemonView(A).pokemonId(),true).stream().noneMatch(MatchActionMatcher::isAttackOption),"Bench action attached to unrelated Active");for(UUID p:List.of(B,WATCH))check(s.view(p,p.equals(WATCH)?null:p).options().stream().noneMatch(o->o.labelZh().startsWith("攻击：备战")),"Foreign seat/spectator received source authority");
        var chosen=actions.getFirst();atomic(m,()->s.submit(B,A,paid.revision(),chosen.index()));s.submit(A,A,paid.revision(),chosen.index());check(m.activePokemonView(B).damage()==120&&m.currentPlayer().equals(B),"Issued Bench card-face action failed");atomic(m,()->s.submit(A,A,paid.revision(),chosen.index()));
        var n=fixture(a);pay(n);var stale=MatchSession.multiplayer(n,A,B);var issued=stale.view(A,A);var old=issued.options().stream().filter(o->o.labelZh().startsWith("攻击：备战")).findFirst().orElseThrow();n.evolveByEffect(A,source(n),GeneratedRuleBatch48Test.hand(n,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);atomic(n,()->stale.submit(A,A,issued.revision(),old.index()));
    }
    static void schema(){var j=GeneratedRuleBatch8Test.entry("sv3-5_en_065").getAsJsonArray("attacks").get(1).getAsJsonObject().deepCopy();for(JsonElement bad:List.of(new JsonPrimitive("true"),new JsonPrimitive(1),JsonNull.INSTANCE)){j.add("mayUseFromBench",bad);reject(()->BundledCombatData.attack(j,EffectRegistry.standard()));}var old=new AttackDefinition("old","old",Map.of(),10,EnergyType.COLORLESS,EffectSupport.SUPPORTED,null);check(!old.mayUseFromBench()&&attack().withEffectProgram(EffectProgram.EMPTY).mayUseFromBench(),"Old constructor default or program clone lost permission");}
}
