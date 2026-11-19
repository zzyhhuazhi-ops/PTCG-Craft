import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Damage calculation retains attached Energy; attack-effect discards happen afterwards. */
public final class PlannedEnergyDiscardTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final String PLAN="{\"type\":\"plan_energy_discard\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":60,\"minimum\":0,\"energy_type\":\"FIRE\",\"bind\":\"selected\"}";
    static final String DAMAGE="{\"type\":\"damage_per_bound\",\"target\":\"OPPONENT_ACTIVE\",\"bind\":\"selected\",\"amount\":90}";
    static final String COMMIT="{\"type\":\"commit_energy_discard\",\"ref\":\"selected\"}";
    static final String PROBE="{\"type\":\"conditional\",\"conditions\":[{\"type\":\"ENERGY_COUNT\",\"target\":\"SELF\",\"value\":3}],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10}]}";
    static void check(boolean b,String reason){GeneratedRuleBatch8Test.check(b,reason);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static CardInstance attach(ServerMatch m,UUID owner,UUID host,String id){var c=hand(m,owner,id);m.attachEnergyByEffect(A,owner,EffectSourceZone.HAND,c.instanceId(),A,host);return c;}
    static void submit(ServerMatch m,List<CardInstance> cards){var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),cards.stream().map(CardInstance::instanceId).toList());}
    static AttackDefinition program(String before,String damage,String after){return GeneratedRuleBatch8Test.hit(0,false).withEffectProgram(BeforeDamageAttackTest.attack(before,damage,after).effectProgram());}
    static ServerMatch game(AttackDefinition attack){return GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.FIRE,EnergyType.COLORLESS,GeneratedRuleBatch8Test.trainer("swsh5_en_124"),attack);}
    static AttackDefinition inferno(String id){
        var data=GeneratedRuleBatch8Test.entry(id).getAsJsonArray("attacks");
        for(var element:data){var json=element.getAsJsonObject().deepCopy();if(!json.toString().contains("damage_per_bound"))continue;
            var triggers=json.getAsJsonArray("triggers");JsonObject plan=null;
            for(var trigger:triggers){var b=trigger.getAsJsonObject();if(b.get("trigger").getAsString().equals("BEFORE_ATTACK"))plan=b.getAsJsonArray("effects").get(0).getAsJsonObject();}
            check(plan!=null&&plan.get("type").getAsString().equals("plan_energy_discard"),"Published Inferno X must retain Energy through damage calculation");
            var a=BundledCombatData.attack(json,EffectRegistry.standard());AttackProgramRules.validate(a);return a;
        }throw new AssertionError("Missing Inferno X "+id);
    }
    public static void main(String[] args){actualPrints();liveCalculation();unitSelection();authorityAndOwnership();optionalAndAll();copiedActor();malformed();
        System.out.println("PlannedEnergyDiscardTest passed: six actual published Inferno X prints, current damage-time Energy facts, genuine pre-damage mutation, physical-card counts, own field and foreign physical ownership, zero/all/optional choices, server-issued selection authority and replay rejection, copied actor, closed cross-phase pairing");}
    static void actualPrints(){for(String id:List.of("me2_en_013","me2_en_109","me2_en_125","me2_en_130","mebsp_en_023","mebsp_en_029")){
        var a=inferno(id);check(a.baseDamage()==0&&a.energyCost().equals(Map.of(EnergyType.FIRE,2)),"Printed Inferno X base/cost changed "+id);
        for(int take:List.of(0,1,3)){var m=game(a);var attached=new ArrayList<CardInstance>();for(int i=0;i<3;i++)attached.add(attach(m,A,active(m,A),"energy"));
            m.attack(A,a.id());check(m.lastAttack()==null&&m.activePokemonView(A).attachedEnergyIds().size()==3,"Plan already discarded or resolved damage before selection");
            submit(m,attached.subList(0,take));check(m.lastAttack().finalDamage()==90*take&&m.activePokemonView(A).attachedEnergyIds().size()==3-take,"Actual Inferno X selected-card count/discard mismatch "+id);
            check(m.privateView(A).discard().containsAll(attached.subList(0,take)),"Selected physical Energy missing from owner discard");
        }
    }}
    static void liveCalculation(){for(boolean genuineBefore:List.of(false,true)){
        String prefix=genuineBefore?"{\"type\":\"discard_energy\",\"target\":\"SELF\",\"amount\":1,\"minimum\":1},":"";
        var a=program(prefix+PLAN,DAMAGE+","+PROBE,COMMIT);AttackProgramRules.validate(a);var m=game(a);var cs=new ArrayList<CardInstance>();for(int i=0;i<3;i++)cs.add(attach(m,A,active(m,A),"energy"));
        m.attack(A,a.id());if(genuineBefore)submit(m,List.of(cs.removeFirst()));submit(m,List.of(cs.getFirst()));
        check(m.lastAttack().finalDamage()==(genuineBefore?90:100),"Damage used post-discard state or froze facts before a genuine pre-damage effect");
    }
        var a=program(PLAN,DAMAGE+","+PROBE,COMMIT);var m=game(a);var special=attach(m,A,active(m,A),"special");attach(m,A,active(m,A),"energy");m.attack(A,a.id());submit(m,List.of(special));
        check(m.lastAttack().finalDamage()==100&&m.activePokemonView(A).attachedEnergyIds().size()==1,"Multi-unit Energy counted as multiple cards or removed before damage");
    }
    static void authorityAndOwnership(){var a=program(PLAN,DAMAGE,COMMIT);var m=game(a);var own=attach(m,A,active(m,A),"energy");var back=attach(m,A,bench(m,A),"energy");var foreign=attach(m,B,bench(m,A),"energy");attach(m,A,active(m,A),"wrong");
        var enemy=hand(m,B,"energy");m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,enemy.instanceId(),B,active(m,B));m.attack(A,a.id());var p=m.effectCardPrompt(A);
        check(new HashSet<>(p.candidates()).equals(Set.of(own,back,foreign)),"Own field selection confused controller, physical owner or Energy type");
        var snapshot=m.clientView(A);var rng=m.randomTranscript();
        reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(own.instanceId())));
        reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(enemy.instanceId())));
        reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(own.instanceId(),own.instanceId())));
        check(snapshot.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"Invalid choice changed state or random transcript");
        submit(m,List.of(back,foreign));check(m.lastAttack().finalDamage()==180&&m.privateView(A).discard().contains(back)&&m.privateView(B).discard().contains(foreign),"Cross-host commit lost physical owner");
        var done=m.clientView(A);reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(back.instanceId())));check(done.equals(m.clientView(A)),"Stale submission repeated the attack");
        check(m.replayTranscript().stream().filter(e->e.operation().equals("使用攻击")).count()==1,"Damage executed twice");
    }
    static void unitSelection(){for(boolean enough:List.of(false,true)){
        String plan=PLAN.replace("ALL_OWN_POKEMON","SELF").replace("\"amount\":60,\"minimum\":0","\"amount\":2,\"minimum\":2,\"count_mode\":\"UNITS\"");
        String damage="{\"type\":\"conditional\",\"conditions\":[{\"type\":\"BOUND_COUNT\",\"bind\":\"selected\",\"value\":2}],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":80}]}";
        var a=program(plan,damage,COMMIT);var m=game(a);var card=attach(m,A,active(m,A),enough?"special":"energy");m.attack(A,a.id());submit(m,List.of(card));check(m.lastAttack().finalDamage()==(enough?80:0)&&m.privateView(A).discard().contains(card),"Required Energy units confused with physical cards or insufficient selection received bonus");
    }
        String plan=PLAN.replace("ALL_OWN_POKEMON","SELF").replace("\"amount\":60,\"minimum\":0","\"amount\":2,\"minimum\":2,\"count_mode\":\"UNITS\"");var a=program(plan,DAMAGE,COMMIT);var m=game(a);var one=attach(m,A,active(m,A),"energy");attach(m,A,active(m,A),"energy");m.attack(A,a.id());var p=m.effectCardPrompt(A);var before=m.clientView(A);reject(()->submit(m,List.of(one)));check(before.equals(m.clientView(A)),"Insufficient unit choice mutated the match");submit(m,p.candidates());check(m.lastAttack().finalDamage()==180,"Two separate unit cards did not complete selection");
    }
    static void optionalAndAll(){String all=PLAN.replace("\"amount\":60,\"minimum\":0,","\"all\":true,");for(boolean decline:List.of(false,true)){
        var a=program("{\"type\":\"choice\",\"optional\":true,\"effects\":["+all+"]}",DAMAGE,COMMIT);AttackProgramRules.validate(a);var m=game(a);attach(m,A,active(m,A),"energy");attach(m,A,bench(m,A),"special");
        m.attack(A,a.id());var p=m.effectChoicePrompt(A);m.chooseEffectOption(A,p.resolutionId(),p.step(),decline?1:0);
        check(m.lastAttack().finalDamage()==(decline?0:180)&&m.activePokemonView(A).attachedEnergyIds().size()==(decline?1:0)&&m.benchPokemonView(A).getFirst().attachedEnergyIds().size()==(decline?1:0),"Optional all plan mutated on decline or counted units");
    }
        var a=program(PLAN,DAMAGE,COMMIT);var m=game(a);m.attack(A,a.id());check(!m.hasPendingEffectChoice()&&m.lastAttack().finalDamage()==0,"Empty selection stalled or reused a count");
    }
    static void copiedActor(){var a=program(PLAN.replace(",\"energy_type\":\"FIRE\"",""),DAMAGE,COMMIT);var m=CopyAttackTest.fixture(CopyAttackTest.copier(),a,EnergyType.COLORLESS,0);CopyAttackTest.pay(m);var choice=CopyAttackTest.choice(m);CopyAttackTest.copy(m,choice,List.of(),List.of());var p=m.effectCardPrompt(CopyAttackTest.A);
        check(p.candidates().size()==1&&m.activePokemonView(CopyAttackTest.A).attachedEnergyIds().contains(p.candidates().getFirst().instanceId()),"Copied plan read provider field instead of actual actor");var c=p.candidates().getFirst();m.submitEffectCards(CopyAttackTest.A,p.resolutionId(),p.step(),List.of(c.instanceId()));check(m.lastAttack().finalDamage()==90&&m.privateView(CopyAttackTest.A).discard().contains(c),"Copied commit used provider ownership or wrong damage");
    }
    static void malformed(){for(String[] bad:List.of(
        new String[]{PLAN,DAMAGE,""},new String[]{"","",COMMIT},new String[]{PLAN,DAMAGE,COMMIT.replace("selected","other")},
        new String[]{PLAN+",{\"type\":\"draw\",\"amount\":1}",DAMAGE,COMMIT},new String[]{PLAN,DAMAGE,"{\"type\":\"draw\",\"amount\":1},"+COMMIT},
        new String[]{PLAN+","+PLAN,DAMAGE,COMMIT},new String[]{PLAN,DAMAGE,COMMIT+","+COMMIT},
        new String[]{COMMIT,"",PLAN},new String[]{PLAN.replace("ALL_OWN_POKEMON","OPPONENT_ACTIVE"),DAMAGE,COMMIT},
        new String[]{PLAN.replace("\"amount\":60","\"count_mode\":\"DISTINCT_TYPES\",\"amount\":60"),DAMAGE,COMMIT},
        new String[]{PLAN,DAMAGE,COMMIT.replace("\"ref\"","\"bind\"")},new String[]{PLAN,DAMAGE,COMMIT.replace("\"type\"","\"target\":\"OWN_ACTIVE\",\"type\"")}
    ))reject(()->AttackProgramRules.validate(program(bad[0],bad[1],bad[2])));
        for(EffectTrigger trigger:List.of(EffectTrigger.ON_CARD_PLAYED,EffectTrigger.ONCE_PER_TURN))for(String effect:List.of(PLAN,COMMIT))reject(()->ResumableProgramRules.validate(new EffectProgram(List.of(BeforeDamageAttackTest.binding(trigger,effect))),trigger==EffectTrigger.ONCE_PER_TURN,trigger));
    }
}
