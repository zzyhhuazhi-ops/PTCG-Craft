import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch99Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm12_en_199","sc_csm2_5c_079_sr","sm6_en_103","sc_csm1cc_190_sr");
    static boolean misty(String id){return IDS.indexOf(id)<2;}
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static AttackDefinition attack(String id,String group){return new AttackDefinition(id,id,Map.of(),40,EnergyType.WATER,EffectSupport.SUPPORTED,null).withUsageLimit(group==null?null:new SharedUsageLimit(group,SharedUsageLimit.Period.GAME));}
    static ServerMatch game(String id,EnergyType type,String name,boolean protectedStadium,String toolId){
        var trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();var stadium=protectedStadium?DynamicPassiveModifierTest.trainer("STADIUM","{\"type\":\"prevent_item_supporter_effects\",\"target\":\"SELF\"}"):DynamicPassiveModifierTest.plainStadium();
        String[] ids={"base","base","base","evolved","energy","energy","energy","fire","special","trainer","trainer","trainer","tool","stadium","surge"};
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        var attacks=List.of(attack("gx",SharedUsageLimit.GX),attack("vstar",SharedUsageLimit.VSTAR),attack("tackle",null),CopyAttackTest.copier());
        for(String key:Arrays.stream(ids).distinct().toList()){
            boolean pokemon=Set.of("base","evolved").contains(key),energy=Set.of("energy","fire","special").contains(key);
            var category=key.equals("trainer")||key.equals("surge")?TrainerCategory.SUPPORTER:key.equals("tool")?TrainerCategory.TOOL:key.equals("stadium")?TrainerCategory.STADIUM:TrainerCategory.NONE;
            defs.add(new CardDefinition(key,key,pokemon?CardCategory.POKEMON:energy?CardCategory.ENERGY:CardCategory.TRAINER,category,"H",Set.of("international"),key.equals("base"),Set.of("energy","fire").contains(key),60,Map.of(),EffectSupport.SUPPORTED,null,Set.of(),pokemon?Set.of(key.equals("evolved")?EnergyType.FIRE:type):Set.of()));
            if(pokemon)profiles.put(key,new PokemonProfile(1000,0,key.equals("evolved")?"base":null,null,0,null,0,1,attacks,List.of(),key.equals("evolved")?EnergyType.FIRE:type,key.equals("evolved")?"Other":name,null));
        }
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.WATER,"fire",EnergyType.FIRE),Map.of("trainer",trainer,"stadium",stadium,"tool",NonPokemonBatch2Test.DATA.trainer(toolId).orElseThrow(),"surge",NonPokemonBatch2Test.DATA.trainer("sm10_en_178").orElseThrow()),Map.of("special",EnergyProfile.fixed(EnergyType.WATER,2)));
        for(int seed=0;seed<500;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("base")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,25);m.drawCardsByEffect(A,B,25);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->List.of("base","evolved","tool","stadium","surge","special","fire").stream().allMatch(k->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(k)))&&m.privateView(p).hand().stream().filter(c->c.definitionId().equals("trainer")).count()>=2&&m.privateView(p).hand().stream().filter(c->c.definitionId().equals("energy")).count()>=4))return m;
        }throw new AssertionError("GX permission fixture unavailable");
    }
    static ServerMatch game(String id){return game(id,EnergyType.WATER,"Zygarde-GX",false,"sm11_en_203");}
    static void settle(ServerMatch m){AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),AiDifficulty.MASTER);}
    static void used(ServerMatch m,UUID p){if(!m.currentPlayer().equals(p))m.endTurn(m.currentPlayer());m.attack(p,"gx");settle(m);m.endTurn(other(p));}
    static void stadium(ServerMatch m,UUID p){m.playTrainer(p,hand(m,p,"stadium").instanceId(),null);}
    static void play(ServerMatch m,UUID p){m.playTrainer(p,hand(m,p,"trainer").instanceId(),null);}
    static void choose(ServerMatch m,UUID p,int option){var q=m.effectChoicePrompt(p);m.chooseEffectOption(p,q.resolutionId(),q.step(),option);}
    static void submit(ServerMatch m,UUID p,List<CardInstance> cards){var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),cards.stream().map(CardInstance::instanceId).toList());}
    static void permission(ServerMatch m,UUID p,String id){if(!misty(id)&&m.clientView(p).stadium()==null)stadium(m,p);play(m,p);if(misty(id))choose(m,p,1);settle(m);}
    static boolean usable(ServerMatch m,UUID p,String id){return m.activeAttacks(p).stream().anyMatch(a->a.id().equals(id));}
    static ServerMatch aiGame(String id){var m=game(id);used(m,A);if(!misty(id))stadium(m,A);return m;}
    public static void main(String[] args){payments();scopes();later();bonnie();repeat();toolAndCopy();guard();ai();schema();System.out.println("NonPokemonBatch99Test passed: EN/CN Misty-Lorelei and Bonnie, exact pre-search optional payment/used marker/repeat gate, live name/type/new entries and Water Memory, protected Stadium, retained GX marker and expiry, copied/Z attacks, actual holder immunity/locks and all AI difficulties");}
    static void payments(){for(String id:IDS.subList(0,2))for(UUID p:List.of(A,B))for(boolean gx:List.of(false,true)){
        var m=game(id);if(gx)used(m,p);else if(p.equals(B))m.endTurn(A);var source=hand(m,p,"trainer");int handCount=m.privateView(p).hand().size();play(m,p);check(m.canChooseEffectOption(p,1)==gx,"Misty payment offered before GX used");
        if(!gx){var before=m.clientView(p);GeneratedRuleBatch8Test.reject(()->choose(m,p,1));check(before.equals(m.clientView(p)),"Invalid GX cost changed hand");choose(m,p,0);settle(m);continue;}
        choose(m,p,1);var cost=m.effectCardPrompt(p);check(cost.minimum()==5&&cost.maximum()==5&&!cost.candidates().contains(source),"Misty cost not exact five OTHER cards");var chosen=cost.candidates().subList(0,5);var before=m.clientView(p);var rng=m.randomTranscript();GeneratedRuleBatch8Test.reject(()->submit(m,p,chosen.subList(0,4)));GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(other(p),cost.resolutionId(),cost.step(),chosen.stream().map(CardInstance::instanceId).toList()));check(before.equals(m.clientView(p))&&rng.equals(m.randomTranscript()),"Forged cost changed state");submit(m,p,chosen);
        var search=m.effectCardPrompt(p);check(search!=null&&search.candidates().stream().allMatch(c->c.definitionId().equals("energy"))&&m.privateView(p).discard().containsAll(chosen)&&m.privateView(p).hand().size()==handCount-6,"Cost occurred after search or wrong Energy filter");submit(m,p,List.of());settle(m);check(usable(m,p,"gx"),"Payment failed to grant GX");GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(p,cost.resolutionId(),cost.step(),chosen.stream().map(CardInstance::instanceId).toList()));
    }
        for(int n=0;n<5;n++){var m=game(IDS.getFirst());used(m,A);NonPokemonBatch90Test.trim(m,A,n+1);play(m,A);check(!m.canChooseEffectOption(A,1),"Insufficient five-card cost was offered");choose(m,A,0);settle(m);}
    }
    static void scopes(){for(String id:IDS)for(UUID p:List.of(A,B))for(boolean matches:List.of(false,true)){
        var m=game(id,matches?EnergyType.WATER:EnergyType.FIRE,matches?"Zygarde-GX":"Other",false,"sm11_en_203");used(m,p);permission(m,p,id);check(SharedUsageLimitTest.marker(m.clientView(p),p).gxUsed()&&usable(m,p,"gx")==matches,"GX permission reset marker or broadened recipients");
        if(matches){m.attack(p,"gx");settle(m);}else{var before=m.clientView(p);GeneratedRuleBatch8Test.reject(()->m.attack(p,"gx"));check(before.equals(m.clientView(p)),"Wrong recipient attacked");m.endTurn(p);}
        m.endTurn(other(p));check(!usable(m,p,"gx"),"GX permission persisted next own turn");
    }}
    static void later(){for(String id:List.of(IDS.getFirst(),IDS.get(2)))for(String change:List.of("bench","new","evolve","expire")){
        var m=game(id);used(m,A);permission(m,A,id);
        if(change.equals("new"))m.playBasicPokemonToBench(A,hand(m,A,"base").instanceId());
        if(change.equals("bench")||change.equals("new"))m.switchActiveByEffect(A,A,m.benchPokemonView(A).getLast().pokemonId());
        if(change.equals("evolve"))m.evolveByEffect(A,active(m,A),hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        if(change.equals("expire")){m.endTurn(A);m.endTurn(B);}
        check(usable(m,A,"gx")==Set.of("bench","new").contains(change),"GX cached old holder/type or survived expiry: "+change);
    }
        var m=game(IDS.getFirst(),EnergyType.COLORLESS,"Silvally-GX",false,"sm9_en_157");used(m,A);permission(m,A,IDS.getFirst());check(!usable(m,A,"gx"),"Non-Water Silvally qualified");m.playTrainer(A,hand(m,A,"tool").instanceId(),active(m,A));check(usable(m,A,"gx"),"Later Water Memory did not qualify");m.disableToolEffectsByEffect(A,A,active(m,A));check(!usable(m,A,"gx"),"Suppressed Water Memory retained GX permission");
    }
    static void bonnie(){for(String id:IDS.subList(2,4))for(boolean protectedStadium:List.of(false,true))for(UUID owner:List.of(A,B)){
        var m=game(id,EnergyType.WATER,"Zygarde-GX",protectedStadium,"sm11_en_203");used(m,A);var before=m.clientView(A);var source=hand(m,A,"trainer");check(!m.canPlayTrainer(A,source.instanceId(),null),"Bonnie played without Stadium");GeneratedRuleBatch8Test.reject(()->play(m,A));check(before.equals(m.clientView(A)),"Bonnie without Stadium consumed card");
        if(owner.equals(B)){m.endTurn(A);stadium(m,B);m.endTurn(B);}else stadium(m,A);var card=m.clientView(A).stadium();play(m,A);settle(m);check((m.clientView(A).stadium()!=null)==protectedStadium&&usable(m,A,"gx"),"Protected Stadium stopped Bonnie permission");if(!protectedStadium)check(m.privateView(owner).discard().contains(card),"Bonnie Stadium discarded to wrong owner");m.attack(A,"gx");settle(m);
    }
        var m=game(IDS.get(2));stadium(m,A);play(m,A);settle(m);m.attack(A,"gx");settle(m);m.endTurn(B);check(!usable(m,A,"gx"),"Bonnie before first GX granted a saved extra use");
    }
    static void repeat(){var m=game(IDS.getFirst());used(m,A);NonPokemonBatch67Test.prize(m,B);m.playTrainer(A,hand(m,A,"surge").instanceId(),null);permission(m,A,IDS.getFirst());play(m,A);check(!m.canChooseEffectOption(A,1)&&m.canChooseEffectOption(A,0),"Second Misty discarded five again or blocked base search");var before=m.clientView(A);GeneratedRuleBatch8Test.reject(()->choose(m,A,1));check(before.equals(m.clientView(A)),"Repeated Misty payment consumed cards");choose(m,A,0);settle(m);}
    static void toolAndCopy(){for(boolean copy:List.of(false,true)){
        var m=game(IDS.getFirst());used(m,A);permission(m,A,IDS.getFirst());if(copy){var c=m.copyAttackOptions(A,"copy").stream().filter(o->o.attackId().equals("gx")).findFirst().orElseThrow();check(c.effectiveAttack()!=null,"Qualified Water copy cannot use GX");m.attachEnergy(A,hand(m,A,"energy").instanceId(),active(m,A));m.copyAttackWithEnergyDiscards(A,"copy",c.pokemonId(),c.entrySequence(),"gx",List.of(),List.of());}
        else {m.playTrainer(A,hand(m,A,"tool").instanceId(),active(m,A));for(int i=0;i<4;i++)m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,hand(m,A,"energy").instanceId(),A,active(m,A));var o=m.borrowedAttackOptions(A).stream().filter(x->x.attackId().equals("barreling-blitz-gx")).findFirst().orElseThrow();check(o.attack()!=null,"Water Z Tool GX not permitted");NonPokemonBatch97Test.use(m,A,o);}
        settle(m);check(SharedUsageLimitTest.marker(m.clientView(A),A).gxUsed(),"Repeated GX lost marker");
    }
        var m=game(IDS.getFirst());m.attack(A,"vstar");m.endTurn(B);used(m,A);permission(m,A,IDS.getFirst());GeneratedRuleBatch8Test.reject(()->m.attack(A,"vstar"));
    }
    static void guard(){for(boolean lock:List.of(false,true)){
        var m=game(IDS.getFirst());used(m,A);if(lock)EventTargetBindingTest.field(m).lockAttackUntilOwnerNextTurnStart(B,A,active(m,A),"gx");
        else {var e=EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"prevent_effects\",\"target\":\"SELF\",\"source\":\"TRAINER\",\"from\":\"ANY\",\"scope\":\"EFFECTS_ONLY\",\"duration\":\"LEAVES_PLAY\"}").getAsJsonObject());EventTargetBindingTest.field(m).addProtection(A,A,active(m,A),ProtectionEffect.from(e));}
        permission(m,A,IDS.getFirst());check(!usable(m,A,"gx"),"GX permission removed existing attack lock or Trainer immunity");GeneratedRuleBatch8Test.reject(()->m.attack(A,"gx"));
    }}
    static void ai(){for(String id:IDS)for(var d:AiDifficulty.values()){
        var m=aiGame(id);play(m,A);if(misty(id))choose(m,A,1);var session=MatchSession.multiplayer(m,A,B);AiCardOperationTest.settle(session,d);check(usable(m,A,"gx"),"AI failed permission cost/search");var v=session.view(A,A);var option=v.options().stream().filter(o->o.enabled()&&o.type()==ActionType.ATTACK&&o.labelZh().contains("gx")).findFirst().orElseThrow();session.submit(A,A,v.revision(),option.index());AiCardOperationTest.settle(session,d);AiCardOperationTest.finish(session,d);check(m.phase()==MatchPhase.FINISHED,"AI extra GX match stalled");
    }}
    static void schema(){for(String arg:List.of("\"pokemon_type\":\"FIRE\"","\"pokemon_name\":\"Other\"","\"pokemon_type\":\"WATER\",\"reset\":true"))GeneratedRuleBatch8Test.reject(()->EffectRegistry.standard().parse(JsonParser.parseString("{\"type\":\"turn_gx_permission\",\"target\":\"SELF\","+arg+"}").getAsJsonObject()));var e=new EffectDefinition(TurnGxPermission.TYPE,EffectTarget.SELF,Map.of("pokemon_type","WATER"),List.of(),List.of());GeneratedRuleBatch8Test.reject(()->ResumableProgramRules.validate(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.AFTER_ATTACK,List.of(),List.of(e)))),true,EffectTrigger.AFTER_ATTACK));}
}
