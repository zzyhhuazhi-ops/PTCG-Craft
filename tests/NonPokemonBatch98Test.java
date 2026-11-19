import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch98Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm11_en_195","sm11_en_203","sm12_en_190");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String key){return DynamicPassiveModifierTest.hand(m,p,key);}
    static String required(String id){return switch(id){case "sm11_en_195"->"air-slash";case "sm11_en_203"->"tackle";default->"dragon-claw";};}
    static AttackDefinition nativeAttack(String name){return new AttackDefinition(name,name,Map.of(),10,EnergyType.GRASS,EffectSupport.SUPPORTED,null);}
    static ServerMatch game(String id,boolean wrong,boolean weakness){
        var trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();
        var response=BeforeDamageAttackTest.attack("","","{\"type\":\"damage_counter\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":2},{\"type\":\"poison\",\"target\":\"OPPONENT_ACTIVE\"}");
        var attacks=List.of(nativeAttack(wrong?"wrong":required(id)),nativeAttack("native-gx").withUsageLimit(new SharedUsageLimit(SharedUsageLimit.GX,SharedUsageLimit.Period.GAME)),CopyAttackTest.copier(),response);
        String[] ids={"base","base","base","evolved","evolved","energy","energy","energy","energy","special","special","trainer","trainer","stadium"};
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String key:Arrays.stream(ids).distinct().toList()){
            boolean pokemon=Set.of("base","evolved").contains(key),energy=Set.of("energy","special").contains(key);
            defs.add(new CardDefinition(key,key,pokemon?CardCategory.POKEMON:energy?CardCategory.ENERGY:CardCategory.TRAINER,key.equals("trainer")?TrainerCategory.TOOL:key.equals("stadium")?TrainerCategory.STADIUM:TrainerCategory.NONE,"H",Set.of("international"),key.equals("base"),key.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null,Set.of(),pokemon?Set.of(EnergyType.GRASS):Set.of()));
            if(pokemon)profiles.put(key,new PokemonProfile(5000,0,key.equals("evolved")?"base":null,weakness?EnergyType.GRASS:null,weakness?2:0,null,0,1,key.equals("base")?attacks:List.of(nativeAttack("other")),key.equals("evolved")?List.of(NonPokemonBatch97Test.memory()):List.of(),EnergyType.GRASS,key,null));
        }
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.GRASS),Map.of("trainer",trainer,"stadium",NonPokemonBatch2Test.DATA.trainer("swsh3_en_162").orElseThrow()),Map.of("special",EnergyProfile.fixed(EnergyType.COLORLESS,2)));
        for(int seed=0;seed<300;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cs=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cs.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,ids[i%ids.length]));decks.add(cs);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("base")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,26);m.drawCardsByEffect(A,B,26);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->List.of("evolved","trainer","stadium").stream().allMatch(k->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(k)))&&m.privateView(p).hand().stream().filter(c->c.definitionId().equals("special")).count()>=2&&m.privateView(p).hand().stream().filter(c->c.definitionId().equals("energy")).count()>=5))return m;
        }throw new AssertionError("Z Tool fixture unavailable");
    }
    static void pay(ServerMatch m,UUID p,String key,int n){for(int i=0;i<n;i++)m.attachEnergyByEffect(p,p,EffectSourceZone.HAND,hand(m,p,key).instanceId(),p,active(m,p));}
    static void equip(ServerMatch m,UUID p){m.playTrainer(p,hand(m,p,"trainer").instanceId(),active(m,p));}
    static ServerMatch.BorrowedAttackOption option(ServerMatch m,UUID p){return m.borrowedAttackOptions(p).stream().filter(o->o.abilityId().startsWith("tool:")).findFirst().orElseThrow();}
    static void use(ServerMatch m,UUID p,ServerMatch.BorrowedAttackOption o){NonPokemonBatch97Test.use(m,p,o);}
    static void settle(ServerMatch m){AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),AiDifficulty.MASTER);}
    static ServerMatch aiGame(String id){var m=game(id,false,false);pay(m,A,"energy",4);return m;}
    public static void main(String[] args){eligibility();shared();fly();normal();dragon();authority();ai();schema();System.out.println("NonPokemonBatch98Test passed: three printed Z Tool GX attacks, native ownership versus memory/copy/locks, paid costs and holder type, shared GX use, next-turn damage/effect guard, until-tails retry, physical basic Energy discard, stale authority and all AI difficulties");}
    static void eligibility(){for(String id:IDS)for(UUID p:List.of(A,B)){
        var m=game(id,false,false);if(p.equals(B))m.endTurn(A);equip(m,p);var o=option(m,p);check(o.attack()!=null,"Missing qualified Z attack");var before=m.clientView(p);var rng=m.randomTranscript();GeneratedRuleBatch8Test.reject(()->use(m,p,o));check(before.equals(m.clientView(p))&&rng.equals(m.randomTranscript())&&!SharedUsageLimitTest.marker(m.clientView(p),p).gxUsed(),"Unpaid Z changed state");
        int cost=id.equals(IDS.getLast())?3:4;pay(m,p,"energy",cost-1);check(!m.canPayActiveAttackCost(p,o.attack()),"Z cost waived");pay(m,p,"energy",1);check(m.canPayActiveAttackCost(p,o.attack()),"Paid Z unavailable");
        check(m.opponentAttackLockOptions(other(p)).stream().noneMatch(a->a.id().equals(o.attackId()))&&m.copyAttackOptions(other(p),"copy").stream().noneMatch(a->a.attackId().equals(o.attackId())),"Tool attack became possessed/copyable");
        EventTargetBindingTest.field(m).lockAttackUntilOwnerNextTurnStart(other(p),p,active(m,p),required(id));check(option(m,p).attack()!=null,"Lock on native attack disabled Z");
        m.evolveByEffect(p,active(m,p),hand(m,p,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        check(m.borrowedAttackOptions(p).stream().anyMatch(a->a.attackId().equals(required(id)))&&m.borrowedAttackOptions(p).stream().noneMatch(a->a.abilityId().startsWith("tool:")),"Memory-supplied attack qualified Z holder");
        var wrong=game(id,true,false);equip(wrong,A);check(wrong.borrowedAttackOptions(A).isEmpty(),"Wrong host qualified Z");
    }}
    static void shared(){for(String id:IDS)for(boolean nativeFirst:List.of(false,true)){
        var m=aiGame(id);equip(m,A);var o=option(m,A);if(nativeFirst)m.attack(A,"native-gx");else use(m,A,o);settle(m);check(SharedUsageLimitTest.marker(m.clientView(A),A).gxUsed()&&!SharedUsageLimitTest.marker(m.clientView(A),B).gxUsed(),"Z did not consume own shared GX");m.endTurn(B);
        var before=m.clientView(A);GeneratedRuleBatch8Test.reject(()->use(m,A,o));GeneratedRuleBatch8Test.reject(()->m.attack(A,"native-gx"));check(before.equals(m.clientView(A))&&option(m,A).attack()==null,"Shared GX was reusable across Tools/native attacks");
    }}
    static void fly(){for(UUID p:List.of(A,B))for(boolean remove:List.of(false,true)){
        var m=game(IDS.getFirst(),false,true);if(p.equals(B))m.endTurn(A);equip(m,p);pay(m,p,"energy",4);use(m,p,option(m,p));settle(m);check(m.activePokemonView(other(p)).damage()==360,"Flying Z ignored holder type/weakness");
        if(remove){UUID old=active(m,p);m.switchActiveByEffect(other(p),p,m.benchPokemonView(p).getFirst().pokemonId());m.switchActiveByEffect(other(p),p,old);}
        m.attack(other(p),"hit");settle(m);check(m.activePokemonView(p).damage()==(remove?110:0)&&m.activePokemonView(p).conditions().contains(SpecialCondition.POISONED)==remove,"Flying Z damage/effect guard or Bench clearing wrong");
        if(!remove){m.endTurn(p);m.attack(other(p),"hit");settle(m);check(m.activePokemonView(p).damage()==110,"Flying Z guard did not expire");}
    }}
    static void normal(){var seen=new HashSet<Integer>();for(int skip=0;skip<16;skip++){
        var m=game(IDS.get(1),false,true);equip(m,A);pay(m,A,"energy",4);for(int i=0;i<skip;i++)m.flipCoinsByEffect(A,1);int n=m.randomTranscript().size();use(m,A,option(m,A));settle(m);int heads=AttackCoinMatchTest.newHeads(m,n);seen.add(heads);check(m.activePokemonView(B).damage()==(200+40*heads)*2&&m.randomTranscript().getLast().result()==1,"Normal Z until-tails damage wrong");
    }check(seen.contains(0)&&seen.stream().anyMatch(h->h>0),"Normal Z tests missed either coin result");
        var m=aiGame(IDS.get(1));equip(m,A);m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);int n=m.randomTranscript().size();use(m,A,option(m,A));check(m.hasPendingEffectChoice(),"Normal Z missing eligible retry");var first=m.effectChoicePrompt(A);m.chooseEffectOption(A,first.resolutionId(),first.step(),2);var q=m.effectChoicePrompt(A);int retryStart=m.randomTranscript().size();m.chooseEffectOption(A,q.resolutionId(),q.step(),0);settle(m);int heads=AttackCoinMatchTest.newHeads(m,retryStart);check(m.activePokemonView(B).damage()==200+40*heads&&m.randomTranscript().size()>n,"Normal Z counted discarded retry results");
    }
    static void dragon(){for(int basic=0;basic<=4;basic++){
        var m=game(IDS.getLast(),false,true);equip(m,A);pay(m,A,"special",2);pay(m,A,"energy",basic);UUID bench=m.benchPokemonView(A).getFirst().pokemonId();var stay=hand(m,A,"energy");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,stay.instanceId(),A,bench);
        var discarded=EventTargetBindingTest.field(m).attachedEnergy(A,active(m,A)).stream().filter(c->c.definitionId().equals("energy")).toList();use(m,A,option(m,A));settle(m);
        check(m.activePokemonView(B).damage()==basic*160&&m.activePokemonView(A).attachedEnergyIds().size()==2&&m.privateView(A).discard().containsAll(discarded)&&m.benchPokemonView(A).getFirst().attachedEnergyIds().contains(stay.instanceId()),"Dragon Z counted special units or wrong host/physical discard");
        check(SharedUsageLimitTest.marker(m.clientView(A),A).gxUsed(),"Zero-basic Z refunded GX");
    }}
    static void authority(){for(String id:IDS)for(boolean reattach:List.of(false,true)){
        var m=aiGame(id);equip(m,A);var o=option(m,A);var before=m.clientView(A);GeneratedRuleBatch8Test.reject(()->use(m,B,o));check(before.equals(m.clientView(A)),"Wrong actor mutated Z");
        if(reattach){var tool=m.activePokemonView(A).attachedTools().getFirst();m.discardToolByEffect(A,A,active(m,A),tool.instanceId());m.attachToolByEffect(A,A,EffectSourceZone.DISCARD,tool.instanceId(),A,active(m,A));}else m.disableToolEffectsByEffect(A,A,active(m,A));
        var after=m.clientView(A);GeneratedRuleBatch8Test.reject(()->use(m,A,o));check(after.equals(m.clientView(A)),"Stale/suppressed Tool declared Z");
    }}
    static void ai(){for(String id:IDS)for(var d:AiDifficulty.values()){
        var m=aiGame(id);equip(m,A);var session=MatchSession.multiplayer(m,A,B);var view=session.view(A,A);String name=option(m,A).nameZh();var selected=view.options().stream().filter(o->o.enabled()&&o.type()==ActionType.ATTACK&&o.labelZh().contains(name)).findFirst().orElseThrow();session.submit(A,A,view.revision(),selected.index());AiCardOperationTest.settle(session,d);check(SharedUsageLimitTest.marker(m.clientView(A),A).gxUsed(),"AI failed Z attack "+id+d);AiCardOperationTest.finish(session,d);check(m.phase()==MatchPhase.FINISHED,"AI Z match stalled");
    }}
    static void schema(){for(String id:IDS){var program=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow().ongoingProgram();var e=ToolGrantedAttack.fromProgram(program);AttackProgramRules.validate(ToolGrantedAttack.materialize(e,EnergyType.WATER));var bad=new HashMap<>(e.arguments());bad.put("holder_attack_id","wrong");GeneratedRuleBatch8Test.reject(()->ToolGrantedAttack.validate(new EffectDefinition(e.type(),e.target(),bad,List.of(),List.of())));GeneratedRuleBatch8Test.reject(()->TrainerProgramRules.validate(new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(e))))));}}
}
