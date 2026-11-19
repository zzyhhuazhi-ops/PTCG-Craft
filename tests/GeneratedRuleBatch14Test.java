import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** Generated programs with printed costs: dynamic counts, public recovery and optional whole-hand discard. */
public final class GeneratedRuleBatch14Test {
    static final UUID A=GeneratedRuleBatch13Test.A,B=GeneratedRuleBatch13Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return GeneratedRuleBatch13Test.active(m,p);}
    static UUID bench(ServerMatch m,UUID p){return GeneratedRuleBatch13Test.bench(m,p);}
    static AttackDefinition attack(String id,int slot){return GeneratedRuleBatch12Test.attack(id,slot);}
    static ServerMatch fixture(AttackDefinition a,EnergyType e){return GeneratedRuleBatch12Test.fixture(a,e);}
    static void pay(ServerMatch m,AttackDefinition a){GeneratedRuleBatch13Test.pay(m,a);}
    static void attach(ServerMatch m,UUID owner,UUID host,String id){GeneratedRuleBatch13Test.attach(m,owner,host,id);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static void done(ServerMatch m){GeneratedRuleBatch13Test.done(m);}
    public static void main(String[] args){dynamicCoins();fieldThreshold();sprint();crystal();benchHealing();ownCounters();bothStatuses();evolutionLock();System.out.println("GeneratedRuleBatch14Test passed: live Energy/counter flips, field thresholds, Sprint empty-hand/deck, exact typed recovery, Bench healing, own counters, both Active statuses and Evolution attack lock");}

    static void dynamicCoins(){
        for(String id:List.of("bw1_en_030","swsh1_en_007","me3_en_025")){
            var a=attack(id,id.equals("bw1_en_030")?0:1);var m=fixture(a,id.equals("swsh1_en_007")?EnergyType.GRASS:EnergyType.WATER);
            pay(m,a);attach(m,A,active(m,A),"special");attach(m,A,active(m,A),"wrong");attach(m,A,bench(m,A),"energy");attach(m,B,active(m,B),"energy");
            int from=m.randomTranscript().size();m.attack(A,a.id());var flips=AttackCoinGateTest.events(m,from,"attack-effect-coin:");
            int n=id.equals("me3_en_025")?5:id.equals("swsh1_en_007")?5:4,per=id.equals("me3_en_025")?90:id.equals("swsh1_en_007")?60:10;
            check(flips.size()==n,"Energy coins counted cards, wrong type or wrong owner: "+id+" expected "+n+" got "+flips.size()+" "+m.randomTranscript().subList(from,m.randomTranscript().size()));int heads=(int)flips.stream().filter(e->e.result()==0).count();
            check(m.activePokemonView(B).damage()==a.baseDamage()+heads*per,"per-head damage or printed base changed");done(m);
        }
        for(int counters:List.of(0,3,21)){
            var a=attack("xy1_en_100",1);var m=fixture(a,EnergyType.GRASS);pay(m,a);if(counters>0)m.placeDamageCountersByEffect(A,A,active(m,A),counters);
            int from=m.randomTranscript().size();m.attack(A,a.id());var flips=AttackCoinGateTest.events(m,from,"attack-effect-coin:");
            check(flips.size()==counters,"finite counter count truncated or zero generated a coin");check(m.activePokemonView(B).damage()==30*flips.stream().filter(e->e.result()==0).count(),"counter-based heads damage");done(m);
        }
    }
    static void fieldThreshold(){
        for(String id:List.of("me1_en_048","sv6-5_en_030"))for(boolean enough:List.of(false,true)){
            boolean lightning=id.startsWith("me1");var a=attack(id,0);var m=fixture(a,lightning?EnergyType.LIGHTNING:EnergyType.DARKNESS);
            attach(m,A,active(m,A),"special");if(lightning)attach(m,A,bench(m,A),"energy");if(enough)attach(m,A,bench(m,A),"energy");attach(m,A,active(m,A),"wrong");attach(m,B,active(m,B),"energy");
            m.attack(A,a.id());check(m.activePokemonView(B).damage()==a.baseDamage()+(enough?(lightning?90:50):0),"own typed threshold includes wrong type/owner or misses Bench units");done(m);
        }
    }
    static void sprint(){
        for(String mode:List.of("normal","empty-hand","one-deck","empty-deck","both-empty")){
            var ability=GeneratedRuleBatch8Test.ability("sm8_en_082");var m=GeneratedRuleBatch8Test.fixture(List.of(ability),EnergyType.GRASS,EnergyType.GRASS);UUID source=bench(m,A);
            if(mode.equals("one-deck")||mode.equals("empty-deck")||mode.equals("both-empty"))m.discardTopDeckCardsByEffect(A,A,m.privateView(A).deck().size()-(mode.equals("one-deck")?1:0));
            if(mode.equals("empty-hand")||mode.equals("both-empty"))GeneratedRuleBatch13Test.clearHand(m);
            if(mode.equals("both-empty")){reject(()->m.useAbility(A,source,ability.id()));check(!m.canUseAbility(A,source,ability.id()),"effectless Sprint allowed");continue;}
            var oldHand=m.privateView(A).hand();int draw=Math.min(4,m.privateView(A).deck().size());m.useAbility(A,source,ability.id());
            check(m.privateView(A).hand().size()==draw&&m.privateView(A).discard().containsAll(oldHand),"Sprint requires a hand card or drew wrong short-deck count: "+mode);
            reject(()->m.useAbility(A,source,ability.id()));check(m.currentPlayer().equals(A)&&!m.hasPendingEffectChoice(),"Sprint consumed turn or leaked a choice");
        }
    }
    static void crystal(){
        for(int available:List.of(0,1,3,4)){
            var trainer=GeneratedRuleBatch8Test.trainer("sm10_en_173");var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.FIRE,EnergyType.GRASS,trainer,GeneratedRuleBatch8Test.hit(10,false));
            var basics=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).limit(available).toList();
            if(basics.size()<available){m.drawCardsByEffect(A,A,m.privateView(A).deck().size());basics=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).limit(available).toList();}
            check(basics.size()==available,"fixture needs basic Fire");var special=hand(m,A,"special");var wrong=hand(m,A,"wrong");var discard=new ArrayList<UUID>(basics.stream().map(CardInstance::instanceId).toList());discard.add(special.instanceId());discard.add(wrong.instanceId());m.discardHandCardsByEffect(A,A,discard);
            var card=hand(m,A,"trainer");if(available==0){var before=m.clientView(A);reject(()->m.playTrainer(A,card.instanceId(),null));check(before.equals(m.clientView(A)),"failed recovery consumed card/state");continue;}
            int before=m.privateView(A).hand().size();m.playTrainer(A,card.instanceId(),null);var p=m.effectCardPrompt(A);check(p.minimum()==Math.min(3,available)&&p.maximum()==3&&p.candidates().size()==available,"public recovery amount/type filter");
            reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(special.instanceId())));
            var chosen=basics.stream().limit(3).map(CardInstance::instanceId).toList();reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),chosen));m.submitEffectCards(A,p.resolutionId(),p.step(),chosen);reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),chosen));
            check(m.privateView(A).hand().size()==before-1+chosen.size()&&m.privateView(A).discard().contains(card)&&m.privateView(A).discard().contains(special)&&m.privateView(A).discard().contains(wrong),"recovery conservation/filter");check(m.currentPlayer().equals(A)&&!m.hasPendingEffectChoice(),"Item did not finish");
        }
    }
    static void benchHealing(){
        var a=attack("xybsp_en_056",1);var m=fixture(a,EnergyType.GRASS);pay(m,a);UUID extra=hand(m,A,"basic").instanceId();m.playBasicPokemonToBench(A,extra);
        m.placeDamageCountersByEffect(A,A,active(m,A),15);m.placeDamageCountersByEffect(A,A,bench(m,A),13);m.placeDamageCountersByEffect(A,A,extra,5);m.placeDamageCountersByEffect(A,B,bench(m,B),12);
        var selected=m.activePokemonView(A).attachedEnergyIds().subList(0,2);m.attackWithEnergyDiscards(A,a.id(),List.of(),selected);
        check(m.activePokemonView(A).damage()==150&&m.benchPokemonView(A).stream().mapToInt(v->v.damage()).sum()==30&&m.benchPokemonView(B).getFirst().damage()==120,"Bench healing scope or cap");
        done(m);check(m.activePokemonView(A).attachedEnergyIds().size()==2&&m.privateView(A).discard().stream().map(CardInstance::instanceId).toList().containsAll(selected),"heal/discard sequence or selected Energy changed");
    }
    static void ownCounters(){
        for(boolean self:List.of(false,true)){
            var a=attack("sm11_en_139",1);var m=fixture(a,EnergyType.DARKNESS);pay(m,a);UUID host=self?active(m,A):bench(m,A);m.attack(A,a.id());
            var p=m.effectPokemonPrompt(A);reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),active(m,B)));GeneratedRuleBatch13Test.chooseHost(m,host);done(m);
            check(m.activePokemonView(B).damage()==120&&m.activePokemonView(A).damage()==(self?30:0)&&m.benchPokemonView(A).getFirst().damage()==(self?0:30),"selected own counters changed scope/sequence");
        }
    }
    static void bothStatuses(){
        for(String id:List.of("sm1_en_028","sv3_en_129")){
            var a=attack(id,0);var m=fixture(a,EnergyType.DARKNESS);pay(m,a);m.attack(A,a.id());done(m);var status=id.startsWith("sm")?SpecialCondition.CONFUSED:SpecialCondition.POISONED;
            for(UUID owner:List.of(A,B)){check(m.activePokemonView(owner).conditions().contains(status)&&m.benchPokemonView(owner).getFirst().conditions().isEmpty(),"both-Active status scope");check(m.activePokemonView(owner).damage()==(status==SpecialCondition.POISONED?10:0),"poison checkup timing");}
        }
        var a=attack("sm11_en_089",0);var m=fixture(a,EnergyType.GRASS);pay(m,a);int from=m.randomTranscript().size();m.attack(A,a.id());
        check(AttackCoinGateTest.events(m,from,"").size()==2,"both sleeping Actives must each check recovery");m.endTurn(B);if(m.activePokemonView(A).conditions().contains(SpecialCondition.ASLEEP))m.clearSpecialConditionsByEffect(A,A,active(m,A),Set.of(SpecialCondition.ASLEEP));m.attack(A,a.id());
        check(m.activePokemonView(B).damage()==0,"Rest Well bonus must not give a non-damaging attack damage");done(m);
    }
    static AttackDefinition draft(){
        try(var in=GeneratedRuleBatch14Test.class.getResourceAsStream("/rule-data-batch14-draft-attacks.json")){
            var json=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("cards").get(0).getAsJsonObject().getAsJsonArray("attacks").get(0).getAsJsonObject().deepCopy();
            json.addProperty("damageType","COLORLESS");var a=BundledCombatData.attack(json,EffectRegistry.standard());AttackProgramRules.validate(a);return a;
        }catch(IOException e){throw new UncheckedIOException(e);}
    }
    static void evolutionLock(){
        for(String mode:List.of("basic","evolved","switched","expired")){
            var a=draft();var m=fixture(a,EnergyType.WATER);pay(m,a);
            if(!mode.equals("basic")){m.endTurn(A);m.evolveByEffect(B,active(m,B),hand(m,B,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.endTurn(B);}
            m.attack(A,a.id());done(m);check(m.mayAttack(B)==mode.equals("basic"),"Evolution-only attack lock");
            if(mode.equals("evolved"))reject(()->m.attack(B,a.id()));
            if(mode.equals("switched")){UUID old=active(m,B);m.switchActiveByEffect(B,B,bench(m,B));m.switchActiveByEffect(B,B,old);check(m.mayAttack(B),"Bench did not clear Evolution lock");}
            if(mode.equals("expired")){m.endTurn(B);m.endTurn(A);check(m.mayAttack(B),"Evolution lock did not expire next turn");}
        }
    }
}

