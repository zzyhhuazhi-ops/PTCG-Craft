import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Real generated programs with explicit printed-label metadata and server choices. */
public final class GeneratedRuleBatch18Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final Ruleset RULES=GeneratedRuleBatch8Test.RULES;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static Set<PokemonFamily> labels(String label){return label.equals("NONE")?Set.of():Set.of(PokemonFamily.valueOf(label));}
    static ServerMatch game(String label,TrainerProfile trainer,AttackDefinition attack,List<AbilityDefinition> abilities){return fixture(abilities,EnergyType.COLORLESS,EnergyType.COLORLESS,trainer,attack,labels(label));}
    static ServerMatch game(String label,TrainerProfile trainer){return game(label,trainer,GeneratedRuleBatch8Test.hit(100,false),List.of());}
    static TrainerProfile plain(){return GeneratedRuleBatch8Test.trainer("swsh5_en_124");}
    static void suppress(ServerMatch m,UUID p){m.disableAbilityEffectsByEffect(A,p,active(m,p));m.disableAbilityEffectsByEffect(A,p,bench(m,p));}
    static ServerMatch fixture(List<AbilityDefinition> abilities,EnergyType energy,EnergyType type,TrainerProfile trainer,AttackDefinition attack,Set<PokemonFamily> labels){
        String[] ids={"basic","basic","basic","basic","energy","energy","wrong","special","tool","tool","tool","tool","trainer","evolved","rulebox"};
        var defs=new ArrayList<CardDefinition>();
        for(String id:Arrays.stream(ids).distinct().toList()){
            boolean pokemon=Set.of("basic","evolved","rulebox").contains(id),isEnergy=Set.of("energy","wrong","special").contains(id);
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:isEnergy?CardCategory.ENERGY:CardCategory.TRAINER,
                id.equals("trainer")?trainer.category():id.equals("tool")?TrainerCategory.TOOL:TrainerCategory.NONE,"H",Set.of("international"),
                id.equals("basic")||id.equals("rulebox"),id.equals("energy")||id.equals("wrong"),60,Map.of(),EffectSupport.SUPPORTED,null,
                id.equals("basic")?labels:id.equals("rulebox")?Set.of(PokemonFamily.EX,PokemonFamily.RULE_BOX):Set.of(),pokemon?Set.of(id.equals("evolved")?EnergyType.PSYCHIC:type):Set.of()));
        }
        var pokemon=new HashMap<String,PokemonProfile>();
        for(String id:List.of("basic","evolved","rulebox"))pokemon.put(id,new PokemonProfile(500,1,id.equals("evolved")?"basic":null,null,0,null,0,
            id.equals("rulebox")?2:1,List.of(attack),id.equals("rulebox")?List.of():abilities,id.equals("evolved")?EnergyType.PSYCHIC:type));
        var combat=new CombatCatalog(pokemon,Map.of("energy",energy,"wrong",energy==EnergyType.FIRE?EnergyType.WATER:EnergyType.FIRE),
            Map.of("trainer",trainer,"tool",DynamicPassiveModifierTest.plainTool()),Map.of("special",EnergyProfile.fixed(energy,2)));
        for(int seed=0;seed<500;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->{var basics=m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).toList();return basics.size()<2||basics.subList(0,2).stream().anyMatch(c->!c.definitionId().equals("basic"));}))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,28);m.drawCardsByEffect(A,B,28);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->Arrays.stream(ids).allMatch(id->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(id))))&&
                m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).count()>=3&&m.privateView(A).hand().stream().filter(c->c.definitionId().equals("tool")).count()>=5)return m;
        }throw new AssertionError("No batch 8 fixture");
    }

    public static void main(String[] args){metadata();counts();capsule();auras();styleCost();stadium();searches();healing();turnBonus();prizeBonus();gxKnockout();System.out.println("GeneratedRuleBatch18Test passed: printed labels, count/top-card eligibility, passive modifiers, filtered private search, healing, turn bonuses and conditional GX KO");}
    static void metadata(){var db=BundledRules.loadCardDefinitions();
        for(var row:Map.of("sv4_en_070","FUTURE","sv4_en_124","ANCIENT","sm4_en_057","ULTRA_BEAST","swsh5_en_085","SINGLE_STRIKE","swsh5_en_087","RAPID_STRIKE","swsh8_en_185","FUSION_STRIKE").entrySet())check(db.find(row.getKey()).orElseThrow().pokemonFamilies().contains(PokemonFamily.valueOf(row.getValue())),"Missing explicit label "+row);
        check(!db.find("sv1_en_143").orElseThrow().pokemonFamilies().contains(PokemonFamily.FUTURE),"Old Iron Treads ex inferred Future from name");
        check(db.find("swsh5_en_140").orElseThrow().battleStyles().contains(BattleStyle.RAPID_STRIKE)&&db.find("swsh5_en_138").orElseThrow().battleStyles().contains(BattleStyle.RAPID_STRIKE),"Non-Pokemon Battle Style lost");
        check(db.find("swsh5_en_140").orElseThrow().pokemonFamilies().isEmpty(),"Energy given Pokemon-family metadata");
        check(!db.find("sv4_en_056").orElseThrow().pokemonFamilies().contains(PokemonFamily.RULE_BOX),"Printed label invented Rule Box");
    }
    static void counts(){for(var row:Map.of("sv5_en_119","ANCIENT","swsh5_en_083","RAPID_STRIKE","swsh8_en_130","FUSION_STRIKE","sm6_en_056","ULTRA_BEAST").entrySet())for(boolean qualifies:List.of(false,true)){
        var atk=GeneratedRuleBatch6Test.attack(row.getKey(),0);var m=game(qualifies?row.getValue():"NONE",plain(),atk,List.of());
        var ex=hand(m,A,"rulebox");m.playBasicPokemonToBench(A,ex.instanceId());m.attack(A,atk.id());int per=Set.of("swsh5_en_083","sm6_en_056").contains(row.getKey())?20:30;
        check(m.lastAttack().finalDamage()==(qualifies?per*2:0),"Family field count included Rule Box/opponent or omitted Active "+row);
        if(qualifies){var n=game(row.getValue(),plain(),atk,List.of());n.evolveByEffect(A,bench(n,A),hand(n,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);n.attack(A,atk.id());check(n.lastAttack().finalDamage()==per,"Family count used card underneath Evolution");}
    }}
    static void capsule(){for(String label:List.of("FUTURE","ANCIENT","NONE"))for(boolean disabled:List.of(false,true)){
        var m=game(label,GeneratedRuleBatch8Test.trainer("sv4_en_164"));m.playTrainer(A,hand(m,A,"trainer").instanceId(),active(m,A));m.modifyRetreatCostByEffect(A,A,active(m,A),10);if(disabled)m.disableToolEffectsByEffect(A,A,active(m,A));boolean applies=label.equals("FUTURE")&&!disabled;
        check(m.activeRetreatCost(A)==(applies?0:11),"Capsule free retreat eligibility/suppression");m.attack(A,"hit");check(m.lastAttack().finalDamage()==(applies?120:100),"Capsule active-only damage eligibility");
    }
        var atk=GeneratedRuleBatch8Test.hit(100,true);var m=game("FUTURE",GeneratedRuleBatch8Test.trainer("sv4_en_164"),atk,List.of());m.playTrainer(A,hand(m,A,"trainer").instanceId(),active(m,A));m.attack(A,atk.id());check(m.benchPokemonView(B).getFirst().damage()==100,"Capsule boosted Bench damage");
    }
    static void auras(){for(String id:List.of("swsh5_en_025","sm7_en_102"))for(boolean qualifies:List.of(false,true)){
        boolean reduce=id.startsWith("sm");var m=game(qualifies?(reduce?"ULTRA_BEAST":"SINGLE_STRIKE"):"NONE",plain(),GeneratedRuleBatch8Test.hit(100,false),List.of(GeneratedRuleBatch8Test.ability(id)));
        if(reduce){suppress(m,A);m.disableAbilityEffectsByEffect(A,B,active(m,B));}else{suppress(m,B);m.disableAbilityEffectsByEffect(A,A,active(m,A));}
        m.attack(A,"hit");check(m.lastAttack().finalDamage()==100+(qualifies?(reduce?-10:30):0),"Bench-source aura label/owner "+id);
    }}
    static void styleCost(){var atk=BundledCombatData.attack(JsonParser.parseString("{\"id\":\"cost\",\"nameZh\":\"成本\",\"energyCost\":{\"COLORLESS\":3},\"baseDamage\":100,\"effectSupport\":\"SUPPORTED\"}").getAsJsonObject(),EffectRegistry.standard());
        for(String label:List.of("SINGLE_STRIKE","RAPID_STRIKE","FUSION_STRIKE","FUTURE","NONE")){
            var m=game(label,plain(),atk,List.of(GeneratedRuleBatch8Test.ability("swsh11_en_118")));GeneratedRuleBatch13Test.attach(m,A,active(m,A),"energy");boolean style=label.endsWith("STRIKE");check(m.canPayActiveAttackCost(A,atk)==style,"Wild Style counted wrong label or omitted opponent Active");
            m.disableAbilityEffectsByEffect(A,A,active(m,A));check(!m.canPayActiveAttackCost(A,atk),"Wild Style remained via Bench source");
        }
    }
    static void stadium(){for(String label:List.of("RAPID_STRIKE","SINGLE_STRIKE","NONE")){
        var m=game(label,GeneratedRuleBatch8Test.trainer("swsh5_en_138"));m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);int expected=label.equals("RAPID_STRIKE")?0:1;check(m.activeRetreatCost(A)==expected&&m.activeRetreatCost(B)==expected,"Tower of Waters both owners/label");m.discardStadiumByEffect(A);check(m.activeRetreatCost(A)==1,"Removed Tower remained");
    }}
    static void searches(){for(String id:List.of("swsh8_en_227","swsh6_en_131","sv4_en_180")){
        String label=id.startsWith("sv")?"FUTURE":id.startsWith("swsh8")?"FUSION_STRIKE":"RAPID_STRIKE";boolean bench=id.equals("swsh6_en_131");var m=game(label,GeneratedRuleBatch8Test.trainer(id));m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);
        if(id.startsWith("sv")){var cost=m.effectCardPrompt(A);check(cost.minimum()==1&&cost.maximum()==1,"Techno Radar missed discard cost");m.submitEffectCards(A,cost.resolutionId(),cost.step(),List.of(hand(m,A,"wrong").instanceId()));}
        var p=m.effectCardPrompt(A);check(p.minimum()==0&&p.candidates().stream().allMatch(c->c.definitionId().equals("basic")),"Label search leaked wrong families/stages "+id);check(m.clientView(B).effectSelection()==null,"Private candidate leak");
        var wrong=m.privateView(A).deck().stream().filter(c->c.definitionId().equals("rulebox")).findFirst().orElseThrow();GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(wrong.instanceId())));
        var chosen=p.candidates().getFirst();int benches=m.benchPokemonView(A).size();m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(chosen.instanceId()));
        if(bench)check(m.benchPokemonView(A).size()==benches+1&&m.effectCardReveal()==null,"Bench search performed hand reveal");else{var reveal=m.effectCardReveal();check(reveal!=null&&!m.privateView(A).hand().contains(chosen),"Search skipped reveal-before-transfer");m.acknowledgeEffectReveal(B,reveal.id());check(m.privateView(A).hand().contains(chosen),"Search failed transfer");}
    }}
    static void healing(){var m=game("RAPID_STRIKE",GeneratedRuleBatch8Test.trainer("swsh6_en_153"));m.placeDamageCountersByEffect(A,A,active(m,A),7);m.placeDamageCountersByEffect(A,A,bench(m,A),8);var other=hand(m,A,"rulebox");m.playBasicPokemonToBench(A,other.instanceId());m.placeDamageCountersByEffect(A,A,other.instanceId(),9);m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);var p=m.effectPokemonPrompt(A);check(p.targets().size()==2&&!p.canFinish(),"Siebold offered non-label target/zero choice");m.chooseEffectPokemon(A,p.resolutionId(),p.step(),active(m,A));p=m.effectPokemonPrompt(A);check(p.canFinish(),"Siebold cannot finish after one");m.finishEffectPokemonSelection(A,p.resolutionId(),p.step());check(m.activePokemonView(A).damage()==10&&m.benchPokemonView(A).getFirst().damage()==80,"Siebold wrong healing/selection count");
        var atk=GeneratedRuleBatch6Test.attack("sv5_en_077",0);var n=game("ANCIENT",plain(),atk,List.of());n.placeDamageCountersByEffect(A,A,bench(n,A),12);n.attack(A,atk.id());var choose=n.effectPokemonPrompt(A);check(choose.targets().size()==1&&choose.targets().getFirst().pokemonId().equals(bench(n,A)),"Ancient heal offered Active");n.chooseEffectPokemon(A,choose.resolutionId(),choose.step(),bench(n,A));check(n.benchPokemonView(A).getFirst().damage()==20,"Ancient Bench heal wrong amount");
    }
    static void turnBonus(){for(String label:List.of("FUSION_STRIKE","SINGLE_STRIKE","NONE")){
        var m=game(label,GeneratedRuleBatch8Test.trainer("swsh8_en_236"));m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);m.attack(A,"hit");check(m.lastAttack().finalDamage()==(label.equals("FUSION_STRIKE")?130:100),"Power Tablet label bonus");m.endTurn(B);m.attack(A,"hit");check(m.lastAttack().finalDamage()==100,"Power Tablet leaked into next turn");
    }}
    static void prizeBonus(){for(boolean qualifies:List.of(false,true)){
        var m=game(qualifies?"SINGLE_STRIKE":"FUSION_STRIKE",GeneratedRuleBatch8Test.trainer("swsh6_en_144"));GeneratedRuleBatch9Test.take(m,B,2);m.playTrainer(A,hand(m,A,"trainer").instanceId(),null);m.attack(A,"hit");check(m.lastAttack().finalDamage()==(qualifies?140:100),"Karen bonus did not count opponent Prizes and current label");
    }}
    static void gxKnockout(){var atk=GeneratedRuleBatch6Test.attack("sm8_en_132",1);for(boolean qualifies:List.of(false,true)){
        var m=game(qualifies?"ULTRA_BEAST":"NONE",plain(),atk,List.of());UUID victim=active(m,B);m.attack(A,atk.id());check((m.phase()==MatchPhase.PRIZE_SELECTION)==qualifies,"Conditional GX KO ignored label");check(m.activeAttacks(A).stream().noneMatch(a->a.id().equals(atk.id())),"Conditional miss did not consume GX usage");
    }}
}
