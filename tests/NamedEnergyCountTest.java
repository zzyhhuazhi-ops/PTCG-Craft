import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Physical printed Energy identity remains independent of current supplied units and effects. */
public final class NamedEnergyCountTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static void check(boolean b,String why){TypedEnergyCountTest.check(b,why);}
    static void reject(Runnable r){TypedEnergyCountTest.reject(r);}
    static EnergyCountQuery query(String ids){return EnergyCountQuery.from(Map.of("count_mode","CARDS","definition_ids",ids));}
    static AttackDefinition attack(String scope,String ids){return TypedEnergyCountTest.attack(scope,",\"count_mode\":\"CARDS\",\"definition_ids\":\""+ids+"\"");}
    static void attach(ServerMatch m,UUID p,UUID host,String id){TypedEnergyCountTest.attach(m,p,host,id);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static boolean count(ServerMatch m,String scope,String ids,int expected){return m.matchesEffectCondition(A,active(m,A),new ConditionDefinition(ConditionType.ENERGY_COUNT,Map.of("target",scope,"value",""+expected,"comparison","==","count_mode","CARDS","definition_ids",ids),List.of()));}
    public static void main(String[] args){identity();groups();lifecycle();suppression();staged();copies();borrowed();schema();System.out.println("NamedEnergyCountTest passed: physical identities independent of typed/wildcard/multi/zero provision, exact public scopes, movement/switch/return, suppression, pre-damage attachment, copied/borrowed actual source, immutable strict filters and old query compatibility");}
    static void identity(){
        var multi=new EnergyProfile(List.of(new EnergyProfile.Unit(EnumSet.allOf(EnergyType.class))));
        var entries=List.of(new EnergyCountQuery.Contribution(multi,false,"wild"),new EnergyCountQuery.Contribution(EnergyProfile.fixed(EnergyType.LIGHTNING,4),false,"named"),new EnergyCountQuery.Contribution(new EnergyProfile(List.of()),false,"named"),new EnergyCountQuery.Contribution(EnergyProfile.basic(EnergyType.LIGHTNING),true,"basic"));
        check(query("named").count(entries)==2&&query("named|wild").count(entries)==3&&query("absent").count(entries)==0,"Identity counted supply/unknown cards");
        check(EnergyCountQuery.ALL_UNITS.count(entries)==6&&new EnergyCountQuery(null,EnergyCountQuery.Mode.CARDS).count(entries)==4,"Old constructors or units changed");
        var ids=new HashSet<>(Set.of("named"));var q=new EnergyCountQuery(null,EnergyCountQuery.Mode.CARDS,EnergyCountQuery.CardKind.ANY,ids);ids.clear();check(q.count(entries)==2,"Query retained mutable set");
        reject(()->query("named").count(List.of(new EnergyCountQuery.Contribution(multi,false))));
    }
    static void groups(){for(String scope:List.of("SELF","OWN_ACTIVE","OWN_BENCH","ALL_OWN_POKEMON","OPPONENT_ACTIVE","OPPONENT_BENCH","ALL_OPPONENT_POKEMON")){
        var m=DynamicEnergyProvisionTest.fixture(EnergyProfile.fixed(EnergyType.WATER,2),attack(scope,"special"));attach(m,A,active(m,A),"special");attach(m,A,bench(m,A),"special");attach(m,B,active(m,B),"special");attach(m,A,active(m,A),"energy");
        int n=scope.equals("ALL_OWN_POKEMON")?2:scope.equals("OPPONENT_BENCH")?0:1;check(count(m,scope,"special",n),"Wrong identity scope "+scope);var rng=m.randomTranscript();m.clientView(A);m.clientView(B);check(rng.equals(m.randomTranscript()),"Read spent RNG");m.attack(A,"hit");check(m.activePokemonView(B).damage()==20+60*n,"Named damage scope "+scope);
    }}
    static void lifecycle(){var m=DynamicEnergyProvisionTest.fixture(DynamicEnergyProvisionTest.conditional(),attack("SELF","special"));UUID host=active(m,A),reserve=bench(m,A);var card=DynamicPassiveModifierTest.hand(m,A,"special");m.attachEnergy(A,card.instanceId(),host);check(count(m,"SELF","special",1),"Initial named attachment missing");m.evolve(A,host,DynamicPassiveModifierTest.hand(m,A,"evolved").instanceId());check(count(m,"SELF","special",1),"Evolution/provision changed printed identity");m.moveAttachedEnergyByEffect(A,A,host,A,reserve,List.of(card.instanceId()));check(count(m,"SELF","special",0)&&count(m,"OWN_BENCH","special",1),"Move cached old host");m.switchActiveByEffect(A,A,reserve);check(count(m,"SELF","special",1)&&count(m,"OWN_BENCH","special",0),"Switch cached scope");m.returnAttachedEnergyToHandByEffect(A,A,reserve,List.of(card.instanceId()));check(count(m,"ALL_OWN_POKEMON","special",0)&&m.privateView(A).hand().contains(card),"Returned card still counted");}
    static void suppression(){for(boolean zero:List.of(false,true)){
        var profile=zero?new EnergyProfile(List.of()):EnergyProfile.fixed(EnergyType.WATER,2);var m=DynamicEnergyProvisionTest.fixture(profile,DynamicEnergyProvisionTest.stadium(),attack("SELF","special"));attach(m,A,active(m,A),"special");check(count(m,"SELF","special",1),"Zero units lost physical card");m.playTrainer(A,DynamicPassiveModifierTest.hand(m,A,"stadium").instanceId(),null);check(count(m,"SELF","special",1)&&TypedEnergyCountTest.count(m,A,active(m,A),1,"COLORLESS",null,null),"Override changed printed identity");m.attack(A,"hit");check(m.lastAttack().finalDamage()==80,"Suppressed identity failed in damage program");
    }}
    static void staged(){var a=attack("SELF","special");a=a.withEffectProgram(new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.BEFORE_ATTACK,"{\"type\":\"attach_energy\",\"source\":\"HAND\",\"target\":\"OWN_ACTIVE\",\"target_scope\":\"OWN_ACTIVE\",\"definition_ids\":\"special\",\"amount\":1,\"minimum\":1}"),a.effectProgram().triggers().getFirst())));var m=DynamicEnergyProvisionTest.fixture(EnergyProfile.fixed(EnergyType.WATER,2),a);m.attack(A,"hit");var p=m.effectCardPrompt(A);check(p!=null&&m.activePokemonView(B).damage()==0,"No real suspended attachment");var c=p.candidates().stream().filter(x->x.definitionId().equals("special")).findFirst().orElseThrow();m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(c.instanceId()));var t=m.effectAttachmentPrompt(A);m.chooseEffectAttachmentTarget(A,t.resolutionId(),t.step(),active(m,A));check(m.activePokemonView(B).damage()==80&&m.currentPlayer().equals(B),"Damage used pre-attachment identity snapshot");}
    static void copies(){var m=CopyAttackTest.fixture(CopyAttackTest.copier(),attack("SELF","energy"),EnergyType.PSYCHIC,0);CopyAttackTest.pay(m);var c=CopyAttackTest.choice(m);CopyAttackTest.copy(m,c,List.of(),List.of());check(m.lastAttack().finalDamage()==80&&m.activePokemonView(CopyAttackTest.B).attachedEnergyIds().isEmpty(),"Copied identity read provider attachments");}
    static void borrowed(){var m=GeneratedRuleBatch51Test.fixture(List.of(attack("SELF","energy")));var host=GeneratedRuleBatch51Test.active(m);GeneratedRuleBatch51Test.pay(m,host,2);GeneratedRuleBatch51Test.pay(m,GeneratedRuleBatch51Test.provider(m),1);var o=GeneratedRuleBatch51Test.option(m,host,"hit");GeneratedRuleBatch51Test.use(m,o);check(m.lastAttack().finalDamage()==70,"Borrowed identity read provider instead of holder");}
    static void schema(){for(String ids:List.of("","named|","|named","named|named"," named","named *","named||other"))reject(()->query(ids));for(String mode:List.of("UNITS","DISTINCT_TYPES"))reject(()->EnergyCountQuery.from(Map.of("count_mode",mode,"definition_ids","named")));reject(()->EnergyCountQuery.from(Map.of("count_mode","CARDS","definition_ids","named","energy_type","LIGHTNING")));
        for(String target:List.of("HAND","DECK","DISCARD","SELECT_OWN_POKEMON"))reject(()->AttackProgramRules.validate(attack(target,"named")));
        reject(()->DynamicEnergyProvisionTest.energy("{\"energyUnits\":[[\"WATER\"]],\"provisionRules\":[{\"conditions\":[{\"type\":\"ENERGY_COUNT\",\"count_mode\":\"CARDS\",\"definition_ids\":\"named\",\"value\":1}],\"energyUnits\":[[\"WATER\"],[\"WATER\"]]}]}"));
        DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"hit_points_modifier\",\"amount\":100,\"conditions\":[{\"type\":\"HAS_ENERGY\",\"count_mode\":\"CARDS\",\"definition_ids\":\"named\"}]}");
    }
}
