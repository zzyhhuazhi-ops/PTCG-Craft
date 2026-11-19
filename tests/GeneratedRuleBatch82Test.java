import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual Mega Greninja ex printings; synthetic opponents isolate the printed rules. */
public final class GeneratedRuleBatch82Test {
    static final UUID A=new UUID(0,8211),B=new UUID(0,8212),WATCH=new UUID(0,8213);
    static final String BASIC="me4_en_020",STAGE1="me4_en_021",CARD="me4_en_022",BUBBLE="me4_en_084";
    static final String ABILITY="mortal-shuriken",ATTACK="ninja-spinner";
    static final List<String> PRINTS=List.of(CARD,"me4_en_100","me4_en_116","me4_en_122","mebsp_en_081");
    static final CardDatabase DB=BundledRules.loadCardDefinitions();static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static ServerMatch game(){return game(CARD,null,null);}
    static ServerMatch game(String card,EnergyType weakness,EnergyType resistance){
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        var printed=new ArrayList<>(PRINTS);printed.add(BASIC);printed.add(STAGE1);
        for(String id:printed){defs.add(DB.find(id).orElseThrow());profiles.put(id,DATA.pokemon(id).orElseThrow());}
        defs.add(DB.find(BUBBLE).orElseThrow());
        for(String id:List.of("base","water","wrong","dual"))defs.add(new CardDefinition(id,id,id.equals("base")?CardCategory.POKEMON:CardCategory.ENERGY,
            TrainerCategory.NONE,"J",Set.of("international"),id.equals("base"),Set.of("water","wrong").contains(id),60,Map.of(),EffectSupport.SUPPORTED,null));
        profiles.put("base",new PokemonProfile(1000,1,null,weakness,weakness==null?0:2,resistance,resistance==null?0:30,1,List.of(GeneratedRuleBatch8Test.hit(500,false)),List.of(),EnergyType.COLORLESS));
        // Synthetic two-unit flexible supply exercises the official Neo Upper return interaction.
        // Neo Upper itself is still unaudited; this fixture does not register or enable that real card.
        var unit=new EnergyProfile.Unit(EnumSet.allOf(EnergyType.class));
        var catalog=new CombatCatalog(profiles,Map.of("water",EnergyType.WATER,"wrong",EnergyType.FIRE),Map.of(),Map.of(
            BUBBLE,DATA.energyProfile(BUBBLE).orElseThrow(),"dual",new EnergyProfile(List.of(unit,unit))));
        String[] pool={BASIC,BASIC,STAGE1,STAGE1,card,card,"base","base","water","water","water","wrong",BUBBLE,"dual"};
        for(int seed=0;seed<1500;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cs=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cs.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,pool[i%pool.length]));decks.add(cs);}
            var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(m.privateView(A).hand().stream().filter(c->c.definitionId().equals(BASIC)).count()<2||m.privateView(B).hand().stream().filter(c->c.definitionId().equals("base")).count()<2)continue;
            for(UUID p:List.of(A,B)){String id=p.equals(A)?BASIC:"base";m.chooseOpeningActive(p,hand(m,p,id).instanceId());m.placeOpeningBench(p,hand(m,p,id).instanceId());m.startAfterOpening(p);}
            OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,30);m.drawCardsByEffect(A,B,30);m.endTurn(A);m.endTurn(B);
            if(Arrays.stream(pool).anyMatch(id->m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals(id))))continue;
            if(List.of(STAGE1,card,"water").stream().anyMatch(id->m.privateView(A).hand().stream().filter(c->c.definitionId().equals(id)).count()<(id.equals("water")?4:2)))continue;
            for(UUID host:List.of(active(m,A),bench(m,A)))m.evolve(A,host,hand(m,A,STAGE1).instanceId());
            reject(()->m.evolve(A,active(m,A),hand(m,A,card).instanceId()));m.endTurn(A);m.endTurn(B);
            for(UUID host:List.of(active(m,A),bench(m,A)))m.evolve(A,host,hand(m,A,card).instanceId());return m;
        }throw new AssertionError("No actual Mega Greninja fixture: "+card);
    }
    static CardInstance attach(ServerMatch m,UUID host,String id){var c=hand(m,A,id);m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,c.instanceId(),A,host);return c;}
    static CardInstance attach(ServerMatch m,String id){return attach(m,active(m,A),id);}
    static void pay(ServerMatch m){attach(m,"water");attach(m,"water");}
    static void submit(ServerMatch m,CardInstance c){var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(c.instanceId()));}
    static void chooseMore(ServerMatch m,boolean yes){var p=m.effectChoicePrompt(A);check(p.optionsZh().size()==2,"Ninja Spinner optional branch missing");m.chooseEffectOption(A,p.resolutionId(),p.step(),yes?0:1);}
    static void use(ServerMatch m,UUID target){m.useAbility(A,active(m,A),ABILITY,target);submit(m,hand(m,A,"water"));}
    static void settle(ServerMatch m){int cap=12;while(m.phase()==MatchPhase.PRIZE_SELECTION&&cap-->0)m.takePrizeAt(m.pendingChoicePlayer(),0);while(m.phase()==MatchPhase.CHOOSE_REPLACEMENT&&cap-->0){UUID p=m.pendingChoicePlayer();m.chooseReplacement(p,m.benchPokemonView(p).getFirst().pokemonId());}check(cap>0,"KO did not finish");}
    public static void main(String[] args){printedAndReprints();abilityTargets();costAuthority();quotaAndSuppression();abilityProtection();spinner();specialEnergy();returnAuthority();knockouts();copiedNoWater();System.out.println("GeneratedRuleBatch82Test passed: all five actual Mega Greninja ex printings and named two-stage evolution,350HP/Water/Lightning weakness/retreat1/three prizes, Active-only Basic Water hand cost, private atomic choices and per-source quota, opposing Active/Bench60 counters, suppression/protection, WW120 or200 with optional damage before typed Energy return, actual Bubble Water and synthetic two-unit flexible return, copied zero-Water bonus and KO ordering");}
    static void printedAndReprints(){var original=DATA.pokemon(CARD).orElseThrow();
        for(String id:PRINTS){var c=DB.find(id).orElseThrow();var p=DATA.pokemon(id).orElseThrow();
            check(c.effectSupport()==EffectSupport.SUPPORTED&&!c.basicPokemon()&&c.pokemonFamilies().containsAll(Set.of(PokemonFamily.EX,PokemonFamily.RULE_BOX,PokemonFamily.MEGA_EVOLUTION_EX)),"Printed family/status: "+id);
            check(p.hitPoints()==350&&p.retreatCost()==1&&p.prizeCards()==3&&p.pokemonType()==EnergyType.WATER&&p.weaknessType()==EnergyType.LIGHTNING&&p.weaknessMultiplier()==2&&p.resistanceType()==null&&p.evolvesFromName().equals("Frogadier"),"Printed Mega profile: "+id);
            check(p.attacks().size()==1&&p.attacks().getFirst().id().equals(ATTACK)&&p.attacks().getFirst().baseDamage()==120&&p.attacks().getFirst().energyCost().equals(Map.of(EnergyType.WATER,2)),"Printed Ninja Spinner: "+id);
            check(p.abilities().size()==1&&p.abilities().getFirst().id().equals(ABILITY)&&p.abilities().getFirst().kind()==AbilityDefinition.Kind.ACTIVATED_ONCE_PER_TURN&&p.attacks().equals(original.attacks())&&p.abilities().equals(original.abilities()),"Reprint rules diverged: "+id);
            AttackProgramRules.validate(p.attacks().getFirst());ActivatedAbilityRules.validate(p.abilities().getFirst());
            var m=game(id,null,null);check(m.activePokemonView(A).evolutionCardIds().equals(List.of(BASIC,STAGE1,id)),"Reprint rejected named Frogadier evolution: "+id);use(m,bench(m,B));check(m.benchPokemonView(B).getFirst().damage()==60,"Reprint Ability: "+id);pay(m);m.attack(A,ATTACK);chooseMore(m,false);check(m.lastAttack().finalDamage()==120,"Reprint attack: "+id);
        }
    }
    static void abilityTargets(){for(boolean onBench:List.of(false,true))for(boolean weak:List.of(false,true)){
        var m=game(CARD,weak?EnergyType.WATER:null,weak?null:EnergyType.WATER);UUID target=onBench?bench(m,B):active(m,B);var before=m.clientView(A);
        reject(()->m.useAbility(A,active(m,A),ABILITY));reject(()->m.useAbility(A,active(m,A),ABILITY,active(m,A)));reject(()->m.useAbility(A,active(m,A),ABILITY,bench(m,A)));reject(()->m.useAbility(B,active(m,A),ABILITY,target));
        check(before.equals(m.clientView(A)),"Illegal declared Ability target changed state");use(m,target);
        check(m.activePokemonView(B).damage()==(onBench?0:60)&&m.benchPokemonView(B).getFirst().damage()==(onBench?60:0)&&m.currentPlayer().equals(A),"Six counters used weakness/resistance or wrong target");
    }}
    static void costAuthority(){
        var m=game();UUID host=active(m,A),target=bench(m,B);var water=hand(m,A,"water");var wrong=hand(m,A,"wrong");var special=hand(m,A,BUBBLE);var other=m.privateView(B).hand().getFirst();
        m.useAbility(A,host,ABILITY,target);var p=m.effectCardPrompt(A);var before=m.clientView(A);var rng=m.randomTranscript();
        check(p.minimum()==1&&p.maximum()==1&&p.candidates().stream().allMatch(c->c.definitionId().equals("water"))&&m.privateView(A).hand().contains(water)&&m.benchPokemonView(B).getFirst().damage()==0,"Cost eligibility or cost/reward order");
        var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);for(UUID viewer:List.of(B,WATCH)){var view=session.view(viewer,viewer.equals(WATCH)?null:viewer);check(view.game().effectSelection()==null,"Ability cost hand exposed");String json=new com.google.gson.Gson().toJson(view);for(var c:p.candidates())check(!json.contains(c.instanceId().toString()),"Private cost candidate id exposed");}
        reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(water.instanceId())));reject(()->m.submitEffectCards(A,p.resolutionId(),p.step()+1,List.of(water.instanceId())));
        for(var bad:List.of(wrong,special,other))reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(bad.instanceId())));
        reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(water.instanceId(),water.instanceId())));
        check(before.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"Forged cost mutated state/RNG");submit(m,water);check(m.privateView(A).discard().contains(water)&&m.benchPokemonView(B).getFirst().damage()==60,"Cost identity/declared target lost");reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(water.instanceId())));
        for(String id:List.of("wrong",BUBBLE,"dual",BASIC)){var no=game();var c=hand(no,A,id);GeneratedRuleBatch35Test.trim(no,A,1,List.of(c));var state=no.clientView(A);check(!no.canUseAbility(A,active(no,A),ABILITY,active(no,B)),"Non-Basic Water cost allowed: "+id);reject(()->no.useAbility(A,active(no,A),ABILITY,active(no,B)));check(state.equals(no.clientView(A)),"Unaffordable cost changed state");}
    }
    static void quotaAndSuppression(){var m=game();UUID first=active(m,A),second=bench(m,A),target=active(m,B);var before=m.clientView(A);
        check(!m.canUseAbility(A,second,ABILITY,target),"Bench source offered Active-only Ability");reject(()->m.useAbility(A,second,ABILITY,target));check(before.equals(m.clientView(A)),"Bench rejection paid cost");
        use(m,target);check(!m.canUseAbility(A,first,ABILITY,target),"Same source reused Ability");m.switchActiveByEffect(A,A,second);check(m.canUseAbility(A,second,ABILITY,target),"Second source shared quota");use(m,target);
        m.switchActiveByEffect(A,A,first);check(!m.canUseAbility(A,first,ABILITY,target)&&m.activePokemonView(B).damage()==120,"Switch reset source quota");m.endTurn(A);m.endTurn(B);check(m.canUseAbility(A,first,ABILITY,target),"Per-turn quota failed reset");
        m.disableAbilityEffectsByEffect(A,A,first);var suppressed=m.clientView(A);check(!m.canUseAbility(A,first,ABILITY,target),"Suppressed Mortal Shuriken usable");reject(()->m.useAbility(A,first,ABILITY,target));check(suppressed.equals(m.clientView(A)),"Suppressed source paid cost");
    }
    static void abilityProtection(){var m=game();UUID target=bench(m,B);m.endTurn(A);StarterDeck100CounterMoveTest.shield(m,B,target,"ABILITY");m.endTurn(B);var paid=hand(m,A,"water");m.useAbility(A,active(m,A),ABILITY,target);submit(m,paid);
        check(m.benchPokemonView(B).getFirst().damage()==0&&m.privateView(A).discard().contains(paid)&&!m.canUseAbility(A,active(m,A),ABILITY,active(m,B)),"Protected target prevented cost or refunded quota");
    }
    static void spinner(){for(boolean more:List.of(false,true))for(int mode:List.of(0,1,2)){
        var m=game(CARD,mode==1?EnergyType.WATER:null,mode==2?EnergyType.WATER:null);var initial=m.clientView(A);reject(()->m.attack(A,ATTACK));check(initial.equals(m.clientView(A)),"Unpaid WW attack mutated state");attach(m,"wrong");var water=attach(m,"water");reject(()->m.attack(A,ATTACK));attach(m,"water");
        m.attack(A,ATTACK);check(m.activePokemonView(B).damage()==0&&m.activePokemonView(A).attachedEnergyIds().size()==3,"Ninja Spinner resolved before choice");chooseMore(m,more);int base=more?200:120,expected=mode==1?base*2:mode==2?base-30:base;
        if(more){check(m.activePokemonView(B).damage()==expected&&m.currentPlayer().equals(A)&&m.activePokemonView(A).attachedEnergyIds().contains(water.instanceId()),"Return happened before damage/attack ended before return");submit(m,water);}
        check(m.lastAttack().finalDamage()==expected&&m.activePokemonView(B).damage()==expected&&m.currentPlayer().equals(B)&&m.activePokemonView(A).attachedEnergyIds().size()==(more?2:3)&&m.privateView(A).hand().contains(water)==more,"Optional bonus/return/weakness/resistance result");
    }}
    static void specialEnergy(){for(String id:List.of(BUBBLE,"dual")){
        var m=game();var returned=attach(m,id);if(id.equals(BUBBLE))attach(m,"water");
        m.attack(A,ATTACK);chooseMore(m,true);var p=m.effectCardPrompt(A);check(p.candidates().contains(returned),"Currently supplied Water excluded: "+id);submit(m,returned);
        check(m.lastAttack().finalDamage()==200&&m.privateView(A).hand().contains(returned)&&!m.activePokemonView(A).attachedEnergyIds().contains(returned.instanceId())&&m.activePokemonView(A).attachedEnergyIds().size()==(id.equals(BUBBLE)?1:0),"Special return split units or lost physical card: "+id);
    }}
    static void returnAuthority(){var m=game();var water=attach(m,"water");attach(m,"water");var wrong=attach(m,"wrong");var ownBench=attach(m,bench(m,A),"water");var handOnly=hand(m,A,"water");
        m.attack(A,ATTACK);var choice=m.effectChoicePrompt(A);var before=m.clientView(A);reject(()->m.chooseEffectOption(B,choice.resolutionId(),choice.step(),0));reject(()->m.chooseEffectOption(A,choice.resolutionId(),choice.step(),2));check(before.equals(m.clientView(A)),"Forged opt-in changed state");chooseMore(m,true);
        var p=m.effectCardPrompt(A);check(p.minimum()==1&&p.maximum()==1&&p.candidates().size()==2&&p.candidates().stream().allMatch(c->c.definitionId().equals("water")),"Return allowed wrong host/type or wrong count");var pending=m.clientView(A);
        reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(water.instanceId())));for(var bad:List.of(wrong,ownBench,handOnly))reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(bad.instanceId())));
        reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(water.instanceId(),water.instanceId())));reject(()->m.chooseEffectOption(A,choice.resolutionId(),choice.step(),0));check(pending.equals(m.clientView(A)),"Forged Energy return changed damage/state");submit(m,water);reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(water.instanceId())));
        // Effects-only immunity (the official Banette interaction) must not erase damage modifiers.
        var shielded=game();shielded.endTurn(A);StarterDeck100CounterMoveTest.shield(shielded,B,active(shielded,B),"ATTACK");shielded.endTurn(B);var paid=attach(shielded,"water");attach(shielded,"water");shielded.attack(A,ATTACK);chooseMore(shielded,true);submit(shielded,paid);check(shielded.activePokemonView(B).damage()==200,"Defender effect immunity prevented bonus damage");
    }
    static void knockouts(){var m=game();m.placeDamageCountersByEffect(A,B,active(m,B),60);m.placeDamageCountersByEffect(A,B,active(m,B),20);var water=attach(m,"water");attach(m,"water");m.attack(A,ATTACK);chooseMore(m,true);
        check(m.hasPendingCardSelection()&&m.activePokemonView(B).damage()==1000&&m.privateView(A).prizes().size()==6,"KO occurred before printed return effect");submit(m,water);check(m.privateView(A).hand().contains(water)&&m.phase()==MatchPhase.PRIZE_SELECTION,"Return lost before KO");settle(m);check(m.currentPlayer().equals(B)&&m.privateView(A).prizes().size()==5,"Ninja Spinner KO/turn/prize flow");
        var counter=game();mortalLethal(counter);check(counter.phase()==MatchPhase.PRIZE_SELECTION,"Ability counters did not KO");settle(counter);check(counter.currentPlayer().equals(A)&&counter.privateView(A).prizes().size()==5,"Ability KO ended turn or awarded wrong prizes");
        var mega=game();mega.endTurn(A);mega.attack(B,mega.activeAttacks(B).getFirst().id());settle(mega);check(mega.privateView(B).prizes().size()==3&&mega.privateView(A).discard().stream().anyMatch(c->c.definitionId().equals(CARD)),"Actual Mega did not award three prizes");
    }
    static void mortalLethal(ServerMatch m){m.placeDamageCountersByEffect(A,B,active(m,B),60);m.placeDamageCountersByEffect(A,B,active(m,B),34);use(m,active(m,B));}
    static void copiedNoWater(){for(boolean more:List.of(false,true)){
        var attack=DATA.pokemon(CARD).orElseThrow().attacks().getFirst();var m=CopyAttackTest.fixture(CopyAttackTest.copier(),attack,EnergyType.COLORLESS,0);UUID a=CopyAttackTest.A,b=CopyAttackTest.B;CopyAttackTest.pay(m);var attached=m.activePokemonView(a).attachedEnergyIds();
        var option=m.copyAttackOptions(a,"copy").stream().filter(o->o.attackId().equals(ATTACK)).findFirst().orElseThrow();CopyAttackTest.copy(m,option,List.of(),List.of());var p=m.effectChoicePrompt(a);m.chooseEffectOption(a,p.resolutionId(),p.step(),more?0:1);
        check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(b)&&m.lastAttack().finalDamage()==(more?200:120)&&m.activePokemonView(b).damage()==(more?200:120)&&m.activePokemonView(a).attachedEnergyIds().equals(attached),"Copied Ninja Spinner required Water return before bonus or used donor energy/owner");
    }}
}
