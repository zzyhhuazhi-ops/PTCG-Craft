import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Synthetic match integration, including complete pure-damage multi-coin attempts and rejection of unsupported cross-phase programs. */
public final class AttackCoinMatchTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B,WATCH=new UUID(0,7474);
    static final String COIN="{\"type\":\"coin_flip\",\"flips\":2,\"bind\":\"retry\",\"effects\":[]}";
    static void check(boolean b,String s){BeforeDamageAttackTest.check(b,s);}
    static void reject(Runnable r){BeforeDamageAttackTest.reject(r);}
    static AttackDefinition nativeAttack(String fields) {
        return BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"投币夹具\",\"energyCost\":{},\"baseDamage\":20,\"damageType\":\"GRASS\",\"effectSupport\":\"SUPPORTED\","+fields+"}").getAsJsonObject(),EffectRegistry.standard());
    }
    static TrainerProfile tool(){return DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"reroll_attack_coins\",\"target\":\"SELF\",\"pokemon_type\":\"COLORLESS\"}");}
    static ServerMatch game(AttackDefinition attack,EnergyType type,boolean attach) {
        var m=DynamicPassiveModifierTest.fixture(tool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),attack,List.of(),0,null,Map.of("basic",type,"evolved",type));
        if(attach)m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"tool").instanceId(),m.activePokemonView(A).pokemonId());
        return m;
    }
    static void choose(ServerMatch m,int option){var p=m.effectChoicePrompt(A);m.chooseEffectOption(A,p.resolutionId(),p.step(),option);}
    static int newHeads(ServerMatch m,int from){return (int)m.randomTranscript().subList(from,m.randomTranscript().size()).stream().filter(e->e.result()==0).count();}
    static long uses(ServerMatch m){return m.replayTranscript().stream().filter(e->e.operation().startsWith("发动重投道具：")).count();}
    public static void main(String[] args) {
        nativeDamage();guardedSuction();phases();nativeCondition();nativeProtection();eligibility();session();knockoutAndCleanup();unsupportedPlanBoundary();schema();
        repeatedTurns();confusionAndNonAttack();disabledWhilePending();copied();variableAndUntilTails();multipleDamageSequences();conditionalDamageSequences();namedDamageScope();
        System.out.println("AttackCoinMatchTest passed: native damage/status/protection, BEFORE/ON/AFTER programs, real Tool authority, UI and cleanup, complete multi-sequence/conditional ON_ATTACK retry without extra RNG or replayed prefix/suffix. Unsupported cross-phase/state-changing multi-sequence programs reject before mutation; the bundled catalog is checked separately.");
    }
    static void guardedSuction() {
        var json=GeneratedRuleBatch8Test.entry("sv3_en_069").getAsJsonArray("attacks").get(0).getAsJsonObject().deepCopy();
        json.addProperty("id","hit");json.add("energyCost",new com.google.gson.JsonObject());
        var attack=BundledCombatData.attack(json,EffectRegistry.standard());
        for(boolean retry:List.of(false,true)) {
            var m=game(attack,EnergyType.COLORLESS,true);int n=m.randomTranscript().size();
            var bench=m.benchPokemonView(B).getFirst().pokemonId();m.attack(A,"hit");
            var p=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,p.resolutionId(),p.step(),bench);
            check(m.activePokemonView(B).pokemonId().equals(bench),"Guarded coin lost completed switch");
            check(m.hasPendingEffectChoice(),"Guarded AFTER coin missing retry decision");
            int damage=m.activePokemonView(B).damage();check(damage>0,"Guarded damage missing");
            choose(m,retry?0:1);
            check(m.activePokemonView(B).pokemonId().equals(bench)&&m.activePokemonView(B).damage()==damage,"Retry repeated switch/damage prefix");
            check(m.randomTranscript().size()==n+(retry?2:1)&&uses(m)==(retry?1:0),"Guarded coin count");
            check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Guarded retry did not finish turn");
        }
    }
    static void nativeDamage() {
        var attack=nativeAttack("\"coinDamage\":{\"flips\":2,\"perHeads\":30}");
        for(boolean retry:List.of(false,true)) {
            var m=game(attack,EnergyType.COLORLESS,true);int n=m.randomTranscript().size(),turn=m.turnNumber();m.attack(A,"hit");
            check(m.hasPendingEffectChoice()&&m.randomTranscript().size()==n+1&&m.activePokemonView(B).damage()==0,"Native damage did not wait after first coin");
            var p=m.effectChoicePrompt(A);var rng=m.randomTranscript();var view=m.clientView(A);
            reject(()->m.chooseEffectOption(B,p.resolutionId(),p.step(),0));reject(()->m.chooseEffectOption(A,p.resolutionId(),p.step()+1,0));reject(()->m.chooseEffectOption(A,new UUID(0,99),p.step(),0));reject(()->m.endTurn(A));
            check(rng.equals(m.randomTranscript())&&view.equals(m.clientView(A)),"Invalid choice mutated match");
            choose(m,2);check(m.randomTranscript().size()==n+2&&m.activePokemonView(B).damage()==0,"Finish sequence committed damage before final decision");
            int resultStart=retry?n+2:n;choose(m,retry?0:1);int heads=newHeads(m,resultStart);
            check(m.activePokemonView(B).damage()==(20+heads*30)*2&&m.currentPlayer().equals(B)&&m.turnNumber()==turn+1,"Native reroll damage or turn incorrect");
            check(m.randomTranscript().size()==n+(retry?4:2)&&uses(m)==(retry?1:0),"Native retry RNG or usage incorrect");
            reject(()->m.chooseEffectOption(A,p.resolutionId(),p.step(),0));
        }
    }
    static void phases() {
        String heads="{\"type\":\"conditional\",\"conditions\":[{\"type\":\"COIN_RESULT\",\"coin\":\"retry\",\"value\":\"ALL_HEADS\"}],\"effects\":[";
        String damage=heads+"{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":20}]}";
        String confuse=heads+"{\"type\":\"confuse\",\"target\":\"OPPONENT_ACTIVE\"}]}";
        for(var phase:List.of(EffectTrigger.BEFORE_ATTACK,EffectTrigger.ON_ATTACK,EffectTrigger.AFTER_ATTACK)) {
            var attack=BeforeDamageAttackTest.attack(phase==EffectTrigger.BEFORE_ATTACK?COIN:"",phase==EffectTrigger.ON_ATTACK?COIN+","+damage:phase==EffectTrigger.BEFORE_ATTACK?damage:"",phase==EffectTrigger.AFTER_ATTACK?COIN+","+confuse:confuse);
            var m=game(attack,EnergyType.COLORLESS,true);int n=m.randomTranscript().size();m.attack(A,"hit");
            check(m.hasPendingEffectChoice()&&m.activePokemonView(B).damage()==(phase==EffectTrigger.AFTER_ATTACK?80:0),"Phase paused at wrong damage boundary: "+phase);
            choose(m,0);boolean both=newHeads(m,n+1)==2;
            check(m.currentPlayer().equals(B)&&m.activePokemonView(B).damage()==(phase!=EffectTrigger.AFTER_ATTACK&&both?120:80),"Program retry damage wrong: "+phase);
            check(m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED)==both,"Program retry retained original branch: "+phase);
            check(m.randomTranscript().size()==n+3&&uses(m)==1,"Program retry count wrong: "+phase);
        }
    }
    static void nativeCondition() {
        var attack=nativeAttack("\"conditions\":[{\"target\":\"DEFENDER\",\"condition\":\"CONFUSED\",\"coinCondition\":{\"flips\":2,\"requiredHeads\":2}}]");
        var m=game(attack,EnergyType.COLORLESS,true);int n=m.randomTranscript().size();m.attack(A,"hit");
        check(m.hasPendingEffectChoice()&&m.activePokemonView(B).damage()==40&&m.activePokemonView(B).conditions().isEmpty(),"Native status changed before coin decision");
        choose(m,0);boolean both=newHeads(m,n+1)==2;
        check(m.activePokemonView(B).damage()==40&&m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED)==both&&m.currentPlayer().equals(B),"Native status retry repeated damage or used discarded result");
    }
    static void nativeProtection() {
        var attack=nativeAttack("\"coinProtection\":{\"flips\":2,\"requiredHeads\":2,\"scope\":\"DAMAGE_AND_EFFECTS\"}");
        var m=game(attack,EnergyType.COLORLESS,true);int n=m.randomTranscript().size();m.attack(A,"hit");check(m.hasPendingEffectChoice(),"Native protection did not pause");
        choose(m,0);boolean both=newHeads(m,n+1)==2;check(m.activePokemonView(B).damage()==40&&m.currentPlayer().equals(B),"Native protection replayed damage");
        m.attack(B,"hit");check(m.activePokemonView(A).damage()==(both?0:40),"Protection did not use replacement coins");
    }
    static void eligibility() {
        var attack=nativeAttack("\"coinDamage\":{\"flips\":2,\"perHeads\":30}");
        for(String mode:List.of("NONE","WRONG_TYPE","DISABLED","BENCH","REMOVED")) {
            var m=game(attack,mode.equals("WRONG_TYPE")?EnergyType.FIRE:EnergyType.COLORLESS,!mode.equals("NONE")&&!mode.equals("BENCH"));
            var active=m.activePokemonView(A).pokemonId();
            if(mode.equals("DISABLED"))m.disableToolEffectsByEffect(A,A,active);
            if(mode.equals("BENCH"))m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"tool").instanceId(),m.benchPokemonView(A).getFirst().pokemonId());
            if(mode.equals("REMOVED"))m.discardToolByEffect(A,A,active,m.activePokemonView(A).attachedTools().getFirst().instanceId());
            int n=m.randomTranscript().size();m.attack(A,"hit");check(!m.hasPendingEffectChoice()&&m.randomTranscript().size()==n+2&&uses(m)==0,"Ineligible Tool changed attack path: "+mode);
        }
    }
    static void session() {
        var m=game(nativeAttack("\"coinDamage\":{\"flips\":2,\"perHeads\":30}"),EnergyType.COLORLESS,true);
        var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);m.attack(A,"hit");var rng=m.randomTranscript();var v=session.view(A,A);
        var option=v.options().stream().filter(o->o.labelZh().contains("全部作废")).findFirst().orElseThrow();
        check(session.view(B,B).options().stream().noneMatch(o->o.labelZh().contains("全部作废"))&&session.view(WATCH,null).options().stream().noneMatch(o->o.labelZh().contains("全部作废")),"Retry offered to opponent/spectator");
        session.view(A,A);check(rng.equals(m.randomTranscript()),"UI refresh rolled coins");
        session.submit(A,A,v.revision(),option.index());check(m.currentPlayer().equals(B)&&uses(m)==1,"UI retry did not complete");
        reject(()->session.submit(A,A,v.revision(),option.index()));
    }
    static void knockoutAndCleanup() {
        var attack=nativeAttack("\"baseDamage\":300,\"coinProtection\":{\"flips\":2,\"requiredHeads\":2},\"selfDamage\":30,\"discardAllAttachedEnergyAfterAttack\":true");
        var m=game(attack,EnergyType.COLORLESS,true);var e=DynamicPassiveModifierTest.hand(m,A,"energy");m.attachEnergy(A,e.instanceId(),m.activePokemonView(A).pokemonId());m.attack(A,"hit");
        check(m.phase()==MatchPhase.TURN&&m.activePokemonView(B).damage()==600&&m.activePokemonView(A).damage()==0&&!m.privateView(A).discard().contains(e),"Native tail/KO committed before decision");
        // The synthetic attack is Grass and its Active user is Grass-weak: native self damage includes W/R.
        choose(m,0);check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.activePokemonView(A).damage()==60&&m.privateView(A).discard().contains(e),"Native tail did not resume once");
        check(m.replayTranscript().stream().filter(x->x.operation().equals("使用攻击")).count()==1,"Attack audit duplicated");
        m.takePrizeAt(A,0);m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());
        check(m.currentPlayer().equals(B)&&m.privateView(A).prizes().size()==5,"Native retry awarded duplicate Prize or skipped turn");
    }
    static void unsupportedPlanBoundary() {
        var a=BeforeDamageAttackTest.attack(COIN+","+COIN.replace("retry","other"),"","");var m=game(a,EnergyType.COLORLESS,true);var v=m.clientView(A);var rng=m.randomTranscript();
        reject(()->m.attack(A,a.id()));check(v.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"Unsupported cross-phase rejection occurred after mutation");
        var ordinary=game(a,EnergyType.COLORLESS,false);int n=ordinary.randomTranscript().size();ordinary.attack(A,a.id());check(ordinary.randomTranscript().size()==n+4,"Compatibility guard broke ordinary multi-source attacks");
    }
    static void schema() {
        String effect="{\"type\":\"reroll_attack_coins\",\"pokemon_type\":\"COLORLESS\"}";
        reject(()->DynamicPassiveModifierTest.trainer("STADIUM",effect));reject(()->DynamicPassiveModifierTest.energy(effect));
        reject(()->DynamicPassiveModifierTest.trainer("TOOL",effect.replace("COLORLESS","UNKNOWN")));
        reject(()->DynamicPassiveModifierTest.trainer("TOOL",effect+",{\"type\":\"tool_capacity\",\"amount\":2}"));
    }
    static void repeatedTurns() {
        var a=BeforeDamageAttackTest.attack(COIN,"","");var m=game(a,EnergyType.COLORLESS,true);
        UUID physical=m.activePokemonView(A).attachedTools().getFirst().instanceId();
        for(int turn=1;turn<=2;turn++) {
            m.attack(A,"hit");choose(m,0);check(uses(m)==turn,"Physical Tool failed to reset on next own turn");m.endTurn(B);
            check(m.activePokemonView(A).attachedTools().getFirst().instanceId().equals(physical),"Tool changed during turn reset fixture");
        }
    }
    static void confusionAndNonAttack() {
        var a=nativeAttack("\"coinDamage\":{\"flips\":1,\"perHeads\":30}");boolean pass=false,fail=false;
        for(int skip=0;skip<24&&!(pass&&fail);skip++) {
            var m=game(a,EnergyType.COLORLESS,true);m.flipCoinsByEffect(A,2);
            check(!m.hasPendingEffectChoice()&&uses(m)==0,"Non-attack coin offered retry");
            for(int i=0;i<skip;i++)m.flipCoinsByEffect(A,1);
            m.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);int n=m.randomTranscript().size();m.attack(A,"hit");
            if(m.hasPendingEffectChoice()) {
                pass=true;check(m.randomTranscript().size()==n+2,"Confusion success coin entered retry sequence");choose(m,0);
                check(m.randomTranscript().size()==n+3&&uses(m)==1,"Retry replayed confusion");
            } else {
                fail=true;check(m.lastAttack().selfDamage()==30&&m.randomTranscript().size()==n+1&&uses(m)==0,"Confusion failure offered attack retry");
            }
        }
        check(pass&&fail,"Confusion fixture did not cover both results");
    }
    static void disabledWhilePending() {
        var m=game(nativeAttack("\"coinDamage\":{\"flips\":1,\"perHeads\":30}"),EnergyType.COLORLESS,true);m.attack(A,"hit");
        var p=m.effectChoicePrompt(A);var rng=m.randomTranscript();
        reject(()->m.disableToolEffectsByEffect(A,A,m.activePokemonView(A).pokemonId()));
        check(p.equals(m.effectChoicePrompt(A))&&rng.equals(m.randomTranscript())&&uses(m)==0,"Another effect mutated state while coin choice pending");
        choose(m,1);check(m.currentPlayer().equals(B),"Keep result could not finish after invalid concurrent effect");
        var ended=game(nativeAttack("\"coinDamage\":{\"flips\":1,\"perHeads\":30}"),EnergyType.COLORLESS,true);ended.attack(A,"hit");var old=ended.effectChoicePrompt(A);ended.concede(A);
        check(!ended.hasPendingEffectChoice(),"Concession retained pending coin choice");reject(()->ended.chooseEffectOption(A,old.resolutionId(),old.step(),0));
    }
    static ServerMatch copyGame() {
        var copy=CopyAttackTest.copier();var selected=CopyAttackTest.selected(",\"coinDamage\":{\"flips\":2,\"perHeads\":10}");
        var defs=new ArrayList<CardDefinition>();defs.add(CopyAttackTest.card("alpha",true));defs.add(CopyAttackTest.card("beta",true));defs.add(CopyAttackTest.card("energy",false));
        defs.add(new CardDefinition("tool","tool",CardCategory.TRAINER,TrainerCategory.TOOL,"H",Set.of("international"),false,false,60,Map.of(),EffectSupport.SUPPORTED,null));
        var combat=new CombatCatalog(Map.of("alpha",new PokemonProfile(300,0,null,EnergyType.FIRE,2,null,0,1,List.of(copy),List.of(),EnergyType.COLORLESS),
            "beta",new PokemonProfile(300,0,null,EnergyType.PSYCHIC,2,null,0,1,List.of(selected),List.of(),EnergyType.FIRE)),Map.of("energy",EnergyType.GRASS),Map.of("tool",tool()));
        for(int seed=0;seed<100;seed++) {
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(CopyAttackTest.A,CopyAttackTest.B)) {
                var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,i%3==0?(owner.equals(CopyAttackTest.A)?"alpha":"beta"):i%3==1?"energy":"tool"));decks.add(cards);
            }
            var m=new ServerMatch(CopyAttackTest.A,CopyAttackTest.B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),combat,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,CopyAttackTest.A,CopyAttackTest.B);
            if(List.of(CopyAttackTest.A,CopyAttackTest.B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).count()<2))continue;
            OpeningTestSupport.complete(m,CopyAttackTest.A,CopyAttackTest.B,1);m.drawCardsByEffect(CopyAttackTest.A,CopyAttackTest.A,20);m.drawCardsByEffect(CopyAttackTest.A,CopyAttackTest.B,20);m.endTurn(CopyAttackTest.A);m.endTurn(CopyAttackTest.B);return m;
        }
        throw new AssertionError("No copy Tool fixture");
    }
    static void copied() {
        var m=copyGame();UUID actor=CopyAttackTest.A;CopyAttackTest.pay(m);var physical=DynamicPassiveModifierTest.hand(m,actor,"tool");
        m.playTrainer(actor,physical.instanceId(),m.activePokemonView(actor).pokemonId());var selected=CopyAttackTest.choice(m);int n=m.randomTranscript().size();CopyAttackTest.copy(m,selected,List.of(),List.of());
        var p=m.effectChoicePrompt(actor);m.chooseEffectOption(actor,p.resolutionId(),p.step(),0);int heads=newHeads(m,n+1);
        check(m.activePokemonView(CopyAttackTest.B).damage()==40+heads*10&&m.currentPlayer().equals(CopyAttackTest.B)&&uses(m)==1,"Copied retry used printed provider type/cost instead of actual Colorless attacker");
        check(m.randomTranscript().size()==n+3&&m.activePokemonView(actor).attachedEnergyIds().size()==1,"Copied retry repeated payment or rolled twice");
    }
    static void variableAndUntilTails() {
        String effect="{\"type\":\"coin_flip\",\"flips\":0,\"flips_from\":\"ENERGY_COUNT\",\"flips_target\":\"SELF\",\"effects\":[{\"type\":\"repeat_per_heads\",\"effects\":[{\"type\":\"discard_top_deck\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1}]}]}";
        var a=BeforeDamageAttackTest.attack("","",effect);
        for(int count:List.of(0,3)) {
            var m=game(a,EnergyType.COLORLESS,true);UUID host=m.activePokemonView(A).pokemonId();
            for(int i=0;i<count;i++)m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,DynamicPassiveModifierTest.hand(m,A,"energy").instanceId(),A,host);
            int n=m.randomTranscript().size(),discard=m.privateView(B).discard().size();m.attack(A,"hit");
            if(count==0){check(!m.hasPendingEffectChoice()&&m.randomTranscript().size()==n&&uses(m)==0,"Zero dynamic coins consumed retry or generated a prompt");continue;}
            check(m.privateView(B).discard().size()==discard,"Provisional dynamic results executed a branch");choose(m,0);
            check(m.randomTranscript().size()==n+4&&m.privateView(B).discard().size()==discard+newHeads(m,n+1),"Variable attack retry used wrong live count or repeated discard");
        }
        var until=CoinUntilTailsTest.damageAttack();var m=game(until,EnergyType.COLORLESS,true);int n=m.randomTranscript().size();m.attack(A,until.id());
        check(m.hasPendingEffectChoice()&&m.randomTranscript().size()==n+1&&m.activePokemonView(B).damage()==0,"Until-tails attack did not offer early retry");
        choose(m,0);var coins=m.randomTranscript().subList(n+1,m.randomTranscript().size());
        check(coins.getLast().result()==1&&coins.subList(0,coins.size()-1).stream().allMatch(e->e.result()==0),"Until-tails retry retained old stopping point");
        check(m.activePokemonView(B).damage()==(coins.size()-1)*60&&uses(m)==1,"Until-tails damage included discarded original coin");
    }
    static void multipleDamageSequences() {
        String first=COIN.replace("\"flips\":2","\"flips\":1");
        String second="{\"type\":\"coin_flip\",\"flips\":2,\"effects\":[{\"type\":\"repeat_per_heads\",\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}]}]}";
        String bonus="{\"type\":\"conditional\",\"conditions\":[{\"type\":\"COIN_RESULT\",\"coin\":\"retry\",\"value\":\"HEADS\"}],\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":20}]}";
        var a=BeforeDamageAttackTest.attack("{\"type\":\"draw\",\"amount\":1}",first+","+second+","+bonus,"{\"type\":\"draw\",\"amount\":1}");
        var m=game(a,EnergyType.COLORLESS,true);int n=m.randomTranscript().size(),hand=m.privateView(A).hand().size();m.attack(A,"hit");
        check(m.privateView(A).hand().size()==hand+1&&m.activePokemonView(B).damage()==0,"Multi-sequence attack replayed or skipped before phase");
        choose(m,2);check(m.randomTranscript().size()==n+3&&m.activePokemonView(B).damage()==0,"Multi-sequence damage committed before final decision");
        var p=m.effectChoicePrompt(A);check(p.optionsZh().size()==2,"Final whole-attempt prompt should have only retry/keep");choose(m,0);
        var coins=m.randomTranscript().subList(n+3,m.randomTranscript().size());int firstBonus=coins.getFirst().result()==0?20:0;
        long secondHeads=coins.subList(1,3).stream().filter(e->e.result()==0).count();
        check(coins.size()==3&&m.activePokemonView(B).damage()==2*(40+firstBonus+secondHeads*30),"Multi-sequence retry did not replace every original group");
        check(m.privateView(A).hand().size()==hand+2&&m.currentPlayer().equals(B)&&uses(m)==1,"Multi-sequence attack repeated prefix/suffix or consumed retry twice");
        reject(()->m.chooseEffectOption(A,p.resolutionId(),p.step(),0));
    }
    static void conditionalDamageSequences() {
        String first=COIN.replace("\"flips\":2","\"flips\":1");
        String later="{\"type\":\"conditional\",\"conditions\":[{\"type\":\"COIN_RESULT\",\"coin\":\"retry\",\"value\":\"HEADS\"}],\"effects\":[{\"type\":\"coin_flip\",\"flips\":2,\"effects\":[{\"type\":\"repeat_per_heads\",\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10}]}]}]}";
        var a=BeforeDamageAttackTest.attack("",first+","+later,"");boolean skipped=false,taken=false;
        for(int skip=0;skip<24&&!(skipped&&taken);skip++) {
            var m=game(a,EnergyType.COLORLESS,true);for(int i=0;i<skip;i++)m.flipCoinsByEffect(A,1);int n=m.randomTranscript().size();m.attack(A,"hit");
            choose(m,0);var coins=m.randomTranscript().subList(n+1,m.randomTranscript().size());boolean gate=coins.getFirst().result()==0;
            skipped|=!gate;taken|=gate;long heads=coins.subList(1,coins.size()).stream().filter(e->e.result()==0).count();
            check(coins.size()==(gate?3:1)&&m.activePokemonView(B).damage()==2*(40+heads*10),"Conditional final path retained original later coin group");
            check(m.currentPlayer().equals(B)&&uses(m)==1,"Conditional planned attack did not finish exactly once");
        }
        check(skipped&&taken,"Conditional reroll did not cover both final paths");
    }
    static void namedDamageScope() {
        String dynamic="{\"type\":\"coin_flip\",\"flips\":0,\"flips_from\":\"DAMAGE_COUNTER_COUNT\",\"flips_target\":\"CHOSEN_POKEMON\",\"effects\":[{\"type\":\"repeat_per_heads\",\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10}]}]}";
        var a=BeforeDamageAttackTest.attack(AttackPokemonBindingsTest.select("chosen","OPPONENT","BENCH",AttackPokemonBindingsTest.counters(2)),
            COIN.replace("\"flips\":2","\"flips\":1")+","+AttackPokemonBindingsTest.use("chosen",dynamic),"");
        var m=game(a,EnergyType.COLORLESS,true);UUID target=m.benchPokemonView(B).getFirst().pokemonId();int n=m.randomTranscript().size();m.attack(A,"hit");
        check(m.hasPendingPokemonSelection()&&m.randomTranscript().size()==n,"Coin planner ran before chosen-target prefix");AttackPokemonBindingsTest.choose(m,target);
        choose(m,2);check(m.randomTranscript().size()==n+3&&m.benchPokemonView(B).getFirst().damage()==20,"Named planning lost target-dependent count");choose(m,0);
        var coins=m.randomTranscript().subList(n+3,m.randomTranscript().size());long heads=coins.subList(1,3).stream().filter(e->e.result()==0).count();
        check(coins.size()==3&&m.activePokemonView(B).damage()==2*(40+10*heads)&&m.benchPokemonView(B).getFirst().damage()==20,"Named child context lost confirmed ledger or repeated committed prefix");
        var ended=game(BeforeDamageAttackTest.attack("",COIN+","+COIN.replace("retry","second"),""),EnergyType.COLORLESS,true);ended.attack(A,"hit");var p=ended.effectChoicePrompt(A);ended.concede(A);
        check(!ended.hasPendingEffectChoice(),"Concession retained planned damage coins");reject(()->ended.chooseEffectOption(A,p.resolutionId(),p.step(),0));
    }
}
