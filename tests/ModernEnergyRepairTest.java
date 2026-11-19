import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernEnergyRepairTest {
    static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){CopyAttackTest.check(ok,why);}
    static void reject(Runnable action){CopyAttackTest.reject(action);}
    static CardInstance hand(ServerMatch m,String id){return ModernRuleRepair2Test.hand(m,A,id);}
    static ServerMatch game(TrainerProfile trainer,List<AbilityDefinition> abilities,AttackDefinition attack,EnergyType type,int start){
        return game(trainer,abilities,attack,type,start,Map.of());
    }
    static ServerMatch game(TrainerProfile trainer,List<AbilityDefinition> abilities,AttackDefinition attack,EnergyType type,int start,Map<String,String> names){
        return game(trainer,abilities,attack,type,start,names,Map.of());
    }
    static ServerMatch game(TrainerProfile trainer,List<AbilityDefinition> abilities,AttackDefinition attack,EnergyType type,int start,Map<String,String> names,Map<String,Set<EnergyType>> printedTypes){
        return game(trainer,abilities,attack,type,start,names,printedTypes,Map.of());
    }
    static ServerMatch game(TrainerProfile trainer,List<AbilityDefinition> abilities,AttackDefinition attack,EnergyType type,int start,Map<String,String> names,Map<String,Set<EnergyType>> printedTypes,Map<String,TrainerProfile> extraTrainers){
        String[] ids=java.util.stream.Stream.concat(java.util.stream.Stream.of("basic","basic","basic","basic","evolved","wrongmon","energy","energy","energy","wrong","special","trainer"),extraTrainers.keySet().stream().sorted()).toArray(String[]::new);
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:Arrays.stream(ids).distinct().toList()){
            boolean pokemon=Set.of("basic","evolved","wrongmon").contains(id),energy=Set.of("energy","wrong","special").contains(id);
            var t=id.equals("wrongmon")?EnergyType.DRAGON:type;String name=id.equals("basic")?"Duskull":id.equals("evolved")?"Dusclops":id;
            name=names.getOrDefault(id,name);
            defs.add(new CardDefinition(id,name,pokemon?CardCategory.POKEMON:energy?CardCategory.ENERGY:CardCategory.TRAINER,
                id.equals("trainer")?trainer.category():extraTrainers.containsKey(id)?extraTrainers.get(id).category():TrainerCategory.NONE,"I",Set.of("international"),pokemon&&!id.equals("evolved"),energy&&!id.equals("special"),60,Map.of(),EffectSupport.SUPPORTED,null,Set.of(),pokemon?printedTypes.getOrDefault(id,Set.of(t)):Set.of()));
            if(pokemon)profiles.put(id,new PokemonProfile(500,0,id.equals("evolved")?"basic":null,null,0,null,0,1,List.of(attack),id.equals("wrongmon")?List.of():abilities,t,name,id.equals("evolved")?"Duskull":null));
        }
        var trainers=new HashMap<>(extraTrainers);trainers.put("trainer",trainer);
        var combat=new CombatCatalog(profiles,Map.of("energy",type,"wrong",EnergyType.FIRE),trainers,Map.of("special",EnergyProfile.fixed(type,2)));
        for(int seed=start;seed<start+2000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->{var basics=m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).limit(2).toList();return basics.size()<2||basics.stream().anyMatch(c->!c.definitionId().equals("basic"));}))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,24);m.endTurn(A);m.endTurn(B);
            if(Arrays.stream(ids).allMatch(id->m.privateView(A).hand().stream().anyMatch(c->c.definitionId().equals(id)))&&m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).count()>=3)return m;
        }throw new AssertionError("energy repair fixture");
    }
    public static void main(String[] args){dino();generator();lana();duskull();cassiopeia();endTurnTrainers();System.out.println("ModernEnergyRepairTest passed: Dino Cry six prints, Electric Generator two, Lana three, Duskull three, Cassiopeia three, Katy two, Boxed Order one; targeting, optional choices, privacy, continuation and turn endings");}
    static void dino(){
        for(String id:List.of("sv1_en_125","sv1_en_231","sv1_en_247","sv1_en_254","sv4-5_en_245","svbsp_en_029"))for(int count:List.of(0,1,2)){
            var ability=DATA.pokemon(id).orElseThrow().abilities().getFirst();var m=game(DATA.trainer("swsh5_en_124").orElseThrow(),List.of(ability),GeneratedRuleBatch8Test.hit(40,false),EnergyType.FIGHTING,0);
            UUID active=m.activePokemonView(A).pokemonId();m.evolveByEffect(A,active,hand(m,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            m.playBasicPokemonToBench(A,hand(m,"wrongmon").instanceId());m.playBasicPokemonToBench(A,hand(m,"basic").instanceId());
            var correct=m.benchPokemonView(A).stream().filter(v->v.topCardId().equals("basic")).map(v->v.pokemonId()).toList();
            var energies=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).limit(2).toList();
            var discards=new ArrayList<>(energies);discards.add(hand(m,"wrong"));discards.add(hand(m,"special"));m.discardHandCardsByEffect(A,A,discards.stream().map(CardInstance::instanceId).toList());
            m.useAbility(A,active,ability.id());var p=m.effectCardPrompt(A);check(new HashSet<>(p.candidates()).equals(new HashSet<>(energies)),"Dino energy filter");
            var chosen=energies.stream().limit(count).toList();m.submitEffectCards(A,p.resolutionId(),p.step(),chosen.stream().map(CardInstance::instanceId).toList());
            if(count>0){var q=m.effectAttachmentPrompt(A);check(new HashSet<>(q.targets()).equals(new HashSet<>(correct)),"Dino includes evolved Fighting or wrong type");
                for(int i=0;i<chosen.size();i++){var c=chosen.get(i);reject(()->m.assignEffectEnergyDistribution(A,q.resolutionId(),q.step(),c.instanceId(),active));m.assignEffectEnergyDistribution(A,q.resolutionId(),q.step(),c.instanceId(),correct.get(i));}
                m.confirmEffectEnergyDistribution(A,q.resolutionId(),q.step());}
            check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Dino must end turn even when zero attached");
            for(int i=0;i<count;i++){UUID target=correct.get(i),card=chosen.get(i).instanceId();check(m.benchPokemonView(A).stream().anyMatch(v->v.pokemonId().equals(target)&&v.attachedEnergyIds().contains(card)),"Dino distribution lost card");}
        }
    }
    static void generator(){
        int attached=0;
        for(String id:List.of("sv1_en_170","sv4-5_en_079"))for(int seed:List.of(0,10,30,60))for(boolean take:List.of(false,true)){
            var m=game(DATA.trainer(id).orElseThrow(),List.of(),GeneratedRuleBatch8Test.hit(40,false),EnergyType.LIGHTNING,seed);var trainer=hand(m,"trainer");
            var before=m.privateView(A);var window=before.deck().subList(before.deck().size()-5,before.deck().size());
            m.playTrainer(A,trainer.instanceId(),null);var p=m.clientView(A).effectSelection();List<CardInstance> chosen=List.of();
            if(p!=null){check(p.candidates().stream().allMatch(c->window.contains(c)&&c.definitionId().equals("energy")),"Generator exposes wrong window/Energy");
                chosen=take?p.candidates().stream().limit(2).toList():List.of();m.submitEffectCards(A,p.resolutionId(),p.step(),chosen.stream().map(CardInstance::instanceId).toList());
                if(!chosen.isEmpty()){var q=m.effectAttachmentPrompt(A);check(!q.targets().contains(m.activePokemonView(A).pokemonId()),"Generator allows Active");
                    for(var c:chosen)m.assignEffectEnergyDistribution(A,q.resolutionId(),q.step(),c.instanceId(),q.targets().getFirst());m.confirmEffectEnergyDistribution(A,q.resolutionId(),q.step());attached+=chosen.size();}}
            var left=new HashSet<>(before.deck());left.removeAll(chosen);check(new HashSet<>(m.privateView(A).deck()).equals(left)&&m.privateView(A).hand().size()==before.hand().size()-1,"Generator gives extra cards");
            check(m.randomTranscript().stream().filter(r->r.operation().equals("effect-top-energy-remainder:"+A)).count()==Math.max(0,left.size()-1),"Generator shuffle missing");
            check(m.currentPlayer().equals(A)&&!m.hasPendingEffectChoice(),"Generator continuation");
        }check(attached>0,"Generator seeds never attach");
    }
    static void lana(){
        for(String id:List.of("sv6_en_155","sv6_en_207","sv6_en_219"))for(int count:List.of(0,3)){
            var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,DATA.trainer(id).orElseThrow(),GeneratedRuleBatch8Test.hit(40,false));
            var eligible=List.of(hand(m,"basic"),hand(m,"evolved"),hand(m,"energy"));var discard=new ArrayList<>(eligible);discard.add(hand(m,"rulebox"));discard.add(hand(m,"special"));discard.add(hand(m,"tool"));
            m.discardHandCardsByEffect(A,A,discard.stream().map(CardInstance::instanceId).toList());var trainer=hand(m,"trainer");m.playTrainer(A,trainer.instanceId(),null);var p=m.effectCardPrompt(A);
            check(new HashSet<>(p.candidates()).equals(new HashSet<>(eligible)),"Lana accepts Rule Box, Special Energy or Trainer");
            var chosen=eligible.stream().limit(count).toList();m.submitEffectCards(A,p.resolutionId(),p.step(),chosen.stream().map(CardInstance::instanceId).toList());
            check(m.privateView(A).hand().containsAll(chosen)&&!m.hasPendingEffectChoice(),"Lana recovery continuation");
        }
    }
    static void duskull(){
        for(String id:List.of("sv6-5_en_018","sv6-5_en_068","sv8-5_en_035"))for(int count:List.of(0,3)){
            var attack=DATA.pokemon(id).orElseThrow().attacks().getFirst();var m=game(DATA.trainer("swsh5_en_124").orElseThrow(),List.of(),attack,EnergyType.PSYCHIC,0);
            ModernRuleRepair2Test.pay(m,A,m.activePokemonView(A).pokemonId(),"energy",1);
            var candidates=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("basic")).limit(3).toList();check(candidates.size()==3,"Duskull fixture");
            var discard=new ArrayList<>(candidates);discard.add(hand(m,"evolved"));discard.add(hand(m,"wrongmon"));m.discardHandCardsByEffect(A,A,discard.stream().map(CardInstance::instanceId).toList());
            m.attack(A,attack.id());var p=m.effectCardPrompt(A);check(new HashSet<>(p.candidates()).equals(new HashSet<>(candidates)),"Duskull named Basic filter");
            m.submitEffectCards(A,p.resolutionId(),p.step(),candidates.stream().limit(count).map(CardInstance::instanceId).toList());
            check(m.benchPokemonView(A).size()==count+1&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Duskull placement/turn continuation");
        }
    }
    static void cassiopeia(){
        for(String id:List.of("sv6-5_en_056","sv6-5_en_086","sv6-5_en_094"))for(int count:List.of(1,2)){
            var m=game(DATA.trainer(id).orElseThrow(),List.of(),GeneratedRuleBatch8Test.hit(40,false),EnergyType.GRASS,0);var trainer=hand(m,"trainer");
            check(!m.canPlayTrainer(A,trainer.instanceId(),null),"Cassiopeia used with other hand cards");reject(()->m.playTrainer(A,trainer.instanceId(),null));
            m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().filter(c->!c.equals(trainer)).map(CardInstance::instanceId).toList());
            m.playTrainer(A,trainer.instanceId(),null);var p=m.effectCardPrompt(A);check(p.minimum()==1&&p.maximum()==2,"Cassiopeia up-to count");reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));
            var chosen=p.candidates().stream().limit(count).toList();m.submitEffectCards(A,p.resolutionId(),p.step(),chosen.stream().map(CardInstance::instanceId).toList());
            check(new HashSet<>(m.privateView(A).hand()).equals(new HashSet<>(chosen))&&m.effectCardReveal()==null&&!m.hasPendingEffectChoice(),"Cassiopeia privacy/continuation");
        }
    }
    static void endTurnTrainers(){
        for(String id:List.of("sv1_en_177","sv1_en_237","sv5_en_143"))for(boolean take:List.of(false,true)){
            var m=game(DATA.trainer(id).orElseThrow(),List.of(),GeneratedRuleBatch8Test.hit(40,false),EnergyType.GRASS,0);var trainer=hand(m,"trainer");
            int bHand=m.privateView(B).hand().size(),aHand=m.privateView(A).hand().size();m.playTrainer(A,trainer.instanceId(),null);
            if(id.equals("sv5_en_143")){
                var p=m.effectCardPrompt(A);check(p.minimum()==0&&p.maximum()==2&&p.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).trainerCategory()==TrainerCategory.ITEM),"Boxed Order Item search");
                var chosen=take?p.candidates().stream().limit(2).toList():List.<CardInstance>of();m.submitEffectCards(A,p.resolutionId(),p.step(),chosen.stream().map(CardInstance::instanceId).toList());
                if(!chosen.isEmpty()){var reveal=m.effectCardReveal();check(reveal!=null&&m.currentPlayer().equals(A),"end turn before search confirmation");m.acknowledgeEffectReveal(reveal.confirmingPlayer(),reveal.id());}
                check(m.privateView(A).hand().size()==aHand-1+chosen.size(),"Boxed Order wrong hand result");
            }else check(m.privateView(A).hand().size()==8,"Katy must shuffle then draw 8");
            check(m.currentPlayer().equals(B)&&m.privateView(B).hand().size()==bHand+1&&!m.hasPendingEffectChoice()&&m.privateView(A).discard().contains(trainer),"Trainer did not complete and end turn exactly once");
            reject(()->m.playTrainer(A,trainer.instanceId(),null));
        }
    }
}
