import com.google.gson.*;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.nio.file.*;
import java.util.*;

/** Printed conditions retain Stadium ownership and the real immediately preceding evolution name. */
public final class PrintedDamageConditionTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    record Case(String card,JsonObject attack,JsonObject condition,int bonus) { }
    static final List<Case> CASES=load();
    static List<Case> load(){try{
        var out=new ArrayList<Case>();
        for(String line:Files.readAllLines(Path.of("outputs/pokemon-conditional-damage-programs.jsonl"))){
            var row=JsonParser.parseString(line).getAsJsonObject();
            for(var value:row.getAsJsonObject("conversion").getAsJsonObject("combatEntry").getAsJsonArray("attacks")){
                var a=value.getAsJsonObject();if(!a.has("effects"))continue;
                for(var e:a.getAsJsonArray("effects")){
                    var effect=e.getAsJsonObject();if(!effect.get("type").getAsString().equals("conditional"))continue;
                    var condition=effect.getAsJsonArray("conditions").get(0).getAsJsonObject();
                    if(!condition.has("own_stadium")&&!condition.has("from_pokemon_name"))continue;
                    out.add(new Case(row.get("id").getAsString(),a,condition,effect.getAsJsonArray("effects").get(0).getAsJsonObject().get("amount").getAsInt()));
                }
            }
        }return out;
    }catch(Exception e){throw new RuntimeException(e);}}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static AttackDefinition attack(Case c){var a=c.attack.deepCopy();a.add("energyCost",new JsonObject());a.addProperty("damageType","COLORLESS");return BundledCombatData.attack(a,EffectRegistry.standard());}
    static int base(Case c){return c.attack.get("baseDamage").getAsInt();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch28Test.hand(m,p,id);}
    static void next(ServerMatch m,UUID p){while(!m.currentPlayer().equals(p))m.endTurn(m.currentPlayer());}
    public static void main(String[] args){stadiums();evolutions();stageSkip();schema();ai();System.out.println("PrintedDamageConditionTest passed: "+CASES.size()+" actual source attacks, both seats, Stadium ownership/change/absence, exact evolution names, skip-stage, switch/devolve/expiry, invalid schema and 32 four-tier AI matches");}
    static ServerMatch stadiumGame(Case c,UUID p,int owner){
        var config=new GeneratedRuleBatch28Test.Config();config.attack=attack(c);config.defenderAttack=config.attack;
        config.trainer=DynamicPassiveModifierTest.plainStadium();var m=GeneratedRuleBatch28Test.fixture(config);
        if(owner!=0){UUID seat=owner==1?p:other(p);next(m,seat);m.playTrainer(seat,hand(m,seat,"trainer").instanceId(),null);}
        next(m,p);return m;
    }
    static void stadiums(){for(var c:CASES)if(c.condition.has("own_stadium"))for(UUID p:List.of(A,B))for(int owner:List.of(0,1,2)){
        var m=stadiumGame(c,p,owner);var any=new ConditionDefinition(ConditionType.STADIUM_IN_PLAY,Map.of("target","SELF"),List.of());
        check(m.matchesEffectCondition(p,active(m,p),any)==(owner!=0),"Legacy any-Stadium condition changed");
        m.attack(p,attack(c).id());check(m.lastAttack().finalDamage()==base(c)+(owner==1?c.bonus:0),"Stadium owner bonus "+c.card+" "+p+" "+owner);
    }
        var c=CASES.stream().filter(x->x.condition.has("own_stadium")).findFirst().orElseThrow();
        for(UUID p:List.of(A,B)){var m=stadiumGame(c,p,1);m.discardStadiumByEffect(p);m.attack(p,attack(c).id());check(m.lastAttack().finalDamage()==base(c),"Discarded Stadium bonus persisted");}
    }
    static PokemonProfile profile(AttackDefinition a,String name,String parent,String parentName){return new PokemonProfile(1000,0,parent,null,0,null,0,1,List.of(a),List.of(),EnergyType.COLORLESS,name,parentName);}
    static ServerMatch evolutionGame(Case c,UUID p,String mode){
        String required=c.condition.get("from_pokemon_name").getAsString();String actual=mode.equals("wrong")?"Ditto Prism Star":required;
        var config=new GeneratedRuleBatch28Test.Config();config.attack=attack(c);config.defenderAttack=config.attack;
        config.names=Map.of("attacker",actual,"defender",actual);
        config.restoredProfiles=Map.of("evolved",profile(config.attack,"Evolved","attacker",actual),"defender_evolved",profile(config.attack,"Evolved","defender",actual));
        var m=GeneratedRuleBatch28Test.fixture(config);next(m,p);
        UUID id=mode.equals("switch")?m.benchPokemonView(p).getFirst().pokemonId():active(m,p);
        if(!mode.equals("none"))m.evolveByEffect(p,id,hand(m,p,p.equals(A)?"evolved":"defender_evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        if(mode.equals("switch"))m.switchActiveByEffect(p,p,id);
        if(mode.equals("devolve"))m.devolvePokemonToHandByEffect(p,p,id);
        if(mode.equals("expired")){m.endTurn(p);m.endTurn(other(p));}
        return m;
    }
    static void evolutions(){for(var c:CASES)if(c.condition.has("from_pokemon_name"))for(UUID p:List.of(A,B))for(String mode:List.of("none","right","wrong","switch","devolve","expired")){
        var m=evolutionGame(c,p,mode);m.attack(p,attack(c).id());int expected=base(c)+(Set.of("right","switch").contains(mode)?c.bonus:0);
        check(m.lastAttack().finalDamage()==expected,"Evolution-source bonus "+c.card+" "+p+" "+mode+" "+m.lastAttack().finalDamage()+" != "+expected);
    }}
    static void stageSkip(){for(UUID p:List.of(A,B))for(boolean skip:List.of(false,true)){
        var a=GeneratedRuleBatch8Test.hit(10,false);var profiles=Map.of("base",profile(a,"Shinx",null,null),"middle",profile(a,"Luxio","base","Shinx"),"top",profile(a,"Luxray","middle","Luxio"));
        var f=new BattlefieldState(A,B,profiles,new ServerRandom(1));f.beginTurn(p);f.placeInitialActive(p,new CardInstance(UUID.randomUUID(),p,"base"));
        var id=f.activeView(p).pokemonId();f.beginTurn(other(p));f.beginTurn(p);
        if(!skip)f.evolve(p,id,new CardInstance(UUID.randomUUID(),p,"middle"));
        f.evolve(p,id,new CardInstance(UUID.randomUUID(),p,"top"),skip?EvolutionPermission.CARD_EFFECT_SKIP_ONE_STAGE_STRICT_TIMING:EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        check(f.evolvedThisTurnFrom(p,id,"luxio")==!skip,"Rare Candy wrongly counted a skipped Luxio");
        check(f.evolvedThisTurnFrom(p,id,"Shinx")==skip,"Actual stage-skip source not retained");
        f.devolveByEffect(p,id);check(!f.evolvedThisTurnFrom(p,id,skip?"Shinx":"Luxio"),"Devolution inherited removed top card's evolution event");
    }}
    static void schema(){for(String condition:List.of("{\"type\":\"EVOLVED_THIS_TURN\",\"from_pokemon_name\":\" \"}","{\"type\":\"STADIUM_IN_PLAY\",\"own_stadium\":\"yes\"}")){
        GeneratedRuleBatch8Test.reject(()->{var a=BundledCombatData.attack(JsonParser.parseString("{\"id\":\"bad\",\"nameZh\":\"bad\",\"energyCost\":{},\"baseDamage\":10,\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":10,\"conditions\":["+condition+"]}]}").getAsJsonObject(),EffectRegistry.standard());AttackProgramRules.validate(a);});
    }}
    static void ai(){var stadium=CASES.stream().filter(c->c.card.equals("swsh10_en_025")).findFirst().orElseThrow();var raid=CASES.stream().filter(c->c.card.equals("swsh2_en_062")).findFirst().orElseThrow();
        for(UUID p:List.of(A,B))for(var level:AiDifficulty.values())for(String mode:List.of("own","opponent","normal","wrong")){
            boolean s=mode.equals("own")||mode.equals("opponent");var c=s?stadium:raid;var m=s?stadiumGame(c,p,mode.equals("own")?1:2):evolutionGame(c,p,mode.equals("normal")?"right":"wrong");
            var session=MatchSession.multiplayer(m,A,B);var view=session.view(p,p);var option=view.options().stream().filter(o->o.enabled()&&o.type()==ActionType.ATTACK).findFirst().orElseThrow();session.submit(p,p,view.revision(),option.index());
            check(m.lastAttack().finalDamage()==base(c)+(Set.of("own","normal").contains(mode)?c.bonus:0),"AI attack condition resolved incorrectly");
            AiCardOperationTest.settle(session,level);AiCardOperationTest.finish(session,level);check(m.phase()==MatchPhase.FINISHED,"AI stalled on printed conditional damage "+level);
        }
    }
}
