import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernWindowDamageRepairTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static AttackDefinition attack(String id,int slot){var a=DATA.pokemon(id).orElseThrow().attacks().get(slot);AttackProgramRules.validate(a);return a;}
    static ServerMatch game(AttackDefinition attack,int first,boolean weak){
        return game(attack,first,weak,List.of());
    }
    static ServerMatch game(AttackDefinition attack,int first,boolean weak,List<AttackDefinition> extraAttacks){
        return game(attack,first,weak,extraAttacks,Map.of());
    }
    static ServerMatch game(AttackDefinition attack,int first,boolean weak,List<AttackDefinition> extraAttacks,Map<String,Integer> retreats){
        var attacks=new ArrayList<>(List.of(attack,CopyAttackTest.copier()));attacks.addAll(extraAttacks);
        var names=List.of("basic","future-pokemon","future-item","future-name-only","energy","special");
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();var trainers=new HashMap<String,TrainerProfile>();
        for(String id:names){boolean pokemon=Set.of("basic","future-pokemon").contains(id),energy=Set.of("energy","special").contains(id);var category=pokemon?CardCategory.POKEMON:energy?CardCategory.ENERGY:CardCategory.TRAINER;
            defs.add(new CardDefinition(id,id,category,category==CardCategory.TRAINER?TrainerCategory.ITEM:TrainerCategory.NONE,"I",Set.of("international"),pokemon,id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null,Set.of(),pokemon?Set.of(attack.damageType()):Set.of(),Set.of("future-pokemon","future-item").contains(id)?Set.of(BattleStyle.FUTURE):Set.of()));
            if(pokemon)profiles.put(id,new PokemonProfile(2000,retreats.getOrDefault(id,0),null,weak?attack.damageType():null,weak?2:0,null,0,1,attacks,List.of(),attack.damageType()));
            if(category==CardCategory.TRAINER)trainers.put(id,TrainerPlayLockTest.item());}
        var catalog=new CombatCatalog(profiles,Map.of("energy",attack.damageType()),trainers,Map.of("special",EnergyProfile.fixed(attack.damageType(),2)));
        var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,names.get(i%names.size())));decks.add(cards);}
        for(int seed=first;seed<first+2000;seed++){var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).count()<2))continue;OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,16);m.endTurn(A);m.endTurn(B);
            if(m.privateView(A).hand().stream().anyMatch(c->c.definitionId().equals("energy"))&&m.privateView(A).hand().stream().anyMatch(c->c.definitionId().equals("special"))){ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",1);ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"special",1);return m;}}
        throw new AssertionError("Window fixture");
    }
    static List<CardInstance> top(ServerMatch m,int amount){var deck=m.privateView(A).deck();var cards=new ArrayList<>(deck.subList(Math.max(0,deck.size()-amount),deck.size()));Collections.reverse(cards);return List.copyOf(cards);}
    public static void main(String[] args){windows();quagsire();tentacruel();System.out.println("ModernWindowDamageRepairTest passed: five printed top windows, actual Energy/Future metadata, short decks, public reveal and authority, Active/Bench weakness, post-damage discard/shuffle, Quagsire count and Tentacruel first-tails branch");}
    static void windows(){
        for(String id:List.of("sv4_en_032","sv5_en_062","svalt_en_072","svalt_en_120","svbsp_en_098"))for(int size:List.of(0,1,3,5,8))for(boolean weak:List.of(false,true))for(boolean bench:List.of(false,true)){
            boolean energy=id.equals("sv4_en_032");if(bench&&!energy)continue;var a=attack(id,0);var m=game(a,0,weak);m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-size);var before=m.privateView(A);var w=top(m,energy?6:5);int rng=m.randomTranscript().size();
            var matched=w.stream().filter(c->energy?m.cardDefinition(c.definitionId()).category()==CardCategory.ENERGY:m.cardDefinition(c.definitionId()).battleStyles().contains(BattleStyle.FUTURE)).toList();var returned=w.stream().filter(c->!matched.contains(c)).toList();
            UUID target=bench?m.benchPokemonView(B).getFirst().pokemonId():m.activePokemonView(B).pokemonId();m.attackTargets(A,a.id(),energy?List.of(target):List.of());
            if(size>0){var r=m.effectCardReveal();check(r.cardIds().equals(w.stream().map(CardInstance::definitionId).toList())&&m.privateView(A).deck().equals(before.deck())&&m.privateView(A).discard().equals(before.discard())&&m.activePokemonView(B).damage()==0&&m.randomTranscript().size()==rng,"Window used bottom, moved cards or damaged before acknowledgement");
                CopyAttackTest.reject(()->m.acknowledgeEffectReveal(A,r.id()));m.acknowledgeEffectReveal(B,r.id());CopyAttackTest.reject(()->m.acknowledgeEffectReveal(B,r.id()));}
            int expected=matched.size()*(energy?60:70)*(weak&&!bench?2:1);check(EventTargetBindingTest.field(m).pokemonView(B,target).damage()==expected,"Window damage counted wrong physical metadata or Bench weakness");
            var expectedDeck=new HashSet<>(before.deck());expectedDeck.removeAll(matched);check(new HashSet<>(m.privateView(A).deck()).equals(expectedDeck)&&m.privateView(A).deck().size()==expectedDeck.size()&&m.privateView(A).discard().containsAll(matched)&&m.privateView(A).discard().size()==before.discard().size()+matched.size(),"Window discarded/kept wrong physical cards");
            check((m.randomTranscript().size()>rng)==(!returned.isEmpty()&&expectedDeck.size()>1)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Window incorrect shuffle/continuation");
        }
    }
    static void quagsire(){var a=attack("svbsp_en_156",1);Set<Integer> counts=new HashSet<>();boolean special=false;
        for(int seed=0;seed<24;seed++)for(int size:List.of(0,1,3,6)){var m=game(a,seed,false);m.drawCardsByEffect(A,A,m.privateView(A).deck().size()-size);var before=m.privateView(A);var w=top(m,3);int count=(int)w.stream().filter(c->m.cardDefinition(c.definitionId()).category()==CardCategory.ENERGY).count();counts.add(count);special|=w.stream().anyMatch(c->c.definitionId().equals("special"));int rng=m.randomTranscript().size();m.attack(A,a.id());
            check(m.lastAttack().finalDamage()==count*80&&m.privateView(A).discard().containsAll(w)&&m.privateView(A).discard().size()==before.discard().size()+w.size()&&m.privateView(A).deck().size()==size-w.size()&&!m.hasPendingEffectChoice()&&m.randomTranscript().size()==rng&&m.currentPlayer().equals(B),"Quagsire did not count discarded Energy before damage");}
        check(counts.containsAll(List.of(0,1,2))&&special,"Quagsire mixed/special/zero scenarios missing");}
    static void tentacruel(){var a=attack("sv3-5_en_073",1);Set<Integer> seen=new HashSet<>();
        for(int seed=0;seed<32;seed++){var m=game(a,seed,false);int rng=m.randomTranscript().size();m.attack(A,a.id());var coins=m.randomTranscript().subList(rng,m.randomTranscript().size());int heads=coins.size()-1;seen.add(heads);
            check(coins.getLast().result()==1&&coins.subList(0,heads).stream().allMatch(c->c.result()==0)&&m.lastAttack().finalDamage()==heads*90&&m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED)==(heads==0)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Tentacruel first-tails condition, shared coin result or damage phase wrong");}
        check(seen.contains(0)&&seen.stream().anyMatch(n->n>1),"Tentacruel missing zero/multiple-heads branches");}
}
