import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch122Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("xy4_en_097","xy4_en_098");
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static TrainerProfile trainer(String id){return NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();}
    static void settle(ServerMatch m){NonPokemonBatch103Test.settle(m);}
    static ServerMatch game(String id){return game(id,GeneratedRuleBatch8Test.hit(100,false),trainer("sm8_en_173"),false);}
    static ServerMatch game(String id,AttackDefinition attack,TrainerProfile item,boolean weakness){return game(id,attack,item,weakness,List.of());}
    static ServerMatch game(String id,AttackDefinition attack,TrainerProfile item,boolean weakness,List<AbilityDefinition> abilities){
        var kinds=List.of("basic","basic","basic","trainer","trainer","item","energy","energy","plain","modern","gx","v","evolved","stadium","regular");
        var trainers=Map.of("trainer",trainer(id),"item",item,"stadium",trainer("sm6_en_111"),"regular",DynamicPassiveModifierTest.plainTool());
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String key:new LinkedHashSet<>(kinds)){
            boolean mon=!trainers.containsKey(key)&&!key.equals("energy");var family=switch(key){case "basic","evolved"->Set.of(PokemonFamily.EX_LEGACY);case "modern"->Set.of(PokemonFamily.EX);case "gx"->Set.of(PokemonFamily.GX);case "v"->Set.of(PokemonFamily.V);default->Set.<PokemonFamily>of();};
            defs.add(new CardDefinition(key,key,mon?CardCategory.POKEMON:trainers.containsKey(key)?CardCategory.TRAINER:CardCategory.ENERGY,trainers.containsKey(key)?trainers.get(key).category():TrainerCategory.NONE,"H",Set.of("international"),mon&&!key.equals("evolved"),key.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null,family,mon?Set.of(EnergyType.GRASS):Set.of()));
            if(mon)profiles.put(key,new PokemonProfile(1000,1,key.equals("evolved")?"basic":null,weakness?EnergyType.COLORLESS:null,weakness?2:0,null,0,1,List.of(attack),abilities,EnergyType.GRASS));
        }
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.GRASS),trainers);
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,kinds.get(i%kinds.size())));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->{var basics=m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).toList();return basics.size()<2||basics.subList(0,2).stream().anyMatch(c->!c.definitionId().equals("basic"));}))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,28);m.drawCardsByEffect(A,B,28);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().anyMatch(p->new LinkedHashSet<>(kinds).stream().anyMatch(key->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals(key)))||m.privateView(p).hand().stream().filter(c->c.definitionId().equals("trainer")).count()<2))continue;return m;
        }throw new AssertionError("No Flare fixture");
    }
    static ServerMatch aiGame(String id){return game(id,GeneratedRuleBatch8Test.hit(300,false),trainer("sm8_en_173"),false);}
    static CardInstance equip(ServerMatch m,UUID owner,UUID host){var c=hand(m,owner,"trainer");m.playTrainer(owner,c.instanceId(),host);return c;}
    static void discarded(ServerMatch m,CardInstance c){check(m.privateView(c.ownerId()).discard().contains(c),"Gear missed original owner's discard");for(UUID p:List.of(A,B))check(!m.privateView(p).hand().contains(c)&&!m.privateView(p).deck().contains(c)&&!m.privateView(p).lostZone().contains(c)&&(!p.equals(c.ownerId())?!m.privateView(p).discard().contains(c):true),"Gear duplicated/wrong departure destination");}
    public static void main(String[] args){attachment();effectAttachment();modifiers();departures();lostZone();locks();payment();ai();schema();System.out.println("NonPokemonBatch122Test passed: opponent legacy EX-only Flare Gear, actual ownership and capacity, signed cost/damage and recoil/Bench/Weakness, suppression, all departure destinations including Faba and Lost Vacuum, off-field recovery/reuse, hand lock, attack Tool payment and four AI difficulties");}
    static void attachment(){for(String id:IDS)for(UUID p:List.of(A,B))for(boolean benched:List.of(false,true)){
        var m=game(id);if(p.equals(B))m.endTurn(A);UUID target=benched?bench(m,other(p)):active(m,other(p));var c=hand(m,p,"trainer");
        reject(()->m.playTrainer(other(p),c.instanceId(),target));reject(()->m.playTrainer(p,c.instanceId(),active(m,p)));reject(()->m.playTrainer(p,c.instanceId(),UUID.randomUUID()));
        var field=EventTargetBindingTest.field(m);field.addProtection(p,other(p),target,new ProtectionEffect(new EffectProtection(EffectProtection.Source.TRAINER,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));
        check(m.canPlayTrainer(p,c.instanceId(),target),"Bright Veil incorrectly blocked Flare attachment");equip(m,p,target);check(field.pokemonView(other(p),target).attachedTools().contains(c)&&c.ownerId().equals(p),"Gear changed ownership or field");
        var next=hand(m,p,"trainer");reject(()->m.playTrainer(p,next.instanceId(),target));check(m.privateView(p).hand().contains(next),"Rejected capacity consumed hand card");
        for(String key:List.of("plain","modern","gx","v")){var n=game(id);UUID q=other(p);if(p.equals(B))n.endTurn(A);var card=hand(n,q,key);EventTargetBindingTest.field(n).placeOnBench(q,card);PairedTrainerPlayTest.zone(n,q,"hand").remove(card);PairedTrainerPlayTest.zone(n,q,"bench").add(card);UUID host=PokemonIdentityTestSupport.logical(n,q,card.instanceId());reject(()->n.playTrainer(p,hand(n,p,"trainer").instanceId(),host));}
    }}
    static void effectAttachment(){for(String id:IDS)for(UUID p:List.of(A,B))for(var source:EffectSourceZone.values()){
        var m=game(id);var c=hand(m,p,"trainer");if(source!=EffectSourceZone.HAND){PairedTrainerPlayTest.zone(m,p,"hand").remove(c);PairedTrainerPlayTest.zone(m,p,source==EffectSourceZone.DECK?"deck":"discard").add(c);}
        reject(()->m.attachToolByEffect(A,p,source,c.instanceId(),p,active(m,p)));m.attachToolByEffect(A,p,source,c.instanceId(),other(p),active(m,other(p)));m.discardToolByEffect(A,other(p),active(m,other(p)),c.instanceId());discarded(m,c);
        var regular=hand(m,p,"regular");reject(()->m.attachToolByEffect(A,p,EffectSourceZone.HAND,regular.instanceId(),other(p),active(m,other(p))));
    }}
    static void modifiers(){
        for(UUID p:List.of(A,B))for(String mode:List.of("live","disabled","lab","evolved","switched")){
            var m=game(IDS.getFirst());if(p.equals(B))m.endTurn(A);UUID q=other(p),host=active(m,q);equip(m,p,host);check(!m.canPayAttackCost(q,host,GeneratedRuleBatch8Test.hit(100,false)),"Head Ringer failed to add colorless cost to free attack");
            if(mode.equals("disabled"))m.disableToolEffectsByEffect(p,q,host);if(mode.equals("lab"))m.playTrainer(p,hand(m,p,"stadium").instanceId(),null);m.endTurn(p);
            if(mode.equals("evolved"))m.evolveByEffect(q,host,hand(m,q,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);if(mode.equals("switched"))m.switchActiveByEffect(q,q,bench(m,q));
            boolean free=Set.of("disabled","lab","switched").contains(mode);check(m.canPayAttackCost(q,active(m,q),GeneratedRuleBatch8Test.hit(100,false))==free,"Head Ringer wrong live host/suppression "+mode);
            if(!free){reject(()->m.attack(q,"hit"));m.attachEnergy(q,hand(m,q,"energy").instanceId(),active(m,q));}m.attack(q,"hit");check(m.lastAttack().finalDamage()==100,"Head Ringer changed damage");
        }
        for(UUID p:List.of(A,B))for(String mode:List.of("active","weakness","bench","zero","recoil","own-bench","disabled","protected")){
            var attack=mode.equals("recoil")?AttackSelfDamageCalculationTest.attack("native",30,EnergyType.COLORLESS,""):mode.equals("own-bench")?AttackSelfDamageCalculationTest.program(AttackSelfDamageCalculationTest.trigger("AFTER_ATTACK","{\"type\":\"bench_damage\",\"target\":\"OWN_BENCH\",\"amount\":30}"),""):GeneratedRuleBatch8Test.hit(mode.equals("zero")?10:100,mode.equals("bench"));
            var m=game(IDS.get(1),attack,trainer("sm8_en_173"),mode.equals("weakness"));if(p.equals(B))m.endTurn(A);UUID q=other(p);if(mode.equals("protected"))EventTargetBindingTest.field(m).addProtection(p,q,active(m,q),new ProtectionEffect(new EffectProtection(EffectProtection.Source.TRAINER,EffectProtection.Relation.OPPONENT,EffectProtection.Scope.EFFECTS_ONLY),ContinuousEffect.Expiry.LEAVES_PLAY));equip(m,p,active(m,q));if(mode.equals("disabled"))m.disableToolEffectsByEffect(p,q,active(m,q));m.endTurn(p);m.attack(q,"hit");settle(m);
            int expected=mode.equals("zero")?0:mode.equals("disabled")?100:mode.equals("weakness")?160:Set.of("recoil","own-bench").contains(mode)?20:80;
            check((mode.equals("bench")?m.benchPokemonView(p).getFirst().damage():m.activePokemonView(p).damage())==expected,"Jamming Net damage scope "+mode);
            if(mode.equals("recoil"))check(m.activePokemonView(q).damage()==30,"Net lowered own recoil");if(mode.equals("own-bench"))check(m.benchPokemonView(q).getFirst().damage()==30,"Net lowered own Bench damage");
        }
    }
    static void departures(){for(String id:IDS)for(UUID p:List.of(A,B))for(String mode:List.of("discard","tool-hand","pokemon-hand","pokemon-deck","knockout","disabled-hand","lab-hand")){
        var m=game(id);if(p.equals(B))m.endTurn(A);UUID q=other(p),host=bench(m,q);var c=equip(m,p,host);
        if(mode.equals("disabled-hand"))m.disableToolEffectsByEffect(p,q,host);if(mode.equals("lab-hand"))m.playTrainer(p,hand(m,p,"stadium").instanceId(),null);
        switch(mode){case "discard"->m.discardToolByEffect(p,q,host,c.instanceId());case "pokemon-hand"->m.movePokemonByEffect(p,q,host,LeavePlayDestination.HAND);case "pokemon-deck"->m.movePokemonByEffect(p,q,host,LeavePlayDestination.DECK_SHUFFLED);case "knockout"->m.knockOutPokemonByEffect(p,q,host);default->m.returnAttachedToolToHandByEffect(p,q,host,c.instanceId());}settle(m);discarded(m,c);
        m.recoverDiscardCardByEffect(m.currentPlayer(),p,c.instanceId(),CardEffectDestination.HAND);check(m.privateView(p).hand().contains(c)&&!m.privateView(p).discard().contains(c),"Off-field recovery redirected again");
        m.attachToolByEffect(m.currentPlayer(),p,EffectSourceZone.HAND,c.instanceId(),q,active(m,q));m.returnAttachedToolToHandByEffect(m.currentPlayer(),q,active(m,q),c.instanceId());discarded(m,c);
    }}
    static void lostZone(){for(String id:IDS)for(UUID p:List.of(A,B))for(String removal:List.of("sm8_en_173","swsh11_en_162")){
        var m=game(id,GeneratedRuleBatch8Test.hit(100,false),trainer(removal),false);if(p.equals(B))m.endTurn(A);var c=equip(m,p,active(m,other(p)));m.playTrainer(p,hand(m,p,"item").instanceId(),null);
        if(removal.equals("swsh11_en_162")){var cost=m.effectCardPrompt(p);m.submitEffectCards(p,cost.resolutionId(),cost.step(),List.of(hand(m,p,"plain").instanceId()));}
        var prompt=m.effectCardPrompt(p);check(prompt.candidates().contains(c),"Flare missing removal target");m.submitEffectCards(p,prompt.resolutionId(),prompt.step(),List.of(c.instanceId()));settle(m);discarded(m,c);
    }}
    static void locks(){for(String id:IDS){var lock=BundledCombatData.ability(JsonParser.parseString("{\"id\":\"tool-lock\",\"nameZh\":\"test\",\"kind\":\"PASSIVE\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"passive_trainer_play_lock\",\"target\":\"OPPONENT_ACTIVE\",\"trainer_categories\":\"TOOL\"}]}").getAsJsonObject(),EffectRegistry.standard());var m=game(id,GeneratedRuleBatch8Test.hit(10,false),trainer("sm8_en_173"),false,List.of(lock));m.endTurn(A);var c=hand(m,B,"trainer");check(!m.canPlayTrainer(B,c.instanceId(),active(m,A)),"Hand Tool lock ignored opponent Gear");reject(()->m.playTrainer(B,c.instanceId(),active(m,A)));}
        for(String id:IDS)for(boolean disabled:List.of(false,true)){
            var m=game(id,GeneratedRuleBatch8Test.hit(100,false),trainer("xy11_en_103"),false);UUID host=active(m,B);var gear=equip(m,A,host);if(disabled)m.disableToolEffectsByEffect(A,B,host);m.endTurn(A);
            var incoming=hand(m,B,"plain");PairedTrainerPlayTest.zone(m,B,"hand").remove(incoming);PairedTrainerPlayTest.zone(m,B,"deck").add(incoming);m.playTrainer(B,hand(m,B,"item").instanceId(),null);
            var target=m.effectPokemonPrompt(B);m.chooseEffectPokemon(B,target.resolutionId(),target.step(),host);var cards=m.effectCardPrompt(B);m.submitEffectCards(B,cards.resolutionId(),cards.step(),List.of(incoming.instanceId()));settle(m);
            check(m.activePokemonView(B).topCardId().equals("plain"),"Ninja replacement fixture failed");discarded(m,gear);
        }
    }
    static void payment(){String cost="{\"type\":\"discard_tool\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":60,\"minimum\":0,\"bind\":\"tools\"}";String damage="{\"type\":\"damage_per_bound\",\"target\":\"OPPONENT_ACTIVE\",\"bind\":\"tools\",\"amount\":40}";var attack=BeforeDamageAttackTest.attack(cost,damage,"");for(String id:IDS){var m=game(id,attack,trainer("sm8_en_173"),false);var c=equip(m,A,active(m,B));m.endTurn(A);m.attachEnergy(B,hand(m,B,"energy").instanceId(),active(m,B));m.attack(B,"hit");var prompt=m.effectCardPrompt(B);m.submitEffectCards(B,prompt.resolutionId(),prompt.step(),List.of(c.instanceId()));settle(m);discarded(m,c);check(m.lastAttack().finalDamage()==80,"Own-field opponent-owned Tool failed attack payment or kept removed reduction");}}
    static void ai(){for(String id:IDS)for(var level:AiDifficulty.values()){var m=aiGame(id);var s=MatchSession.multiplayer(m,A,B);var view=s.view(A,A);var c=hand(m,A,"trainer");var options=view.options().stream().filter(o->o.enabled()&&o.type()==ActionType.TRAINER&&o.cardKeys().contains("hand:"+c.instanceId())).toList();check(options.size()==2&&options.stream().noneMatch(o->o.cardKeys().contains("field:"+active(m,A))),"UI/AI exposed illegal own target or omitted opponent Active/Bench");s.submit(A,A,view.revision(),options.getFirst().index());AiCardOperationTest.settle(s,level);AiCardOperationTest.finish(s,level);check(m.phase()==MatchPhase.FINISHED,"Flare AI stalled "+level);}}
    static void schema(){var e=new EffectDefinition("flare_gear",EffectTarget.SELF,Map.of(),List.of(),List.of());var program=new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.PASSIVE,List.of(),List.of(e))));for(var source:List.of(PassiveModifierProgram.Source.STADIUM,PassiveModifierProgram.Source.ABILITY,PassiveModifierProgram.Source.ENERGY))reject(()->PassiveModifierProgram.validate(program,source));reject(()->PassiveRuleProgram.validate(new EffectDefinition(e.type(),EffectTarget.OPPONENT_ACTIVE,Map.of(),List.of(),List.of())));reject(()->PassiveRuleProgram.validate(new EffectDefinition(e.type(),e.target(),Map.of("amount","1"),List.of(),List.of())));}
}
