import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class ModernRuleRepair2Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void reject(Runnable op){CopyAttackTest.reject(op);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return m.privateView(p).hand().stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow();}
    static void pay(ServerMatch m,UUID owner,UUID host,String energy,int count){for(int i=0;i<count;i++)m.attachEnergyByEffect(A,owner,EffectSourceZone.HAND,hand(m,owner,energy).instanceId(),owner,host);}
    static AttackDefinition attack(String id,int slot){var a=DATA.pokemon(id).orElseThrow().attacks().get(slot);AttackProgramRules.validate(a);return a;}
    static ServerMatch game(AttackDefinition a,EnergyType type){return GeneratedRuleBatch8Test.fixture(List.of(),type,type,DATA.trainer("swsh5_en_124").orElseThrow(),a);}
    public static void main(String[] args){petal();raid();diver();coins();smog();System.out.println("ModernRuleRepair2Test passed: six real card programs; conditional hand payment, Bench ex damage, typed KO recovery, both trainer coin branches, lethal-damage deck-to-Bench continuation");}
    static void petal(){
        var a=attack("sv10_en_014",1);
        for(int amount:List.of(0,1,2,3)) {
            var m=game(a,EnergyType.GRASS);pay(m,A,active(m,A),"energy",1);
            var extra=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).skip(amount).map(CardInstance::instanceId).toList();
            if(!extra.isEmpty())m.discardHandCardsByEffect(A,A,extra);
            int available=(int)m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).count();check(available==amount,"fixture Grass count");
            int old=m.privateView(A).discard().size();m.attack(A,a.id());var p=m.clientView(A).effectSelection();
            if(p!=null){check(p.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"wrong energy accepted as cost");
                var selection=p.candidates().stream().limit(2).map(CardInstance::instanceId).toList();
                reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),selection));m.submitEffectCards(A,p.resolutionId(),p.step(),selection);}
            check(m.activePokemonView(B).damage()==(amount>=2?130:0),"Petal Blade Dance must do nothing unless two discarded");
            check(m.privateView(A).discard().size()==old+Math.min(2,amount),"partial mandatory discard count");
            check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"petal stalled");
        }
    }
    static void raid(){
        var a=attack("sv10_en_078",1);var m=game(a,EnergyType.LIGHTNING);pay(m,A,active(m,A),"energy",3);
        m.endTurn(A);var ex=hand(m,B,"rulebox");m.playBasicPokemonToBench(B,ex.instanceId());m.endTurn(B);
        var target=m.benchPokemonView(B).stream().filter(p->p.topCardId().equals("rulebox")).findFirst().orElseThrow().pokemonId();
        reject(()->m.attack(A,a.id(),active(m,B)));reject(()->m.attack(A,a.id(),m.benchPokemonView(B).getFirst().pokemonId()));
        m.attack(A,a.id(),target);
        check(m.benchPokemonView(B).stream().filter(p->p.pokemonId().equals(target)).findFirst().orElseThrow().damage()==210,"Bench ex damage");
        check(m.activePokemonView(A).attachedEnergyIds().isEmpty()&&m.currentPlayer().equals(B),"discard all before damage / continuation");
    }
    static void diver(){
        var ability=DATA.pokemon("sv10_en_055").orElseThrow().abilities().getFirst();TriggeredAbilityRules.validate(ability);
        for(boolean accept:List.of(false,true)){
            var m=GeneratedRuleBatch8Test.fixture(List.of(ability),EnergyType.WATER,EnergyType.WATER,DATA.trainer("swsh5_en_124").orElseThrow(),DamageTriggeredAbilityTest.attack(500,""));
            var target=active(m,B);var energy=hand(m,B,"energy");var wrong=hand(m,B,"wrong");var special=hand(m,B,"special");
            for(var c:List.of(energy,wrong,special))m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,c.instanceId(),B,target);
            m.attack(A,"hit");int guard=10;while(m.triggerOrderPrompt()!=null&&guard-->0){var p=m.triggerOrderPrompt();if(p.optionalDecision())m.decideTriggeredEffect(p.choosingPlayer(),p.id(),accept);else m.chooseTriggeredEffect(p.choosingPlayer(),p.id(),p.entries().getFirst().id());}
            check(guard>0&&m.phase()==MatchPhase.PRIZE_SELECTION,"Diver's Catch pending KO");
            check(m.privateView(B).hand().contains(energy)==accept&&m.privateView(B).discard().contains(energy)!=accept,"Water recovery optional decision");
            check(m.privateView(B).discard().containsAll(List.of(wrong,special)),"wrong/basic-vs-special energy filter");
        }
    }
    // Name, stage and Energy candidates are public definitions; hidden ordering is created by ServerRandom.
    static ServerMatch selectionGame(String trainerId,List<AbilityDefinition> abilities,int seed){
        var trainer=DATA.trainer(trainerId).orElseThrow();TrainerProgramRules.validate(trainer.effectProgram());
        var defs=new ArrayList<CardDefinition>();var pokemon=new HashMap<String,PokemonProfile>();
        for(String id:List.of("basic","evolved","plain","energy","trainer")){
            String name=id.equals("basic")?"Team Rocket's Koffing":id.equals("evolved")?"Team Rocket's Weezing":id;
            boolean p=!id.equals("energy")&&!id.equals("trainer");
            defs.add(new CardDefinition(id,name,p?CardCategory.POKEMON:id.equals("energy")?CardCategory.ENERGY:CardCategory.TRAINER,
                id.equals("trainer")?TrainerCategory.ITEM:TrainerCategory.NONE,"I",Set.of("international"),p&&!id.equals("evolved"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
            boolean evolutionAbility=!abilities.isEmpty()&&abilities.getFirst().eventPolicy().trigger()==EffectTrigger.ON_EVOLVE;
            if(p)pokemon.put(id,new PokemonProfile(120,0,id.equals("evolved")?"basic":null,null,0,null,0,1,List.of(DamageTriggeredAbilityTest.attack(120,"")),id.equals(evolutionAbility?"evolved":"basic")?abilities:List.of(),EnergyType.DARKNESS,name,id.equals("evolved")?"Team Rocket's Koffing":null));
        }
        var combat=new CombatCatalog(pokemon,Map.of("energy",EnergyType.GRASS),Map.of("trainer",trainer));
        for(int n=seed;n<seed+1000;n++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,i<24?"basic":i<30?"evolved":i<36?"plain":i<48?"energy":"trainer"));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),combat,new ServerRandom(n));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).limit(2).filter(c->c.definitionId().equals("basic")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.endTurn(A);m.endTurn(B);
            if(m.privateView(A).hand().stream().anyMatch(c->c.definitionId().equals("trainer")))return m;
        }throw new AssertionError("selection fixture");
    }
    static void coins(){
        for(String id:List.of("zsv10-5_en_081","sv10_en_175")){
            var outcomes=new HashSet<String>();for(int seed=0;seed<40;seed++){
                var m=selectionGame(id,List.of(),seed);var trainer=hand(m,A,"trainer");var rng=m.randomTranscript();
                check(m.canPlayTrainer(A,trainer.instanceId(),null)&&rng.equals(m.randomTranscript()),"trainer preflight consumed coins");
                m.playTrainer(A,trainer.instanceId(),null);var p=m.clientView(A).effectSelection();
                if(id.startsWith("zsv")){
                    outcomes.add(p==null?"no-attach":"attach");
                    if(p!=null){check(p.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"Energy Coin non-basic candidate");
                        var chosen=p.candidates().getFirst();m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(chosen.instanceId()));
                        var target=m.effectAttachmentPrompt(A);m.chooseEffectAttachmentTarget(A,target.resolutionId(),target.step(),target.targets().getFirst());
                        check(m.activePokemonView(A).attachedEnergyIds().contains(chosen.instanceId())||m.benchPokemonView(A).stream().anyMatch(v->v.attachedEnergyIds().contains(chosen.instanceId())),"Energy Coin did not attach");}
                }else{
                    check(p!=null&&!p.candidates().isEmpty(),"Great Ball coin branch lost selection");String category=p.candidates().getFirst().definitionId();outcomes.add(category);
                    check(Set.of("basic","evolved").contains(category)&&p.candidates().stream().allMatch(c->c.definitionId().equals(category)),"Great Ball mixed type/name candidates");
                    var chosen=p.candidates().getFirst();m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(chosen.instanceId()));
                    var reveal=m.effectCardReveal();check(reveal!=null&&reveal.confirmingPlayer().equals(B),"Great Ball must reveal only the chosen card");
                    m.acknowledgeEffectReveal(B,reveal.id());
                    check(m.privateView(A).hand().contains(chosen),"Great Ball did not put selected printing into hand");
                }
                check(!m.hasPendingEffectChoice()&&m.privateView(A).discard().contains(trainer)&&m.currentPlayer().equals(A),"trainer continuation/discard");
                reject(()->m.playTrainer(A,trainer.instanceId(),null));
            }check(outcomes.size()==2,"fixed seeds must cover both outcome classes: "+id+outcomes);
        }
    }
    static void smog(){
        var a=DATA.pokemon("sv10_en_125").orElseThrow().abilities().getFirst();TriggeredAbilityRules.validate(a);
        var m=selectionGame("sv10_en_175",List.of(a),0);m.attack(A,"hit");var p=m.effectCardPrompt(B);
        check(p!=null&&p.candidates().stream().allMatch(c->c.definitionId().equals("basic")),"Smog Signals must select Koffing before KO is removed");
        int before=m.benchPokemonView(B).size();m.submitEffectCards(B,p.resolutionId(),p.step(),p.candidates().stream().limit(2).map(CardInstance::instanceId).toList());
        check(m.benchPokemonView(B).size()==before+2&&m.phase()==MatchPhase.PRIZE_SELECTION,"lethal Smog Signals did not finish before KO/prizes");
        m.takePrizeAt(A,0);m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());
        check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Smog turn continuation");
    }
}
