import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** ME5-5 current top-card names: partner requirements, damage and dynamic coin counts. */
public final class GeneratedRuleBatch37Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static UUID add(ServerMatch m,UUID p,String id){var c=hand(m,p,id);m.playBasicPokemonToBench(p,c.instanceId());return c.instanceId();}
    static void cards(ServerMatch m,List<UUID> ids){GeneratedRuleBatch23Test.cards(m,ids);}
    static AttackDefinition attack(String id){return GeneratedRuleBatch35Test.attack(id,0);}
    public static void main(String[] args){birds();partnerEvolution();pikachu();maushold();schema();System.out.println("GeneratedRuleBatch37Test passed: exact current printed names, own field boundaries, missing partners, Basic-only self attachment, source quotas, evolution/return, named damage and server coin counts including empty/short decks");}
    static void birds(){for(String id:List.of("me5-5_en_011","me5-5_en_018","me5-5_en_055"))for(boolean onBench:List.of(false,true)){
        String self=id.endsWith("011")?"Moltres":id.endsWith("018")?"Articuno":"Zapdos";EnergyType type=id.endsWith("011")?EnergyType.FIRE:id.endsWith("018")?EnergyType.WATER:EnergyType.LIGHTNING;var others=new ArrayList<>(List.of("Articuno","Moltres","Zapdos"));others.remove(self);var a=GeneratedRuleBatch8Test.ability(id);
        var m=fixture(self,others.get(0),others.get(1),type,a,GeneratedRuleBatch8Test.hit(20,false));UUID source=active(m,A);check(!m.canUseAbility(A,source,a.id()),"No partners must hide Ability");reject(()->m.useAbility(A,source,a.id()));
        m.endTurn(A);add(m,B,"p1");add(m,B,"p2");m.endTurn(B);check(!m.canUseAbility(A,source,a.id()),"Opponent partners incorrectly qualify");
        UUID one=add(m,A,"p1");add(m,A,"wrong");check(!m.canUseAbility(A,source,a.id()),"Galarian/extended name or one partner qualifies");UUID two=add(m,A,"p2");check(m.canUseAbility(A,source,a.id()),"Both exact partners must qualify");
        if(onBench)m.switchActiveByEffect(A,A,one);m.useAbility(A,source,a.id());var p=m.effectCardPrompt(A);check(p.minimum()==1&&p.maximum()==1&&p.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"Bird must select one Basic matching Energy, never supplied Special Energy");reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId())));var chosen=p.candidates().getFirst();cards(m,List.of(chosen.instanceId()));var host=onBench?m.benchPokemonView(A).stream().filter(v->v.pokemonId().equals(source)).findFirst().orElseThrow():m.activePokemonView(A);check(host.attachedEnergyIds().equals(List.of(chosen.instanceId()))&&!m.canUseAbility(A,source,a.id()),"Bird attached to Active rather than source or reused source quota");
        UUID duplicate=add(m,A,"source");check(m.canUseAbility(A,duplicate,a.id()),"Distinct source wrongly shares once-per-turn quota");m.movePokemonByEffect(A,A,two,LeavePlayDestination.HAND);check(!m.canUseAbility(A,duplicate,a.id()),"Departed partner retained name condition");
    }}
    static void partnerEvolution(){var a=GeneratedRuleBatch8Test.ability("me5-5_en_011");var m=fixture("Moltres","Articuno","Zapdos",EnergyType.FIRE,a,GeneratedRuleBatch8Test.hit(20,false));add(m,A,"p1");UUID partner=add(m,A,"p2");m.evolveByEffect(A,partner,hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);check(!m.canUseAbility(A,active(m,A),a.id()),"Covered Zapdos counted as current Pokemon");add(m,A,"p2");check(m.canUseAbility(A,active(m,A),a.id()),"Current replacement partner not counted");m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).map(CardInstance::instanceId).toList());check(!m.canUseAbility(A,active(m,A),a.id()),"Partners alone allowed attachment with no matching Basic Energy");}
    static void pikachu(){for(boolean evolve:List.of(false,true)){
        var a=attack("me5-5_en_034");var m=fixture("Pikachu","Pikachu ex","Pikachu",EnergyType.LIGHTNING,null,a);add(m,A,"p1");UUID partner=add(m,A,"p2");add(m,A,"wrong");add(m,A,"v");if(evolve)m.evolveByEffect(A,partner,hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.endTurn(A);add(m,B,"p1");m.endTurn(B);m.attack(A,a.id());check(m.lastAttack().finalDamage()==(evolve?80:120),"Pika Chain counted similar names, opponent, or covered Basic; omitted current Active");
    }}
    static void maushold(){for(int n:List.of(0,1,3))for(int deck:List.of(0,1,6)){
        var a=attack("me5-5_en_125");var m=fixture(n==0?"Maushold ex":"Maushold","Maushold","Maushold",EnergyType.COLORLESS,null,a);if(n==3){add(m,A,"p1");add(m,A,"p2");}add(m,A,"wrong");m.endTurn(A);add(m,B,"p1");m.endTurn(B);int size=m.privateView(B).deck().size();m.discardTopDeckCardsByEffect(A,B,Math.max(0,size-deck));int available=m.privateView(B).deck().size(),discard=m.privateView(B).discard().size(),before=m.randomTranscript().size();m.attack(A,a.id());var coins=m.randomTranscript().subList(before,m.randomTranscript().size()).stream().filter(e->e.bound()==2).toList();long heads=coins.stream().filter(e->e.result()==0).count();check(coins.size()==n,"Gnaw Together count must use exact own current Maushold, including Active");check(m.privateView(B).discard().size()==discard+Math.min(available,heads*2),"Gnaw Together used requested coin count instead of heads or exceeded remaining deck");
    }}
    static void schema(){
        for(String args:List.of("\"pokemon_names\":\"Moltres|Moltres\",\"value\":1","\"pokemon_names\":\"\",\"value\":1","\"pokemon_names\":\"Moltres\",\"compare_to\":\"OPPONENT\"","\"pokemon_names\":\"Moltres\",\"value\":1,\"target\":\"HAND\""))reject(()->EffectSchemaValidator.validate(new ConditionDefinition(ConditionType.POKEMON_COUNT,arguments(args),List.of())));
        for(String args:List.of("\"flips\":1,\"pokemon_names\":\"Maushold\"","\"flips\":0,\"flips_from\":\"POKEMON_COUNT\"","\"flips\":0,\"flips_from\":\"POKEMON_COUNT\",\"pokemon_names\":\"Maushold\",\"card_kind\":\"BASIC\""))reject(()->CoinFlipPolicy.from(new EffectDefinition("coin_flip",EffectTarget.SELF,arguments(args),List.of(),List.of())));
    }
    static Map<String,String> arguments(String fields){var obj=JsonParser.parseString("{"+fields+"}").getAsJsonObject();var args=new HashMap<String,String>();obj.entrySet().forEach(e->args.put(e.getKey(),e.getValue().getAsString()));return args;}
    static ServerMatch fixture(String self,String one,String two,EnergyType type,AbilityDefinition ability,AttackDefinition attack){
        String[] ids={"source","source","p1","p2","wrong","ex","v","energy","energy","special","evolved","other"};
        var names=Map.of("source",self,"p1",one,"p2",two,"wrong","Galarian "+one,"ex",two+" ex","v",self+" V","evolved","Unrelated Evolution","other","Other");
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();for(String id:Arrays.stream(ids).distinct().toList()){
            boolean pokemon=names.containsKey(id);defs.add(new CardDefinition(id,"Translated label "+id,pokemon?CardCategory.POKEMON:CardCategory.ENERGY,TrainerCategory.NONE,"J",Set.of("international"),pokemon&&!id.equals("evolved"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
            if(pokemon)profiles.put(id,new PokemonProfile(500,1,id.equals("evolved")?"p2":null,null,0,null,0,1,List.of(attack),id.equals("source")&&ability!=null?List.of(ability):List.of(),EnergyType.COLORLESS,names.get(id),id.equals("evolved")?two:null));
        }
        var catalog=new CombatCatalog(profiles,Map.of("energy",type),Map.of(),Map.of("special",EnergyProfile.fixed(type,2)));
        for(int seed=0;seed<1000;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cs=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cs.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cs);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),catalog,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals("source"))))continue;
            for(UUID p:List.of(A,B)){m.chooseOpeningActive(p,hand(m,p,"source").instanceId());m.startAfterOpening(p);}OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,22);m.drawCardsByEffect(A,B,22);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->Arrays.stream(ids).allMatch(id->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(id))))&&m.privateView(A).hand().stream().filter(c->c.definitionId().equals("p2")).count()>=2)return m;
        }throw new AssertionError("No named-field fixture");
    }
}
