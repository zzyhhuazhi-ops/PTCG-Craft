import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch46Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final Map<String,EnergyType> MEMORIES=Map.of("sm4_en_094",EnergyType.FIGHTING,"sm4_en_098",EnergyType.PSYCHIC,"sm5_en_121",EnergyType.LIGHTNING,"sm5_en_123",EnergyType.FIRE,"sm9_en_143",EnergyType.GRASS,"sm9_en_157",EnergyType.WATER);
    static final String PARK="sm7-5_en_063";
    static final List<String> IDS=List.of("sm4_en_094","sm4_en_098","sm5_en_121","sm5_en_123","sm9_en_143","sm9_en_157",PARK);
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static CardInstance card(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static GeneratedRuleBatch28Test.Config config(String id){var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();c.ownName="Silvally-GX";c.ownTypes=Set.of(EnergyType.COLORLESS);c.defenderTypes=Set.of(EnergyType.COLORLESS);c.defenderFallback=EnergyType.COLORLESS;c.ownFamilies=Set.of(PokemonFamily.GX,PokemonFamily.RULE_BOX);return c;}
    static ServerMatch aiGame(String id){return GeneratedRuleBatch28Test.fixture(config(id));}
    static CardInstance attach(ServerMatch m,UUID owner,UUID host){var t=card(m,owner,"trainer");m.playTrainer(owner,t.instanceId(),host);return t;}
    public static void main(String[] args){memories();specialEnergy();typedSuppression();reaction();park();System.out.println("NonPokemonBatch46Test passed: six exact-name type replacements, ordinary Silvally/other-GX negatives, both owners and Bench, live Weakness, Tool versus Ability suppression, dynamic typed Energy eligibility/discard, typed suppression/reactions, public views and Burn-only checkup retention");}
    static void typedSuppression(){for(var entry:MEMORIES.entrySet()){
        var ability=DeckDiscardTriggeredAbilityTest.active("{\"type\":\"draw\",\"target\":\"SELF\",\"amount\":1}");
        var stadium=DynamicPassiveModifierTest.trainer("STADIUM","{\"type\":\"suppress_abilities\",\"scope\":\"ALL_IN_PLAY\",\"pokemon_type\":\""+entry.getValue().name()+"\"}");
        var m=DynamicPassiveModifierTest.fixture(config(entry.getKey()).trainer,stadium,EnergyProfile.fixed(EnergyType.COLORLESS,2),DynamicPassiveModifierTest.attack(40,false,false),List.of(ability),0,null,Map.of("basic",EnergyType.COLORLESS),"Silvally-GX");
        UUID owner=DynamicPassiveModifierTest.A,host=active(m,owner);m.playTrainer(owner,card(m,owner,"stadium").instanceId(),null);
        check(m.abilityOptions(owner).stream().anyMatch(o->o.pokemonId().equals(host)),"Typed suppression affected printed Colorless");var tool=card(m,owner,"tool");m.playTrainer(owner,tool.instanceId(),host);
        check(m.abilityOptions(owner).stream().noneMatch(o->o.pokemonId().equals(host)),"Typed suppression ignored Memory type");m.discardToolByEffect(owner,owner,host,tool.instanceId());
        check(m.abilityOptions(owner).stream().anyMatch(o->o.pokemonId().equals(host)),"Typed suppression persisted after Memory departed");
    }}
    static void reaction(){var c=config("sm5_en_123");c.names=Map.of("defender","Silvally-GX");c.ownWeakness=EnergyType.FIRE;
        c.ownAbility=BundledCombatData.ability(com.google.gson.JsonParser.parseString("{\"id\":\"weakness-reaction\",\"nameZh\":\"弱点反应\",\"kind\":\"TRIGGERED\",\"effectSupport\":\"SUPPORTED\",\"eventPolicy\":{\"trigger\":\"ON_DAMAGE\",\"from\":\"OPPONENT_ATTACK\",\"activeOnly\":true,\"lethalOnly\":false,\"optional\":false,\"usage\":\"NONE\",\"requiresWeaknessToOpponentActive\":true},\"effects\":[{\"type\":\"draw\",\"target\":\"SELF\",\"amount\":1}]}").getAsJsonObject(),EffectRegistry.standard());
        var m=GeneratedRuleBatch28Test.fixture(c);m.endTurn(A);attach(m,B,active(m,B));int hand=m.privateView(A).hand().size();m.attack(B,"hit");NonPokemonBatch2Test.settle(m);
        check(m.privateView(A).hand().size()==hand+2,"Weakness-triggered response did not capture Memory type (reaction plus next turn draw)");
    }
    static void memories(){for(var entry:MEMORIES.entrySet())for(UUID p:List.of(A,B))for(String mode:List.of("normal","wrong-name","ordinary","disabled","ability-disabled","removed","bench")){
        var c=config(entry.getKey());c.weakness=true;c.defenderWeakness=entry.getValue();c.ownWeakness=entry.getValue();
        String name=mode.equals("wrong-name")?"Other-GX":mode.equals("ordinary")?"Silvally":"Silvally-GX";
        c.ownName=name;c.names=Map.of("defender",name);c.defenderFamilies=c.ownFamilies;
        var m=GeneratedRuleBatch28Test.fixture(c);if(p.equals(B))m.endTurn(A);
        UUID host=mode.equals("bench")?bench(m,p):active(m,p);var t=attach(m,p,host);
        if(mode.equals("disabled"))m.disableToolEffectsByEffect(p,p,host);
        if(mode.equals("ability-disabled"))m.disableAbilityEffectsByEffect(p,p,host);
        if(mode.equals("removed"))m.discardToolByEffect(p,p,host,t.instanceId());
        boolean changes=Set.of("normal","ability-disabled","bench").contains(mode);
        if(mode.equals("bench")){check(m.benchPokemonView(p).getFirst().pokemonType()==entry.getValue()&&m.activePokemonView(p).pokemonType()==EnergyType.COLORLESS,"Memory changed wrong physical Pokemon");m.switchActiveByEffect(p,p,host);}
        check(m.activePokemonView(p).pokemonType()==(changes?entry.getValue():EnergyType.COLORLESS),"Public type not updated or wrong name/suppression");
        m.attack(p,"hit");check(m.lastAttack().finalDamage()==(changes?200:100),"Memory did not affect live attacking type/Weakness: "+entry+mode+p);
    }
    for(var entry:MEMORIES.entrySet()){
        var c=config(entry.getKey());c.weakness=true;c.defenderWeakness=EnergyType.COLORLESS;var m=GeneratedRuleBatch28Test.fixture(c);attach(m,A,active(m,A));m.attack(A,"hit");check(m.lastAttack().finalDamage()==100,"Memory added type instead of replacing Colorless");
    }}
    static void specialEnergy(){for(var entry:MEMORIES.entrySet())for(String removal:List.of("discard","disable","evolve")){
        var c=config(entry.getKey());c.specialEnergy=new EnergyProfile(EnergyProfile.basic(entry.getValue()).units(),EffectProgram.EMPTY,List.of(),List.of(),new EnergyAttachmentRule(Set.of(),Set.of(entry.getValue()),true));c.names=Map.of("evolved","Other");c.evolvedTypes=Set.of(EnergyType.COLORLESS);
        var m=GeneratedRuleBatch28Test.fixture(c);UUID host=active(m,A);var energy=card(m,A,"energy");
        check(!m.canAttachEnergy(A,energy.instanceId(),host),"Typed Special Energy accepted printed Colorless");var tool=attach(m,A,host);
        check(m.canAttachEnergy(A,energy.instanceId(),host),"Typed Special Energy ignored current Memory type");m.attachEnergy(A,energy.instanceId(),host);
        check(m.activePokemonView(A).attachedEnergyIds().contains(energy.instanceId()),"Valid current-type Energy immediately discarded");
        if(removal.equals("discard"))m.discardToolByEffect(A,A,host,tool.instanceId());
        else if(removal.equals("disable"))m.disableToolEffectsByEffect(A,A,host);
        else m.evolveByEffect(A,host,card(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        check(m.privateView(A).discard().contains(energy)&&!m.activePokemonView(A).attachedEnergyIds().contains(energy.instanceId()),"Invalid Energy retained after Memory stopped applying: "+removal);
    }}
    static void park(){for(UUID controller:List.of(A,B)){
        var m=aiGame(PARK);if(controller.equals(B))m.endTurn(A);m.playTrainer(controller,card(m,controller,"trainer").instanceId(),null);
        for(UUID p:List.of(A,B)){m.applySpecialConditionByEffect(controller,p,SpecialCondition.BURNED);m.applySpecialConditionByEffect(controller,p,SpecialCondition.ASLEEP);}
        int random=m.randomTranscript().size();
        for(int turn=0;turn<6;turn++){var current=m.currentPlayer();m.endTurn(current);NonPokemonBatch2Test.settle(m);for(UUID p:List.of(A,B))check(m.activePokemonView(p).conditions().contains(SpecialCondition.BURNED)&&m.activePokemonView(p).damage()==(turn+1)*20,"Volcano lost Burn or changed burn damage");}
        var coins=m.randomTranscript().subList(random,m.randomTranscript().size()).stream().filter(e->e.operation().startsWith("checkup-burn:")).toList();check(coins.size()==12&&coins.stream().anyMatch(e->e.result()==0),"Volcano did not exercise retained Burn on heads or skipped coins");
        check(List.of(A,B).stream().noneMatch(p->m.activePokemonView(p).conditions().contains(SpecialCondition.ASLEEP)),"Volcano incorrectly prevented sleep recovery");
        m.discardStadiumByEffect(m.currentPlayer());
        for(int turn=0;turn<10&&List.of(A,B).stream().anyMatch(p->m.activePokemonView(p).conditions().contains(SpecialCondition.BURNED));turn++){m.endTurn(m.currentPlayer());NonPokemonBatch2Test.settle(m);}
        check(List.of(A,B).stream().noneMatch(p->m.activePokemonView(p).conditions().contains(SpecialCondition.BURNED)),"Volcano persisted after departure");
    }}
}
