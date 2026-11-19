import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual Metagross and Reversal Energy. Synthetic copier/multiunit supply do not enable unsupported FAQ actors. */
public final class GeneratedRuleBatch94Test {
    static final UUID A=new UUID(0,9411),B=new UUID(0,9412),WATCH=new UUID(0,9413);
    static final String BASIC="me4_en_059",STAGE1="me4_en_060",ALT="me4_en_094",CARD="me4_en_061",REVERSAL="sv2_en_192";
    static final String HAMMER="metallic-hammer",BOUNCE="bounce-back";
    static final CardDatabase DB=BundledRules.loadCardDefinitions();static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID baseBench(ServerMatch m,UUID p){return m.benchPokemonView(p).stream().filter(v->v.topCardId().equals("base")).findFirst().orElseThrow().pokemonId();}
    static UUID donor(ServerMatch m){return m.benchPokemonView(B).stream().filter(v->v.topCardId().equals(CARD)).findFirst().orElseThrow().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static void atomic(ServerMatch m,Runnable r){var a=m.privateView(A);var b=m.privateView(B);var view=m.clientView(A);var rng=m.randomTranscript();reject(r);check(a.equals(m.privateView(A))&&b.equals(m.privateView(B))&&view.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"Rejected action changed private/public state or RNG");}
    static AttackDefinition copier(){return CopyAttackTest.copier().withEffectProgram(new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_ATTACK,"{\"type\":\"copy_attack\",\"target\":\"OPPONENT_BENCH\",\"cost_policy\":\"DECLARED_ATTACK\"}"))));}
    static ServerMatch game(){return game(STAGE1,"NORMAL");}
    static ServerMatch game(String stage,String mode){
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:List.of(BASIC,STAGE1,ALT,CARD)){defs.add(DB.find(id).orElseThrow());profiles.put(id,DATA.pokemon(id).orElseThrow());}defs.add(DB.find(REVERSAL).orElseThrow());
        for(String id:List.of("base","metal","wrong","double"))defs.add(new CardDefinition(id,id,id.equals("base")?CardCategory.POKEMON:CardCategory.ENERGY,TrainerCategory.NONE,"J",Set.of("international"),id.equals("base"),Set.of("metal","wrong").contains(id),60,Map.of(),EffectSupport.SUPPORTED,null));
        profiles.put("base",new PokemonProfile(1000,2,null,mode.equals("WEAK")?EnergyType.METAL:null,mode.equals("WEAK")?2:0,mode.equals("RESIST")?EnergyType.METAL:null,mode.equals("RESIST")?30:0,1,List.of(copier(),GeneratedRuleBatch8Test.hit(100,false)),List.of(),EnergyType.COLORLESS));
        // Explicit two-Metal-unit synthetic Energy, not Neo Upper or Luminous Energy.
        var catalog=new CombatCatalog(profiles,Map.of("metal",EnergyType.METAL,"wrong",EnergyType.WATER),Map.of(),Map.of(REVERSAL,DATA.energyProfile(REVERSAL).orElseThrow(),"double",EnergyProfile.fixed(EnergyType.METAL,2)));
        String[] pool={BASIC,BASIC,BASIC,stage,stage,CARD,CARD,"base","base","base","base","base","base","metal","metal","metal","metal","metal","metal","metal","metal","wrong","wrong",REVERSAL,REVERSAL,"double","double"};
        for(int seed=0;seed<6000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,pool[i%pool.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals(BASIC)))||m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals("base"))||m.privateView(B).hand().stream().filter(c->c.definitionId().equals("base")).count()<2)continue;
            m.chooseOpeningActive(A,hand(m,A,BASIC).instanceId());m.placeOpeningBench(A,hand(m,A,"base").instanceId());
            m.chooseOpeningActive(B,hand(m,B,"base").instanceId());m.placeOpeningBench(B,hand(m,B,"base").instanceId());m.placeOpeningBench(B,hand(m,B,BASIC).instanceId());
            m.startAfterOpening(A);m.startAfterOpening(B);OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,28);m.drawCardsByEffect(A,B,28);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().anyMatch(p->List.of(stage,CARD,"wrong",REVERSAL).stream().anyMatch(id->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals(id))))
                ||m.privateView(A).hand().stream().filter(c->c.definitionId().equals("metal")).count()<6||m.privateView(B).hand().stream().filter(c->c.definitionId().equals("metal")).count()<3||m.privateView(A).hand().stream().filter(c->c.definitionId().equals("double")).count()<2)continue;
            UUID bh=m.benchPokemonView(B).stream().filter(v->v.topCardId().equals(BASIC)).findFirst().orElseThrow().pokemonId();
            m.evolve(A,active(m,A),hand(m,A,stage).instanceId());atomic(m,()->m.evolve(A,active(m,A),hand(m,A,CARD).instanceId()));m.endTurn(A);
            m.evolve(B,bh,hand(m,B,stage).instanceId());m.endTurn(B);m.evolve(A,active(m,A),hand(m,A,CARD).instanceId());m.endTurn(A);m.evolve(B,bh,hand(m,B,CARD).instanceId());m.endTurn(B);return m;
        }throw new AssertionError("No actual Metagross fixture "+stage+"/"+mode);
    }
    static CardInstance attach(ServerMatch m,UUID p,UUID host,String id){var c=hand(m,p,id);m.attachEnergyByEffect(A,p,EffectSourceZone.HAND,c.instanceId(),p,host);return c;}
    static CardInstance attach(ServerMatch m,String id){return attach(m,A,active(m,A),id);}
    static List<CardInstance> metals(ServerMatch m,int n){var out=new ArrayList<CardInstance>();for(int i=0;i<n;i++)out.add(attach(m,"metal"));return out;}
    static List<CardInstance> pay(ServerMatch m){var cards=metals(m,3);attach(m,"wrong");return cards;}
    static void choose(ServerMatch m,boolean yes){var p=m.effectChoicePrompt(A);check(p.optionsZh().size()==2,"Missing optional bonus choice");m.chooseEffectOption(A,p.resolutionId(),p.step(),yes?0:1);}
    static void discard(ServerMatch m,List<CardInstance> cards){var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),cards.stream().map(CardInstance::instanceId).toList());}
    static void settle(ServerMatch m){int cap=16;while((m.phase()==MatchPhase.PRIZE_SELECTION||m.phase()==MatchPhase.CHOOSE_REPLACEMENT)&&cap-->0){if(m.phase()==MatchPhase.PRIZE_SELECTION)m.takePrizeAt(m.pendingChoicePlayer(),0);else m.chooseReplacement(m.pendingChoicePlayer(),m.benchPokemonView(m.pendingChoicePlayer()).getFirst().pokemonId());}check(cap>0,"KO did not settle");}
    static void shield(ServerMatch m,String scope){var e=EffectRegistry.standard().parse(JsonParser.parseString(EffectOriginProtectionTest.shield("ATTACK",scope,"OPPONENT_ACTIVE","LEAVES_PLAY")).getAsJsonObject());m.executeEffectProgramByRule(A,active(m,A),new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(e)))),EffectTrigger.ON_CARD_PLAYED);}
    static boolean metalCount(ServerMatch m,UUID host,int count){return m.matchesEffectCondition(A,host,new ConditionDefinition(ConditionType.ENERGY_COUNT,Map.of("energy_type","METAL","count_mode","UNITS","value",""+count,"comparison","="),List.of()));}
    static void beginCopy(ServerMatch m){var option=m.copyAttackOptions(A,"copy").stream().filter(o->o.pokemonId().equals(donor(m))&&o.attackId().equals(HAMMER)).findFirst().orElseThrow();m.copyAttackWithEnergyDiscards(A,"copy",option.pokemonId(),option.entrySequence(),option.attackId(),List.of(),List.of());}
    public static void main(String[] args){printedAndEvolution();normalPaymentAndChoice();copiedUnitCounts();selectionAuthority();specialEnergyUnits();reversalEnergy();damageAndKnockoutOrder();boundReset();bounceBack();switchProtectionTargets();System.out.println("GeneratedRuleBatch94Test passed: actual Stage2 Metagross both Metang lines, MMMC150/300 intent, official copied0Metal bonus and1/2/3/4-unit discard, real Reversal and synthetic whole-card multiunit supply, authoritative public Energy choices, damage-before-discard/deferred KO, copied actual source and Bounce Back opponent-authority switching");}
    static void printedAndEvolution(){
        var p=DATA.pokemon(CARD).orElseThrow();var d=DB.find(CARD).orElseThrow();
        check(d.effectSupport()==EffectSupport.SUPPORTED&&!d.basicPokemon()&&!d.pokemonFamilies().contains(PokemonFamily.RULE_BOX)&&d.regulationMark().equals("J")&&p.hitPoints()==180&&p.prizeCards()==1&&p.retreatCost()==3&&p.pokemonType()==EnergyType.METAL&&p.weaknessType()==EnergyType.FIRE&&p.weaknessMultiplier()==2&&p.resistanceType()==EnergyType.GRASS&&p.resistanceAmount()==30&&p.evolvesFromName().equals("Metang"),"Actual Metagross profile");
        check(p.abilities().isEmpty()&&p.attacks().size()==2,"Actual attack/Ability count");var bounce=p.attacks().getFirst();var hammer=p.attacks().getLast();
        check(bounce.id().equals(BOUNCE)&&bounce.baseDamage()==60&&bounce.energyCost().equals(Map.of(EnergyType.METAL,1))&&hammer.id().equals(HAMMER)&&hammer.baseDamage()==150&&hammer.energyCost().equals(Map.of(EnergyType.METAL,3,EnergyType.COLORLESS,1)),"Actual printed attacks/costs");
        p.attacks().forEach(AttackProgramRules::validate);
        var before=hammer.effectProgram().triggers().stream().filter(t->t.trigger()==EffectTrigger.BEFORE_ATTACK).findFirst().orElseThrow().effects().getFirst();
        check(before.type().equals("choice")&&before.arguments().get("optional").equals("true")&&before.effects().getFirst().type().equals("opt_in"),"Hammer must choose intent before damage");
        var after=hammer.effectProgram().triggers().stream().filter(t->t.trigger()==EffectTrigger.AFTER_ATTACK).findFirst().orElseThrow().effects().getFirst().effects().getFirst();
        check(after.type().equals("discard_energy")&&after.target()==EffectTarget.SELF&&after.arguments().equals(Map.of("energy_type","METAL","count_mode","UNITS","amount","3","minimum","3")),"Hammer must discard typed units after damage, not pay Basic-only activation cost");
        for(String stage:List.of(STAGE1,ALT)){var m=game(stage,"NORMAL");check(m.activePokemonView(A).evolutionCardIds().equals(List.of(BASIC,stage,CARD)),"Actual Stage2 must accept both same-name Metang prints");}
    }
    static void normalPaymentAndChoice(){
        for(boolean yes:List.of(false,true))for(String mode:List.of("NORMAL","WEAK","RESIST")){
            var m=game(STAGE1,mode);atomic(m,()->m.attack(A,HAMMER));attach(m,"wrong");var cards=metals(m,2);atomic(m,()->m.attack(A,HAMMER));var selected=new ArrayList<>(cards);selected.add(attach(m,"metal"));
            var rng=m.randomTranscript();m.attack(A,HAMMER);check(m.activePokemonView(B).damage()==0&&m.activePokemonView(A).attachedEnergyIds().size()==4,"Optional intent must precede damage and discard");choose(m,yes);
            int raw=yes?300:150,damage=mode.equals("WEAK")?raw*2:mode.equals("RESIST")?raw-30:raw;
            if(yes){check(m.activePokemonView(B).damage()==damage&&m.currentPlayer().equals(A)&&m.activePokemonView(A).attachedEnergyIds().containsAll(selected.stream().map(CardInstance::instanceId).toList()),"Damage must precede self Energy discard");discard(m,selected);}
            check(m.lastAttack().finalDamage()==damage&&m.currentPlayer().equals(B)&&m.activePokemonView(A).attachedEnergyIds().size()==(yes?1:4)&&m.randomTranscript().equals(rng),"Optional printed damage/payment/W/R/randomness");
        }
        var insufficient=game();metals(insufficient,3);atomic(insufficient,()->insufficient.attack(A,HAMMER));check(!insufficient.hasPendingEffectChoice(),"Missing fourth unit reached optional choice");
    }
    static void copiedUnitCounts(){
        // Actual Slowking Seek Inspiration is unsupported; this explicit C-cost copier borrows the real unmodified attack.
        for(int n:List.of(0,1,2,3,4))for(boolean yes:List.of(false,true)){
            var m=game();m.switchActiveByEffect(A,A,baseBench(m,A));attach(m,"wrong");var attached=metals(m,n);var untouched=new ArrayList<CardInstance>();for(int i=0;i<3;i++)untouched.add(attach(m,B,donor(m),"metal"));
            var rng=m.randomTranscript();beginCopy(m);check(m.activePokemonView(B).damage()==0,"Copy skipped optional intent");choose(m,yes);
            var expected=attached.subList(0,Math.min(n,3));if(yes&&n>0){var q=m.effectCardPrompt(A);check(new HashSet<>(q.candidates()).equals(new HashSet<>(attached))&&m.activePokemonView(B).damage()==300,"Copy did not select actual attacker Metal after damage");
                if(n>=2)atomic(m,()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(attached.getFirst().instanceId())));discard(m,expected);}
            check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B)&&m.lastAttack().finalDamage()==(yes?300:150)&&m.activePokemonView(B).damage()==(yes?300:150),"Official zero/partial Metal opt-in bonus or completion failed: "+n+"/"+yes);
            check(m.activePokemonView(A).attachedEnergyIds().size()==1+n-(yes?Math.min(n,3):0)&&m.privateView(A).discard().containsAll(yes?expected:List.of())&&m.benchPokemonView(B).stream().filter(v->v.pokemonId().equals(donor(m))).findFirst().orElseThrow().attachedEnergyIds().containsAll(untouched.stream().map(CardInstance::instanceId).toList())&&m.randomTranscript().equals(rng),"Copied effect discarded donor/wrong-controller Energy or wrong units");
        }
    }
    static void selectionAuthority(){
        var m=game();var attached=metals(m,4);var wrong=attach(m,"wrong");var elsewhere=attach(m,A,baseBench(m,A),"metal");var handOnly=hand(m,A,"metal");var foreign=attach(m,B,donor(m),"metal");
        m.attack(A,HAMMER);var choice=m.effectChoicePrompt(A);atomic(m,()->m.chooseEffectOption(B,choice.resolutionId(),choice.step(),0));atomic(m,()->m.chooseEffectOption(WATCH,choice.resolutionId(),choice.step(),0));atomic(m,()->m.chooseEffectOption(A,choice.resolutionId(),choice.step(),2));choose(m,true);
        var q=m.effectCardPrompt(A);check(q.minimum()==1&&q.maximum()==3&&new HashSet<>(q.candidates()).equals(new HashSet<>(attached)),"Public physical-card range/type/source incorrect");
        atomic(m,()->m.submitEffectCards(B,q.resolutionId(),q.step(),attached.subList(0,3).stream().map(CardInstance::instanceId).toList()));atomic(m,()->m.submitEffectCards(WATCH,q.resolutionId(),q.step(),List.of(attached.getFirst().instanceId())));atomic(m,()->m.submitEffectCards(A,q.resolutionId(),q.step()+1,List.of(attached.getFirst().instanceId())));
        for(var bad:List.of(wrong,elsewhere,handOnly,foreign))atomic(m,()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(attached.get(0).instanceId(),attached.get(1).instanceId(),bad.instanceId())));
        for(var bad:List.of(List.<UUID>of(),List.of(attached.get(0).instanceId()),List.of(attached.get(0).instanceId(),attached.get(1).instanceId()),List.of(attached.get(0).instanceId(),attached.get(0).instanceId(),attached.get(1).instanceId()),attached.stream().map(CardInstance::instanceId).toList()))atomic(m,()->m.submitEffectCards(A,q.resolutionId(),q.step(),bad));
        atomic(m,()->m.chooseEffectOption(A,choice.resolutionId(),choice.step(),1));privacy(m);discard(m,attached.subList(1,4));
        check(m.activePokemonView(A).attachedEnergyIds().contains(attached.getFirst().instanceId())&&m.privateView(A).discard().containsAll(attached.subList(1,4))&&m.lastAttack().finalDamage()==300,"Server selection did not preserve chosen physical identities");
        atomic(m,()->m.submitEffectCards(A,q.resolutionId(),q.step(),attached.subList(1,4).stream().map(CardInstance::instanceId).toList()));
        var copy=game();copy.switchActiveByEffect(A,A,baseBench(copy,A));attach(copy,"wrong");var o=copy.copyAttackOptions(A,"copy").stream().filter(v->v.attackId().equals(HAMMER)).findFirst().orElseThrow();
        atomic(copy,()->copy.copyAttackWithEnergyDiscards(A,"copy",o.pokemonId(),o.entrySequence()+1,o.attackId(),List.of(),List.of()));atomic(copy,()->copy.copyAttackWithEnergyDiscards(B,"copy",o.pokemonId(),o.entrySequence(),o.attackId(),List.of(),List.of()));
    }
    static void privacy(ServerMatch m){var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);var rng=m.randomTranscript();
        for(UUID viewer:List.of(A,B,WATCH)){String json=new Gson().toJson(session.view(viewer,viewer.equals(WATCH)?null:viewer));check(!json.contains("effectProgram")&&!json.contains("effectiveAttack"),"Executable attack program exposed");for(UUID owner:List.of(A,B)){for(var card:m.privateView(owner).deck())check(!json.contains(card.instanceId().toString()),"Private deck identity leaked through public Energy choice");if(!viewer.equals(owner))for(var card:m.privateView(owner).hand())check(!json.contains(card.instanceId().toString()),"Private hand identity leaked through public Energy choice");}}
        check(m.randomTranscript().equals(rng),"Refreshing issued selection consumed RNG");
    }
    static void specialEnergyUnits(){
        for(boolean twoDoubles:List.of(false,true)){var m=game();var first=attach(m,"double");var second=attach(m,twoDoubles?"double":"metal");if(!twoDoubles)attach(m,"wrong");
            m.attack(A,HAMMER);choose(m,true);var q=m.effectCardPrompt(A);check(q.candidates().containsAll(List.of(first,second)),"Synthetic multiunit Metal excluded");atomic(m,()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(first.instanceId())));discard(m,List.of(first,second));
            check(m.privateView(A).discard().containsAll(List.of(first,second))&&m.activePokemonView(A).attachedEnergyIds().size()==(twoDoubles?0:1)&&m.lastAttack().finalDamage()==300,"Whole-card 2+1 or2+2unit discard split a physical Energy");}
    }
    static void reversalEnergy(){
        check(DB.find(REVERSAL).orElseThrow().effectSupport()==EffectSupport.SUPPORTED&&!DB.find(REVERSAL).orElseThrow().basicEnergy(),"Actual Reversal Energy support");
        var m=game();m.knockOutPokemonByEffect(A,A,baseBench(m,A));settle(m);check(m.privateView(A).prizes().size()==6&&m.privateView(B).prizes().size()==5,"Real prize deficit setup");var reversal=attach(m,REVERSAL);attach(m,"wrong");check(metalCount(m,active(m,A),3),"Actual evolved non-rule-box Reversal must provide3Metal units");
        m.attack(A,HAMMER);choose(m,true);var q=m.effectCardPrompt(A);check(q.candidates().equals(List.of(reversal))&&q.minimum()==1&&q.maximum()==1&&m.activePokemonView(B).damage()==300,"One actual Reversal card must pay3 units after damage");discard(m,List.of(reversal));check(m.privateView(A).discard().contains(reversal)&&m.activePokemonView(A).attachedEnergyIds().size()==1,"Actual Reversal not discarded whole");
        var inactive=game();var kept=attach(inactive,REVERSAL);check(metalCount(inactive,active(inactive,A),0),"Tied-prize Reversal wrongly provided Metal");atomic(inactive,()->inactive.attack(A,HAMMER));
        inactive.knockOutPokemonByEffect(A,A,baseBench(inactive,A));settle(inactive);check(metalCount(inactive,active(inactive,A),3),"Already-attached actual Reversal did not update after prize deficit");inactive.knockOutPokemonByEffect(A,B,baseBench(inactive,B));settle(inactive);check(metalCount(inactive,active(inactive,A),0),"Already-attached actual Reversal cached old prize condition");
        var basics=metals(inactive,3);inactive.attack(A,HAMMER);choose(inactive,true);check(!inactive.effectCardPrompt(A).candidates().contains(kept),"Inactive Reversal was selected by printed special identity instead of live Metal provision");discard(inactive,basics);check(inactive.activePokemonView(A).attachedEnergyIds().equals(List.of(kept.instanceId()))&&inactive.lastAttack().finalDamage()==300,"Colorless fallback must payfourth unit and remain after Metal discard");
    }
    static void damageAndKnockoutOrder(){
        for(String scope:List.of("DAMAGE_ONLY","EFFECTS_ONLY")){var m=game();var cards=pay(m);shield(m,scope);m.attack(A,HAMMER);choose(m,true);check(m.activePokemonView(B).damage()==(scope.equals("DAMAGE_ONLY")?0:300)&&m.hasPendingCardSelection(),"Defender immunity improperly blocked optional bonus/self-discard");discard(m,cards);check(m.privateView(A).discard().containsAll(cards),"Defender shield refunded own Energy");}
        var lethal=game();var cards=pay(lethal);UUID old=active(lethal,B);lethal.placeDamageCountersByEffect(A,B,old,60);lethal.placeDamageCountersByEffect(A,B,old,10);lethal.attack(A,HAMMER);choose(lethal,true);
        check(lethal.activePokemonView(B).pokemonId().equals(old)&&lethal.activePokemonView(B).damage()==1000&&lethal.hasPendingCardSelection()&&lethal.privateView(A).prizes().size()==6&&lethal.activePokemonView(A).attachedEnergyIds().containsAll(cards.stream().map(CardInstance::instanceId).toList()),"Lethal damage must wait for printed discard before KO/prizes");discard(lethal,cards);check(lethal.phase()==MatchPhase.PRIZE_SELECTION&&lethal.privateView(A).discard().containsAll(cards),"Self-discard did not complete before KO");settle(lethal);check(lethal.currentPlayer().equals(B)&&lethal.privateView(A).prizes().size()==5,"Attack KO turn/prize flow");
    }
    static void boundReset(){var m=game();var first=pay(m);m.attack(A,HAMMER);choose(m,true);discard(m,first);m.endTurn(B);metals(m,3);var attached=m.activePokemonView(A).attachedEnergyIds();m.attack(A,HAMMER);choose(m,false);check(m.lastAttack().finalDamage()==150&&m.activePokemonView(B).damage()==450&&m.activePokemonView(A).attachedEnergyIds().equals(attached),"Opt-in bound leaked across completed attacks");}
    static void bounceBack(){
        var m=game();atomic(m,()->m.attack(A,BOUNCE));attach(m,"wrong");atomic(m,()->m.attack(A,BOUNCE));attach(m,"metal");UUID old=active(m,B);m.applySpecialConditionByEffect(A,B,SpecialCondition.POISONED);m.attack(A,BOUNCE);
        check(m.activePokemonView(B).damage()==60&&m.activePokemonView(B).pokemonId().equals(old)&&m.pendingChoicePlayer().equals(B)&&m.hasPendingPokemonSelection(),"Bounce Back must deal60 before opponent chooses replacement");var q=m.effectPokemonPrompt(B);check(q.targets().size()==2&&q.targets().stream().noneMatch(v->v.pokemonId().equals(old)),"Forced switch offered wrong field candidates");UUID replacement=donor(m);atomic(m,()->m.chooseEffectPokemon(A,q.resolutionId(),q.step(),replacement));atomic(m,()->m.chooseEffectPokemon(WATCH,q.resolutionId(),q.step(),replacement));atomic(m,()->m.chooseEffectPokemon(B,q.resolutionId(),q.step(),old));atomic(m,()->m.chooseEffectPokemon(B,q.resolutionId(),q.step()+1,replacement));
        m.chooseEffectPokemon(B,q.resolutionId(),q.step(),replacement);check(active(m,B).equals(replacement)&&m.activePokemonView(B).damage()==0&&m.benchPokemonView(B).stream().anyMatch(v->v.pokemonId().equals(old)&&v.damage()==60&&v.conditions().isEmpty())&&m.currentPlayer().equals(B),"Bounce Back retained old Active/status or damaged replacement");atomic(m,()->m.chooseEffectPokemon(B,q.resolutionId(),q.step(),replacement));
        var empty=game();for(var v:List.copyOf(empty.benchPokemonView(B)))empty.movePokemonByEffect(A,B,v.pokemonId(),LeavePlayDestination.HAND);attach(empty,"metal");empty.attack(A,BOUNCE);check(empty.activePokemonView(B).damage()==60&&!empty.hasPendingEffectChoice()&&empty.currentPlayer().equals(B),"NoBench Bounce Back should still deal60 and finish");
        for(String scope:List.of("DAMAGE_ONLY","EFFECTS_ONLY")){var guarded=game();attach(guarded,"metal");UUID previous=active(guarded,B);shield(guarded,scope);guarded.attack(A,BOUNCE);if(scope.equals("DAMAGE_ONLY")){var pick=guarded.effectPokemonPrompt(B);guarded.chooseEffectPokemon(B,pick.resolutionId(),pick.step(),donor(guarded));check(!active(guarded,B).equals(previous)&&guarded.benchPokemonView(B).stream().anyMatch(v->v.pokemonId().equals(previous)&&v.damage()==0),"Damage guard incorrectly blocked switch effect");}else {if(guarded.hasPendingPokemonSelection()){var pick=guarded.effectPokemonPrompt(B);guarded.chooseEffectPokemon(B,pick.resolutionId(),pick.step(),donor(guarded));}check(active(guarded,B).equals(previous)&&guarded.activePokemonView(B).damage()==60&&!guarded.hasPendingEffectChoice()&&guarded.currentPlayer().equals(B),"Effects-only guard failed to block forced switch or blocked damage");}}
    }
    static void protectOwnHost(ServerMatch m,UUID owner,UUID host){
        // Explicit synthetic temporary guard; no unsupported Skeledirge profile is activated.
        var guard=EffectRegistry.standard().parse(JsonParser.parseString(EffectOriginProtectionTest.shield("ATTACK","EFFECTS_ONLY","SELF","LEAVES_PLAY")).getAsJsonObject());
        m.executeEffectProgramByRule(owner,host,new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(guard)))),EffectTrigger.ON_CARD_PLAYED);
    }
    static void switchProtectionTargets(){
        // Advanced Rules v3.4 C-04: force-switch affects only the former Active, never its chosen replacement.
        for(boolean oldProtected:List.of(false,true))for(boolean benchProtected:List.of(false,true)){
            var m=game();UUID old=active(m,B),replacement=donor(m);m.endTurn(A);
            if(oldProtected)protectOwnHost(m,B,old);if(benchProtected)protectOwnHost(m,B,replacement);m.endTurn(B);attach(m,"metal");m.attack(A,BOUNCE);
            if(m.hasPendingPokemonSelection()){
                check(m.pendingChoicePlayer().equals(B),"C-04 replacement choice belongs to affected owner");var q=m.effectPokemonPrompt(B);
                check(q.targets().stream().anyMatch(v->v.pokemonId().equals(replacement)),"C-04 must retain protected Bench as a legal replacement candidate");
                atomic(m,()->m.chooseEffectPokemon(A,q.resolutionId(),q.step(),replacement));m.chooseEffectPokemon(B,q.resolutionId(),q.step(),replacement);
            }
            check(active(m,B).equals(oldProtected?old:replacement)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"C-04 Bounce Back checked wrong affected Pokemon: oldProtected="+oldProtected+", benchProtected="+benchProtected);
            var damaged=oldProtected?m.activePokemonView(B):m.benchPokemonView(B).stream().filter(v->v.pokemonId().equals(old)).findFirst().orElseThrow();
            check(damaged.damage()==60,"C-04 effects-only guards changed printed60 damage");
        }
        // Explicit synthetic gust attack control, not a fake supported card. C-05 affects only the selected Bench.
        for(boolean oldProtected:List.of(false,true))for(boolean benchProtected:List.of(false,true)){
            var attack=BeforeDamageAttackTest.attack("","","{\"type\":\"gust\",\"target\":\"SELECT_OPPONENT_POKEMON\"}");
            var m=BeforeDamageAttackTest.fixture(attack);UUID a=BeforeDamageAttackTest.A,b=BeforeDamageAttackTest.B,old=active(m,b),replacement=m.benchPokemonView(b).getLast().pokemonId();
            m.endTurn(a);if(oldProtected)protectOwnHost(m,b,old);if(benchProtected)protectOwnHost(m,b,replacement);m.endTurn(b);m.attack(a,attack.id());
            check(m.pendingChoicePlayer().equals(a),"C-05 gust choice must belong to effect controller");var q=m.effectPokemonPrompt(a);
            check(q.targets().stream().anyMatch(v->v.pokemonId().equals(replacement)),"C-05 protected Bench may be selected before its protection stops movement");
            BeforeDamageAttackTest.reject(()->m.chooseEffectPokemon(b,q.resolutionId(),q.step(),replacement));m.chooseEffectPokemon(a,q.resolutionId(),q.step(),replacement);
            check(active(m,b).equals(benchProtected?old:replacement)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(b),"C-05 gust checked wrong affected Pokemon: oldProtected="+oldProtected+", benchProtected="+benchProtected);
        }
    }

}
