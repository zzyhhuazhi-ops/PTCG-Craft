import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Real exclusive programs, with named synthetic hosts isolating public event and payment boundaries. */
public final class ExclusiveRulesTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void reject(Runnable r){BeforeDamageAttackTest.reject(r);}
    static AbilityDefinition ability(String id){return DATA.pokemon(id).orElseThrow().abilities().getFirst();}
    static AttackDefinition attack(String id){return DATA.pokemon(id).orElseThrow().attacks().getFirst();}
    static CardInstance hand(ServerMatch m,String id){return GeneratedRuleBatch8Test.hand(m,id);}
    static void acceptTriggers(ServerMatch m){
        for(int i=0;m.triggerOrderPrompt()!=null&&i<12;i++){
            var p=m.triggerOrderPrompt();
            if(p.optionalDecision())m.decideTriggeredEffect(p.choosingPlayer(),p.id(),true);
            else m.chooseTriggeredEffect(p.choosingPlayer(),p.id(),p.entries().getFirst().id());
        }
        check(m.triggerOrderPrompt()==null,"trigger loop");
    }
    static void acknowledge(ServerMatch m){
        if(m.effectCardReveal()!=null)m.acknowledgeEffectReveal(m.pendingChoicePlayer(),m.effectCardReveal().id());
    }
    static ServerMatch fixture(List<AbilityDefinition> abilities,AttackDefinition attack,EnergyType type,String name){
        var ids=List.of("basic","basic","basic","basic","energy","energy","wrong","special","evolved","evolved","tool","tool","tool","tool","tool");
        var defs=new ArrayList<CardDefinition>();
        for(var id:new LinkedHashSet<>(ids)){
            boolean pokemon=Set.of("basic","evolved").contains(id),energy=Set.of("energy","wrong","special").contains(id);
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:energy?CardCategory.ENERGY:CardCategory.TRAINER,
                id.equals("tool")?TrainerCategory.TOOL:TrainerCategory.NONE,"H",Set.of("international"),id.equals("basic"),id.equals("energy")||id.equals("wrong"),60,Map.of(),EffectSupport.SUPPORTED,null,
                Set.of(),pokemon?Set.of(id.equals("evolved")?EnergyType.PSYCHIC:type):Set.of()));
        }
        var profiles=new HashMap<String,PokemonProfile>();
        profiles.put("basic",new PokemonProfile(500,1,null,null,0,null,0,1,List.of(attack),abilities,type,name,null));
        profiles.put("evolved",new PokemonProfile(500,1,"basic",null,0,null,0,1,List.of(attack),List.of(),EnergyType.PSYCHIC,"Evolution",name));
        var catalog=new CombatCatalog(profiles,Map.of("energy",type,"wrong",type==EnergyType.FIRE?EnergyType.WATER:EnergyType.FIRE),Map.of("tool",DynamicPassiveModifierTest.plainTool()),Map.of("special",EnergyProfile.fixed(type,2)));
        for(int seed=0;seed<500;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(var owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids.get(i%ids.size())));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,18);m.drawCardsByEffect(A,B,18);
            // Avoid triggering tested end-turn abilities while advancing the setup.
            m.disableAbilityEffectsByEffect(A,A,m.activePokemonView(A).pokemonId());
            m.disableAbilityEffectsByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId());
            m.endTurn(A);m.endTurn(B);
            if(m.privateView(A).hand().stream().filter(c->c.definitionId().equals("evolved")).count()>=1
                &&m.privateView(A).deck().stream().anyMatch(c->c.definitionId().equals("evolved"))
                &&m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).count()>=3
                &&m.privateView(A).hand().stream().anyMatch(c->c.definitionId().equals("special")))return m;
        }
        throw new AssertionError("exclusive fixture");
    }
    public static void main(String[] args){
        umbreon();symbols();twinkle();pump();vitality();resonance();bloom();snorlax();
        System.out.println("ExclusiveRulesTest passed: damaged targets, exact-name auras, field-type search, Water payment, distributed Energy/end-turn, actual evolution events, end-turn draw and retreat block");
    }
    static void umbreon(){
        var a=attack("sc_cbb2c_0615_triple_star");
        for(boolean bench:List.of(false,true)){
            var m=ModernWindowDamageRepairTest.game(a,0,true);UUID active=m.activePokemonView(B).pokemonId(),back=m.benchPokemonView(B).getFirst().pokemonId();
            m.placeDamageCountersByEffect(A,B,bench?back:active,1);
            check(!m.matchesAttackDamageTargetFilter(B,bench?active:back,a),"undamaged candidate offered");
            reject(()->m.attackTargets(A,a.id(),List.of(bench?active:back)));
            m.attackTargets(A,a.id(),List.of(bench?back:active));
            check(EventTargetBindingTest.field(m).pokemonView(B,bench?back:active).damage()==(bench?70:130),"Blindside damage/weakness");
        }
    }
    static void symbols(){
        for(var row:Map.of("sc_cs5_5c_008_r",EnergyType.FIRE,"sc_cs6_5c_015_r",EnergyType.WATER,"sc_cs6ac_057_r",EnergyType.LIGHTNING).entrySet()){
            var a=ability(row.getKey());String name=DATA.pokemon(row.getKey()).orElseThrow().pokemonName();
            for(String mode:List.of("same","ex","wrong","bench","evolved")){
                var m=fixture(List.of(a),GeneratedRuleBatch8Test.hit(40,mode.equals("bench")),mode.equals("wrong")?EnergyType.PSYCHIC:row.getValue(),mode.equals("same")?name:name+" ex");
                if(mode.equals("evolved"))m.evolve(A,m.activePokemonView(A).pokemonId(),hand(m,"evolved").instanceId());
                m.attack(A,"hit");int dealt=mode.equals("bench")?m.benchPokemonView(B).getFirst().damage():m.activePokemonView(B).damage();
                check(dealt==(mode.equals("ex")?60:40),"Symbol "+row.getKey()+" "+mode+" "+dealt);
            }
        }
    }
    static void twinkle(){
        for(int types:List.of(1,2))for(boolean empty:List.of(false,true)){
            var a=attack("sc_cs5ac_092_k");var m=fixture(List.of(),a,EnergyType.GRASS,"Eevee");
            if(types==2)m.evolve(A,m.benchPokemonView(A).getFirst().pokemonId(),hand(m,"evolved").instanceId());
            m.attachEnergy(A,hand(m,"energy").instanceId(),m.activePokemonView(A).pokemonId());int count=m.privateView(A).hand().size();
            m.attack(A,a.id());var p=m.effectCardPrompt(A);
            check(p.minimum()==0&&p.maximum()==types,"Twinkle must count distinct field types");
            reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),p.candidates().subList(0,types+1).stream().map(CardInstance::instanceId).toList()));
            var selected=empty?List.<UUID>of():p.candidates().subList(0,types).stream().map(CardInstance::instanceId).toList();
            reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),selected));m.submitEffectCards(A,p.resolutionId(),p.step(),selected);
            reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),selected));
            check(m.privateView(A).hand().size()==count+selected.size()&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Twinkle continuation");
        }
    }
    static void pump(){
        var a=ability("sc_cs5_5c_015_k");var m=fixture(List.of(a),GeneratedRuleBatch8Test.hit(40,false),EnergyType.WATER,"Radiant Blastoise");
        UUID host=m.activePokemonView(A).pokemonId(),target=m.benchPokemonView(B).getFirst().pokemonId();int before=m.privateView(A).discard().size();
        m.useAbility(A,host,a.id());var p=m.effectCardPrompt(A);
        check(p.minimum()==1&&p.maximum()==1&&p.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"Pump Water cost filter");
        reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(hand(m,"special").instanceId())));
        m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));var t=m.effectPokemonPrompt(A);
        reject(()->m.chooseEffectPokemon(A,t.resolutionId(),t.step(),m.activePokemonView(B).pokemonId()));
        m.chooseEffectPokemon(A,t.resolutionId(),t.step(),target);
        reject(()->m.chooseEffectPokemon(A,t.resolutionId(),t.step(),target));
        check(m.benchPokemonView(B).getFirst().damage()==20&&m.privateView(A).discard().size()==before+1&&!m.hasPendingEffectChoice(),"Pump conservation");
        reject(()->m.useAbility(A,host,a.id()));
    }
    static void vitality(){
        for(int n:List.of(0,1,3)){
            var a=ability("sc_cs5_5c_014_r");var m=fixture(List.of(a),GeneratedRuleBatch8Test.hit(40,false),EnergyType.WATER,"Blastoise");
            m.useAbility(A,m.activePokemonView(A).pokemonId(),a.id());var p=m.effectCardPrompt(A);int count=Math.min(n,p.candidates().size());
            var selected=p.candidates().stream().sorted(Comparator.comparing(c->!c.definitionId().equals("special"))).limit(count).map(CardInstance::instanceId).toList();
            m.submitEffectCards(A,p.resolutionId(),p.step(),selected);
            if(count>0){
                var t=m.effectAttachmentPrompt(A);check(t!=null,"Vitality missing allocation");
                for(int i=0;i<count;i++)m.assignEffectEnergyDistribution(A,t.resolutionId(),t.step(),selected.get(i),i%2==0?m.activePokemonView(A).pokemonId():m.benchPokemonView(A).getFirst().pokemonId());
                m.confirmEffectEnergyDistribution(A,t.resolutionId(),t.step());
                reject(()->m.confirmEffectEnergyDistribution(A,t.resolutionId(),t.step()));
            }
            acknowledge(m);
            int attached=m.activePokemonView(A).attachedEnergyIds().size()+m.benchPokemonView(A).getFirst().attachedEnergyIds().size();
            check(attached==count&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Vitality must finish allocations before ending turn");
        }
    }
    static void resonance(){
        var a=ability("sc_cbb2c_0115_triple_star");
        for(String mode:List.of("hand","wrong-name","deck","self")){
            var deckEvolution=ResumableAbilityTest.ability("deck-evolve","{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,\"card_category\":\"POKEMON\",\"destination\":\"EVOLVE_SELF\"}");
            var m=fixture(List.of(a,deckEvolution),GeneratedRuleBatch8Test.hit(40,false),EnergyType.COLORLESS,mode.equals("wrong-name")?"Pikachu":"Eevee");
            UUID host=m.activePokemonView(A).pokemonId(),other=m.benchPokemonView(A).getFirst().pokemonId();
            if(mode.equals("self"))m.disableAbilityEffectsByEffect(A,A,other);
            if(mode.equals("deck")){
                m.useAbility(A,other,"deck-evolve");var q=m.effectCardPrompt(A);
                m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(q.candidates().getFirst().instanceId()));acknowledge(m);
            }else m.evolve(A,mode.equals("self")?host:other,hand(m,"evolved").instanceId());
            if(!mode.equals("hand")){check(m.triggerOrderPrompt()==null&&!m.hasPendingEffectChoice(),"Resonance false event: "+mode);continue;}
            acceptTriggers(m);var p=m.effectCardPrompt(A);check(p!=null&&p.candidates().stream().allMatch(c->c.definitionId().equals("evolved")),"Resonance evolution filter");
            m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));acknowledge(m);
            check(m.activePokemonView(A).topCardId().equals("evolved")&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"Resonance did not evolve original Eevee");
        }
    }
    static void bloom(){
        var a=ability("sc_cs5bc_004_k");var m=fixture(List.of(a),GeneratedRuleBatch8Test.hit(40,false),EnergyType.GRASS,"Radiant Venusaur");
        m.disableAbilityEffectsByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId());
        var cards=m.privateView(A).hand();m.discardHandCardsByEffect(A,A,cards.subList(2,cards.size()).stream().map(CardInstance::instanceId).toList());
        m.endTurn(A);acceptTriggers(m);check(m.privateView(A).hand().size()==4&&m.currentPlayer().equals(B),"Sunny Bloom draw-to-four/end-turn");
    }
    static void snorlax(){
        var a=ability("sc_cs5ac_093_r");var m=fixture(List.of(a),attack("sc_cs5ac_093_r"),EnergyType.COLORLESS,"Snorlax");
        var p=m.activePokemonView(A);ModernRuleRepair2Test.pay(m,A,p.pokemonId(),"energy",3);ModernRuleRepair2Test.pay(m,A,p.pokemonId(),"special",1);
        m.attack(A,"collapse");check(m.activePokemonView(A).damage()==0&&m.activePokemonView(B).damage()==150,"Collapse target or damage");
        m.attachEnergy(B,DynamicPassiveModifierTest.hand(m,B,"energy").instanceId(),m.activePokemonView(B).pokemonId());
        var energy=m.activePokemonView(B).attachedEnergyIds().getFirst();
        reject(()->m.retreat(B,m.benchPokemonView(B).getFirst().pokemonId(),List.of(energy)));
        m.disableAbilityEffectsByEffect(B,A,m.activePokemonView(A).pokemonId());
        m.retreat(B,m.benchPokemonView(B).getFirst().pokemonId(),List.of(energy));
    }
}
