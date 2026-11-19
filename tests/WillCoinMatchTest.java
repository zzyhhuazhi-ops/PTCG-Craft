import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual server transactions; printed Will remains gated until every native entry is audited. */
public final class WillCoinMatchTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final String WILL="{\"type\":\"turn_choose_next_coin\",\"target\":\"SELF\"}";
    static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
    static void reject(Runnable action){GeneratedRuleBatch8Test.reject(action);}
    static TrainerProfile trainer(String effects){return BundledCombatData.trainer(JsonParser.parseString(
        "{\"trainerCategory\":\"SUPPORTER\",\"triggers\":[{\"trigger\":\"ON_CARD_PLAYED\",\"effects\":["+effects+"]}]}").getAsJsonObject(),EffectRegistry.standard());}
    static ServerMatch game(AttackDefinition attack){var c=new GeneratedRuleBatch28Test.Config();c.trainer=trainer(WILL);c.attack=attack;c.defenderAttack=attack;return GeneratedRuleBatch28Test.fixture(c);}
    static void play(ServerMatch m,UUID p){m.playTrainer(p,DynamicPassiveModifierTest.hand(m,p,"trainer").instanceId(),null);}
    static void choose(ServerMatch m,UUID p,boolean heads){var q=m.effectChoicePrompt(p);check(q.optionsZh().getFirst().startsWith("一树："),"Not an outcome choice");m.chooseEffectOption(p,q.resolutionId(),q.step(),heads?0:1);}
    public static void main(String[] args){nativeDamage();trainerContinuation();attackPhases();attemptGates();ownDamageGuards();perTarget();expiry();confusion();schema();
        System.out.println("WillCoinMatchTest passed: actual Trainer and attack continuations, both seats/sides, first-coin consumption, authority, zero mutation before choice, native damage/status/protection, phase prefixes, turn expiry and confusion exclusion. Other native entry audits remain separate.");}
    static void nativeDamage(){for(UUID p:List.of(A,B))for(boolean heads:List.of(false,true)){
        var m=game(AttackCoinMatchTest.nativeAttack("\"coinDamage\":{\"flips\":2,\"perHeads\":30}"));if(p.equals(B))m.endTurn(A);
        play(m,p);int n=m.randomTranscript().size();UUID opponent=p.equals(A)?B:A;m.attack(p,"hit");
        check(m.hasPendingEffectChoice()&&m.randomTranscript().size()==n&&m.activePokemonView(opponent).damage()==0,"Coin/damage committed before side selected");
        var q=m.effectChoicePrompt(p);var view=m.clientView(p);
        reject(()->m.chooseEffectOption(opponent,q.resolutionId(),q.step(),0));reject(()->m.chooseEffectOption(p,q.resolutionId(),q.step()+1,0));reject(()->m.chooseEffectOption(p,q.resolutionId(),q.step(),2));reject(()->m.endTurn(p));
        check(!m.canChooseEffectOption(p,-1)&&!m.canChooseEffectOption(p,2)&&m.canChooseEffectOption(p,1),"Outcome choice validity incorrect");
        check(view.equals(m.clientView(p))&&m.randomTranscript().size()==n,"Invalid command mutated match");choose(m,p,heads);
        var events=m.randomTranscript().subList(n,m.randomTranscript().size());check(events.size()==2&&events.getFirst().result()==(heads?0:1),"Chosen side not applied to first coin");
        int total=(int)events.stream().filter(e->e.result()==0).count();check(m.activePokemonView(opponent).damage()==20+total*30&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(opponent),"Native damage/continuation wrong");
        reject(()->m.chooseEffectOption(p,q.resolutionId(),q.step(),0));
    }}
    static void trainerContinuation(){var c=new GeneratedRuleBatch28Test.Config();c.trainer=trainer(WILL+",{\"type\":\"draw\",\"amount\":1},{\"type\":\"coin_flip\",\"flips\":2,\"effects\":[{\"type\":\"draw\",\"amount\":1}]},{\"type\":\"draw\",\"amount\":1}");
        var m=GeneratedRuleBatch28Test.fixture(c);int n=m.randomTranscript().size(),hand=m.privateView(A).hand().size();play(m,A);
        check(m.privateView(A).hand().size()==hand&&m.randomTranscript().size()==n,"Trainer prefix missing or coin ran early");choose(m,A,false);
        check(m.privateView(A).hand().size()==hand+2&&m.randomTranscript().size()==n+2&&!m.hasPendingEffectChoice(),"Trainer prefix repeated or suffix lost");
    }
    static void attackPhases(){for(var phase:List.of(EffectTrigger.BEFORE_ATTACK,EffectTrigger.ON_ATTACK,EffectTrigger.AFTER_ATTACK)){
        String coin="{\"type\":\"coin_flip\",\"flips\":1,\"bind\":\"chosen\",\"effects\":[]}";
        var attack=BeforeDamageAttackTest.attack(phase==EffectTrigger.BEFORE_ATTACK?coin:"",phase==EffectTrigger.ON_ATTACK?coin:"",phase==EffectTrigger.AFTER_ATTACK?coin:"");
        var m=game(attack);play(m,A);int n=m.randomTranscript().size();m.attack(A,"hit");check(m.hasPendingEffectChoice()&&m.randomTranscript().size()==n,"Program phase bypassed Will: "+phase);choose(m,A,true);
        check(m.randomTranscript().size()==n+1&&m.randomTranscript().get(n).result()==0&&m.currentPlayer().equals(B),"Program phase did not resume: "+phase);
    }
        for(String nativeFields:List.of("\"conditions\":[{\"target\":\"DEFENDER\",\"condition\":\"CONFUSED\",\"coinCondition\":{\"flips\":1,\"requiredHeads\":1}}]","\"coinProtection\":{\"flips\":1,\"requiredHeads\":1,\"scope\":\"DAMAGE_AND_EFFECTS\"}")){
            var m=game(AttackCoinMatchTest.nativeAttack(nativeFields));play(m,A);m.attack(A,"hit");choose(m,A,true);check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Native suffix did not complete");
        }
    }
    static void expiry(){var m=game(AttackCoinMatchTest.nativeAttack("\"coinDamage\":{\"flips\":1,\"perHeads\":30}"));play(m,A);m.endTurn(A);m.endTurn(B);m.attack(A,"hit");check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Unused Will carried to later turn");}
    static void attemptGates(){for(boolean heads:List.of(false,true)){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=trainer(WILL);c.attack=AttackCoinMatchTest.nativeAttack("\"coinDamage\":{\"flips\":1,\"perHeads\":30}");
        c.defenderAttack=BeforeDamageAttackTest.attack("","","{\"type\":\"attack_coin_gate\",\"target\":\"OPPONENT_ACTIVE\"}");
        var m=GeneratedRuleBatch28Test.fixture(c);m.endTurn(A);m.attack(B,c.defenderAttack.id());play(m,A);int n=m.randomTranscript().size();m.attack(A,"hit");
        check(m.randomTranscript().size()==n&&m.hasPendingEffectChoice(),"Attempt gate ran before Will choice");choose(m,A,heads);
        check(m.randomTranscript().get(n).result()==(heads?0:1)&&m.randomTranscript().size()==n+(heads?2:1)&&m.currentPlayer().equals(B),"Attempt gate did not consume Will once before attack body");
    }
        boolean tested=false;
        for(int skip=0;skip<24&&!tested;skip++){
            var c=new GeneratedRuleBatch28Test.Config();c.trainer=trainer(WILL+",{\"type\":\"turn_supporter_limit\",\"target\":\"SELF\",\"amount\":3}");
            c.defenderAttack=DeckDiscardTriggeredAbilityTest.attack(0,"{\"type\":\"trainer_attempt_coin_gate\",\"target\":\"OPPONENT_ACTIVE\",\"scope\":\"PLAYER\",\"source\":\"HAND\",\"on_tails\":\"DISCARD_INSTEAD_OF_USE\",\"duration\":\"OPPONENT_NEXT_TURN\"}");
            var m=GeneratedRuleBatch28Test.fixture(c);m.endTurn(A);m.attack(B,c.defenderAttack.id());for(int i=0;i<skip;i++)m.flipCoinsByEffect(A,1);int first=m.randomTranscript().size();play(m,A);
            if(m.randomTranscript().get(first).result()!=0)continue;tested=true;var card=DynamicPassiveModifierTest.hand(m,A,"trainer");int n=m.randomTranscript().size();play(m,A);
            check(m.privateView(A).hand().contains(card)&&m.randomTranscript().size()==n,"Trainer gate spent card or RNG before choice");choose(m,A,false);
            check(m.privateView(A).discard().contains(card)&&m.randomTranscript().size()==n+1&&m.mayPlaySupporter(A),"Failed Trainer attempt used Supporter allowance or lost source");
        }check(tested,"Trainer gate fixture never got first Will into play");
    }
    static void confusion(){boolean passed=false,failed=false;for(int skip=0;skip<24&&!(passed&&failed);skip++){
        var m=game(AttackCoinMatchTest.nativeAttack("\"coinDamage\":{\"flips\":1,\"perHeads\":30}"));for(int i=0;i<skip;i++)m.flipCoinsByEffect(A,1);play(m,A);m.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);int n=m.randomTranscript().size();m.attack(A,"hit");
        check(m.randomTranscript().size()==n+1,"Confusion incorrectly became a selectable coin");if(m.hasPendingEffectChoice()){passed=true;choose(m,A,false);check(m.randomTranscript().size()==n+2&&m.randomTranscript().get(n+1).result()==1,"Confusion used the Will reservation");}else failed=true;
    }check(passed&&failed,"Confusion test missed one branch");}
    static void ownDamageGuards(){
        for(String mode:List.of("native","self_damage","recoil"))for(boolean heads:List.of(false,true)){
            var c=new GeneratedRuleBatch28Test.Config();c.trainer=trainer(WILL);c.ownAbility=CoinPreventDamageAbilityTest.luck();
            c.attack=AttackSelfDamageCalculationTest.attack(mode,30,EnergyType.COLORLESS,"");var m=GeneratedRuleBatch28Test.fixture(c);play(m,A);int n=m.randomTranscript().size();m.attack(A,"hit");
            check(m.randomTranscript().size()==n&&m.activePokemonView(A).damage()==0&&m.activePokemonView(B).damage()==40,"Self guard pause lost committed opposing damage: "+mode);choose(m,A,heads);
            check(m.activePokemonView(A).damage()==(heads?0:30)&&m.activePokemonView(B).damage()==40&&m.randomTranscript().size()==n+1&&m.currentPlayer().equals(B),"Self guard repeated damage or lost choice: "+mode);
        }
        for(boolean group:List.of(false,true)){
            var c=new GeneratedRuleBatch28Test.Config();c.trainer=trainer(WILL);c.ownAbility=CoinPreventDamageAbilityTest.luck();
            c.attack=group?BeforeDamageAttackTest.attack("","","{\"type\":\"bench_damage\",\"target\":\"OWN_BENCH\",\"amount\":30},{\"type\":\"draw\",\"amount\":1}")
                :AttackCoinMatchTest.nativeAttack("\"damageTarget\":\"ALL_POKEMON\",\"targetCount\":0");
            var m=GeneratedRuleBatch28Test.fixture(c);play(m,A);int n=m.randomTranscript().size(),hand=m.privateView(A).hand().size();m.attack(A,"hit");choose(m,A,true);
            check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Multi-target Ability coin stalled");
            check(m.activePokemonView(B).damage()==(group?40:20)&&m.benchPokemonView(B).getFirst().damage()==(group?0:20),"Multi-target resume repeated earlier damage");
            check(m.randomTranscript().size()==n+(group?1:2)&&m.privateView(A).hand().size()==hand+(group?1:0),"Multi-target resume repeated coins or suffix");
        }
    }
    static void perTarget(){
        var attack=BundledCombatData.attack(JsonParser.parseString("""
            {"id":"hit","nameZh":"Roust fixture","energyCost":{},"baseDamage":0,"damageType":"COLORLESS","damageTarget":"ALL_OPPONENT_POKEMON","targetCount":0,"effectSupport":"SUPPORTED",
             "triggers":[{"trigger":"ON_ATTACK","effects":[{"type":"per_target_coin_damage","target":"ALL_OPPONENT_POKEMON","amount":150}]}]}
            """).getAsJsonObject(),EffectRegistry.standard());
        var m=game(attack);play(m,A);int n=m.randomTranscript().size();var session=MatchSession.multiplayer(m,A,B);m.attack(A,"hit");
        var target=m.effectChoicePrompt(A);check(target.optionsZh().size()==2&&m.randomTranscript().size()==n,"Target order must precede any coin");
        var view=session.view(A,A);check(view.options().stream().filter(o->o.enabled()&&o.type()==ActionType.TARGET_SELECT).count()==2,"First-coin targets missing public anchors");
        reject(()->m.chooseEffectOption(B,target.resolutionId(),target.step(),1));m.chooseEffectOption(A,target.resolutionId(),target.step(),1);
        check(m.randomTranscript().size()==n&&m.choosingCoinOutcome(A),"Selecting target consumed Will or RNG");
        check(session.view(A,A).options().stream().anyMatch(o->o.type()==ActionType.COIN_HEADS)&&session.view(B,B).options().stream().noneMatch(o->o.type()==ActionType.COIN_HEADS),"Outcome controls leaked or missing");
        choose(m,A,true);check(m.benchPokemonView(B).getFirst().damage()==150&&m.randomTranscript().size()==n+2&&m.currentPlayer().equals(B),"Selected Bench did not receive the guaranteed first heads");
        for(var difficulty:dev.hua.ptcglive.ai.AiDifficulty.values()){
            var ai=game(attack);play(ai,A);ai.attack(A,"hit");AiCardOperationTest.settle(MatchSession.multiplayer(ai,A,B),difficulty);
            check(ai.currentPlayer().equals(B)&&!ai.hasPendingEffectChoice(),"AI could not finish first-target and side selection: "+difficulty);
        }
    }
    static void schema(){for(String bad:List.of(WILL.replace("SELF","OPPONENT_ACTIVE"),WILL.replace("}",",\"amount\":1}"),WILL.replace("}",",\"effects\":[{\"type\":\"draw\",\"amount\":1}]}")))reject(()->trainer(bad));}
}
