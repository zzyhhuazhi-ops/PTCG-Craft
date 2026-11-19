import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Source movement prohibition is distinct from destination immunity and healing. */
public final class CounterTransferRulesTest {
    static final UUID A=new UUID(0,7801),B=new UUID(0,7802);
    static final String ALL="{\"type\":\"move_damage_counter\",\"target\":\"CHOSEN_POKEMON\",\"destination\":\"OPPONENT_ACTIVE\",\"all\":true}";
    static final String SELECT="{\"type\":\"select_pokemon\",\"target\":\"SELECT_OWN_POKEMON\",\"position\":\"BENCH\",\"effects\":["+ALL+"]}";
    static final String BAN="{\"type\":\"prevent_damage_counter_move\",\"target\":\"ALL_OWN_POKEMON\"},{\"type\":\"prevent_damage_counter_move\",\"target\":\"ALL_OPPONENT_POKEMON\"}";
    static void check(boolean b,String s){GeneratedRuleBatch8Test.check(b,s);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static AbilityDefinition ban(){return BundledCombatData.ability(JsonParser.parseString("{\"id\":\"watchful-eye\",\"nameZh\":\"Watchful Eye\",\"kind\":\"PASSIVE\",\"effectSupport\":\"SUPPORTED\",\"effects\":["+BAN+"]}").getAsJsonObject(),EffectRegistry.standard());}
    static AttackDefinition attack(){String withHealingProbe=SELECT.replace(ALL,ALL+",{\"type\":\"draw\",\"amount\":1,\"conditions\":[{\"type\":\"HEALED_THIS_TURN\",\"target\":\"CHOSEN_POKEMON\"}]}");return DamageTriggeredAbilityTest.attack(0,SourceEnergyCounterTest.after(withHealingProbe));}
    record Fixture(ServerMatch m,UUID blocker){}
    static Fixture game(UUID blockerOwner,boolean blockerActive){
        var defs=new ArrayList<CardDefinition>();for(String id:List.of("base","blocker","evolved","energy"))defs.add(new CardDefinition(id,id,id.equals("energy")?CardCategory.ENERGY:CardCategory.POKEMON,TrainerCategory.NONE,"J",Set.of("international"),Set.of("base","blocker").contains(id),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
        var profiles=new HashMap<String,PokemonProfile>();for(String id:List.of("base","blocker","evolved"))profiles.put(id,new PokemonProfile(1000,1,id.equals("evolved")?"blocker":null,EnergyType.FIRE,2,EnergyType.GRASS,30,1,List.of(attack()),id.equals("blocker")?List.of(ban()):List.of(),EnergyType.FIRE));
        var catalog=new CombatCatalog(profiles,Map.of("energy",EnergyType.FIRE));String[] pool={"base","base","blocker","evolved","energy"};
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,pool[i%pool.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.getFirst(),decks.getLast(),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("base")).count()<2||m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals("blocker"))))continue;
            UUID blocker=null;for(UUID p:List.of(A,B)){
                var first=hand(m,p,p.equals(blockerOwner)&&blockerActive?"blocker":"base");m.chooseOpeningActive(p,first.instanceId());
                if(p.equals(blockerOwner)&&blockerActive)blocker=first.instanceId();m.placeOpeningBench(p,hand(m,p,"base").instanceId());
                if(p.equals(blockerOwner)&&!blockerActive){var c=hand(m,p,"blocker");blocker=c.instanceId();m.placeOpeningBench(p,c.instanceId());}m.startAfterOpening(p);
            }
            OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,30);m.drawCardsByEffect(A,B,30);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().anyMatch(p->Arrays.stream(pool).anyMatch(id->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals(id)))))continue;
            return new Fixture(m,blocker==null?null:PokemonIdentityTestSupport.logical(m,blockerOwner,blocker));
        }throw new AssertionError("No counter-transfer fixture");
    }
    static UUID source(ServerMatch m){return m.benchPokemonView(A).stream().filter(p->p.topCardId().equals("base")).findFirst().orElseThrow().pokemonId();}
    static int damage(ServerMatch m,UUID owner,UUID id){return m.activePokemonView(owner).pokemonId().equals(id)?m.activePokemonView(owner).damage():m.benchPokemonView(owner).stream().filter(p->p.pokemonId().equals(id)).findFirst().orElseThrow().damage();}
    static void place(ServerMatch m,UUID owner,UUID id,int n){while(n>0){int batch=Math.min(60,n);m.placeDamageCountersByEffect(A,owner,id,batch);n-=batch;}}
    static void shield(ServerMatch m,UUID owner,UUID id,String kind){var effect=EffectRegistry.standard().parse(JsonParser.parseString(EffectOriginProtectionTest.shield(kind,"EFFECTS_ONLY","SELF","LEAVES_PLAY")).getAsJsonObject());
        if(owner.equals(B))m.endTurn(A);m.executeEffectProgramByRule(owner,id,new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(effect)))),EffectTrigger.ON_CARD_PLAYED);if(owner.equals(B))m.endTurn(B);
    }
    static void choose(ServerMatch m,UUID id){var p=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,p.resolutionId(),p.step(),id);}
    public static void main(String[] args){whole();empty();immunity();prohibition();liveSources();schema();System.out.println("CounterTransferRulesTest passed: all current counters including0/>60, own Bench authority, no healing or damage, source ban before removal, destination immunity after removal, live multiple sources/suppression/evolution/removal and closed schema");}
    static void whole(){for(int n:List.of(0,1,70)){
        var m=game(null,false).m();UUID source=source(m);place(m,A,source,n);int handBefore=m.privateView(A).hand().size();m.attack(A,"hit");var p=m.effectPokemonPrompt(A);var pending=m.clientView(A);
        check(p.targets().size()==1&&p.targets().getFirst().pokemonId().equals(source),"Wrong source scope");
        reject(()->m.chooseEffectPokemon(B,p.resolutionId(),p.step(),source));reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),active(m,A)));reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),active(m,B)));
        check(pending.equals(m.clientView(A)),"Forged selection mutated state");choose(m,source);check(damage(m,A,source)==0&&m.activePokemonView(B).damage()==n*10&&m.currentPlayer().equals(B),"All counters used count cap or weakness/resistance");
        check(m.privateView(A).hand().size()==handBefore,"Transfer marked healing inside the attack continuation");reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),source));
    }}
    static void empty(){var m=game(null,false).m();m.movePokemonByEffect(A,A,source(m),LeavePlayDestination.HAND);m.attack(A,"hit");check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Empty Bench stalled transfer");}
    static void immunity(){
        var m=game(null,false).m();UUID from=source(m);place(m,A,from,70);shield(m,B,active(m,B),"ATTACK");m.attack(A,"hit");choose(m,from);check(damage(m,A,from)==0&&m.activePokemonView(B).damage()==0,"Immune destination must not restore source counters");
    }
    static void prohibition(){for(UUID owner:List.of(A,B))for(boolean active:List.of(false,true)){
        var f=game(owner,active);var m=f.m();UUID from=source(m);place(m,A,from,5);m.attack(A,"hit");choose(m,from);check(damage(m,A,from)==50&&m.activePokemonView(B).damage()==0,"Live Watchful Eye lost source counters");
    }
        var immune=game(B,false);var m=immune.m();UUID from=source(m);place(m,A,from,2);shield(m,A,from,"ABILITY");m.attack(A,"hit");choose(m,from);check(damage(m,A,from)==0&&m.activePokemonView(B).damage()==20,"Source immunity to opposing Ability did not exclude its continuous rule");
    }
    static void liveSources(){for(String mode:List.of("DISABLE","EVOLVE","REMOVE")){
        var f=game(A,false);var m=f.m();UUID from=source(m);place(m,A,from,3);m.moveDamageCountersByEffect(A,A,from,B,active(m,B),1);check(damage(m,A,from)==30,"Fixed public transfer bypassed ban");
        if(mode.equals("DISABLE"))m.disableAbilityEffectsByEffect(A,A,f.blocker());
        if(mode.equals("EVOLVE"))m.evolve(A,f.blocker(),hand(m,A,"evolved").instanceId());
        if(mode.equals("REMOVE"))m.movePokemonByEffect(A,A,f.blocker(),LeavePlayDestination.HAND);
        m.moveDamageCountersByEffect(A,A,from,B,active(m,B),1);check(damage(m,A,from)==20&&m.activePokemonView(B).damage()==10,"Stale movement prohibition after "+mode);
        if(mode.equals("DISABLE")){m.endTurn(A);m.endTurn(B);}
        if(mode.equals("EVOLVE"))m.devolvePokemonToHandByEffect(A,A,f.blocker());
        if(mode.equals("REMOVE"))m.playBasicPokemonToBench(A,hand(m,A,"blocker").instanceId());
        m.moveDamageCountersByEffect(A,A,from,B,active(m,B),1);check(damage(m,A,from)==20&&m.activePokemonView(B).damage()==10,"Prohibition did not return after "+mode);
    }
        var f=game(B,false);var m=f.m();UUID from=source(m);place(m,A,from,3);var c=hand(m,A,"blocker");m.playBasicPokemonToBench(A,c.instanceId());m.disableAbilityEffectsByEffect(A,B,f.blocker());m.moveDamageCountersByEffect(A,A,from,B,active(m,B),1);check(damage(m,A,from)==30,"Second source did not preserve prohibition");
        m.disableAbilityEffectsByEffect(A,A,c.instanceId());m.moveDamageCountersByEffect(A,A,from,B,active(m,B),1);check(damage(m,A,from)==20,"Both disabled sources still prohibit transfer");
    }
    static void schema(){for(String bad:List.of(ALL.replace("true","false"),ALL.replace("\"all\":true","\"all\":true,\"amount\":1"),ALL.replace("CHOSEN_POKEMON","OWN_ACTIVE"),ALL.replace("OPPONENT_ACTIVE","CHOSEN_POKEMON")))reject(()->EffectRegistry.standard().parse(JsonParser.parseString(bad).getAsJsonObject()));
        for(String position:List.of("ACTIVE","ANY"))reject(()->AttackProgramRules.validate(DamageTriggeredAbilityTest.attack(0,SourceEnergyCounterTest.after(SELECT.replace("BENCH",position)))));
        reject(()->TriggeredAbilityRules.validate(DamageTriggeredAbilityTest.ability("bad",true,false,false,SELECT)));
        var m=game(null,false).m();reject(()->m.moveDamageCountersByEffect(A,A,source(m),B,active(m,B),61));
    }
}
