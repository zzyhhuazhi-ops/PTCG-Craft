import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Typed attack-source guards read the actual top Pokemon, independently of Energy and attack damage type. */
public final class GeneratedRuleBatch26Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static AbilityDefinition ability(String id){return GeneratedRuleBatch8Test.ability(id);}
    static TrainerProfile tool(String id){return GeneratedRuleBatch8Test.trainer(id);}
    static CardInstance card(ServerMatch m,UUID p,String id){return GeneratedRuleBatch16Test.card(m,p,id);}
    static ServerMatch guard(String id,Set<EnergyType> types,EnergyType fallback,boolean bypass,boolean spread){return fixture(ability(id),null,null,types,fallback,Set.of(EnergyType.COLORLESS),Set.of(),false,bypass,spread);}
    static void strike(ServerMatch m){GeneratedRuleBatch16Test.attack(m);}
    public static void main(String[] args){passiveTypes();reductions();liveAndBypass();tools();temporary();schema();System.out.println("GeneratedRuleBatch26Test passed: typed source protection, dual/excluded types, profile fallback, actual top card, Basic/family conjunctions, Tool hosts, suppression, expiry, bypass and damage/effect scopes");}
    static void passiveTypes(){for(String id:List.of("sm1_en_046","sv3_en_078"))for(Set<EnergyType> types:List.of(Set.of(EnergyType.FIRE),Set.of(EnergyType.WATER),Set.of(EnergyType.LIGHTNING),Set.of(EnergyType.FIRE,EnergyType.WATER))){
        var m=guard(id,types,EnergyType.COLORLESS,false,false);strike(m);boolean blocked=types.contains(id.equals("sm1_en_046")?EnergyType.FIRE:EnergyType.LIGHTNING);check(m.lastAttack().finalDamage()==(blocked?0:100)&&m.activePokemonView(B).damage()==(blocked?30:130)&&m.activePokemonView(B).conditions().contains(SpecialCondition.POISONED),"Typed damage guard confused source type with attack type/effects");
    }
        var m=guard("sm1_en_046",Set.of(),EnergyType.FIRE,false,false);strike(m);check(m.lastAttack().finalDamage()==0,"Legacy definition without type tags skipped combat-profile fallback");
        var n=guard("sm1_en_046",Set.of(EnergyType.WATER),EnergyType.FIRE,false,false);strike(n);check(n.lastAttack().finalDamage()==100,"Combat fallback overrode current printed types");
    }
    static void reductions(){for(String id:List.of("me2_en_022","sm3_en_035","xy3_en_052","sm4_en_056"))for(Set<EnergyType> types:List.of(Set.of(EnergyType.FIRE),Set.of(EnergyType.WATER),Set.of(EnergyType.PSYCHIC),Set.of(EnergyType.FIRE,EnergyType.WATER))){
        var m=guard(id,types,EnergyType.COLORLESS,false,false);strike(m);boolean reduced=id.equals("sm4_en_056")?!types.contains(EnergyType.FIRE):types.contains(EnergyType.FIRE)||types.contains(EnergyType.WATER);check(m.lastAttack().finalDamage()==(reduced?70:100),"Typed reduction OR/exclusion semantics "+id+" "+types);
    }}
    static void liveAndBypass(){for(boolean bypass:List.of(false,true)){
        var m=guard("sm1_en_046",Set.of(EnergyType.FIRE),EnergyType.FIRE,bypass,false);if(!bypass)m.disableAbilityEffectsByEffect(A,B,active(m,B));strike(m);check(m.lastAttack().finalDamage()==100,"Typed passive suppression/bypass: "+m.lastAttack().finalDamage());
    }
        var n=guard("sm1_en_046",Set.of(EnergyType.FIRE),EnergyType.FIRE,false,true);strike(n);check(n.benchPokemonView(B).getFirst().damage()==0&&n.activePokemonView(B).damage()==30,"Typed guard did not apply on Bench or blocked counters");
        var m=fixture(ability("sm1_en_046"),null,null,Set.of(EnergyType.FIRE),EnergyType.WATER,Set.of(EnergyType.COLORLESS),Set.of(),false,false,false);m.evolveByEffect(A,active(m,A),card(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);strike(m);check(m.lastAttack().finalDamage()==100,"Type filter read covered Basic rather than evolved top card");
    }
    static void tools(){for(String id:List.of("sm8_en_174","sm8_en_175","sm8_en_176","sm8_en_177","sm10_en_172"))for(PokemonFamily family:List.of(PokemonFamily.GX,PokemonFamily.EX_LEGACY,PokemonFamily.EX,PokemonFamily.V))for(boolean rightHost:List.of(false,true)){
        EnergyType type=switch(id){case "sm8_en_174"->EnergyType.GRASS;case "sm8_en_175"->EnergyType.PSYCHIC;case "sm8_en_176"->EnergyType.FIGHTING;case "sm8_en_177"->EnergyType.DRAGON;default->EnergyType.LIGHTNING;};var m=fixture(null,tool(id),null,Set.of(type),type,Set.of(rightHost?EnergyType.FAIRY:EnergyType.WATER),Set.of(family),false,false,false);equip(m);strike(m);boolean blocked=rightHost&&(family==PokemonFamily.GX||family==PokemonFamily.EX_LEGACY);check(m.lastAttack().finalDamage()==(blocked?0:100),"Fairy Charm did not conjoin source type/family/host "+id);
    }
        for(EnergyType source:List.of(EnergyType.GRASS,EnergyType.FIRE,EnergyType.WATER,EnergyType.LIGHTNING,EnergyType.PSYCHIC))for(boolean correctHost:List.of(false,true)){
            var m=fixture(null,tool("me2-5_en_211"),null,Set.of(source),source,Set.of(correctHost?EnergyType.DRAGON:EnergyType.FAIRY),Set.of(),false,false,false);equip(m);strike(m);check(m.lastAttack().finalDamage()==(correctHost&&source!=EnergyType.PSYCHIC?50:100),"Thick Scale four source types/Dragon host");
        }
        var wrong=fixture(null,tool("sm8_en_174"),null,Set.of(EnergyType.WATER),EnergyType.WATER,Set.of(EnergyType.FAIRY),Set.of(PokemonFamily.GX),false,false,false);equip(wrong);strike(wrong);check(wrong.lastAttack().finalDamage()==100,"Family-only match ignored Charm type");
        var disabled=fixture(null,tool("sm8_en_174"),null,Set.of(EnergyType.GRASS),EnergyType.GRASS,Set.of(EnergyType.FAIRY),Set.of(PokemonFamily.GX),false,false,false);equip(disabled);disabled.disableToolEffectsByEffect(A,B,active(disabled,B));strike(disabled);check(disabled.lastAttack().finalDamage()==100,"Disabled typed Tool still prevented damage");
    }
    static void attach(ServerMatch m,UUID owner,UUID host,String id){var c=card(m,owner,id);m.attachEnergyByEffect(m.currentPlayer(),owner,EffectSourceZone.HAND,c.instanceId(),owner,host);}
    static void equip(ServerMatch m){m.endTurn(A);m.playTrainer(B,card(m,B,"tool").instanceId(),active(m,B));m.endTurn(B);}
    static void temporary(){for(boolean full:List.of(false,true))for(Set<EnergyType> types:List.of(Set.of(EnergyType.DRAGON),Set.of(EnergyType.WATER),Set.of(EnergyType.COLORLESS),Set.of(EnergyType.DRAGON,EnergyType.COLORLESS))){
        var attack=GeneratedRuleBatch6Test.attack(full?"xy9_en_082":"me2-5_en_179",full?0:1);var m=fixture(null,null,attack,Set.of(EnergyType.FIRE),EnergyType.FIRE,types,Set.of(),false,false,false);m.attack(A,attack.id());attach(m,B,active(m,B),"energy");m.attack(B,"strike");boolean blocked=full?types.contains(EnergyType.DRAGON):!types.contains(EnergyType.COLORLESS);check(m.lastAttack().finalDamage()==(blocked?0:100)&&m.activePokemonView(A).damage()==(blocked?(full?0:30):130),"Temporary typed protection damage/effect scope "+types+" "+full);
        int before=m.activePokemonView(A).damage();m.placeDamageCountersByEffect(A,A,active(m,A),1);check(m.activePokemonView(A).damage()==before+10,"Protection blocked rule-origin counters");
    }
        var protect=GeneratedRuleBatch6Test.attack("me2-5_en_179",1);var m=fixture(null,null,protect,Set.of(EnergyType.FIRE),EnergyType.FIRE,Set.of(EnergyType.WATER),Set.of(),false,false,false);m.attack(A,protect.id());m.evolveByEffect(B,active(m,B),card(m,B,"defender_evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);attach(m,B,active(m,B),"energy");m.attack(B,"strike");check(m.lastAttack().finalDamage()==100,"Basic type guard protected against Evolution");
        var n=fixture(null,null,protect,Set.of(EnergyType.FIRE),EnergyType.FIRE,Set.of(EnergyType.WATER),Set.of(),false,false,false);n.attack(A,protect.id());n.endTurn(B);n.endTurn(A);attach(n,B,active(n,B),"energy");n.attack(B,"strike");check(n.lastAttack().finalDamage()==100,"Typed temporary guard survived its expiry");
    }
    static void schema(){for(String value:List.of("","FIRE|","FIRE|FIRE","FIRE|WATTER"," FIRE"))reject(()->AttackSourceFilter.from(Map.of("attacker_pokemon_types",value)));
        reject(()->ProtectionEffect.from(EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"prevent_damage\",\"source\":\"ABILITY\",\"from\":\"OPPONENT\",\"scope\":\"DAMAGE_ONLY\",\"duration\":\"OPPONENT_TURN_END\",\"attacker_pokemon_types\":\"FIRE\"}").getAsJsonObject())));
    }
    static ServerMatch fixture(AbilityDefinition ability,TrainerProfile tool,AttackDefinition ownAttack,Set<EnergyType> types,EnergyType fallback,Set<EnergyType> defenderTypes,Set<PokemonFamily> families,boolean evolved,boolean bypass,boolean spread){
        var attack=GeneratedRuleBatch16Test.strike(bypass,spread);var defenderType=defenderTypes.stream().sorted().findFirst().orElse(EnergyType.COLORLESS);var defs=new ArrayList<CardDefinition>();
        for(String id:List.of("attacker","evolved","defender","defender_evolved","energy","special","tool")){
            boolean pokemon=Set.of("attacker","evolved","defender","defender_evolved").contains(id);
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:id.equals("tool")?CardCategory.TRAINER:CardCategory.ENERGY,id.equals("tool")?TrainerCategory.TOOL:TrainerCategory.NONE,"H",Set.of("international"),
                id.equals("attacker")||id.equals("defender"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null,
                id.equals(evolved?"evolved":"attacker")?families:Set.of(),pokemon?(id.startsWith("defender")?defenderTypes:id.equals("evolved")?Set.of(EnergyType.WATER):types):Set.of()));
        }
        var profiles=new HashMap<String,PokemonProfile>();
        for(String id:List.of("attacker","evolved","defender","defender_evolved"))profiles.put(id,new PokemonProfile(500,1,id.equals("evolved")?"attacker":id.equals("defender_evolved")?"defender":null,null,0,null,0,1,List.of(!id.startsWith("defender")&&ownAttack!=null?ownAttack:attack),id.equals("defender")&&ability!=null?List.of(ability):List.of(),id.startsWith("defender")?defenderType:fallback,id.equals("defender")?"Defender":"Attacker",null));
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.COLORLESS),Map.of("tool",tool==null?DynamicPassiveModifierTest.plainTool():tool),Map.of("special",EnergyProfile.fixed(EnergyType.COLORLESS,2)));
        for(int seed=0;seed<100;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){
                var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,i%6<2?(owner.equals(A)?"attacker":"defender"):i%6==2?"energy":i%6==3?"special":i%6==4?(owner.equals(A)?"evolved":"defender_evolved"):"tool"));decks.add(cards);
            }
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,20);m.drawCardsByEffect(A,B,20);m.endTurn(A);m.endTurn(B);
            if(evolved){var c=card(m,A,"evolved");m.evolveByEffect(A,active(m,A),c.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);}
            attach(m,A,active(m,A),"energy");return m;
        }throw new AssertionError("No guard fixture");
    }

}
