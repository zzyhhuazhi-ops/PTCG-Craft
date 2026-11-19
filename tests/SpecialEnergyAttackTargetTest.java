import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Public recipient eligibility uses physical Special Energy, never provided units or active card effects. */
public final class SpecialEnergyAttackTargetTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B,WATCH=new UUID(0,6400);
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static JsonObject json(){return JsonParser.parseString("""
        {"id":"hit","nameZh":"特殊能量目标","energyCost":{},"baseDamage":40,"damageType":"GRASS",
         "effectSupport":"SUPPORTED","damageTarget":"OPPONENT_ANY","targetCount":1,"targetHasSpecialEnergy":true}
        """).getAsJsonObject();}
    static AttackDefinition parse(JsonObject j){return BundledCombatData.attack(j,EffectRegistry.standard());}
    static AttackDefinition attack(){return parse(json());}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance attach(ServerMatch m,UUID p,UUID host,String id){var c=DynamicPassiveModifierTest.hand(m,p,id);m.attachEnergyByEffect(m.currentPlayer(),p,EffectSourceZone.HAND,c.instanceId(),p,host);return c;}
    static ServerMatch game(EnergyProfile e,AttackDefinition a,int resistance){return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicEnergyProvisionTest.stadium(),e,a,List.of(),resistance);}
    static ServerMatch game(){return game(EnergyProfile.fixed(EnergyType.COLORLESS,2),attack(),0);}
    static void atomic(ServerMatch m,Runnable r){var a=m.clientView(A);var b=m.privateView(B);var rng=m.randomTranscript();reject(r);check(a.equals(m.clientView(A))&&b.equals(m.privateView(B))&&rng.equals(m.randomTranscript()),"Rejected target changed zones/turn/selection/RNG");}
    public static void main(String[] args){physical();emptyAndBasic();lifecycle();session();costAndConfusion();adapters();schema();System.out.println("SpecialEnergyAttackTargetTest passed: physical Special Energy versus Basic/zero/wild/multiple units, suppression, live move/switch/return/discard, Active WR and Bench exclusion, empty recipients, atomic costs/RNG, authority/replay/privacy, native Bench/borrow/copy preservation and strict schema/phase guard");}
    static void physical(){for(var energy:List.of(new EnergyProfile(List.of()),EnergyProfile.fixed(EnergyType.COLORLESS,3),new EnergyProfile(List.of(new EnergyProfile.Unit(EnumSet.allOf(EnergyType.class))))))for(boolean bench:List.of(false,true))for(boolean suppressed:List.of(false,true)){
        var m=game(energy,attack(),30);UUID target=bench?bench(m,B):active(m,B);attach(m,B,target,"special");
        if(suppressed)m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"stadium").instanceId(),null);
        check(m.matchesAttackDamageTargetFilter(B,target,attack()),"Physical Special Energy vanished under provision/effect changes");
        m.attack(A,"hit",target);int damage=bench?m.benchPokemonView(B).getFirst().damage():m.activePokemonView(B).damage();check(damage==(bench?40:50),"Special recipient Active/Bench WR order wrong");
    }}
    static void emptyAndBasic(){for(boolean basic:List.of(false,true)){
        var m=game();if(basic){attach(m,B,active(m,B),"energy");attach(m,B,bench(m,B),"energy");}attach(m,A,active(m,A),"special");
        for(UUID target:List.of(active(m,A),bench(m,A),active(m,B),bench(m,B),new UUID(1,2)))atomic(m,()->m.attack(A,"hit",target));
        int turn=m.turnNumber();m.attack(A,"hit");check(m.activePokemonView(B).damage()==0&&m.benchPokemonView(B).getFirst().damage()==0&&m.lastAttack().finalDamage()==0&&m.currentPlayer().equals(B)&&m.turnNumber()==turn+1,"No recipient must be a legal no-damage attack");
    }}
    static void lifecycle(){var m=game();UUID from=active(m,B),to=bench(m,B);var energy=attach(m,B,from,"special");
        m.moveAttachedEnergyByEffect(A,B,from,B,to,List.of(energy.instanceId()));atomic(m,()->m.attack(A,"hit",from));check(m.matchesAttackDamageTargetFilter(B,to,attack()),"Moved Special not on new host");
        m.switchActiveByEffect(A,B,to);check(m.matchesAttackDamageTargetFilter(B,active(m,B),attack()),"Switch changed physical eligibility");
        m.returnAttachedEnergyToHandByEffect(A,B,to,List.of(energy.instanceId()));atomic(m,()->m.attack(A,"hit",to));check(!m.matchesAttackDamageTargetFilter(B,to,attack()),"Returned Special retained eligibility");
        m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,energy.instanceId(),B,to);m.discardAttachedEnergyByEffect(A,B,to,List.of(energy.instanceId()));check(!m.matchesAttackDamageTargetFilter(B,to,attack()),"Discarded Special retained eligibility");
        m.attack(A,"hit");check(m.lastAttack().finalDamage()==0,"Removed recipient fallback hit Active");
    }
    static void session(){var m=game();var e=attach(m,B,bench(m,B),"special");var s=MatchSession.multiplayer(m,A,B);s.addSpectator(WATCH);var rng=m.randomTranscript();var v=s.view(A,A);var choices=v.options().stream().filter(o->o.labelZh().contains("特殊能量目标")).toList();check(choices.size()==1&&choices.getFirst().cardKeys().contains("field:"+bench(m,B)),"UI offered ineligible recipients");
        var option=choices.getFirst();for(UUID viewer:List.of(B,WATCH)){var dto=s.view(viewer,viewer.equals(WATCH)?null:viewer);check(dto.options().stream().noneMatch(o->o.labelZh().contains("特殊能量目标")),"Foreign recipient authority");String serialized=new Gson().toJson(dto);for(var c:m.privateView(A).hand())check(!serialized.contains(c.instanceId().toString()),"Recipient choices leaked hidden hand identity");atomic(m,()->s.submit(viewer,A,v.revision(),option.index()));}
        check(rng.equals(m.randomTranscript()),"Reading choices consumed randomness");s.submit(A,A,v.revision(),option.index());check(m.benchPokemonView(B).getFirst().damage()==40,"Issued filtered choice failed");atomic(m,()->s.submit(A,A,v.revision(),option.index()));
        // Change the board through an issued Trainer action so this is the actual client revision contract.
        var item=BundledCombatData.trainer(JsonParser.parseString("{\"trainerCategory\":\"ITEM\",\"effects\":[{\"type\":\"return_energy\",\"target\":\"ALL_OPPONENT_POKEMON\",\"all\":true}]}").getAsJsonObject(),EffectRegistry.standard());
        var n=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.fixed(EnergyType.COLORLESS,2),attack(),List.of(),0,item);
        attach(n,B,bench(n,B),"special");var table=MatchSession.multiplayer(n,A,B);var issued=table.view(A,A);var stale=issued.options().stream().filter(o->o.labelZh().contains("特殊能量目标")).findFirst().orElseThrow();
        String itemKey="hand:"+DynamicPassiveModifierTest.hand(n,A,"item").instanceId();var returned=issued.options().stream().filter(o->o.cardKeys().contains(itemKey)).findFirst().orElseThrow();table.submit(A,A,issued.revision(),returned.index());check(n.benchPokemonView(B).getFirst().attachedEnergyIds().isEmpty(),"Issued Trainer did not remove Special Energy");
        atomic(n,()->table.submit(A,A,issued.revision(),stale.index()));atomic(n,()->n.attack(A,"hit",bench(n,B)));
        check(table.view(A,A).options().stream().filter(o->o.labelZh().contains("特殊能量目标")).allMatch(o->!o.cardKeys().contains("field:"+bench(n,B))),"Stale target stayed offered");
    }
    static void costAndConfusion(){var j=json();j.add("energyCost",JsonParser.parseString("{\"GRASS\":1}"));j.addProperty("discardSelectedAttachedEnergyAfterAttack",1);var m=game(EnergyProfile.fixed(EnergyType.COLORLESS,2),parse(j),0);
        atomic(m,()->m.attackWithEnergyDiscards(A,"hit",List.of(),List.of()));var paid=attach(m,A,active(m,A),"energy");m.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);atomic(m,()->m.attackWithEnergyDiscards(A,"hit",List.of(active(m,B)),List.of(paid.instanceId())));
        int rng=m.randomTranscript().size();m.attackWithEnergyDiscards(A,"hit",List.of(),List.of(paid.instanceId()));check(m.randomTranscript().subList(rng,m.randomTranscript().size()).stream().filter(x->x.operation().startsWith("attack-confusion:")).count()==1,"Empty-recipient attack skipped/doubled Confusion");
        boolean failed=m.activePokemonView(A).damage()==30;check(m.activePokemonView(A).attachedEnergyIds().contains(paid.instanceId())==failed&&m.lastAttack().finalDamage()==0&&m.currentPlayer().equals(B),"Failed/successful empty attack paid discard at wrong stage");
    }
    static void adapters(){var j=json();j.addProperty("mayUseFromBench",true);j.add("energyCost",JsonParser.parseString("{\"COLORLESS\":1}"));var a=parse(j);check(a.withEffectProgram(EffectProgram.EMPTY).targetHasSpecialEnergy(),"Program replacement lost recipient filter");var copied=CopyAttackProgram.materialize(CopyAttackTest.copier(),a,EnergyType.PSYCHIC);check(copied.targetHasSpecialEnergy()&&!copied.mayUseFromBench()&&copied.damageType()==EnergyType.PSYCHIC,"Copy lost filter or granted declaration permission");
        for(boolean borrowed:List.of(false,true)){
            var m=GeneratedRuleBatch51Test.fixture(List.of(a));UUID owner=GeneratedRuleBatch51Test.A,other=GeneratedRuleBatch51Test.B,source=borrowed?GeneratedRuleBatch51Test.benchMew(m):GeneratedRuleBatch51Test.provider(m);GeneratedRuleBatch51Test.pay(m,source,1);var e=GeneratedRuleBatch51Test.hand(m,other,"energy");UUID target=m.benchPokemonView(other).getFirst().pokemonId();m.attachEnergyByEffect(owner,other,EffectSourceZone.HAND,e.instanceId(),other,target);
            var table=MatchSession.multiplayer(m,owner,other);var v=table.view(owner,owner);String prefix=borrowed?"借用":"攻击：备战";var options=v.options().stream().filter(o->o.labelZh().contains(prefix)&&o.labelZh().contains("特殊能量目标")&&o.cardKeys().getFirst().equals("field:"+source)).toList();check(options.size()==1&&options.getFirst().cardKeys().contains("field:"+target),"Bench/borrow adapter lost filter");table.submit(owner,owner,v.revision(),options.getFirst().index());check(m.benchPokemonView(other).getFirst().damage()==40&&m.activePokemonView(other).damage()==0,"Bench/borrow adapter damaged ineligible target");
        }
    }
    static void schema(){for(JsonElement bad:List.of(new JsonPrimitive("true"),new JsonPrimitive(1),JsonNull.INSTANCE,new JsonArray())){var j=json();j.add("targetHasSpecialEnergy",bad);reject(()->parse(j));}
        for(String scope:List.of("OPPONENT_ACTIVE","OPPONENT_BENCH","ALL_OPPONENT_BENCH","ALL_OPPONENT_POKEMON","ALL_POKEMON")){var j=json();j.addProperty("damageTarget",scope);if(scope.startsWith("ALL"))j.addProperty("targetCount",0);reject(()->parse(j));}
        var count=json();count.addProperty("targetCount",2);reject(()->parse(count));var before=json();before.add("triggers",JsonParser.parseString("[{\"trigger\":\"BEFORE_ATTACK\",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}]"));reject(()->parse(before));
        var family=json();family.addProperty("targetPokemonFamilies","V");var filtered=parse(family);var m=game(EnergyProfile.fixed(EnergyType.COLORLESS,2),filtered,0);attach(m,B,active(m,B),"special");check(!m.matchesAttackDamageTargetFilter(B,active(m,B),filtered),"Energy predicate replaced family predicate");m.attack(A,"hit");check(m.lastAttack().finalDamage()==0,"Combined filter damaged wrong family");
        var old=new AttackDefinition("old","old",Map.of(),10,EnergyType.COLORLESS,EffectSupport.SUPPORTED,null);check(!old.targetHasSpecialEnergy(),"Old constructor changed target semantics");
    }
}
