import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Real Ancient Bulwark / Lava Wall prints and damage-time interactions with existing printed attacks. */
public final class GeneratedRuleBatch57Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> BASTIODON=List.of("me5_en_062","me5_en_093","mebsp_en_085");
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static void attach(ServerMatch m,UUID p,String id){DynamicAttackSourceGuardTest.attach(m,p,active(m,p),id);}
    static AbilityDefinition guard(String id){return GeneratedRuleBatch8Test.ability(id);}
    static AttackDefinition attack(String id,String name){for(var e:GeneratedRuleBatch8Test.entry(id).getAsJsonArray("attacks")){var j=e.getAsJsonObject();if(j.get("id").getAsString().equals(name))return BundledCombatData.attack(j,EffectRegistry.standard());}throw new AssertionError(id+" / "+name);}
    static EnergyProfile energy(String id){return BundledCombatData.specialEnergy(GeneratedRuleBatch8Test.entry(id),EffectRegistry.standard());}
    static ServerMatch game(AttackDefinition attack,List<AbilityDefinition> abilities,EnergyType type,EnergyProfile special){
        String[] ids={"basic","basic","basic","basic","energy","energy","special","evolved"};var defs=new ArrayList<CardDefinition>();
        for(String id:Arrays.stream(ids).distinct().toList())defs.add(new CardDefinition(id,id,Set.of("basic","evolved").contains(id)?CardCategory.POKEMON:CardCategory.ENERGY,TrainerCategory.NONE,"H",Set.of("international"),id.equals("basic"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
        var catalog=new CombatCatalog(Map.of("basic",new PokemonProfile(700,0,null,null,0,null,0,1,List.of(attack),abilities),
            "evolved",new PokemonProfile(700,0,"basic",null,0,null,0,1,List.of(attack),abilities)),Map.of("energy",type),Map.of(),Map.of("special",special));
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).count()<3))continue;
            OpeningTestSupport.complete(m,A,B,2);m.drawCardsByEffect(A,A,28);m.drawCardsByEffect(A,B,28);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("energy")).count()>=4&&m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals("special"))&&m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals("evolved"))))return m;
        }throw new AssertionError("No real-program fixture");
    }
    static ServerMatch game(AttackDefinition a,AbilityDefinition guard,EnergyType type){return game(a,List.of(guard),type,EnergyProfile.fixed(type,2));}
    public static void main(String[] args){printed();bulwarkPrints();inferno();secondaryDamage();gaia();ignition();hide();gust();heatran();
        System.out.println("GeneratedRuleBatch57Test passed: all four new prints; actual costs and metadata; three Ancient Bulwark prints, six Inferno X prints, all ten published discard-before-Bench programs, real Double Turbo and Ignition provision, Gaia Impact, Hide 'n' Sneak, pre-damage gust, actual Heatran damage/status/expiry");}
    static void printed(){var db=BundledRules.loadCardDefinitions();for(String id:BASTIODON){check(db.find(id).orElseThrow().effectSupport()==EffectSupport.SUPPORTED,"Missing whole card "+id);var j=GeneratedRuleBatch8Test.entry(id);var a=attack(id,"hammer-in");check(j.get("hitPoints").getAsInt()==160&&a.baseDamage()==160&&a.energyCost().equals(Map.of(EnergyType.METAL,2,EnergyType.COLORLESS,1)),"Bastiodon printed facts changed "+id);}
        var a=attack("me5_en_007","lava-wall");check(db.find("me5_en_007").orElseThrow().effectSupport()==EffectSupport.SUPPORTED&&a.baseDamage()==120&&a.energyCost().equals(Map.of(EnergyType.FIRE,2,EnergyType.COLORLESS,1)),"Heatran printed facts changed");
        var singe=attack("me5_en_007","singe");check(singe.baseDamage()==0&&singe.energyCost().equals(Map.of(EnergyType.FIRE,1))&&singe.conditions().getFirst().condition()==SpecialCondition.BURNED,"Singe changed");
    }
    static void bulwarkPrints(){for(String id:BASTIODON)for(int n:List.of(0,2,3)){
        var m=game(GeneratedRuleBatch8Test.hit(90,true),guard(id),EnergyType.COLORLESS);for(int i=0;i<n;i++)attach(m,A,"energy");m.attack(A,"hit");check(m.benchPokemonView(B).stream().allMatch(v->v.damage()==(n<=2?0:90)),"Real guard did not protect entire Bench "+id+"/"+n);
    }}
    static void inferno(){for(String id:List.of("me2_en_013","me2_en_109","me2_en_125","me2_en_130","mebsp_en_023","mebsp_en_029"))for(int n:List.of(2,3)){
        var a=PlannedEnergyDiscardTest.inferno(id);var m=game(a,guard("me5_en_062"),EnergyType.FIRE);for(int i=0;i<n;i++)attach(m,A,"energy");m.attack(A,a.id());var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));
        check(m.lastAttack().finalDamage()==(n==3?90:0)&&m.activePokemonView(A).attachedEnergyIds().size()==n-1,"Official Inferno X / Ancient Bulwark timing "+id+"/"+n);
    }}
    static void secondaryDamage(){var turbo=energy("swsh9_en_151");var rows=List.of(
        List.of("me2-5_en_033","flamebody-cannon","90"),List.of("sv9_en_027","flamebody-cannon","90"),List.of("svbsp_en_181","flamebody-cannon","90"),
        List.of("sm3_en_031","tornado-shot","30"),List.of("smalt_en_058","tornado-shot","30"),List.of("sm7-5_en_006","fire-stream","20"),List.of("smalt_en_108","fire-stream","20"),
        List.of("swsh7_en_060","teraspark","40"),List.of("swshalt_en_057","teraspark","40"),List.of("swshalt_en_141","teraspark","40"));
        for(var row:rows)for(boolean withTurbo:List.of(false,true)){
            var a=attack(row.getFirst(),row.get(1));var type=a.energyCost().keySet().stream().filter(t->t!=EnergyType.COLORLESS).findFirst().orElseThrow();var m=game(a,List.of(guard("me5_en_062")),type,turbo);
            for(int i=0;i<(withTurbo?2:3);i++)attach(m,A,"energy");if(withTurbo)attach(m,A,"special");m.attack(A,a.id());
            for(int i=0;i<8&&m.hasPendingEffectChoice();i++){
                if(m.hasPendingPokemonSelection()){var p=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,p.resolutionId(),p.step(),p.targets().getFirst().pokemonId());}
                else{var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),p.candidates().stream().limit(p.minimum()).map(CardInstance::instanceId).toList());}
            }
            check(!m.hasPendingEffectChoice()&&m.lastAttack().finalDamage()==a.baseDamage()-(withTurbo?20:0),"Real primary attack failed "+row);
            int expected=Math.max(0,Integer.parseInt(row.get(2))-(withTurbo?20:0));var damaged=m.benchPokemonView(B).stream().mapToInt(v->v.damage()).sum();int targets=Set.of("fire-stream","teraspark").contains(a.id())?2:1;
            check(damaged==expected*targets,"Secondary source facts after discard "+row+"/"+withTurbo+" got "+damaged);
        }
    }
    static void gaia(){var a=attack("me5_en_047","gaia-impact");var m=game(a,guard("me5_en_062"),EnergyType.FIGHTING);for(int i=0;i<3;i++)attach(m,A,"energy");m.attack(A,a.id());check(m.lastAttack().finalDamage()==190&&m.activePokemonView(A).attachedEnergyIds().isEmpty(),"Gaia Impact ordinary discard occurred before guard check");}
    static void ignition(){for(String id:List.of("me5_en_068","me5_en_094"))for(boolean evolved:List.of(false,true)){
        var a=attack(id,"feather-rondo");var m=game(a,List.of(guard("me5_en_062")),EnergyType.COLORLESS,energy("me2_en_124"));
        if(evolved)m.evolveByEffect(A,active(m,A),hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);attach(m,A,"special");m.attack(A,a.id());
        check(m.lastAttack().finalDamage()==(evolved?140:0),"Ignition supplied units confused with one physical card "+id+"/"+evolved);
        check(m.activePokemonView(A).attachedEnergyIds().isEmpty(),"Ignition end-turn discard missing");
    }}
    static void hide(){var m=game(GeneratedRuleBatch8Test.hit(90,false),List.of(guard("me5_en_062"),guard("me5_en_034")),EnergyType.PSYCHIC,EnergyProfile.fixed(EnergyType.PSYCHIC,2));attach(m,A,"energy");m.attack(A,"hit");check(m.lastAttack().finalDamage()==0,"Attacker Hide 'n' Sneak wrongly negated defender-side guard");}
    static void gust(){var a=attack("sv1_en_147","drag-off");var m=DynamicAttackSourceGuardTest.positionGame(a,guard("me5_en_062"));attach(m,A,"energy");attach(m,A,"energy");m.attack(A,a.id());var p=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,p.resolutionId(),p.step(),p.targets().getFirst().pokemonId());check(m.lastAttack().finalDamage()==30,"Actual pre-damage gust did not remove Benched source guard");}
    static void heatran(){var a=attack("me5_en_007","lava-wall");for(boolean burned:List.of(false,true)){
        var m=game(a,List.of(),EnergyType.FIRE,EnergyProfile.fixed(EnergyType.FIRE,2));for(UUID p:List.of(A,B))for(int i=0;i<3;i++)attach(m,p,"energy");m.attack(A,a.id());check(m.lastAttack().finalDamage()==120,"Actual Lava Wall damage missing");if(burned)m.applySpecialConditionByEffect(B,B,SpecialCondition.BURNED);m.attack(B,a.id());check(m.lastAttack().finalDamage()==(burned?0:120),"Actual Lava Wall status guard wrong");m.endTurn(A);
        if(!m.activePokemonView(B).conditions().contains(SpecialCondition.BURNED))m.applySpecialConditionByEffect(B,B,SpecialCondition.BURNED);m.attack(B,a.id());check(m.lastAttack().finalDamage()==120,"Actual Lava Wall expiration wrong");
    }}
}
