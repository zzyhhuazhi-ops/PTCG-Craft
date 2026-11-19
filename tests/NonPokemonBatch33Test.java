import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch33Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("bw10_en_092","bw10_en_093","xy10_en_108","swsh5_en_131","swsh5_en_133","swsh6_en_151","swsh6_en_154","swsh7_en_153","swsh7_en_158");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static CardInstance card(ServerMatch m,UUID owner,String id){return DynamicPassiveModifierTest.hand(m,owner,id);}
    static ServerMatch game(String id,boolean wrong,boolean weakness){
        var trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();var grant=ToolGrantedAttack.fromProgram(trainer.ongoingProgram());
        String name=wrong?"Other":grant.arguments().getOrDefault("pokemon_name","Scroll Holder");
        Set<BattleStyle> styles=wrong||!grant.arguments().containsKey("holder_battle_style")?Set.of():Set.of(BattleStyle.valueOf(grant.arguments().get("holder_battle_style")));
        var defs=new ArrayList<CardDefinition>();String[] ids={"base","base","base","energy","energy","energy","energy","double","trainer","shield"};
        for(String key:Arrays.stream(ids).distinct().toList())defs.add(new CardDefinition(key,key,key.equals("base")?CardCategory.POKEMON:Set.of("energy","double").contains(key)?CardCategory.ENERGY:CardCategory.TRAINER,
            Set.of("trainer","shield").contains(key)?TrainerCategory.TOOL:TrainerCategory.NONE,"H",Set.of("international"),key.equals("base"),false,60,Map.of(),EffectSupport.SUPPORTED,null,Set.of(),key.equals("base")?Set.of(EnergyType.GRASS):Set.of(),key.equals("base")?styles:Set.of()));
        var all=new EnergyProfile.Unit(EnumSet.allOf(EnergyType.class));
        var combat=new CombatCatalog(Map.of("base",new PokemonProfile(1000,1,null,weakness?EnergyType.GRASS:null,weakness?2:0,null,0,1,List.of(GeneratedRuleBatch8Test.hit(10,false)),List.of(),EnergyType.GRASS,name,null)),Map.of(),
            Map.of("trainer",trainer,"shield",NonPokemonBatch2Test.DATA.trainer("xy1_en_119").orElseThrow()),
            Map.of("energy",new EnergyProfile(List.of(all)),"double",new EnergyProfile(List.of(all,all))));
        for(int seed=0;seed<300;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cs=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cs.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cs);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("base")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,23);m.drawCardsByEffect(A,B,23);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("energy")).count()>=6&&List.of("trainer","double","shield").stream().allMatch(k->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(k)))))return m;
        }throw new AssertionError("Tool attack fixture unavailable");
    }
    static void attach(ServerMatch m,UUID owner,String id,int n){for(int i=0;i<n;i++)m.attachEnergyByEffect(A,owner,EffectSourceZone.HAND,card(m,owner,id).instanceId(),owner,m.activePokemonView(owner).pokemonId());}
    static void equip(ServerMatch m){m.playTrainer(A,card(m,A,"trainer").instanceId(),m.activePokemonView(A).pokemonId());}
    static ServerMatch.BorrowedAttackOption option(ServerMatch m){return m.borrowedAttackOptions(A).stream().filter(o->o.abilityId().startsWith("tool:")).findFirst().orElseThrow();}
    static void strike(ServerMatch m,boolean bench){var o=option(m);check(o.attack()!=null,o.attackId()+": "+o.unsupportedReasonZh());var targets=o.attack().damageTarget().requiresTargetChoice()?List.of(bench?m.benchPokemonView(B).getFirst().pokemonId():m.activePokemonView(B).pokemonId()):List.<UUID>of();GeneratedRuleBatch51Test.use(m,o,null,targets,null);}
    static void mainAttack(String id){
        var m=game(id,false,true);equip(m);attach(m,A,"energy",5);
        if(id.equals("swsh5_en_133"))m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),3);
        if(id.equals("swsh6_en_151"))attach(m,B,"double",1);
        strike(m,id.equals("bw10_en_093"));NonPokemonBatch2Test.settle(m);
        int damage=switch(id){case "bw10_en_092","xy10_en_108"->400;case "bw10_en_093"->0;case "swsh5_en_131"->60;case "swsh5_en_133"->80;case "swsh6_en_151"->220;case "swsh6_en_154"->120;case "swsh7_en_153"->180;default->600;};
        check(m.activePokemonView(B).damage()==damage,"Tool damage/type/weakness: "+id+" "+m.activePokemonView(B).damage());
        check(m.benchPokemonView(B).getFirst().damage()==(id.equals("bw10_en_093")?100:id.equals("swsh5_en_131")?30:0),"Tool Bench damage: "+id);
        check(m.activePokemonView(A).attachedEnergyIds().size()==(id.equals("bw10_en_092")||id.equals("swsh7_en_153")?3:id.equals("xy10_en_108")?2:id.equals("swsh7_en_158")?0:5),"Tool Energy discard: "+id);
    }
    static void limits(){for(String id:IDS){var m=game(id,true,false);equip(m);check(m.borrowedAttackOptions(A).isEmpty(),"Wrong holder can borrow "+id);
        m=game(id,false,false);equip(m);check(!m.canPayActiveAttackCost(A,option(m).attack()),"Unpaid Tool attack");attach(m,A,"energy",5);check(m.canPayActiveAttackCost(A,option(m).attack()),"Paid Tool attack unavailable");m.disableToolEffectsByEffect(A,A,m.activePokemonView(A).pokemonId());check(m.borrowedAttackOptions(A).isEmpty(),"Disabled Tool retains attack");
    }}
    static void unitDiscard(){var m=game("bw10_en_092",false,false);equip(m);attach(m,A,"double",1);attach(m,A,"energy",1);strike(m,false);
        var q=m.effectCardPrompt(A);check(q!=null,"Missing Tool Energy choice");
        var chosen=q.candidates().stream().filter(c->c.definitionId().equals("double")).findFirst().orElseThrow();
        m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(chosen.instanceId()));NonPokemonBatch2Test.settle(m);
        check(m.activePokemonView(A).attachedEnergyIds().size()==1&&m.privateView(A).discard().contains(chosen),"Two-energy card did not pay two Energy");
    }
    static void pierce(){for(String id:List.of("bw10_en_092","swsh6_en_154")){var m=game(id,false,true);equip(m);attach(m,A,"energy",5);m.attachToolByEffect(A,B,EffectSourceZone.HAND,card(m,B,"shield").instanceId(),B,m.activePokemonView(B).pokemonId());strike(m,false);NonPokemonBatch2Test.settle(m);check(m.activePokemonView(B).damage()==(id.equals("bw10_en_092")?400:120),"Tool attack did not ignore defender reduction");}}
    public static void main(String[] args){for(String id:IDS)mainAttack(id);limits();unitDiscard();pierce();System.out.println("NonPokemonBatch33Test passed: nine printed Tool attacks, live host/style and suppression, paid costs, actual holder type, selected/spread Bench damage, Energy units and defender-effect piercing");}
}

