import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernTargetRuleRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String why){ModernRuleRepair2Test.check(b,why);}
    static CardInstance hand(ServerMatch m,String id){return ModernRuleRepair2Test.hand(m,A,id);}
    public static void main(String[] args){wugtrio();cofagrigus();vital();iron();System.out.println("ModernTargetRuleRepairTest passed: selected Active/Bench damage with hand costs, Ability-filtered both-field counters, unrecoverable trainer, damaged-target attack and exact named-Tool cost");}
    static void wugtrio(){for(String id:List.of("sv5_en_060","sv5_en_190"))wugtrio(id);}
    static void wugtrio(String id){
        var a=DATA.pokemon(id).orElseThrow().attacks().getFirst();AttackProgramRules.validate(a);
        for(boolean bench:List.of(false,true))for(int count:List.of(0,1,3)){
            var m=ModernQuantityRepairTest.game(a,List.of(),EnergyType.WATER,"swsh5_en_124");ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",1);
            UUID target=bench?m.benchPokemonView(B).getFirst().pokemonId():m.activePokemonView(B).pokemonId();m.attack(A,a.id(),target);var q=m.effectCardPrompt(A);
            check(q.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).category()==CardCategory.ENERGY),"Wugtrio accepted non Energy");
            var chosen=q.candidates().stream().limit(count).map(CardInstance::instanceId).toList();check(chosen.size()==count,"Wugtrio fixture");m.submitEffectCards(A,q.resolutionId(),q.step(),chosen);
            check((bench?m.benchPokemonView(B).getFirst():m.activePokemonView(B)).damage()==60*count&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Wugtrio wrong target, repeated payment or stalled");
        }
    }
    static void cofagrigus(){
        var a=DATA.pokemon("sv8_en_083").orElseThrow().attacks().getFirst();AttackProgramRules.validate(a);
        var ability=PossessedAbilityAttackSourceFilterTest.ability();var m=ModernFieldRuleRepairTest.game(a,List.of(ability),EnergyType.PSYCHIC);
        m.playBasicPokemonToBench(A,hand(m,"wrongmon").instanceId());UUID plain=m.benchPokemonView(A).getLast().pokemonId();ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",1);
        m.attack(A,a.id());check(m.activePokemonView(A).damage()==60&&m.activePokemonView(B).damage()==60&&m.benchPokemonView(B).getFirst().damage()==60,"Cofagrigus missed either player's Ability hosts");
        check(m.benchPokemonView(A).stream().filter(p->p.pokemonId().equals(plain)).findFirst().orElseThrow().damage()==0&&!m.hasPendingEffectChoice(),"Cofagrigus damaged non-Ability host or stalled");
    }
    static void vital(){
        var m=ModernCountRepairTest.game("sv6-5_en_062",List.of());var trainer=hand(m,"trainer");check(!m.canPlayTrainer(A,trainer.instanceId(),null),"Unrecoverable marker enabled useless healing");
        UUID host=m.benchPokemonView(A).getFirst().pokemonId();m.placeDamageCountersByEffect(A,A,host,20);m.playTrainer(A,trainer.instanceId(),host);
        check(m.benchPokemonView(A).getFirst().damage()==50&&m.privateView(A).discard().contains(trainer),"Vital heal or discard failed");
        var policy=new ServerMatch.CardSelectionPolicy(1,1,CardCategory.TRAINER,TrainerCategory.ITEM,false,Set.of());
        for(var destination:List.of(CardEffectDestination.HAND,CardEffectDestination.DECK_SHUFFLED))CopyAttackTest.reject(()->m.recoverDiscardCardsByEffect(A,A,policy,List.of(trainer.instanceId()),destination));
        check(m.privateView(A).discard().contains(trainer)&&!m.hasPendingEffectChoice(),"Vital recovery rejection was not atomic");
    }
    static void iron(){
        for(String id:List.of("sv4_en_158","sv4_en_216","svalt_en_049")){
            var first=DATA.pokemon(id).orElseThrow().attacks().getFirst();AttackProgramRules.validate(first);
            var m=ModernAttackFlowRepairTest.game(first,0);UUID front=m.activePokemonView(B).pokemonId(),bench=m.benchPokemonView(B).getFirst().pokemonId();m.placeDamageCountersByEffect(A,B,bench,1);
            CopyAttackTest.reject(()->m.attack(A,first.id(),front));m.attack(A,first.id(),bench);
            check(m.benchPokemonView(B).getFirst().damage()==60&&m.activePokemonView(B).damage()==0&&!m.hasPendingEffectChoice(),"Iron Jugulis ignored damaged-target filter");
            var second=DATA.pokemon(id).orElseThrow().attacks().get(1);AttackProgramRules.validate(second);
            for(String tool:List.of("wrongtool","sv4_en_164","sv4_en_250")){
                var g=toolGame(second);UUID host=g.activePokemonView(A).pokemonId();g.playTrainer(A,hand(g,tool).instanceId(),host);
                if(tool.equals("wrongtool"))CopyAttackTest.reject(()->g.attack(A,second.id()));
                else {g.attack(A,second.id());check(g.lastAttack().finalDamage()==150&&!g.hasPendingEffectChoice(),"Exact Future Booster did not enable three-unit cost");}
            }
        }
    }
    static ServerMatch toolGame(AttackDefinition attack){
        return toolGame(attack,List.of());
    }
    static ServerMatch toolGame(AttackDefinition attack,List<AbilityDefinition> abilities){
        String[] ids={"basic","basic","basic","basic","energy","energy","energy","sv4_en_164","sv4_en_250","wrongtool"};
        var defs=new ArrayList<CardDefinition>();for(String id:Arrays.stream(ids).distinct().toList())defs.add(StadiumActivationTest.definition(id,id.equals("basic")?CardCategory.POKEMON:id.equals("energy")?CardCategory.ENERGY:CardCategory.TRAINER,id.endsWith("tool")||id.startsWith("sv4")?TrainerCategory.TOOL:TrainerCategory.NONE,id.equals("basic")));
        var unit=new EnergyProfile.Unit(EnumSet.allOf(EnergyType.class));
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(500,0,null,null,0,null,0,1,List.of(attack),abilities,EnergyType.COLORLESS)),Map.of(),Map.of("sv4_en_164",DynamicPassiveModifierTest.plainTool(),"sv4_en_250",DynamicPassiveModifierTest.plainTool(),"wrongtool",DynamicPassiveModifierTest.plainTool()),Map.of("energy",new EnergyProfile(List.of(unit,unit,unit))));
        for(int seed=0;seed<100;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals("basic"))))continue;
            OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,24);m.endTurn(A);m.endTurn(B);
            if(Arrays.stream(ids).allMatch(id->m.privateView(A).hand().stream().anyMatch(c->c.definitionId().equals(id)))){ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",1);return m;}
        }throw new AssertionError("No tool fixture");
    }
}
