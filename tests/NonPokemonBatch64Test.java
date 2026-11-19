import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch64Test {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static final List<String> IDS=List.of("sm4_en_092","swsh8_en_242");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static TrainerProfile profile(String id){return NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static UUID host(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static ServerMatch aiGame(String id){var c=new GeneratedRuleBatch28Test.Config();c.trainer=profile(id);return GeneratedRuleBatch28Test.fixture(c);}
    static ServerMatch fixture(List<AbilityDefinition> abilities,String name){return DynamicPassiveModifierTest.fixture(profile(IDS.getFirst()),profile(IDS.getLast()),EnergyProfile.fixed(EnergyType.COLORLESS,2),GeneratedRuleBatch8Test.hit(100,false),abilities,0,null,Map.of("basic",EnergyType.COLORLESS,"evolved",EnergyType.COLORLESS),name);}
    static CardInstance attach(ServerMatch m,UUID p,String id){var c=hand(m,p,id);m.attachEnergyByEffect(p,p,EffectSourceZone.HAND,c.instanceId(),p,host(m,p));return c;}
    static List<CardInstance> mixed(ServerMatch m,UUID p){return List.of(attach(m,p,"energy"),attach(m,p,"special"));}
    static void install(ServerMatch m,UUID p,boolean tool,boolean stadium){if(tool)m.playTrainer(p,hand(m,p,"tool").instanceId(),host(m,p));if(stadium)m.playTrainer(p,hand(m,p,"stadium").instanceId(),null);}
    static ServerMatch retreatGame(String id){var m=fixture(List.of(),null);install(m,A,id.equals(IDS.getFirst()),id.equals(IDS.getLast()));mixed(m,A);return m;}
    public static void main(String[] args){payments();guards();otherDiscard();invalidAndFree();coinGate();schema();System.out.println("NonPokemonBatch64Test passed: exact successful-retreat Energy replacement, both owners, basic/special/mixed multi-unit cards and whole-card identity, combined sources and Tool suppression, New Moon immunity and hand-return/discard-recovery guards, failed coin retention, source removal, unrelated discard exclusion and zero-cost/invalid payment atomicity");}
    static void payments(){for(UUID p:List.of(A,B))for(String source:List.of("tool","stadium","both"))for(String payment:List.of("basic","mixed","double"))for(boolean disabled:List.of(false,true)){
        var m=fixture(List.of(),null);if(p.equals(B))m.endTurn(A);boolean tool=!source.equals("stadium"),stadium=!source.equals("tool");install(m,p,tool,stadium);if(disabled)m.disableToolEffectsByEffect(p,p,host(m,p));
        List<CardInstance> paid=payment.equals("basic")?List.of(attach(m,p,"energy"),attach(m,p,"energy"),attach(m,p,"energy")):payment.equals("mixed")?mixed(m,p):List.of(attach(m,p,"special"),attach(m,p,"special"));
        if(payment.equals("double"))m.modifyRetreatCostByEffect(p,p,host(m,p),-1);
        UUID old=host(m,p),next=bench(m,p);m.retreat(p,next,paid.stream().map(CardInstance::instanceId).toList());
        check(host(m,p).equals(next)&&m.benchPokemonView(p).stream().filter(v->v.pokemonId().equals(old)).findFirst().orElseThrow().attachedEnergyIds().isEmpty(),"Retreat did not detach exact payment");
        for(var card:paid){boolean returned=tool&&!disabled||stadium&&m.cardDefinition(card.definitionId()).basicEnergy();check(m.privateView(p).hand().contains(card)==returned&&m.privateView(p).discard().contains(card)!=returned,"Wrong retreat destination: "+source+payment+disabled);}
    }}
    static void guards(){for(String source:List.of("tool","stadium","both"))for(String guard:List.of("moon","return","colony")){
        var abilities=guard.equals("moon")?NonPokemonBatch2Test.DATA.pokemon("sv3_en_092").orElseThrow().abilities():List.of(DiscardAbilityCountTest.passive("guard",guard.equals("return")?"{\"type\":\"prevent_return_to_hand\",\"target\":\"ALL_OPPONENT_POKEMON\"}":"{\"type\":\"unrecoverable_from_discard\",\"target\":\"ALL_OPPONENT_POKEMON\",\"sources\":\"ABILITY|TRAINER\",\"destination\":\"HAND\"}"));
        var m=fixture(abilities,guard.equals("moon")?"Solrock":null);boolean tool=!source.equals("stadium"),stadium=!source.equals("tool");install(m,A,tool,stadium);var paid=mixed(m,A);m.retreat(A,bench(m,A),paid.stream().map(CardInstance::instanceId).toList());
        for(var card:paid){boolean returned=!guard.equals("return")&&(tool||stadium&&!guard.equals("moon")&&m.cardDefinition(card.definitionId()).basicEnergy());check(m.privateView(A).hand().contains(card)==returned&&m.privateView(A).discard().contains(card)!=returned,"Wrong guard interaction: "+source+guard);}
    }}
    static void otherDiscard(){for(String id:IDS)for(String mode:List.of("discard","knockout","switch","removed","incoming-tool")){
        var m=fixture(List.of(),null);boolean tool=id.equals(IDS.getFirst());
        if(mode.equals("incoming-tool")&&tool)m.playTrainer(A,hand(m,A,"tool").instanceId(),bench(m,A));else install(m,A,tool,!tool);
        var paid=mixed(m,A);UUID old=host(m,A);
        if(mode.equals("removed")){if(tool)m.discardToolByEffect(A,A,old,m.activePokemonView(A).attachedTools().getFirst().instanceId());else m.discardStadiumByEffect(A);m.retreat(A,bench(m,A),paid.stream().map(CardInstance::instanceId).toList());}
        else if(mode.equals("incoming-tool")){m.retreat(A,bench(m,A),paid.stream().map(CardInstance::instanceId).toList());}
        else if(mode.equals("discard"))m.discardAttachedEnergyByEffect(A,A,old,paid.stream().map(CardInstance::instanceId).toList());
        else if(mode.equals("knockout")){m.knockOutPokemonByEffect(A,A,old);AiCardOperationTest.settle(MatchSession.multiplayer(m,A,B),AiDifficulty.NORMAL);}
        else {m.switchActiveByEffect(A,A,bench(m,A));check(m.benchPokemonView(A).stream().filter(v->v.pokemonId().equals(old)).findFirst().orElseThrow().attachedEnergyIds().containsAll(paid.stream().map(CardInstance::instanceId).toList()),"Switch detached retreat payment");}
        for(var card:paid){boolean returned=mode.equals("incoming-tool")&&!tool&&m.cardDefinition(card.definitionId()).basicEnergy();check(m.privateView(A).hand().contains(card)==returned,"Unrelated movement returned Energy: "+id+mode);if(!mode.equals("switch"))check(m.privateView(A).discard().contains(card)!=returned,"Discard exclusion lost Energy");}
    }}
    static void invalidAndFree(){for(String id:IDS){var m=retreatGame(id);var before=m.clientView(A);var paid=m.activePokemonView(A).attachedEnergyIds();GeneratedRuleBatch8Test.reject(()->m.retreat(A,bench(m,A),List.of(paid.getFirst())));GeneratedRuleBatch8Test.reject(()->m.retreat(A,bench(m,A),List.of(paid.getFirst(),paid.getFirst())));check(before.equals(m.clientView(A)),"Rejected retreat returned Energy");m.modifyRetreatCostByEffect(A,A,host(m,A),-3);UUID old=host(m,A);m.retreat(A,bench(m,A),List.of());check(m.benchPokemonView(A).stream().filter(v->v.pokemonId().equals(old)).findFirst().orElseThrow().attachedEnergyIds().equals(paid),"Free retreat returned unpaid cards");}}
    static void coinGate(){for(String id:IDS){boolean heads=false,tails=false;for(int skip=0;skip<32&&!(heads&&tails);skip++){
        var m=fixture(List.of(RetreatAttemptCoinGateTest.gate()),null);install(m,A,id.equals(IDS.getFirst()),id.equals(IDS.getLast()));var paid=mixed(m,A);UUID old=host(m,A);for(int i=0;i<skip;i++)m.flipCoinsByEffect(A,1);m.retreat(A,bench(m,A),paid.stream().map(CardInstance::instanceId).toList());
        if(host(m,A).equals(old)){tails=true;check(m.activePokemonView(A).attachedEnergyIds().containsAll(paid.stream().map(CardInstance::instanceId).toList())&&paid.stream().noneMatch(c->m.privateView(A).hand().contains(c)||m.privateView(A).discard().contains(c)),"Failed retreat moved Energy");}
        else {heads=true;check(m.privateView(A).hand().contains(paid.getFirst()),"Successful gated retreat did not return Basic Energy");}
    }check(heads&&tails,"Missing retreat gate outcome");}}
    static void schema(){String any="{\"type\":\"retreat_energy_to_hand\",\"energy_kind\":\"ANY\"}",basic=any.replace("ANY","BASIC");GeneratedRuleBatch8Test.reject(()->DynamicPassiveModifierTest.trainer("STADIUM",any));GeneratedRuleBatch8Test.reject(()->DynamicPassiveModifierTest.trainer("TOOL",basic));GeneratedRuleBatch8Test.reject(()->DynamicPassiveModifierTest.energy(any));GeneratedRuleBatch8Test.reject(()->DiscardAbilityCountTest.passive("bad",any));}
}
