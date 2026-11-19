import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernActivationRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String why){ModernRuleRepair2Test.check(b,why);}
    static CardInstance hand(ServerMatch m,String id){return ModernRuleRepair2Test.hand(m,A,id);}
    public static void main(String[] args){durant();quaquaval();feraligatr();meowstic();starmie();System.out.println("ModernActivationRepairTest passed: exact named-field counters, hand-to-bottom payment, short decks, named Trainer costs, source-bound Torrential Heart and Starmie success-only complete-stack discard with protection/KO");}
    static void starmie(){
        for(String id:List.of("sv3-5_en_121","sv4-5_en_119"))for(boolean ownBench:List.of(false,true))for(boolean foeBench:List.of(false,true))for(boolean blocked:List.of(false,true))for(boolean lethal:List.of(false,true)){
            var ability=DATA.pokemon(id).orElseThrow().abilities().getFirst();ActivatedAbilityRules.validate(ability);
            var m=GeneratedRuleBatch8Test.fixture(List.of(ability),EnergyType.WATER,EnergyType.WATER,DATA.trainer("swsh5_en_124").orElseThrow(),GeneratedRuleBatch8Test.hit(10,false));
            UUID source=ownBench?m.benchPokemonView(A).getFirst().pokemonId():m.activePokemonView(A).pokemonId(),target=foeBench?m.benchPokemonView(B).getFirst().pokemonId():m.activePokemonView(B).pokemonId();
            var f=EventTargetBindingTest.field(m);var bottom=f.topCard(A,source);var evolution=hand(m,"evolved");m.evolveByEffect(A,source,evolution.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);var energy=hand(m,"energy");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,energy.instanceId(),A,source);var tool=hand(m,"tool");m.playTrainer(A,tool.instanceId(),source);
            if(lethal)f.placeDamageCounters(B,target,49);
            if(blocked)f.addProtection(B,B,target,new ProtectionEffect(new EffectProtection(EffectProtection.Source.ABILITY,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));
            var before=m.privateView(A);CopyAttackTest.reject(()->m.useAbility(A,source,ability.id(),source));m.useAbility(A,source,ability.id(),target);
            if(blocked){check(f.pokemonView(A,source).attachedTools().size()==1&&m.privateView(A).discard().equals(before.discard())&&f.pokemonView(B,target).damage()==(lethal?490:0),"Blocked Starmie discarded itself or ignored protection");}
            else {check(m.privateView(A).discard().containsAll(List.of(bottom,evolution,energy,tool)),"Starmie failed to discard full source stack");int cap=8;
                while((m.phase()==MatchPhase.PRIZE_SELECTION||m.phase()==MatchPhase.CHOOSE_REPLACEMENT)&&cap-->0){UUID chooser=m.pendingChoicePlayer();if(m.phase()==MatchPhase.PRIZE_SELECTION)m.takePrizeAt(chooser,0);else m.chooseReplacement(chooser,m.benchPokemonView(chooser).getFirst().pokemonId());}
                check(cap>0&&m.privateView(A).prizes().size()==(lethal?5:6)&&m.privateView(B).prizes().size()==6,"Starmie self-discard gave prizes or lethal target failed to settle");}
            check(m.currentPlayer().equals(A)&&!m.hasPendingEffectChoice(),"Starmie did not resume own turn");
        }
    }
    static void feraligatr(){
        for(String id:List.of("sv5_en_041","svbsp_en_089"))for(int mode=0;mode<7;mode++){
            var ability=DATA.pokemon(id).orElseThrow().abilities().getFirst();ActivatedAbilityRules.validate(ability);var hit=GeneratedRuleBatch8Test.hit(80,false);
            var m=ModernFieldRuleRepairTest.game(hit,List.of(ability),EnergyType.WATER);UUID active=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId(),source=mode==1?bench:active;
            if(mode==5)EventTargetBindingTest.field(m).placeDamageCounters(A,source,45);
            m.useAbility(A,source,ability.id());
            if(mode==5){check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.pendingChoicePlayer().equals(B),"Torrential Heart lethal cost stalled");m.takePrizeAt(B,0);m.chooseReplacement(A,bench);check(m.currentPlayer().equals(A)&&!m.hasPendingEffectChoice(),"Ability self-KO failed to resume own turn");m.attack(A,hit.id());check(m.lastAttack().finalDamage()==80,"KO source buff leaked to replacement");continue;}
            check(EventTargetBindingTest.field(m).pokemonView(A,source).damage()==50,"Torrential Heart must place exactly 5 counters");CopyAttackTest.reject(()->m.useAbility(A,source,ability.id()));
            if(mode==1||mode==2||mode==3)m.switchActiveByEffect(A,A,bench);
            if(mode==3)m.switchActiveByEffect(A,A,active);
            if(mode==4){m.endTurn(A);m.endTurn(B);}
            if(mode==6)m.useAbility(A,bench,ability.id());
            m.attack(A,hit.id());check(m.lastAttack().finalDamage()==(mode==2||mode==4?80:200)&&m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Torrential Heart wrong source, duration, stacking or continuation mode="+mode);
        }
    }
    static void durant(){
        var attack=DATA.pokemon("sv4_en_129").orElseThrow().attacks().getFirst();AttackProgramRules.validate(attack);
        for(int counters:List.of(0,2,4)){
            var m=ModernEnergyRepairTest.game(DATA.trainer("swsh5_en_124").orElseThrow(),List.of(),attack,EnergyType.METAL,0,Map.of("basic","Durant","wrongmon","Durant ex"));
            UUID host=m.activePokemonView(A).pokemonId(),bench=m.benchPokemonView(A).getFirst().pokemonId();m.playBasicPokemonToBench(A,hand(m,"wrongmon").instanceId());UUID wrong=m.benchPokemonView(A).getLast().pokemonId();
            var field=EventTargetBindingTest.field(m);if(counters>0){field.placeDamageCounters(A,host,counters);field.placeDamageCounters(A,bench,counters);}field.placeDamageCounters(A,wrong,5);field.placeDamageCounters(B,m.benchPokemonView(B).getFirst().pokemonId(),5);
            ModernRuleRepair2Test.pay(m,A,host,"energy",2);m.attack(A,attack.id());check(m.lastAttack().finalDamage()==counters*40&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Durant counted wrong names/opponent, or omitted own Bench");
        }
    }
    static void quaquaval(){
        var ability=DATA.pokemon("sv8_en_052").orElseThrow().abilities().getFirst();ActivatedAbilityRules.validate(ability);
        for(int held:List.of(0,1,4,5,6,8))for(int remaining:List.of(0,2,9)){
            var m=ModernEnergyRepairTest.game(DATA.trainer("swsh5_en_124").orElseThrow(),List.of(ability),GeneratedRuleBatch8Test.hit(10,false),EnergyType.WATER,0);
            m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-remaining);var cards=m.privateView(A).hand();m.discardHandCardsByEffect(A,A,cards.subList(held,cards.size()).stream().map(CardInstance::instanceId).toList());
            UUID host=m.activePokemonView(A).pokemonId();var before=m.privateView(A);var rng=m.randomTranscript();
            if(held==0||held>=6){CopyAttackTest.reject(()->m.useAbility(A,host,ability.id()));check(before.equals(m.privateView(A))&&rng.equals(m.randomTranscript()),"Empty cost or full hand mutated state");continue;}
            m.useAbility(A,host,ability.id());var q=m.effectCardPrompt(A);check(q.minimum()==1&&q.maximum()==1&&m.privateView(A).deck().equals(before.deck()),"Up-Tempo drew before cost");
            CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of()));var selected=q.candidates().getLast();m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(selected.instanceId()));
            var expectedDeck=new ArrayList<>(before.deck());expectedDeck.addFirst(selected);var expectedHand=new ArrayList<>(before.hand());expectedHand.remove(selected);while(expectedHand.size()<5&&!expectedDeck.isEmpty())expectedHand.add(expectedDeck.removeLast());
            check(m.privateView(A).deck().equals(expectedDeck)&&m.privateView(A).hand().equals(expectedHand)&&m.privateView(A).discard().equals(before.discard())&&rng.equals(m.randomTranscript()),"Up-Tempo shuffled/discarded cost or drew wrong amount");
            check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"Up-Tempo stalled");CopyAttackTest.reject(()->m.useAbility(A,host,ability.id()));
        }
    }
    static ServerMatch meowsticGame(AbilityDefinition ability){
        String[] ids={"basic","basic","basic","basic","basic","energy","sv8_en_166","sv8_en_166","wrong"};var defs=new ArrayList<CardDefinition>();
        for(String id:Arrays.stream(ids).distinct().toList())defs.add(new CardDefinition(id,id.equals("sv8_en_166")?"凉爽逗猫棒":id,id.equals("basic")?CardCategory.POKEMON:id.equals("energy")?CardCategory.ENERGY:CardCategory.TRAINER,Set.of("wrong","sv8_en_166").contains(id)?TrainerCategory.ITEM:TrainerCategory.NONE,"H",Set.of("international"),id.equals("basic"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
        var profile=new PokemonProfile(500,0,null,null,0,null,0,1,List.of(GeneratedRuleBatch8Test.hit(10,false)),List.of(ability),EnergyType.PSYCHIC);
        var combat=new CombatCatalog(Map.of("basic",profile),Map.of("energy",EnergyType.PSYCHIC),Map.of("sv8_en_166",DATA.trainer("sv8_en_166").orElseThrow(),"wrong",new TrainerProfile(TrainerCategory.ITEM,new TrainerEffect(TrainerEffect.Opcode.DRAW,1,0))));
        for(int seed=0;seed<100;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,24);m.endTurn(A);m.endTurn(B);
            if(Arrays.stream(ids).allMatch(id->m.privateView(A).hand().stream().anyMatch(c->c.definitionId().equals(id))))return m;
        }throw new AssertionError("Meowstic fixture");
    }
    static void meowstic(){
        var ability=DATA.pokemon("sv8_en_085").orElseThrow().abilities().getFirst();ActivatedAbilityRules.validate(ability);
        for(boolean hasToy:List.of(false,true)){
            var m=meowsticGame(ability);UUID host=m.activePokemonView(A).pokemonId(),next=m.benchPokemonView(B).getFirst().pokemonId();
            if(!hasToy)m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().filter(c->c.definitionId().equals("sv8_en_166")).map(CardInstance::instanceId).toList());
            var before=m.privateView(A);if(!hasToy){CopyAttackTest.reject(()->m.useAbility(A,host,ability.id()));check(before.equals(m.privateView(A)),"Missing named cost mutated");continue;}
            var old=m.activePokemonView(B);m.useAbility(A,host,ability.id());var q=m.effectCardPrompt(A);check(q.minimum()==1&&q.maximum()==1&&q.candidates().stream().allMatch(c->c.definitionId().equals("sv8_en_166")),"Named Trainer cost filter failed");
            CopyAttackTest.reject(()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(hand(m,"wrong").instanceId())));check(m.activePokemonView(B).equals(old),"Gust before valid cost");var cost=q.candidates().getLast();m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(cost.instanceId()));
            var target=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,target.resolutionId(),target.step(),next);check(m.privateView(A).discard().contains(cost)&&m.activePokemonView(B).pokemonId().equals(next)&&!m.hasPendingEffectChoice(),"Named cost not discarded or gust stalled");CopyAttackTest.reject(()->m.useAbility(A,host,ability.id()));
        }
    }
}
