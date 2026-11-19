import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** All three actual Crobat prints remain unchanged; special setup timing hooks are explicitly synthetic. */
public final class GeneratedRuleBatch92Test {
    static final UUID A=new UUID(0,9211), B=new UUID(0,9212), WATCH=new UUID(0,9213);
    static final String BASIC="me4_en_049", MID="me4_en_050", CARD="me4_en_051";
    static final String ABILITY="nighttime-maneuvers", ATTACK="poison-sound-wave", SECRET="private-secret92";
    static final List<String> PRINTS=List.of(CARD,"me4_en_093","mebsp_en_076");
    static final CardDatabase DB=BundledRules.loadCardDefinitions();
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean value,String why){GeneratedRuleBatch8Test.check(value,why);}
    static void reject(Runnable action){GeneratedRuleBatch8Test.reject(action);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static void atomic(ServerMatch m,Runnable action){
        var a=m.privateView(A);var b=m.privateView(B);var view=m.clientView(A);var rng=m.randomTranscript();var replay=m.replayTranscript();
        reject(action);
        check(a.equals(m.privateView(A))&&b.equals(m.privateView(B))&&view.equals(m.clientView(A))
            &&rng.equals(m.randomTranscript())&&replay.equals(m.replayTranscript()),"Rejected action changed zones, field, private cursor, replay or RNG");
    }
    static CardDefinition synthetic(String id,CardCategory category,TrainerCategory trainer,boolean basic){
        return new CardDefinition(id,id,category,trainer,"J",Set.of("international"),basic,
            category==CardCategory.ENERGY,60,Map.of(),EffectSupport.SUPPORTED,null);
    }
    static ServerMatch game(){return game(CARD,false,"NORMAL");}
    static ServerMatch game(String card,boolean copied,String mode){
        UUID owner=copied?B:A;var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String id:List.of(BASIC,MID,card)){defs.add(DB.find(id).orElseThrow());profiles.put(id,DATA.pokemon(id).orElseThrow());}
        defs.add(synthetic("base",CardCategory.POKEMON,TrainerCategory.NONE,true));
        for(String id:List.of("dark","wrong"))defs.add(synthetic(id,CardCategory.ENERGY,TrainerCategory.NONE,false));
        for(String id:List.of("item","tool",SECRET))defs.add(synthetic(id,CardCategory.TRAINER,id.equals("tool")?TrainerCategory.TOOL:TrainerCategory.ITEM,false));
        profiles.put("base",new PokemonProfile(1000,1,null,mode.equals("WEAK")?EnergyType.DARKNESS:null,
            mode.equals("WEAK")?2:0,mode.equals("RESIST")?EnergyType.DARKNESS:null,mode.equals("RESIST")?30:0,
            1,List.of(GeneratedRuleBatch8Test.hit(10,false),CopyAttackTest.copier()),List.of(),EnergyType.COLORLESS));
        var trainers=Map.of("item",ComposedTrainerIntegrationTest.item("{\"type\":\"draw\",\"amount\":1}"),
            "tool",DynamicPassiveModifierTest.plainTool(),SECRET,ComposedTrainerIntegrationTest.item("{\"type\":\"draw\",\"amount\":1}"));
        var catalog=new CombatCatalog(profiles,Map.of("dark",EnergyType.DARKNESS,"wrong",EnergyType.WATER),trainers);
        String[] pool={BASIC,BASIC,BASIC,BASIC,MID,MID,card,card,"base","base","base","base","dark","dark","wrong","wrong","item","tool"};
        for(int seed=0;seed<4000;seed++){
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)
                cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,i==59?(p.equals(owner)?SECRET:"item"):pool[i%pool.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(m.privateView(owner).hand().stream().filter(c->c.definitionId().equals(BASIC)).count()<2
                ||m.privateView(owner).hand().stream().noneMatch(c->c.definitionId().equals("base"))
                ||m.privateView(copied?A:B).hand().stream().filter(c->c.definitionId().equals("base")).count()<2)continue;
            for(UUID p:List.of(A,B)){
                m.chooseOpeningActive(p,hand(m,p,p.equals(owner)?BASIC:"base").instanceId());
                if(p.equals(owner))m.placeOpeningBench(p,hand(m,p,BASIC).instanceId());
                m.placeOpeningBench(p,hand(m,p,"base").instanceId());m.startAfterOpening(p);
            }
            OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,24);m.drawCardsByEffect(A,B,24);
            if(!mode.equals("FIRST")){m.endTurn(A);m.endTurn(B);}
            if(List.of(MID,card).stream().anyMatch(id->m.privateView(owner).hand().stream().filter(c->c.definitionId().equals(id)).count()<2)
                ||List.of("dark","wrong").stream().anyMatch(id->m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals(id))))continue;
            if(copied)m.endTurn(A);
            UUID back=m.benchPokemonView(owner).stream().filter(p->p.topCardId().equals(BASIC)).findFirst().orElseThrow().pokemonId();
            if(mode.equals("FIRST")){
                // Explicit synthetic timing permission tests absence of an extra first-turn Ability ban.
                for(UUID host:List.of(active(m,owner),back)){
                    m.evolveByEffect(owner,host,hand(m,owner,MID).instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
                    m.evolveByEffect(owner,host,hand(m,owner,card).instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
                }
            }else{
                m.evolve(owner,active(m,owner),hand(m,owner,MID).instanceId());m.evolve(owner,back,hand(m,owner,MID).instanceId());
                m.endTurn(owner);m.endTurn(copied?A:B);
                m.evolve(owner,active(m,owner),hand(m,owner,card).instanceId());m.evolve(owner,back,hand(m,owner,card).instanceId());
            }
            if(copied)m.endTurn(B);
            if(List.of(BASIC,"dark","item",SECRET).stream().anyMatch(id->m.privateView(owner).deck().stream().noneMatch(c->c.definitionId().equals(id))))continue;
            return m;
        }
        throw new AssertionError("No actual Crobat fixture "+card+"/"+copied+"/"+mode);
    }
    static void nextOwn(ServerMatch m){m.endTurn(A);m.endTurn(B);}
    static void attach(ServerMatch m,String id){m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(m,A,id).instanceId(),A,active(m,A));}
    static CardInstance choose(ServerMatch m,UUID host){
        m.useAbility(A,host,ABILITY);var prompt=m.effectCardPrompt(A);var card=prompt.candidates().getFirst();
        m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),List.of(card.instanceId()));return card;
    }
    static void privacy(ServerMatch m){
        var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);var rng=m.randomTranscript();
        for(UUID viewer:List.of(A,B,WATCH)){
            String json=new Gson().toJson(session.view(viewer,viewer.equals(WATCH)?null:viewer));
            for(UUID owner:List.of(A,B)){
                if(!viewer.equals(owner))for(var c:m.privateView(owner).hand())check(!json.contains(c.instanceId().toString()),"Foreign private hand leaked");
                for(var c:m.privateView(owner).deck()){
                    var issued=viewer.equals(owner)?m.clientView(viewer).effectSelection():null;
                    if(issued==null||!issued.candidates().contains(c))check(!json.contains(c.instanceId().toString()),"Deck identity leaked beyond owner-issued candidates");
                }
            }
        }
        check(m.randomTranscript().equals(rng),"Refreshing private/foreign/spectator views consumed RNG");
    }
    static void hiddenChoice(ServerMatch m,CardInstance chosen){
        check(m.effectCardReveal()==null,"Private search created a public reveal");
        for(UUID viewer:Arrays.asList(B,null)){
            String json=new Gson().toJson(m.clientView(viewer));
            check(!json.contains(chosen.instanceId().toString()),"Selected private physical card leaked");
            if(chosen.definitionId().equals(SECRET))check(!json.contains(SECRET),"Selected private card face leaked");
        }
    }
    public static void main(String[] args){
        printedAndNormalEvolution();privateAnyCardChoices();selectionAuthority();smallDecks();activeUsageAndSuppression();
        sourceLifetime();actualAttack();copiedAttack();
        System.out.println("GeneratedRuleBatch92Test passed: all3 actual Crobat named two-stage evolutions, private mandatory any-card top search, hidden exact physical choices/authority, remainder-only shuffle and next draw, singleton/empty decks, active independent usage/suppression/source lifetime, D80 and Confused+Poisoned/checkup/protection/copy");
    }
    static void printedAndNormalEvolution(){
        for(String id:PRINTS){
            var d=DB.find(id).orElseThrow();var p=DATA.pokemon(id).orElseThrow();
            check(d.effectSupport()==EffectSupport.SUPPORTED&&!d.basicPokemon()&&p.hitPoints()==130&&p.pokemonType()==EnergyType.DARKNESS
                &&p.weaknessType()==EnergyType.LIGHTNING&&p.weaknessMultiplier()==2&&p.resistanceType()==EnergyType.FIGHTING
                &&p.resistanceAmount()==30&&p.retreatCost()==1&&p.prizeCards()==1&&p.evolvesFromName().equals("Golbat"),"Actual Crobat profile "+id);
            var ability=p.abilities().getFirst();check(p.abilities().size()==1&&ability.id().equals(ABILITY)
                &&ability.kind()==AbilityDefinition.Kind.ACTIVATED_ONCE_PER_TURN,"Actual Crobat activated usage "+id);
            var binding=ability.effectProgram().triggers().getFirst();
            check(binding.trigger()==EffectTrigger.ONCE_PER_TURN&&binding.conditions().size()==1
                &&binding.conditions().getFirst().type()==ConditionType.IS_ACTIVE,"Actual root Active-only condition "+id);
            var effect=binding.effects().getFirst();check(effect.type().equals("search_deck")&&effect.target()==EffectTarget.DECK
                &&effect.arguments().equals(Map.of("amount","1","minimum","1","destination","DECK_TOP_AFTER_SHUFFLE","reveal","false")),"Actual private exact1 schema "+id);
            var attack=p.attacks().getFirst();check(p.attacks().size()==1&&attack.id().equals(ATTACK)&&attack.baseDamage()==80
                &&attack.energyCost().equals(Map.of(EnergyType.DARKNESS,1)),"Actual Poison Sound Wave "+id);AttackProgramRules.validate(attack);
            var m=game(id,false,"NORMAL");check(m.activePokemonView(A).evolutionCardIds().equals(List.of(BASIC,MID,id))
                &&m.benchPokemonView(A).stream().anyMatch(v->v.evolutionCardIds().equals(List.of(BASIC,MID,id))),"Actual normal named evolution including promo "+id);
        }
    }
    static void privateAnyCardChoices(){
        for(String id:PRINTS)for(String selectedId:List.of(BASIC,"dark","item",SECRET)){
            var m=game(id,false,"NORMAL");UUID source=active(m,A);var before=m.privateView(A);var other=m.privateView(B);
            int rng=m.randomTranscript().size(),replay=m.replayTranscript().size();m.useAbility(A,source,ABILITY);
            var q=m.effectCardPrompt(A);check(q.minimum()==1&&q.maximum()==1&&q.selected().isEmpty()
                &&new HashSet<>(q.candidates()).equals(new HashSet<>(before.deck()))&&q.candidates().size()==before.deck().size(),"Private full-deck physical choices must be any category exactly1");
            check(!q.labelZh().contains("展示")&&!q.labelZh().contains("可以不选"),"Private required selection narration claims reveal or optional0");
            check(m.privateView(A).equals(before)&&m.privateView(B).equals(other)&&m.randomTranscript().size()==rng,"Ability declaration moved/shuffled cards before choice");privacy(m);
            var chosen=q.candidates().stream().filter(c->c.definitionId().equals(selectedId)).findFirst().orElseThrow();hiddenChoice(m,chosen);
            m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(chosen.instanceId()));
            var deck=m.privateView(A).deck();check(deck.size()==before.deck().size()&&new HashSet<>(deck).equals(new HashSet<>(before.deck()))
                &&deck.getLast().equals(chosen)&&m.privateView(A).hand().equals(before.hand())&&m.privateView(B).equals(other)
                &&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A),"Private search moved card to hand, changed zones or failed top placement");
            var random=m.randomTranscript().subList(rng,m.randomTranscript().size());
            check(random.size()==Math.max(0,deck.size()-2)&&random.stream().allMatch(e->e.operation().equals("effect-search-to-top:"+A)),"Must shuffle only N-1 remainder once");
            for(int i=0;i<random.size();i++)check(random.get(i).bound()==deck.size()-1-i,"Selected card was included in remainder shuffle");
            for(var event:m.replayTranscript().subList(replay,m.replayTranscript().size()))check(!event.operation().contains(chosen.instanceId().toString())
                &&!event.operation().contains(SECRET)&&!event.operation().contains("展示"),"Private choice/reveal claim leaked into public replay");
            hiddenChoice(m,chosen);privacy(m);check(!m.canUseAbility(A,source,ABILITY),"Successful source use not consumed");
            m.drawCardsByEffect(A,A,1);check(m.privateView(A).hand().contains(chosen)&&!m.privateView(A).deck().contains(chosen),"Chosen physical card is not next drawn");privacy(m);
        }
    }
    static void selectionAuthority(){
        var m=game();UUID source=active(m,A);m.useAbility(A,source,ABILITY);var q=m.effectCardPrompt(A);
        var first=q.candidates().getFirst();var second=q.candidates().get(1);
        for(var ids:List.of(List.<UUID>of(),List.of(first.instanceId(),first.instanceId()),List.of(first.instanceId(),second.instanceId()),
            List.of(m.privateView(A).hand().getFirst().instanceId()),List.of(m.privateView(B).deck().getFirst().instanceId())))
            atomic(m,()->m.submitEffectCards(A,q.resolutionId(),q.step(),ids));
        atomic(m,()->m.submitEffectCards(B,q.resolutionId(),q.step(),List.of(first.instanceId())));
        atomic(m,()->m.submitEffectCards(WATCH,q.resolutionId(),q.step(),List.of(first.instanceId())));
        atomic(m,()->m.submitEffectCards(A,new UUID(92,1),q.step(),List.of(first.instanceId())));
        atomic(m,()->m.submitEffectCards(A,q.resolutionId(),q.step()+1,List.of(first.instanceId())));
        atomic(m,()->m.useAbility(A,source,ABILITY));check(m.effectCardPrompt(A).equals(q),"Invalid request changed issued private cursor");
        m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(first.instanceId()));
        atomic(m,()->m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(first.instanceId())));
        check(!m.canUseAbility(A,source,ABILITY),"Malformed submissions bypassed per-turn source use");
    }
    static void smallDecks(){
        for(int n:List.of(0,1,2,3)){
            var m=game();UUID source=active(m,A);m.discardTopDeckCardsByEffect(A,A,m.privateView(A).deck().size()-n);
            if(n==0){check(!m.canUseAbility(A,source,ABILITY),"Empty deck offered no-effect private search");atomic(m,()->m.useAbility(A,source,ABILITY));continue;}
            check(m.canUseAbility(A,source,ABILITY),"Nonempty/singleton deck cannot search");int rng=m.randomTranscript().size();var chosen=choose(m,source);
            check(m.privateView(A).deck().size()==n&&m.privateView(A).deck().getLast().equals(chosen)
                &&m.randomTranscript().size()==rng+Math.max(0,n-2)&&m.phase()==MatchPhase.TURN,"Short deck private shuffle/top result");
            if(n==1){m.drawCardsByEffect(A,A,1);check(m.privateView(A).deck().isEmpty()&&m.phase()==MatchPhase.TURN,"Singleton effect draw caused immediate deckout");
                m.endTurn(A);check(m.phase()==MatchPhase.TURN,"Empty own deck lost during opposing turn");m.endTurn(B);
                check(m.phase()==MatchPhase.FINISHED&&m.clientView(A).result().reason()==MatchResult.Reason.DECK_OUT,"Empty deck failed next mandatory draw loss");}
        }
    }
    static void activeUsageAndSuppression(){
        var m=game();UUID first=active(m,A),second=m.benchPokemonView(A).stream().filter(v->v.topCardId().equals(CARD)).findFirst().orElseThrow().pokemonId();
        check(!m.canUseAbility(A,second,ABILITY),"Benched Crobat offered Active-only Ability");atomic(m,()->m.useAbility(A,second,ABILITY));
        choose(m,first);m.switchActiveByEffect(A,A,second);check(m.canUseAbility(A,second,ABILITY),"Benched denied attempt consumed holder use or first holder imposed shared quota");
        choose(m,second);m.switchActiveByEffect(A,A,first);check(!m.canUseAbility(A,first,ABILITY),"Switch out/back reset used source");nextOwn(m);
        check(m.canUseAbility(A,first,ABILITY),"Source use failed next owner turn reset");
        var disabled=game();UUID source=active(disabled,A);disabled.disableAbilityEffectsByEffect(A,A,source);
        check(!disabled.canUseAbility(A,source,ABILITY),"Suppressed Crobat may activate");atomic(disabled,()->disabled.useAbility(A,source,ABILITY));
        nextOwn(disabled);check(disabled.canUseAbility(A,source,ABILITY),"Temporary suppression failed expiry or consumed use");choose(disabled,source);
        var firstTurn=game(CARD,false,"FIRST");check(firstTurn.turnNumber()==1&&firstTurn.canUseAbility(A,active(firstTurn,A),ABILITY),"Synthetic legal-timing setup exposed invented Crobat first-turn ban");choose(firstTurn,active(firstTurn,A));
    }
    static void sourceLifetime(){
        var m=game();UUID source=active(m,A);choose(m,source);m.devolvePokemonToHandByEffect(A,A,source);
        m.evolveByEffect(A,source,hand(m,A,CARD).instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        check(!m.canUseAbility(A,source,ABILITY),"Same-entry devolution/re-evolution reset named usage (existing compatibility semantics)");
        UUID physicalBase=PokemonIdentityTestSupport.physical(m,A,source);UUID physicalTop=EventTargetBindingTest.field(m).topCard(A,source).instanceId();
        UUID replacement=m.benchPokemonView(A).stream().filter(v->v.topCardId().equals(CARD)).findFirst().orElseThrow().pokemonId();
        m.switchActiveByEffect(A,A,replacement);m.movePokemonByEffect(A,A,source,LeavePlayDestination.HAND);
        m.playBasicPokemonToBench(A,physicalBase);UUID fresh=PokemonIdentityTestSupport.logical(m,A,physicalBase);check(!fresh.equals(source),"Physical return/reentry reused logical stay");
        // Explicit synthetic evolution timing hook; no unsupported bounce/evolution Trainer is activated.
        m.evolveByEffect(A,fresh,hand(m,A,MID).instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        m.evolveByEffect(A,fresh,physicalTop,EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.switchActiveByEffect(A,A,fresh);
        check(m.canUseAbility(A,fresh,ABILITY),"New stay inherited departed Crobat turn usage");choose(m,fresh);
    }
    static void shield(ServerMatch m,String scope){
        var e=EffectRegistry.standard().parse(JsonParser.parseString(EffectOriginProtectionTest.shield("ATTACK",scope,"OPPONENT_ACTIVE","LEAVES_PLAY")).getAsJsonObject());
        m.executeEffectProgramByRule(A,active(m,A),new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(e)))),EffectTrigger.ON_CARD_PLAYED);
    }
    static void actualAttack(){
        for(String id:PRINTS)for(String mode:List.of("NORMAL","WEAK","RESIST","DAMAGE_ONLY","EFFECTS_ONLY")){
            var m=game(id,false,mode);atomic(m,()->m.attack(A,ATTACK));attach(m,"wrong");atomic(m,()->m.attack(A,ATTACK));attach(m,"dark");
            if(mode.endsWith("_ONLY"))shield(m,mode);var bench=m.benchPokemonView(B);var rng=m.randomTranscript();m.attack(A,ATTACK);
            int damage=mode.equals("WEAK")?160:mode.equals("RESIST")?50:mode.equals("DAMAGE_ONLY")?0:80;
            boolean status=!mode.equals("EFFECTS_ONLY");check(m.lastAttack().finalDamage()==damage&&m.activePokemonView(B).damage()==damage+(status?10:0)
                &&m.activePokemonView(B).conditions().contains(SpecialCondition.CONFUSED)==status
                &&m.activePokemonView(B).conditions().contains(SpecialCondition.POISONED)==status
                &&m.benchPokemonView(B).equals(bench)&&m.currentPlayer().equals(B)&&m.randomTranscript().equals(rng),"Actual D80/status/checkup/guard scope "+id+"/"+mode);
        }
    }
    static void copiedAttack(){
        for(String id:PRINTS){var m=game(id,true,"NORMAL");attach(m,"wrong");
            var option=m.copyAttackOptions(A,"copy").stream().filter(v->v.attackId().equals(ATTACK)).findFirst().orElseThrow();
            m.copyAttackWithEnergyDiscards(A,"copy",option.pokemonId(),option.entrySequence(),option.attackId(),List.of(),List.of());
            check(m.lastAttack().finalDamage()==80&&m.activePokemonView(B).damage()==90&&m.activePokemonView(B).conditions().containsAll(List.of(SpecialCondition.CONFUSED,SpecialCondition.POISONED)),"Copied actual Poison Sound Wave lost status/checkup or controller "+id);
        }
    }
}
