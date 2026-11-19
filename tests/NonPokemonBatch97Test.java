import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch97Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("swsh4_en_155","sm8_en_194","xy5_en_139");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static boolean energy(String id){return id.equals(IDS.get(1));}
    static boolean stadium(String id){return id.equals(IDS.getLast());}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static AttackDefinition hit(String id,int damage,int cost){return BundledCombatData.attack(JsonParser.parseString("{\"id\":\""+id+"\",\"nameZh\":\""+id+"\",\"energyCost\":{\"COLORLESS\":"+cost+"},\"baseDamage\":"+damage+",\"damageType\":\"GRASS\",\"effectSupport\":\"SUPPORTED\"}").getAsJsonObject(),EffectRegistry.standard());}
    static AbilityDefinition memory(){return BundledCombatData.ability(JsonParser.parseString("{\"id\":\"memory\",\"nameZh\":\"Memory dive\",\"kind\":\"PASSIVE\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"borrow_attacks\",\"target\":\"ALL_OWN_POKEMON\",\"source\":\"PREVIOUS_EVOLUTIONS\"}]}").getAsJsonObject(),EffectRegistry.standard());}
    static ServerMatch game(String id){return game(id,hit("same",70,2),List.of());}
    static ServerMatch game(String id,AttackDefinition baseAttack,List<AbilityDefinition> abilities){
        var ids=List.of("base","base","base","middle","middle","final","final","energy","energy","energy","energy","trainer","trainer","special","other");
        var trainer=energy(id)||id.equals("ability")?DynamicPassiveModifierTest.plainTool():NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String key:new LinkedHashSet<>(ids)){
            boolean pokemon=Set.of("base","middle","final").contains(key);defs.add(new CardDefinition(key,key,pokemon?CardCategory.POKEMON:Set.of("energy","special").contains(key)?CardCategory.ENERGY:CardCategory.TRAINER,key.equals("trainer")?trainer.category():key.equals("other")?TrainerCategory.STADIUM:TrainerCategory.NONE,"H",Set.of("international"),key.equals("base"),key.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null,Set.of(),pokemon?Set.of(key.equals("final")?EnergyType.WATER:EnergyType.GRASS):Set.of()));
            if(pokemon)profiles.put(key,new PokemonProfile(500,1,key.equals("middle")?"base":key.equals("final")?"middle":null,EnergyType.WATER,2,null,0,1,List.of(key.equals("base")?baseAttack:key.equals("middle")?hit("same",20,1):hit("native",10,1)),id.equals("ability")&&key.equals("base")?List.of(memory()):abilities,key.equals("final")?EnergyType.WATER:EnergyType.GRASS,"Solrock",null));
        }
        var special=energy(id)?NonPokemonBatch2Test.DATA.energyProfile(id).orElseThrow():EnergyProfile.fixed(EnergyType.COLORLESS,2);
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.COLORLESS),Map.of("trainer",trainer,"other",NonPokemonBatch2Test.DATA.trainer("swsh10_en_155").orElseThrow()),Map.of("special",special));
        for(int seed=0;seed<300;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,ids.get(i%ids.size())));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("base")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,27);m.drawCardsByEffect(A,B,27);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->List.of("middle","final","trainer","special","other").stream().allMatch(k->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(k)))&&m.privateView(p).hand().stream().filter(c->c.definitionId().equals("energy")).count()>=4))return m;
        }throw new AssertionError("Memory fixture unavailable");
    }
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static void evolve(ServerMatch m,UUID p,UUID host,boolean skip){if(!skip)m.evolveByEffect(p,host,hand(m,p,"middle").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.evolveByEffect(p,host,hand(m,p,"final").instanceId(),skip?EvolutionPermission.CARD_EFFECT_SKIP_ONE_STAGE_STRICT_TIMING:EvolutionPermission.CARD_EFFECT_ALL_TIMING);}
    static void grant(ServerMatch m,String id,UUID p,UUID host){if(energy(id))m.attachEnergy(p,hand(m,p,"special").instanceId(),host);else if(!id.equals("ability"))m.playTrainer(p,hand(m,p,"trainer").instanceId(),stadium(id)?null:host);}
    static void pay(ServerMatch m,UUID p,UUID host,int n){for(int i=0;i<n;i++)m.attachEnergyByEffect(p,p,EffectSourceZone.HAND,hand(m,p,"energy").instanceId(),p,host);}
    static ServerMatch.BorrowedAttackOption option(ServerMatch m,UUID p,String provider){return m.borrowedAttackOptions(p).stream().filter(o->o.holderId().equals(active(m,p))&&o.providerCardId().equals(provider)).findFirst().orElseThrow();}
    static void use(ServerMatch m,UUID p,ServerMatch.BorrowedAttackOption o){m.attackBorrowed(p,o.holderId(),o.holderEntry(),o.holderTop(),o.abilityId(),o.providerId(),o.providerEntry(),o.providerTop(),o.attackId(),null,List.of(),List.of(),null,-1,null,null);}
    static void settle(ServerMatch m){AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),AiDifficulty.MASTER);}
    static ServerMatch aiGame(String id){var m=game(id);evolve(m,A,active(m,A),false);pay(m,A,active(m,A),2);return m;}
    public static void main(String[] args){payableAndPhysical();scopes();suppression();stale();selfAndContinuation();ai();schema();System.out.println("NonPokemonBatch97Test passed: three printed memory sources and legacy Ability, physical stack/skip/same-name attack distinction, payable selected costs and evolved holder type, both sides/attachment scopes, printed ownership, Tool/Energy/Stadium suppression and departure, source-entry/stale forgery, actual SELF continuation and all AI difficulties");}
    static void payableAndPhysical(){var sources=new ArrayList<>(IDS);sources.add("ability");for(String id:sources)for(UUID p:List.of(A,B))for(String provider:List.of("base","middle")){
        var m=game(id);if(p.equals(B))m.endTurn(A);UUID host=active(m,p);var base=EventTargetBindingTest.field(m).topCard(p,host);evolve(m,p,host,false);grant(m,id,p,host);var o=option(m,p,provider);check(o.attack()!=null&&o.attack().damageType()==EnergyType.WATER,"Borrowed attack did not use evolved holder type");
        check(o.providerTop().equals(EventTargetBindingTest.field(m).previousEvolutionCards(p,host).stream().filter(c->c.definitionId().equals(provider)).findFirst().orElseThrow().instanceId()),"Provider identity not physical lower card");
        if(provider.equals("base")){check(!m.canPayActiveAttackCost(p,o.attack()),"Memory grant waived selected cost");var before=m.clientView(p);var rng=m.randomTranscript();GeneratedRuleBatch8Test.reject(()->use(m,p,o));check(before.equals(m.clientView(p))&&rng.equals(m.randomTranscript()),"Unpaid declaration mutated state");}
        pay(m,p,host,2);check(m.opponentAttackLockOptions(other(p)).stream().noneMatch(a->a.id().equals("same")),"Granted attacks became possessed printed attacks");use(m,p,o);settle(m);check(m.activePokemonView(other(p)).damage()==(provider.equals("base")?140:40),"Same attack id selected wrong evolution level or type");
    }
        for(String id:IDS){var m=game(id);UUID host=active(m,A);evolve(m,A,host,true);grant(m,id,A,host);check(m.borrowedAttackOptions(A).stream().anyMatch(o->o.providerCardId().equals("base"))&&m.borrowedAttackOptions(A).stream().noneMatch(o->o.providerCardId().equals("middle")),"Skipped evolution supplied invented attack");}
    }
    static void scopes(){for(String id:IDS){var m=game(id);grant(m,id,A,active(m,A));check(m.borrowedAttackOptions(A).isEmpty(),"Unevolved host gained unrelated attacks");evolve(m,A,active(m,A),false);UUID b=m.benchPokemonView(A).getFirst().pokemonId();evolve(m,A,b,false);m.switchActiveByEffect(A,A,b);check(m.borrowedAttackOptions(A).isEmpty()!=stadium(id),"Attached memory affected another Pokemon");m.endTurn(A);evolve(m,B,active(m,B),false);check(m.borrowedAttackOptions(B).isEmpty()!=stadium(id),"Memory source scope crossed wrong player boundary");}}
    static void suppression(){for(String id:IDS){var m=aiGame(id);grant(m,id,A,active(m,A));check(!m.borrowedAttackOptions(A).isEmpty(),"Missing memory before suppression");if(energy(id))m.playTrainer(A,hand(m,A,"other").instanceId(),null);else if(stadium(id))m.discardStadiumByEffect(A);else m.disableToolEffectsByEffect(A,A,active(m,A));check(m.borrowedAttackOptions(A).isEmpty(),"Suppressed/removed memory remains usable: "+id);}
        var moon=NonPokemonBatch2Test.DATA.pokemon("sv3_en_092").orElseThrow().abilities();var shield=game(IDS.getLast(),hit("same",70,2),moon);evolve(shield,A,active(shield,A),false);grant(shield,IDS.getLast(),A,active(shield,A));check(shield.borrowedAttackOptions(A).isEmpty(),"Stadium-immune host borrowed through Shrine");
        for(String id:IDS){var m=aiGame(id);grant(m,id,A,active(m,A));m.disableAbilityEffectsByEffect(A,A,active(m,A));check(!m.borrowedAttackOptions(A).isEmpty(),"Ability suppression disabled non-Ability permission");}
    }
    static void stale(){for(String id:IDS){var m=aiGame(id);grant(m,id,A,active(m,A));var o=option(m,A,"base");var before=m.clientView(A);GeneratedRuleBatch8Test.reject(()->use(m,B,o));GeneratedRuleBatch8Test.reject(()->m.attackBorrowed(A,o.holderId(),o.holderEntry(),o.holderTop(),o.abilityId(),o.providerId(),o.providerEntry(),o.holderTop(),o.attackId(),null,List.of(),List.of(),null,-1,null,null));check(before.equals(m.clientView(A)),"Forged provider mutated match");
        if(energy(id)){var card=EventTargetBindingTest.field(m).attachedEnergy(A,active(m,A)).stream().filter(c->c.definitionId().equals("special")).findFirst().orElseThrow();m.returnAttachedEnergyToHandByEffect(A,A,active(m,A),List.of(card.instanceId()));m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,card.instanceId(),A,active(m,A));}
        else if(stadium(id)){m.discardStadiumByEffect(A);}
        else {var tool=m.activePokemonView(A).attachedTools().getFirst();m.discardToolByEffect(A,A,active(m,A),tool.instanceId());m.attachToolByEffect(A,A,EffectSourceZone.DISCARD,tool.instanceId(),A,active(m,A));}
        GeneratedRuleBatch8Test.reject(()->use(m,A,o));
    }}
    static void selfAndContinuation(){for(String id:IDS){var attack=BeforeDamageAttackTest.attack("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0}","","{\"type\":\"heal\",\"target\":\"SELF\",\"amount\":20}");var m=game(id,attack,List.of());evolve(m,A,active(m,A),false);grant(m,id,A,active(m,A));m.placeDamageCountersByEffect(A,A,active(m,A),3);pay(m,A,active(m,A),2);use(m,A,option(m,A,"base"));check(m.hasPendingEffectChoice()&&m.activePokemonView(B).damage()==0,"Borrowed attack skipped before-damage choice");var q=m.effectCardPrompt(A);m.submitEffectCards(A,q.resolutionId(),q.step(),List.of());settle(m);check(m.activePokemonView(A).damage()==10&&m.activePokemonView(B).damage()==80,"Borrowed body did not retain actual holder SELF/type");}}
    static void ai(){for(String id:IDS)for(var d:AiDifficulty.values()){var m=aiGame(id);grant(m,id,A,active(m,A));var session=MatchSession.multiplayer(m,A,B);var v=session.view(A,A);var offered=v.options().stream().filter(o->o.type()==ActionType.ATTACK&&o.labelZh().contains("借用 base")).findFirst().orElseThrow();session.submit(A,A,v.revision(),offered.index());AiCardOperationTest.settle(session,d);check(m.currentPlayer().equals(B)&&m.activePokemonView(B).damage()==140,"AI borrowed attack failed "+id+" "+d);AiCardOperationTest.finish(session,d);check(m.phase()==MatchPhase.FINISHED,"AI memory match stalled");}}
    static void schema(){for(String id:IDS){var program=energy(id)?NonPokemonBatch2Test.DATA.energyProfile(id).orElseThrow().passiveProgram():NonPokemonBatch2Test.DATA.trainer(id).orElseThrow().ongoingProgram();var e=program.triggers().getFirst().effects().getFirst();GeneratedRuleBatch8Test.reject(()->BorrowedAttackProgram.validate(program,PassiveModifierProgram.Source.ABILITY));if(stadium(id))GeneratedRuleBatch8Test.reject(()->BorrowedAttackProgram.validate(program,PassiveModifierProgram.Source.ENERGY));else GeneratedRuleBatch8Test.reject(()->BorrowedAttackProgram.validate(program,PassiveModifierProgram.Source.STADIUM));GeneratedRuleBatch8Test.reject(()->TrainerProgramRules.validate(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(e))))));}}
}
