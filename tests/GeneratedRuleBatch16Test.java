import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Generated protection programs against authoritative card families and live field facts. */
public final class GeneratedRuleBatch16Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static AttackDefinition strike(boolean bypass,boolean spread){return BundledCombatData.attack(JsonParser.parseString("""
        {"id":"strike","nameZh":"测试攻击","energyCost":{"COLORLESS":1},"baseDamage":100,"damageType":"COLORLESS","effectSupport":"SUPPORTED",
         "ignoresActiveDefenderDamageEffects":%s,"damageTarget":"%s",
         "triggers":[{"trigger":"AFTER_ATTACK","effects":[
           {"type":"damage_counter","target":"OPPONENT_ACTIVE","amount":2},
           {"type":"poison","target":"OPPONENT_ACTIVE"},
           {"type":"draw","target":"SELF","amount":1}]}]}
        """.formatted(bypass,spread?"ALL_OPPONENT_BENCH":"OPPONENT_ACTIVE")).getAsJsonObject(),EffectRegistry.standard());}
    static ServerMatch fixture(String guard,Set<PokemonFamily> families,String attackerName,boolean evolved,boolean bypass,boolean spread){
        var ability=GeneratedRuleBatch8Test.ability(guard);var attack=strike(bypass,spread);var defs=new ArrayList<CardDefinition>();
        for(String id:List.of("attacker","evolved","defender","energy","special")){
            boolean pokemon=Set.of("attacker","evolved","defender").contains(id);
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:CardCategory.ENERGY,TrainerCategory.NONE,"H",Set.of("international"),
                id.equals("attacker")||id.equals("defender"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null,
                id.equals(evolved?"evolved":"attacker")?families:Set.of(),pokemon?Set.of(EnergyType.COLORLESS):Set.of()));
        }
        var profiles=new HashMap<String,PokemonProfile>();
        for(String id:List.of("attacker","evolved","defender"))profiles.put(id,new PokemonProfile(500,1,id.equals("evolved")?"attacker":null,null,0,null,0,1,List.of(attack),id.equals("defender")?List.of(ability):List.of(),EnergyType.COLORLESS,id.equals("defender")?"Defender":attackerName,null));
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.COLORLESS),Map.of(),Map.of("special",EnergyProfile.fixed(EnergyType.COLORLESS,2)));
        for(int seed=0;seed<100;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){
                var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,i%5<2?(owner.equals(A)?"attacker":"defender"):i%5==2?"energy":i%5==3?"special":"evolved"));decks.add(cards);
            }
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,20);m.drawCardsByEffect(A,B,20);m.endTurn(A);m.endTurn(B);
            if(evolved){var c=card(m,A,"evolved");m.evolveByEffect(A,active(m,A),c.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);}
            attach(m,A,active(m,A),"energy");return m;
        }throw new AssertionError("No guard fixture");
    }
    static CardInstance card(ServerMatch m,UUID owner,String id){return m.privateView(owner).hand().stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow();}
    static void attach(ServerMatch m,UUID owner,UUID host,String id){var c=card(m,owner,id);m.attachEnergyByEffect(A,owner,EffectSourceZone.HAND,c.instanceId(),owner,host);}
    static Set<PokemonFamily> family(String kind){return switch(kind){case "ordinary"->Set.of();case "VMAX"->Set.of(PokemonFamily.V,PokemonFamily.VMAX);case "VSTAR"->Set.of(PokemonFamily.V,PokemonFamily.VSTAR);default->Set.of(PokemonFamily.valueOf(kind));};}
    static void attack(ServerMatch m){int count=m.privateView(A).hand().size();m.attack(A,"strike");check(m.privateView(A).hand().size()==count+1,"Protection swallowed attacker draw");}
    public static void main(String[] args){families();fullGuards();effectAliases();specialEnergy();exceptions();benchGuard();fullHp();schema();System.out.println("GeneratedRuleBatch16Test passed: EX/ex/GX/V family separation, full/effect/damage scopes, special Energy, name exceptions, live Bench aura, HP reduction, suppression and bypass");}
    static void families(){
        var cases=Map.of("sv2_en_097",Set.of("EX","V","VMAX","VSTAR"),"swsh3-5_en_049",Set.of("GX","V","VMAX","VSTAR"),"swsh1_en_139",Set.of("VMAX"),"sv8-5_en_040",Set.of("EX"),"swsh10_en_126",Set.of("V","VMAX","VSTAR"),"xy6_en_005",Set.of("EX_LEGACY"));
        for(var row:cases.entrySet())for(String kind:List.of("ordinary","EX","EX_LEGACY","GX","V","VMAX","VSTAR")){
            var m=fixture(row.getKey(),family(kind),"Attacker",kind.equals("VMAX")||kind.equals("VSTAR"),false,false);attack(m);int damage=row.getValue().contains(kind)?0:100;
            check(m.lastAttack().finalDamage()==damage&&m.activePokemonView(B).damage()==damage+30,"Family/damage/effect boundary "+row.getKey()+" "+kind);check(m.activePokemonView(B).conditions().contains(SpecialCondition.POISONED),"Damage guard prevented Poison");
        }
    }
    static void fullGuards(){
        for(String id:List.of("sm3-5_en_055","xy10_en_050","xy12_en_053"))for(String kind:List.of("ordinary","EX","EX_LEGACY","GX","V"))for(boolean evolved:List.of(false,true)){
            boolean blocked=id.equals("xy12_en_053")?evolved:id.equals("xy10_en_050")?kind.equals("EX_LEGACY"):Set.of("GX","EX_LEGACY").contains(kind);
            var m=fixture(id,family(kind),"Attacker",evolved,false,false);attack(m);check(m.lastAttack().finalDamage()==(blocked?0:100)&&m.activePokemonView(B).damage()==(blocked?0:130),"Full guard source/impact "+id+" "+kind+" "+evolved);
            check(m.activePokemonView(B).conditions().contains(SpecialCondition.POISONED)==!blocked,"Full guard status");
        }
        for(boolean bypass:List.of(false,true)){
            var m=fixture("sm3-5_en_055",family("GX"),"Attacker",false,bypass,false);if(!bypass)m.disableAbilityEffectsByEffect(A,B,active(m,B));attack(m);
            check(m.lastAttack().finalDamage()==100&&m.activePokemonView(B).damage()==(bypass?100:130),"Suppression/defender damage bypass must preserve effect guard when active");
            m.placeDamageCountersByEffect(B,B,active(m,B),1);check(m.activePokemonView(B).damage()==(bypass?110:140),"Attack protection blocked rule-origin counters");
        }
        var m=fixture("sm3-5_en_055",family("GX"),"Attacker",false,false,true);attack(m);check(m.benchPokemonView(B).getFirst().damage()==0,"Full guard inactive on Bench");
    }
    static void effectAliases(){for(String id:List.of("swsh4_en_074","sm9_en_117")){var m=fixture(id,Set.of(),"Attacker",false,false,false);attack(m);check(m.lastAttack().finalDamage()==100&&m.activePokemonView(B).damage()==100&&m.activePokemonView(B).conditions().isEmpty(),"Effects-only guard blocked damage or allowed counters/status");}}
    static void specialEnergy(){for(String mode:List.of("none","self","own-bench","opponent","disabled")){
        var m=fixture("swsh7_en_123",Set.of(),"Attacker",false,false,false);if(!mode.equals("none"))attach(m,mode.equals("opponent")?B:A,mode.equals("opponent")?active(m,B):mode.equals("own-bench")?bench(m,A):active(m,A),"special");
        if(mode.equals("disabled"))m.disableAbilityEffectsByEffect(A,B,active(m,B));attack(m);check(m.lastAttack().finalDamage()==(mode.equals("self")?0:100),"Special Energy guard wrong owner/host/suppression: "+mode);
    }}
    static void exceptions(){for(String name:List.of("Glaceon VMAX","Other VMAX")){var m=fixture("swsh7_en_041",family("VMAX"),name,true,false,false);attack(m);check(m.lastAttack().finalDamage()==(name.equals("Glaceon VMAX")?100:0),"Canonical name exception");}}
    static void benchGuard(){for(boolean disabled:List.of(false,true)){
        var m=fixture("sm10_en_076",Set.of(),"Attacker",false,false,true);m.disableAbilityEffectsByEffect(A,B,active(m,B));if(disabled)m.disableAbilityEffectsByEffect(A,B,bench(m,B));attack(m);
        check(m.benchPokemonView(B).getFirst().damage()==(disabled?100:0)&&m.activePokemonView(B).damage()==30,"Bench aura scope, counter bypass or live suppression");
    }
        var m=fixture("sm10_en_076",Set.of(),"Attacker",false,false,false);attack(m);check(m.lastAttack().finalDamage()==100,"Bench aura protected Active");
    }
    static void fullHp(){for(String mode:List.of("full","damaged","healed","disabled","bypass")){
        var m=fixture("sv4_en_027",Set.of(),"Attacker",false,mode.equals("bypass"),false);
        if(mode.equals("damaged")||mode.equals("healed"))m.placeDamageCountersByEffect(A,B,active(m,B),1);
        if(mode.equals("healed"))m.healDamageByEffect(A,B,active(m,B),10);
        if(mode.equals("disabled"))m.disableAbilityEffectsByEffect(A,B,active(m,B));attack(m);check(m.lastAttack().finalDamage()==(Set.of("full","healed").contains(mode)?20:100),"Full HP live reduction: "+mode);
    }}
    static void schema(){for(String tail:List.of("\"attacker_filter\":\"CLIENT\"","\"attacker_filter\":\"ANY\"","\"amount\":10","\"except_pokemon_names\":\"Glaceon VMAX\"")){
        GeneratedRuleBatch8Test.reject(()->EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"passive_effect_guard\",\"source\":\"ATTACK\",\"from\":\"OPPONENT\",\"scope\":\"DAMAGE_AND_EFFECTS\","+tail+"}").getAsJsonObject()));
    }}
}
