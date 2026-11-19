import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Conditional copy is a declaration gate, not a continuing condition on copied text. */
public final class ConditionalCopyAttackTest {
    static final UUID A=CopyAttackTest.A,B=CopyAttackTest.B,WATCH=CopyAttackTest.WATCH;
    static final String GATE="{\"type\":\"HAND_SIZE\",\"target\":\"SELF\",\"value\":0,\"comparison\":\"==\"}";
    static void check(boolean b,String why){CopyAttackTest.check(b,why);}
    static void reject(Runnable r){CopyAttackTest.reject(r);}
    static AttackDefinition copy(boolean bench,String condition,String extra){
        return BundledCombatData.attack(JsonParser.parseString("""
            {"id":"copy","nameZh":"条件复制","energyCost":{"COLORLESS":1},"baseDamage":0,
            "damageType":"PSYCHIC","effectSupport":"SUPPORTED","mayUseFromBench":%s,
            "triggers":[{"trigger":"ON_ATTACK","effects":[{"type":"copy_attack","target":"ALL_OPPONENT_POKEMON",
            "cost_policy":"DECLARED_ATTACK","conditions":[%s]}]}]%s}
            """.formatted(bench,condition,extra)).getAsJsonObject(),EffectRegistry.standard());
    }
    static AttackDefinition copy(){return copy(false,GATE,"");}
    static ServerMatch fixture(AttackDefinition copy,AttackDefinition body,int seed){return CopyAttackTest.fixture(copy,body,EnergyType.PSYCHIC,seed);}
    static ServerMatch fixture(){return fixture(copy(),CopyAttackTest.selected(""),0);}
    static void empty(ServerMatch m,UUID player){var ids=m.privateView(player).hand().stream().map(CardInstance::instanceId).toList();if(!ids.isEmpty())m.discardHandCardsByEffect(m.currentPlayer(),player,ids);}
    static void atomic(ServerMatch m,Runnable action){var a=m.clientView(A);var b=m.privateView(B);var rng=m.randomTranscript();reject(action);check(a.equals(m.clientView(A))&&b.equals(m.privateView(B))&&rng.equals(m.randomTranscript()),"Rejected conditional copy changed board/hidden zones/RNG");}
    public static void main(String[] args){nonempty();emptyHand();stale();continuation();sessions();borrowed();bench();confusionAndUsage();strict();System.out.println("ConditionalCopyAttackTest passed: actual owner empty-Hand gate, legal no-effect declaration with costs/turn, atomic stale/forged selection, phases after Hand changes, Active/Bench/borrowed session authority, confusion/shared usage and strict schema");}
    static void nonempty(){
        var m=fixture();check(!m.needsCopyAttackSelection(A,copy())&&m.copyAttackOptions(A,"copy").isEmpty(),"Nonempty Hand offered copy");
        atomic(m,()->m.attack(A,"copy"));CopyAttackTest.pay(m);
        var opponent=m.activePokemonView(B);atomic(m,()->m.copyAttackWithEnergyDiscards(A,"copy",opponent.pokemonId(),1,"hit",List.of(),List.of()));
        int turn=m.turnNumber();var rng=m.randomTranscript();var hand=m.privateView(A).hand();m.attack(A,"copy");
        check(m.currentPlayer().equals(B)&&m.turnNumber()==turn+1&&m.activePokemonView(B).damage()==0,"False gate did not spend exactly one turn with no effect");
        check(hand.equals(m.privateView(A).hand())&&rng.equals(m.randomTranscript()),"False gate changed own Hand or RNG");
    }
    static void emptyHand(){for(boolean bench:List.of(false,true)){
        var m=fixture();CopyAttackTest.pay(m);empty(m,A);check(!m.privateView(B).hand().isEmpty(),"Opponent Hand fixture missing");
        check(m.needsCopyAttackSelection(A,copy()),"Gate used copied provider Hand");atomic(m,()->m.attack(A,"copy"));
        var choices=m.copyAttackOptions(A,"copy");check(choices.stream().allMatch(c->c.owner().equals(B)),"Copy scope leaked own field");
        UUID source=bench?m.benchPokemonView(B).getFirst().pokemonId():m.activePokemonView(B).pokemonId();
        var c=choices.stream().filter(o->o.pokemonId().equals(source)).findFirst().orElseThrow();CopyAttackTest.copy(m,c,List.of(),List.of());
        check(m.activePokemonView(B).damage()==80&&m.currentPlayer().equals(B),"Empty-Hand copy lost actual type/cost or scope");
    }
        var unpaid=fixture();empty(unpaid,A);var c=CopyAttackTest.choice(unpaid);atomic(unpaid,()->CopyAttackTest.copy(unpaid,c,List.of(),List.of()));
    }
    static void stale(){var m=fixture();CopyAttackTest.pay(m);empty(m,A);var c=CopyAttackTest.choice(m);m.drawCardsByEffect(A,A,1);
        check(m.copyAttackOptions(A,"copy").isEmpty(),"Copy list not refreshed on Hand change");atomic(m,()->CopyAttackTest.copy(m,c,List.of(),List.of()));
        m.attack(A,"copy");check(m.activePokemonView(B).damage()==0,"Old copied effect survived false gate");
    }
    static void continuation(){var body=CopyAttackTest.selected("""
        ,"triggers":[{"trigger":"BEFORE_ATTACK","effects":[{"type":"search_deck","target":"DECK","amount":1,"minimum":1}]},
        {"trigger":"AFTER_ATTACK","effects":[{"type":"damage_counter","target":"SELF","amount":2}]}]
        """);var m=fixture(copy(),body,0);CopyAttackTest.pay(m);empty(m,A);CopyAttackTest.copy(m,CopyAttackTest.choice(m),List.of(),List.of());
        check(m.effectCardPrompt(A)!=null&&m.activePokemonView(B).damage()==0,"Missing pre-damage selection");BeforeDamageAttackTest.selectFirst(m,A);
        check(m.privateView(A).hand().size()==1&&m.activePokemonView(B).damage()==80&&m.activePokemonView(A).damage()==20&&m.currentPlayer().equals(B),"Gate was rechecked after copied text drew a card or SELF used provider");
    }
    static void sessions(){for(boolean empty:List.of(false,true)){
        var m=fixture();CopyAttackTest.pay(m);if(empty)empty(m,A);var s=MatchSession.multiplayer(m,A,B);s.addSpectator(WATCH);var v=s.view(A,A);
        var option=v.options().stream().filter(o->o.labelZh().startsWith(empty?"复制攻击：条件复制":"攻击：条件复制")).findFirst().orElseThrow();
        check(dev.hua.ptcglive.view.MatchActionMatcher.isAttackOption(option),"Conditional attack missing card-face action");
        atomic(m,()->s.submit(B,A,v.revision(),option.index()));atomic(m,()->s.submit(WATCH,A,v.revision(),option.index()));
        var before=m.randomTranscript();s.view(A,A);check(before.equals(m.randomTranscript()),"Read spent RNG");
        for(UUID p:List.of(B,WATCH)){var dto=s.view(p,p.equals(WATCH)?null:p);var json=new Gson().toJson(dto);check(!json.contains("effectProgram")&&!json.contains("effectiveAttack"),"Executable copied program exposed");}
        s.submit(A,A,v.revision(),option.index());check(m.activePokemonView(B).damage()==(empty?80:0)&&m.currentPlayer().equals(B),"Session conditional branch incorrect");atomic(m,()->s.submit(A,A,v.revision(),option.index()));
    }
    }
    static void borrowed(){for(boolean empty:List.of(false,true)){
        var m=GeneratedRuleBatch51Test.fixture(List.of(copy()));UUID a=GeneratedRuleBatch51Test.A,b=GeneratedRuleBatch51Test.B;
        GeneratedRuleBatch51Test.pay(m,GeneratedRuleBatch51Test.active(m),1);if(empty)empty(m,a);
        var o=GeneratedRuleBatch51Test.option(m,GeneratedRuleBatch51Test.active(m),"copy");
        var choices=m.borrowedCopyAttackOptions(a,o.holderId(),o.holderEntry(),o.holderTop(),o.abilityId(),o.providerId(),o.providerEntry(),o.providerTop(),o.attackId());check(choices.isEmpty()!=empty,"Borrowed gate used wrong owner");
        var s=MatchSession.multiplayer(m,a,b);var v=s.view(a,a);var action=v.options().stream().filter(x->x.labelZh().contains("借用")&&x.labelZh().contains("条件复制")&&(empty?x.labelZh().contains("plain-hit"):!x.labelZh().contains(" → "))).findFirst().orElseThrow();
        s.submit(a,a,v.revision(),action.index());check(m.activePokemonView(b).damage()==(empty?30:0)&&m.currentPlayer().equals(b),"Borrowed conditional session did not use actual holder");
    }}
    static void bench(){for(boolean empty:List.of(false,true)){
        var attack=copy(true,GATE,"");var m=fixture(attack,CopyAttackTest.selected(""),0);UUID host=m.benchPokemonView(A).getFirst().pokemonId();
        var energy=DynamicPassiveModifierTest.hand(m,A,"energy");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,energy.instanceId(),A,host);if(empty)empty(m,A);
        var s=MatchSession.multiplayer(m,A,B);var v=s.view(A,A);var o=v.options().stream().filter(x->x.labelZh().startsWith("攻击：备战")&&x.labelZh().contains("条件复制")).findFirst().orElseThrow();
        s.submit(A,A,v.revision(),o.index());check(m.activePokemonView(B).damage()==(empty?80:0)&&m.currentPlayer().equals(B)&&m.activePokemonView(A).attachedEnergyIds().isEmpty(),"Bench conditional copy used unrelated Active cost or failed");
    }}
    static void confusionAndUsage(){boolean success=false,failure=false;var gx=new SharedUsageLimit(SharedUsageLimit.GX,SharedUsageLimit.Period.GAME);
        for(int seed=0;seed<30&&!(success&&failure);seed++){var m=fixture(copy(false,GATE,",\"usageLimit\":{\"group\":\"gx-attack\",\"period\":\"GAME\"}"),CopyAttackTest.selected(""),seed);CopyAttackTest.pay(m);m.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);m.attack(A,"copy");
            boolean failed=m.activePokemonView(A).damage()==30;failure|=failed;success|=!failed;check(m.sharedUsageExhausted(A,gx)!=failed&&m.activePokemonView(B).damage()==0&&m.currentPlayer().equals(B),"False-gate attack skipped Confusion/shared usage contract");}
        check(success&&failure,"Confusion outcomes not covered");
    }
    static void strict(){AttackProgramRules.validate(copy());for(String gate:List.of(GATE.replace("SELF","OPPONENT_ACTIVE"),GATE.replace("\"value\":0","\"value\":1"),GATE.replace("==",">="),GATE.replace("HAND_SIZE","PRIZE_COUNT"),GATE+","+GATE)){reject(()->AttackProgramRules.validate(copy(false,gate,"")));}
        var valid=copy();var binding=valid.effectProgram().triggers().getFirst();var e=binding.effects().getFirst();
        var root=new TriggeredEffects(binding.trigger(),e.conditions(),List.of(new EffectDefinition(e.type(),e.target(),e.arguments(),List.of(),List.of())));
        reject(()->AttackProgramRules.validate(valid.withEffectProgram(new EffectProgram(List.of(root)))));
        reject(()->CopyAttackProgram.materialize(valid,valid,EnergyType.PSYCHIC));
    }
}
