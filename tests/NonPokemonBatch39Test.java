import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch39Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm11-5_en_055","sm7_en_139","swsh3_en_172","swsh4_en_156","swsh9_en_144","swsh9_en_185","xy9_en_102");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static CardInstance card(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static void play(ServerMatch m){m.playTrainer(A,card(m,A,"trainer").instanceId(),null);}
    static void pick(ServerMatch m,UUID p,UUID id){var q=m.effectPokemonPrompt(p);m.chooseEffectPokemon(p,q.resolutionId(),q.step(),id);}
    static void cards(ServerMatch m,UUID p,List<UUID> ids){var q=m.effectCardPrompt(p);m.submitEffectCards(p,q.resolutionId(),q.step(),ids);}
    static void settle(ServerMatch m){NonPokemonBatch2Test.settle(m);}
    static ServerMatch game(String id){return game(id,"Geodude",EnergyType.FIRE,Set.of(),0);}
    static ServerMatch game(String id,String name,EnergyType type,Set<PokemonFamily> families,int firstSeed){
        var trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();
        String[] ids={"basic","basic","basic","energy","energy","wrong","special","trainer","evolved","stage2"};
        var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String key:Arrays.stream(ids).distinct().toList()){
            boolean pokemon=Set.of("basic","evolved","stage2").contains(key),energy=Set.of("energy","wrong","special").contains(key);
            defs.add(new CardDefinition(key,key,pokemon?CardCategory.POKEMON:energy?CardCategory.ENERGY:CardCategory.TRAINER,key.equals("trainer")?trainer.category():TrainerCategory.NONE,"H",Set.of("international"),key.equals("basic"),key.equals("energy")||key.equals("wrong"),60,Map.of(),EffectSupport.SUPPORTED,null,pokemon?families:Set.of(),pokemon?Set.of(type):Set.of()));
            if(pokemon)profiles.put(key,new PokemonProfile(500,1,key.equals("evolved")?"basic":key.equals("stage2")?"evolved":null,null,0,null,0,1,List.of(GeneratedRuleBatch8Test.hit(100,false)),List.of(),type,name,null));
        }
        var combat=new CombatCatalog(profiles,Map.of("energy",EnergyType.FIRE,"wrong",EnergyType.WATER),Map.of("trainer",trainer),Map.of("special",EnergyProfile.fixed(EnergyType.FIRE,2)));
        for(int seed=firstSeed;seed<firstSeed+300;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID p:List.of(A,B)){var cs=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cs.add(new CardInstance(new UUID(p.getLeastSignificantBits(),i+1),p,ids[i%ids.length]));decks.add(cs);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,28);m.drawCardsByEffect(A,B,28);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->Arrays.stream(ids).allMatch(k->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(k)))))return m;
        }throw new AssertionError("No attachment fixture");
    }
    static void evolve(ServerMatch m,UUID p,UUID host,int stage){for(int i=0;i<stage;i++)m.evolveByEffect(p,host,card(m,p,i==0?"evolved":"stage2").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);}
    static ServerMatch aiGame(String id){
        var m=game(id);if(id.equals("sm7_en_139"))evolve(m,A,bench(m,A),2);
        if(id.equals("swsh3_en_172")||id.startsWith("swsh9_en_"))m.discardHandCardsByEffect(A,A,List.of(card(m,A,"energy").instanceId()));
        if(id.equals("swsh4_en_156")){UUID host=bench(m,A);m.attachEnergy(A,card(m,A,"special").instanceId(),host);m.placeDamageCountersByEffect(A,A,host,5);}
        return m;
    }
    public static void main(String[] args){brock();royal();turbo();elixir();basin();cheese();System.out.println("NonPokemonBatch39Test passed: exact eight names, Stage 2 typed hosts, basic non-GX coin recovery, private six-card Bench attachment, per-player Stadium cost/injury, and one-or-two energized healing targets");}
    static void brock(){for(String name:List.of("Geodude","Graveler","Golem","Onix-GX","Cubone","Rhyhorn","Rhydon","Sudowoodo","Alolan Geodude","Onix","Onix ex")){
        var m=game("sm11-5_en_055",name,EnergyType.FIRE,Set.of(),0);boolean allowed=!name.startsWith("Alolan")&&!name.equals("Onix")&&!name.equals("Onix ex");check(m.canPlayTrainer(A,card(m,A,"trainer").instanceId(),null)==allowed,"Brock used substring instead of exact name "+name);if(!allowed)continue;
        UUID host=bench(m,A);var energy=card(m,A,"special");play(m);pick(m,A,host);check(m.effectCardPrompt(A).candidates().contains(energy),"Brock excluded Special Energy");cards(m,A,List.of(energy.instanceId()));settle(m);check(m.benchPokemonView(A).getFirst().attachedEnergyIds().contains(energy.instanceId())&&!m.energyAttached(A),"Brock failed effect attachment or spent manual quota");
    }}
    static void royal(){for(int stage:List.of(0,1,2))for(EnergyType type:List.of(EnergyType.GRASS,EnergyType.FIRE,EnergyType.WATER,EnergyType.LIGHTNING)){
        var m=game("sm7_en_139","Test",type,Set.of(),0);UUID host=bench(m,A);evolve(m,A,host,stage);boolean allowed=stage==2&&type!=EnergyType.LIGHTNING;check(m.canPlayTrainer(A,card(m,A,"trainer").instanceId(),null)==allowed,"Masked Royal stage/type filter");if(!allowed)continue;
        play(m);check(m.effectPokemonPrompt(A).targets().size()==1,"Masked Royal offered Basic host");pick(m,A,host);var q=m.effectCardPrompt(A);check(q.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).basicEnergy()),"Masked Royal accepted Special Energy");var energy=card(m,A,"wrong");cards(m,A,List.of(energy.instanceId()));settle(m);check(m.benchPokemonView(A).getFirst().attachedEnergyIds().contains(energy.instanceId()),"Masked Royal incorrectly required matching Energy type");
    }}
    static void turbo(){boolean heads=false,tails=false;for(int seed=0;seed<80&&!(heads&&tails);seed++){
        var m=game("swsh3_en_172","Test",EnergyType.FIRE,Set.of(),seed);var energy=card(m,A,"energy");var special=card(m,A,"special");m.discardHandCardsByEffect(A,A,List.of(energy.instanceId(),special.instanceId()));int before=m.randomTranscript().size();play(m);boolean head=GeneratedRuleBatch23Test.coinResult(m,before)==0;heads|=head;tails|=!head;if(head){pick(m,A,bench(m,A));check(m.effectCardPrompt(A).candidates().equals(List.of(energy)),"Turbo Patch accepted Special Energy");cards(m,A,List.of(energy.instanceId()));}settle(m);check(m.benchPokemonView(A).getFirst().attachedEnergyIds().contains(energy.instanceId())==head&&m.privateView(A).discard().contains(special),"Turbo Patch resolved wrong coin branch");
    }check(heads&&tails,"Turbo coin branch coverage");for(PokemonFamily family:List.of(PokemonFamily.GX,PokemonFamily.EX,PokemonFamily.EX_LEGACY,PokemonFamily.V)){
        var m=game("swsh3_en_172","Test",EnergyType.FIRE,Set.of(family),0);m.discardHandCardsByEffect(A,A,List.of(card(m,A,"energy").instanceId()));check(m.canPlayTrainer(A,card(m,A,"trainer").instanceId(),null)==(family!=PokemonFamily.GX),"Turbo confused GX with ex/EX/V");
    }}
    static void elixir(){for(boolean take:List.of(false,true)){
        var m=game("xy9_en_102");UUID host=bench(m,A);m.playBasicPokemonToBench(A,card(m,A,"basic").instanceId());evolve(m,A,m.benchPokemonView(A).getLast().pokemonId(),1);
        for(String kind:List.of("basic","evolved","stage2","special","wrong","energy"))NonPokemonBatch37Test.top(m,A,card(m,A,kind));var window=m.privateView(A).deck().subList(m.privateView(A).deck().size()-6,m.privateView(A).deck().size());var expected=window.stream().filter(c->m.cardDefinition(c.definitionId()).basicEnergy()).toList();int count=m.privateView(A).deck().size(),random=m.randomTranscript().size();play(m);var q=m.effectCardPrompt(A);check(q.candidates().size()==expected.size()&&q.candidates().containsAll(expected)&&q.minimum()==0,"Max Elixir exposed outside-window or Special Energy");GeneratedRuleBatch8Test.reject(()->m.effectCardPrompt(B));cards(m,A,take?List.of(expected.getFirst().instanceId()):List.of());if(take){var a=m.effectAttachmentPrompt(A);check(a.targets().equals(List.of(host)),"Max Elixir allowed Active/evolved recipient");m.assignEffectEnergyDistribution(A,a.resolutionId(),a.step(),expected.getFirst().instanceId(),host);m.confirmEffectEnergyDistribution(A,a.resolutionId(),a.step());}settle(m);check(m.privateView(A).deck().size()==count-(take?1:0)&&m.benchPokemonView(A).getFirst().attachedEnergyIds().size()==(take?1:0)&&m.randomTranscript().size()>random&&!m.energyAttached(A),"Max Elixir failed shuffle, optional decline or attachment");
    }}
    static void basin(){for(String id:List.of("swsh9_en_144","swsh9_en_185")){
        var m=game(id);play(m);for(UUID p:List.of(A,B)){if(p.equals(B))m.endTurn(A);UUID host=bench(m,p);check(!m.canActivateStadium(p),"Magma Basin allowed empty discard");var energy=card(m,p,"energy");var wrong=card(m,p,"wrong");var special=card(m,p,"special");m.discardHandCardsByEffect(p,p,List.of(energy.instanceId(),wrong.instanceId(),special.instanceId()));check(m.canActivateStadium(p),"Magma Basin missed per-player activation");m.activateStadium(p,m.clientView(p).stadium().instanceId(),m.stadiumEntrySequence());var q=m.effectPokemonPrompt(p);check(q.targets().size()==1&&q.targets().getFirst().pokemonId().equals(host),"Magma Basin offered Active");pick(m,p,host);check(m.effectCardPrompt(p).candidates().equals(List.of(energy)),"Magma Basin allowed wrong or Special Energy");cards(m,p,List.of(energy.instanceId()));settle(m);check(m.benchPokemonView(p).getFirst().damage()==20&&m.benchPokemonView(p).getFirst().attachedEnergyIds().contains(energy.instanceId())&&!m.canActivateStadium(p)&&!m.energyAttached(p),"Magma Basin attachment, injury or usage failed");}
    }var wrong=game("swsh9_en_144","Test",EnergyType.WATER,Set.of(),0);play(wrong);wrong.discardHandCardsByEffect(A,A,List.of(card(wrong,A,"energy").instanceId()));check(!wrong.canActivateStadium(A),"Magma Basin accepted non-Fire recipient");}
    static void cheese(){for(int count:List.of(1,2)){
        var m=game("swsh4_en_156");UUID active=m.activePokemonView(A).pokemonId(),bench=bench(m,A);for(UUID host:List.of(active,bench)){m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,card(m,A,"special").instanceId(),A,host);m.placeDamageCountersByEffect(A,A,host,5);}m.playBasicPokemonToBench(A,card(m,A,"basic").instanceId());UUID empty=m.benchPokemonView(A).getLast().pokemonId();m.placeDamageCountersByEffect(A,A,empty,5);play(m);var q=m.effectChoicePrompt(A);m.chooseEffectOption(A,q.resolutionId(),q.step(),count-1);check(m.effectPokemonPrompt(A).targets().stream().noneMatch(p->p.pokemonId().equals(empty)),"Cheese healed a Pokemon with no Energy");pick(m,A,active);if(count==2)pick(m,A,bench);settle(m);check(m.activePokemonView(A).damage()==20&&m.benchPokemonView(A).getFirst().damage()==(count==2?20:50)&&m.benchPokemonView(A).getLast().damage()==50,"Cheese repeated target or wrong heal amount");
    }}
}
