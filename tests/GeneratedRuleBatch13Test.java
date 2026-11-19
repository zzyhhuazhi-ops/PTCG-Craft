import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Real generated effects on controlled hosts: public counts, private costs and captured reaction targets. */
public final class GeneratedRuleBatch13Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String message){GeneratedRuleBatch8Test.check(ok,message);}
    static void reject(Runnable action){GeneratedRuleBatch8Test.reject(action);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static AttackDefinition attack(String id,int slot){return GeneratedRuleBatch12Test.attack(id,slot);}
    static ServerMatch fixture(AttackDefinition a,EnergyType e){return GeneratedRuleBatch12Test.fixture(a,e);}
    static CardInstance hand(ServerMatch m,UUID owner,String id){return DynamicPassiveModifierTest.hand(m,owner,id);}
    static void attach(ServerMatch m,UUID owner,UUID host,String id){m.attachEnergyByEffect(A,owner,EffectSourceZone.HAND,hand(m,owner,id).instanceId(),owner,host);}
    static void pay(ServerMatch m,AttackDefinition a){
        for(int i=0;i<a.energyCost().values().stream().mapToInt(Integer::intValue).sum();i++){
            var c=m.privateView(A).hand().stream().filter(v->v.definitionId().equals("energy")).findFirst();var zone=EffectSourceZone.HAND;
            if(c.isEmpty()){c=m.privateView(A).deck().stream().filter(v->v.definitionId().equals("energy")).findFirst();zone=EffectSourceZone.DECK;}
            m.attachEnergyByEffect(A,A,zone,c.orElseThrow().instanceId(),A,active(m,A));
        }
    }
    static void done(ServerMatch m){check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"attack did not finish exactly once");}
    static void chooseHost(ServerMatch m,UUID host){var p=m.effectPokemonPrompt(A);reject(()->m.chooseEffectPokemon(B,p.resolutionId(),p.step(),host));m.chooseEffectPokemon(A,p.resolutionId(),p.step(),host);}
    static void clearHand(ServerMatch m){var ids=m.privateView(A).hand().stream().map(CardInstance::instanceId).toList();if(!ids.isEmpty())m.discardHandCardsByEffect(A,A,ids);}
    public static void main(String[] args){exactDiscard();handAndSwitchedSelf();discardDraw();trade();typedCounts();searchAndStatus();hazeAndGate();reactions();System.out.println("GeneratedRuleBatch13Test passed: exact Energy, private attachment, source after switch, discard/draw cost, typed field counts/search/status, gates, GX discard and damage reactions");}

    static void exactDiscard(){
        for(String id:List.of("sm1_en_015","sm2_en_106"))for(int available:List.of(0,1,2,3)){
            var a=attack(id,0);var m=fixture(a,EnergyType.GRASS);pay(m,a);UUID self=active(m,A),back=bench(m,A);
            var basic=m.privateView(A).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicEnergy()).limit(available).toList();check(basic.size()==available,"fixture lacks basic Energy");
            var special=hand(m,A,"special");var discard=new ArrayList<UUID>(basic.stream().map(CardInstance::instanceId).toList());discard.add(special.instanceId());m.discardHandCardsByEffect(A,A,discard);
            int before=m.activePokemonView(A).attachedEnergyIds().size();m.attack(A,a.id());
            if(available>0){
                if(m.hasPendingPokemonSelection())chooseHost(m,back);
                var p=m.effectCardPrompt(A);check(p.minimum()==Math.min(available,2)&&p.maximum()==2&&p.candidates().size()==available,"exact public attachment count/filter");
                reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(special.instanceId())));
                var chosen=basic.stream().limit(2).map(CardInstance::instanceId).toList();reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),chosen));m.submitEffectCards(A,p.resolutionId(),p.step(),chosen);
                if(m.hasPendingEnergyDistribution()){
                    var t=m.effectAttachmentPrompt(A);reject(()->m.confirmEffectEnergyDistribution(A,t.resolutionId(),t.step()));int index=0;
                    for(var energy:t.cards()) {UUID host=index++>0?self:back;reject(()->m.assignEffectEnergyDistribution(B,t.resolutionId(),t.step(),energy.instanceId(),host));m.assignEffectEnergyDistribution(A,t.resolutionId(),t.step(),energy.instanceId(),host);}
                    m.confirmEffectEnergyDistribution(A,t.resolutionId(),t.step());
                }else if(m.hasPendingAttachmentTarget()){
                    var t=m.effectAttachmentPrompt(A);reject(()->m.chooseEffectAttachmentTarget(B,t.resolutionId(),t.step(),back));m.chooseEffectAttachmentTarget(A,t.resolutionId(),t.step(),back);
                }
                reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),chosen));
            }
            done(m);int attached=m.activePokemonView(A).attachedEnergyIds().size()-before+m.benchPokemonView(A).getFirst().attachedEnergyIds().size();check(attached==Math.min(available,2),"did not attach as much as possible");
            check(m.privateView(A).discard().contains(special)&&m.activePokemonView(B).damage()==a.baseDamage(),"discard filter or damage stage changed");
            if(id.equals("sm2_en_106"))check(m.activePokemonView(A).attachedEnergyIds().size()==before,"Bench-only attachment reached Active");
        }
    }
    static void handAndSwitchedSelf(){
        var a=attack("sm9_en_120",0);var m=fixture(a,EnergyType.GRASS);pay(m,a);UUID host=bench(m,A);var special=hand(m,A,"special");m.attack(A,a.id());
        if(m.hasPendingPokemonSelection())chooseHost(m,host);var p=m.effectCardPrompt(A);check(p.minimum()==1&&p.candidates().contains(special),"untyped hand attach excluded Special Energy");
        m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(special.instanceId()));if(m.hasPendingAttachmentTarget()){var t=m.effectAttachmentPrompt(A);m.chooseEffectAttachmentTarget(A,t.resolutionId(),t.step(),host);}done(m);check(m.benchPokemonView(A).getFirst().attachedEnergyIds().contains(special.instanceId()),"Cheer Up did not attach to chosen host");
        a=attack("sv8_en_068",0);m=fixture(a,EnergyType.LIGHTNING);pay(m,a);UUID old=active(m,A),replacement=bench(m,A);m.attack(A,a.id());chooseHost(m,replacement);p=m.effectCardPrompt(A);
        check(p.minimum()==0&&p.maximum()==2&&p.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"Return Charge private filter or optional count");var selected=p.candidates().stream().limit(2).map(CardInstance::instanceId).toList();check(selected.size()==2,"fixture lacks hand Energy");m.submitEffectCards(A,p.resolutionId(),p.step(),selected);done(m);
        check(active(m,A).equals(replacement)&&m.activePokemonView(A).attachedEnergyIds().isEmpty()&&m.benchPokemonView(A).stream().filter(v->v.pokemonId().equals(old)).findFirst().orElseThrow().attachedEnergyIds().size()==3,"post-switch this Pokemon attached to replacement");
    }
    static void discardDraw(){
        for(boolean empty:List.of(false,true)){
            var a=attack("sm12_en_086",0);var m=fixture(a,EnergyType.GRASS);pay(m,a);if(empty)clearHand(m);int size=m.privateView(A).hand().size();m.attack(A,a.id());
            if(!empty){var p=m.effectCardPrompt(A);reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));}
            done(m);check(m.privateView(A).hand().size()==(empty?0:size+1),"Cycle Draw drew without discarding");
        }
    }
    static void trade(){
        for(String id:List.of("sm3-5_en_053","sm7_en_035","swsh1_en_147","sv2_en_105"))for(String mode:List.of("normal","empty-hand","empty-deck")){
            var ability=GeneratedRuleBatch8Test.ability(id);var m=GeneratedRuleBatch8Test.fixture(List.of(ability),EnergyType.GRASS,EnergyType.GRASS);UUID source=bench(m,A);
            if(mode.equals("empty-hand")){clearHand(m);check(!m.canUseAbility(A,source,ability.id()),"discard cost not checked before activation");reject(()->m.useAbility(A,source,ability.id()));check(m.abilityOptions(A).stream().noneMatch(ServerMatch.AbilityOption::usedThisTurn),"failed cost spent usage");continue;}
            if(mode.equals("empty-deck"))m.drawCardsByEffect(A,A,m.privateView(A).deck().size());int before=m.privateView(A).hand().size(),deck=m.privateView(A).deck().size();m.useAbility(A,source,ability.id());var p=m.effectCardPrompt(A);
            reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId())));m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));
            int draw=id.equals("sm7_en_035")||id.equals("sv2_en_105")?3:2;check(m.privateView(A).hand().size()==before-1+Math.min(draw,deck),"Trade draw count/empty deck");reject(()->m.useAbility(A,source,ability.id()));check(m.currentPlayer().equals(A),"ability ended the turn");
        }
    }
    static void typedCounts(){
        for(String id:List.of("sm1_en_042","sm3-5_en_029")){
            var a=attack(id,0);var m=fixture(a,id.equals("sm1_en_042")?EnergyType.WATER:EnergyType.LIGHTNING);attach(m,A,active(m,A),"special");attach(m,A,active(m,A),"wrong");attach(m,A,bench(m,A),"energy");attach(m,B,active(m,B),"energy");m.attack(A,a.id());
            check(m.activePokemonView(B).damage()==a.baseDamage()+60,"field typed supply counted cards, wrong types or opponent");done(m);
        }
    }
    static void searchAndStatus(){
        for(boolean select:List.of(false,true)){
            var a=attack("sm8_en_020",0);var m=fixture(a,EnergyType.GRASS);pay(m,a);int before=m.privateView(A).hand().size();m.attack(A,a.id());var p=m.effectCardPrompt(A);check(p.minimum()==0&&p.maximum()==1&&p.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).pokemonTypes().contains(EnergyType.GRASS)),"typed hidden search filter");
            var ids=select?List.of(p.candidates().getFirst().instanceId()):List.<UUID>of();reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),ids));m.submitEffectCards(A,p.resolutionId(),p.step(),ids);if(m.effectCardReveal()!=null)m.acknowledgeEffectReveal(B,m.effectCardReveal().id());done(m);check(m.privateView(A).hand().size()==before+(select?1:0),"typed search optional count");
        }
        for(boolean metal:List.of(false,true)){
            var a=attack("sm5_en_044",0);var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.LIGHTNING,metal?EnergyType.METAL:EnergyType.GRASS,GeneratedRuleBatch8Test.trainer("swsh5_en_124"),a);pay(m,a);m.attack(A,a.id());done(m);check(m.activePokemonView(B).conditions().contains(SpecialCondition.PARALYZED)==metal,"type-gated paralysis");
        }
    }
    static void hazeAndGate(){
        var a=attack("sm3_en_025",2);var m=fixture(a,EnergyType.FIRE);pay(m,a);attach(m,B,active(m,B),"energy");attach(m,B,active(m,B),"special");attach(m,B,bench(m,B),"energy");m.attack(A,a.id());done(m);check(m.activePokemonView(B).attachedEnergyIds().isEmpty()&&m.benchPokemonView(B).getFirst().attachedEnergyIds().size()==1,"Queen's Haze discarded wrong scope");m.endTurn(B);var used=m;reject(()->used.attack(A,a.id()));
        var gate=attack("sm8_en_039",0);m=fixture(gate,EnergyType.FIRE);pay(m,gate);attach(m,B,active(m,B),"energy");m.attack(A,gate.id());int offset=m.randomTranscript().size();m.attack(B,gate.id());var flips=AttackCoinGateTest.events(m,offset,"attack-coin-gate:");check(flips.size()==1&&m.activePokemonView(A).damage()==(flips.getFirst().result()==0?10:0),"generated gate did not govern opponent attack");
    }
    static void reactions(){
        for(String id:List.of("me2-5_en_068","me2-5_en_135","sv1_en_005","xy2_en_021"))for(String mode:List.of("normal","lethal","zero","counters","bench","disabled","switch")){
            var ability=GeneratedRuleBatch8Test.ability(id);var json=JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"Hit\",\"energyCost\":{},\"baseDamage\":"+(mode.equals("lethal")?500:mode.equals("zero")||mode.equals("counters")?0:10)+",\"damageType\":\"COLORLESS\",\"effectSupport\":\"SUPPORTED\"}").getAsJsonObject();
            if(mode.equals("bench"))json.addProperty("damageTarget","ALL_OPPONENT_BENCH");
            if(mode.equals("counters")||mode.equals("switch"))json.add("triggers",JsonParser.parseString("[{\"trigger\":\"AFTER_ATTACK\",\"effects\":["+(mode.equals("switch")?"{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"}":"{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}")+"]}]"));
            var m=GeneratedRuleBatch8Test.fixture(List.of(ability),EnergyType.GRASS,EnergyType.GRASS,GeneratedRuleBatch8Test.trainer("swsh5_en_124"),BundledCombatData.attack(json,EffectRegistry.standard()));UUID attacker=active(m,A),back=bench(m,A);if(mode.equals("disabled"))m.disableAbilityEffectsByEffect(A,B,active(m,B));m.attack(A,"hit");if(mode.equals("switch"))chooseHost(m,back);DamageTriggeredAbilityTest.finishTriggers(m);
            int expected=Set.of("normal","lethal","switch").contains(mode)?id.equals("me2-5_en_135")?50:id.equals("xy2_en_021")?20:30:0;
            int actual=mode.equals("switch")?m.benchPokemonView(A).stream().filter(v->v.pokemonId().equals(attacker)).findFirst().orElseThrow().damage():m.activePokemonView(A).damage();check(actual==expected,"damage reaction boundary: "+id+" "+mode+" got "+actual);if(mode.equals("switch"))check(m.activePokemonView(A).damage()==0,"reaction hit replacement instead of attacker");if(mode.equals("lethal"))check(m.phase()==MatchPhase.PRIZE_SELECTION,"lethal defender reaction skipped KO checkpoint");
        }
        for(String id:List.of("me2-5_en_027","me4_en_052","xybsp_en_048","swsh3-5_en_020")){
            var ability=GeneratedRuleBatch8Test.ability(id);var m=GeneratedRuleBatch8Test.fixture(List.of(ability),EnergyType.GRASS,EnergyType.GRASS);m.attack(A,"hit");DamageTriggeredAbilityTest.finishTriggers(m);
            // Burn may recover during checkup; its guaranteed 20 counters still prove the reaction ran.
            if(id.equals("me2-5_en_027"))check(m.activePokemonView(A).damage()==20,"burn reaction absent");else check(m.activePokemonView(A).conditions().contains(id.equals("me4_en_052")?SpecialCondition.POISONED:SpecialCondition.CONFUSED),"status reaction absent");
        }
    }
}
