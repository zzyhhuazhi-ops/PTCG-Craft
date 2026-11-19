import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernShuffleScopeRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){ModernRuleRepair2Test.check(ok,why);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return ModernRuleRepair2Test.hand(m,p,id);}
    static ServerMatch game(AttackDefinition attack,int start){
        var defs=new CardDatabase(List.of(StadiumActivationTest.definition("basic",CardCategory.POKEMON,TrainerCategory.NONE,true),StadiumActivationTest.definition("evolved",CardCategory.POKEMON,TrainerCategory.NONE,false),StadiumActivationTest.definition("energy",CardCategory.ENERGY,TrainerCategory.NONE,false),StadiumActivationTest.definition("tool",CardCategory.TRAINER,TrainerCategory.TOOL,false)));
        var unit=new EnergyProfile.Unit(EnumSet.allOf(EnergyType.class));var energy=new EnergyProfile(List.of(unit,unit,unit));
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(500,0,null,null,0,null,0,1,List.of(attack),List.of(),EnergyType.PSYCHIC),"evolved",new PokemonProfile(500,0,"basic",null,0,null,0,1,List.of(attack),List.of(),EnergyType.PSYCHIC)),Map.of(),Map.of("tool",DynamicPassiveModifierTest.plainTool()),Map.of("energy",energy));
        for(int seed=start;seed<start+1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,i%10<4?"basic":i%10<6?"evolved":i%10<9?"energy":"tool"));decks.add(deck);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,defs,combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);for(UUID p:List.of(A,B))m.drawCardsByEffect(A,p,20);
            if(List.of(A,B).stream().allMatch(p->List.of("energy","evolved","tool","basic").stream().allMatch(id->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(id)))))return m;
        }throw new AssertionError("shuffle fixture");
    }
    static void pay(ServerMatch m,UUID p,UUID host){m.attachEnergyByEffect(m.currentPlayer(),p,EffectSourceZone.HAND,hand(m,p,"energy").instanceId(),p,host);}
    static void select(ServerMatch m,UUID p,UUID target){var q=m.effectPokemonPrompt(p);m.chooseEffectPokemon(p,q.resolutionId(),q.step(),target);}
    public static void main(String[] args){home();mystical();perfume();angelite();System.out.println("ModernShuffleScopeRepairTest passed: eight prints; whole-stack scoped shuffle, coin branches, second-player first turn, one/two available Bench targets and player-wide Angelite cooldown");}
    static void home(){
        for(String id:List.of("sv6_en_085","sv6_en_179")){
            var a=DATA.pokemon(id).orElseThrow().attacks().get(1);var m=game(a,0);UUID target=m.benchPokemonView(A).getFirst().pokemonId();
            var energy=hand(m,A,"energy");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,energy.instanceId(),A,target);var tool=hand(m,A,"tool");m.playTrainer(A,tool.instanceId(),target);var evolution=hand(m,A,"evolved");m.evolveByEffect(A,target,evolution.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            m.endTurn(A);m.endTurn(B);pay(m,A,m.activePokemonView(A).pokemonId());int before=m.privateView(A).deck().size();m.attack(A,a.id());var q=m.effectPokemonPrompt(A);
            check(q.targets().size()==1&&q.targets().getFirst().pokemonId().equals(target),"Homeward Chime offered Active/opponent");select(m,A,target);
            check(m.privateView(A).deck().size()==before+4&&m.privateView(A).deck().containsAll(List.of(energy,tool,evolution))&&m.benchPokemonView(A).isEmpty()&&!m.hasPendingEffectChoice(),"Homeward Chime lost stack/attachments or stalled");
        }
    }
    static void mystical(){
        boolean heads=false,tails=false;
        for(String id:List.of("sv6-5_en_022","svbsp_en_172"))for(int seed=0;seed<12;seed++){
            var a=DATA.pokemon(id).orElseThrow().attacks().getFirst();var m=game(a,seed);m.endTurn(A);m.endTurn(B);pay(m,A,m.activePokemonView(A).pokemonId());int size=m.privateView(B).deck().size();UUID target=m.benchPokemonView(B).getFirst().pokemonId();m.attack(A,a.id());
            if(m.hasPendingEffectChoice()){heads=true;select(m,A,target);check(m.benchPokemonView(B).isEmpty()&&m.privateView(B).deck().size()==size,"Mystical Return must shuffle one, then opponent draws one");}
            else {tails=true;check(m.benchPokemonView(B).size()==1&&m.privateView(B).deck().size()==size-1,"Mystical Return tails moved target");}
            check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Mystical Return stalled");
        }check(heads&&tails,"Both coin branches required");
    }
    static void perfume(){
        var a=DATA.pokemon("sv6_en_010").orElseThrow().attacks().getFirst();var m=game(a,0);pay(m,A,m.activePokemonView(A).pokemonId());CopyAttackTest.reject(()->m.attack(A,a.id()));m.endTurn(A);pay(m,B,m.activePokemonView(B).pokemonId());UUID target=m.benchPokemonView(A).getFirst().pokemonId();m.attack(B,a.id());select(m,B,target);
        check(m.benchPokemonView(A).isEmpty()&&!m.hasPendingEffectChoice(),"Slowing Perfume second-player opening effect");m.endTurn(A);CopyAttackTest.reject(()->m.attack(B,a.id()));
    }
    static void angelite(){
        for(String id:List.of("sv8_en_086","sv8-5_en_041","sv8-5_en_156"))for(int targets:List.of(1,2)){
            var a=DATA.pokemon(id).orElseThrow().attacks().get(1);var m=game(a,0);m.endTurn(A);
            if(targets==2)m.playBasicPokemonToBench(B,hand(m,B,"basic").instanceId());m.endTurn(B);
            var all=m.benchPokemonView(B).stream().map(v->v.pokemonId()).toList();pay(m,A,m.activePokemonView(A).pokemonId());m.attack(A,a.id());
            for(UUID target:all)select(m,A,target);
            check(m.benchPokemonView(B).isEmpty()&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Angelite short-Bench selection/shuffle");
            m.endTurn(B);UUID replacement=m.benchPokemonView(A).getFirst().pokemonId();pay(m,A,replacement);m.switchActiveByEffect(A,A,replacement);
            CopyAttackTest.reject(()->m.attack(A,a.id()));m.endTurn(A);m.endTurn(B);m.attack(A,a.id());check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Angelite cooldown did not clear after skipped turn");
        }
    }
}
