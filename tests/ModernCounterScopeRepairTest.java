import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Real card programs on controlled fields: target filters, both checkups and ordinary KO continuation. */
public final class ModernCounterScopeRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){ModernRuleRepair2Test.check(ok,why);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return ModernRuleRepair2Test.hand(m,p,id);}
    public static void main(String[] args){shroud();queen();jail();euclase();System.out.println("ModernCounterScopeRepairTest passed: nine audited prints plus two alternate prints; exact name exclusion, possessed Abilities, both checkups, damaged Bench only, HP floor, exact six counters, KO and Prize continuation");}
    static void put(ServerMatch m,UUID owner,CardInstance card){m.discardHandCardsByEffect(A,owner,List.of(card.instanceId()));m.putBasicPokemonFromDiscardToBenchByEffect(A,owner,card.instanceId());}
    static ServerMatch shroudGame(AbilityDefinition shroud){
        var filler=DATA.pokemon("sv3-5_en_085").orElseThrow().abilities().getFirst();
        String[] ids={"froslass","froslass","froslass","froslass","ability","rocket","plain","energy"};
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:Arrays.stream(ids).distinct().toList()){
            boolean pokemon=!id.equals("energy");defs.add(StadiumActivationTest.definition(id,pokemon?CardCategory.POKEMON:CardCategory.ENERGY,TrainerCategory.NONE,pokemon));
            if(pokemon)profiles.put(id,new PokemonProfile(500,0,null,null,0,null,0,1,List.of(GeneratedRuleBatch8Test.hit(10,false)),
                id.equals("froslass")?List.of(shroud):id.equals("plain")?List.of():List.of(filler),EnergyType.WATER,
                id.equals("froslass")?"Froslass":id.equals("rocket")?"Team Rocket's Froslass":id,null));
        }
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.WATER),Map.of());
        for(int seed=0;seed<500;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,ids[i%ids.length]));decks.add(deck);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->!c.definitionId().equals("energy")).limit(2).count()<2
                ||m.privateView(p).hand().stream().filter(c->!c.definitionId().equals("energy")).limit(2).anyMatch(c->!c.definitionId().equals("froslass"))))continue;
            OpeningTestSupport.complete(m,A,B,1);for(UUID p:List.of(A,B))m.drawCardsByEffect(A,p,30);
            if(List.of(A,B).stream().anyMatch(p->List.of("ability","rocket","plain").stream().anyMatch(id->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals(id)))))continue;
            for(UUID p:List.of(A,B))for(String id:List.of("ability","rocket","plain"))put(m,p,hand(m,p,id));
            return m;
        }throw new AssertionError("shroud fixture");
    }
    static void shroud(){
        for(String id:List.of("sv6_en_053","sv6_en_174","svbsp_en_117")){
            var a=DATA.pokemon(id).orElseThrow().abilities().getFirst();TriggeredAbilityRules.validate(a);var m=shroudGame(a);
            UUID disabled=m.benchPokemonView(A).stream().filter(v->v.topCardId().equals("ability")).findFirst().orElseThrow().pokemonId();m.disableAbilityEffectsByEffect(A,A,disabled);
            for(int turn=1;turn<=2;turn++){
                m.endTurn(m.currentPlayer());ModernTriggerCountRepairTest.settle(m);
                check(!m.hasPendingEffectChoice()&&m.phase()==MatchPhase.TURN,"Freezing Shroud stalled");
                for(UUID p:List.of(A,B)){
                    check(m.activePokemonView(p).damage()==0,"Froslass active not exempt");
                    for(var v:m.benchPokemonView(p))check(v.damage()==(Set.of("froslass","plain").contains(v.topCardId())?0:40*turn),"Shroud four sources, disabled possessed Ability, exact name or no-Ability boundary: "+v.topCardId()+"="+v.damage());
                }
            }
        }
    }
    static ServerMatch attackGame(String id,int index){var a=DATA.pokemon(id).orElseThrow().attacks().get(index);AttackProgramRules.validate(a);var m=ModernShuffleScopeRepairTest.game(a,0);m.endTurn(A);m.endTurn(B);ModernShuffleScopeRepairTest.pay(m,A,m.activePokemonView(A).pokemonId());return m;}
    static UUID addBench(ServerMatch m){var c=hand(m,B,"basic");put(m,B,c);return m.benchPokemonView(B).getLast().pokemonId();}
    static void queen(){
        for(String id:List.of("sv3_en_096","sv3_en_212"))for(boolean selfHurt:List.of(false,true)){
            var m=attackGame(id,1);UUID hurt=m.benchPokemonView(B).getFirst().pokemonId(),clean=addBench(m);m.placeDamageCountersByEffect(A,B,hurt,1);
            if(selfHurt)m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),1);
            m.attack(A,"phantom-queen");
            check(m.activePokemonView(B).damage()==200&&m.benchPokemonView(B).stream().filter(v->v.pokemonId().equals(hurt)).findFirst().orElseThrow().damage()==40,"Phantom Queen used source damage or hit Active with counters");
            check(m.benchPokemonView(B).stream().filter(v->v.pokemonId().equals(clean)).findFirst().orElseThrow().damage()==0&&!m.hasPendingEffectChoice(),"Phantom Queen hit undamaged Bench or stalled");
        }
    }
    static void jail(){
        for(String id:List.of("sv8_en_091","sv8_en_221")){
            var m=attackGame(id,1);UUID high=m.benchPokemonView(B).getFirst().pokemonId(),equal=addBench(m),low=addBench(m);m.placeDamageCountersByEffect(A,B,equal,40);m.placeDamageCountersByEffect(A,B,low,45);m.attack(A,"barite-jail");
            check(m.activePokemonView(B).damage()==0,"Barite Jail hit Active");
            for(var v:m.benchPokemonView(B))check(v.damage()==(v.pokemonId().equals(low)?450:400),"Barite Jail healed or missed HP ceiling");
            check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Barite Jail stalled");
        }
    }
    static void euclase(){
        for(String id:List.of("sv8-5_en_026","sv8-5_en_150","svalt_en_159","svalt_en_193"))for(boolean active:List.of(false,true)){
            var m=attackGame(id,1);UUID bench=m.benchPokemonView(B).getFirst().pokemonId(),front=m.activePokemonView(B).pokemonId(),seven=addBench(m);
            m.placeDamageCountersByEffect(A,B,front,active?6:5);m.placeDamageCountersByEffect(A,B,bench,active?5:6);m.placeDamageCountersByEffect(A,B,seven,7);
            UUID target=active?front:bench;m.attack(A,"euclase");var p=m.effectPokemonPrompt(A);
            check(p!=null&&p.targets().size()==1&&p.targets().getFirst().pokemonId().equals(target),"Euclase offered five/seven counters or missed six");
            CopyAttackTest.reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),seven));m.chooseEffectPokemon(A,p.resolutionId(),p.step(),target);
            check(m.phase()==MatchPhase.PRIZE_SELECTION&&A.equals(m.pendingChoicePlayer())&&!m.hasPendingEffectChoice(),"Euclase did not enter ordinary KO/Prize pipeline");
        }
        var m=attackGame("sv8-5_en_026",1);m.attack(A,"euclase");check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Euclase no-target attack stalled");
    }
}
