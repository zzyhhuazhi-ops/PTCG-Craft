import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch32Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static CardInstance card(ServerMatch m,UUID owner,String id){return DynamicPassiveModifierTest.hand(m,owner,id);}
    static ServerMatch game(String id,boolean first,boolean legacy,String evolutionName){
        var trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();String[] ids={"base","base","base","base","stage1","stage1","stage2","trainer","trainer","energy"};var defs=new ArrayList<CardDefinition>();var profiles=new HashMap<String,PokemonProfile>();
        for(String key:Arrays.stream(ids).distinct().toList()){
            boolean pokemon=!Set.of("trainer","energy").contains(key);
            defs.add(new CardDefinition(key,key,pokemon?CardCategory.POKEMON:key.equals("trainer")?CardCategory.TRAINER:CardCategory.ENERGY,key.equals("trainer")?trainer.category():TrainerCategory.NONE,"H",Set.of("international"),key.equals("base"),key.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null,pokemon&&legacy?Set.of(PokemonFamily.EX_LEGACY):Set.of(),pokemon?Set.of(EnergyType.GRASS):Set.of()));
            if(pokemon)profiles.put(key,new PokemonProfile(500,1,key.equals("stage1")?"base":key.equals("stage2")?"stage1":null,null,0,null,0,1,List.of(GeneratedRuleBatch8Test.hit(10,false)),List.of(),EnergyType.GRASS,key.equals("stage1")?evolutionName:key.equals("stage2")?"Aegislash-GX":"Basic",null));
        }
        for(int seed=0;seed<300;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),new CombatCatalog(profiles,Map.of("energy",EnergyType.GRASS),Map.of("trainer",trainer)),new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("base")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,20);m.drawCardsByEffect(A,B,20);if(!first){m.endTurn(A);m.endTurn(B);}
            if(List.of(A,B).stream().allMatch(p->List.of("trainer","base","stage1","stage2").stream().allMatch(k->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(k)))&&List.of("stage1","stage2").stream().allMatch(k->m.privateView(p).deck().stream().anyMatch(c->c.definitionId().equals(k)))))return m;
        }throw new AssertionError("Deck evolution fixture unavailable");
    }
    static void host(ServerMatch m,UUID owner,UUID host){var q=m.effectPokemonPrompt(owner);m.chooseEffectPokemon(owner,q.resolutionId(),q.step(),host);}
    static void evolve(ServerMatch m,UUID owner,String id){var q=m.effectCardPrompt(owner);var card=q.candidates().stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow();m.submitEffectCards(owner,q.resolutionId(),q.step(),List.of(card.instanceId()));NonPokemonBatch2Test.settle(m);}
    public static void main(String[] args){timing();wally();boost();breeder();dusk();System.out.println("NonPokemonBatch32Test passed: first/entry/evolved-turn timing, Wally chained evolution and legacy EX restriction, Boost Shake ending after failed search, separate Breeder hosts and Dusk Stone exact evolution names");}
    static void timing(){for(String id:List.of("xy1_en_116","xy6_en_094","swsh7_en_142","sm10_en_167","swsh3_en_166")){
        var m=game(id,true,false,"Mismagius-GX");check(m.canPlayTrainer(A,card(m,A,"trainer").instanceId(),null)==Set.of("swsh7_en_142","sm10_en_167").contains(id),"First player Supporter/evolution timing: "+id);m.endTurn(A);
        check(m.canPlayTrainer(B,card(m,B,"trainer").instanceId(),null)==!Set.of("xy1_en_116","swsh3_en_166").contains(id),"Second player's first evolution timing: "+id);
        var late=game(id,false,false,"Mismagius-GX");var fresh=card(late,A,"base");late.playBasicPokemonToBench(A,fresh.instanceId());UUID freshHost=late.benchPokemonView(A).getLast().pokemonId();late.playTrainer(A,card(late,A,"trainer").instanceId(),null);var q=late.effectPokemonPrompt(A);check(q.targets().stream().anyMatch(t->t.pokemonId().equals(freshHost))==!Set.of("xy1_en_116","swsh3_en_166").contains(id),"Newly benched host timing: "+id);NonPokemonBatch2Test.settle(late);
    }}
    static void wally(){
        var blocked=game("xy6_en_094",false,true,"Mismagius-GX");check(!blocked.canPlayTrainer(A,card(blocked,A,"trainer").instanceId(),null),"Wally evolved a legacy EX");
        for(String id:List.of("xy6_en_094","xy1_en_116")){var m=game(id,false,false,"Mismagius-GX");UUID active=m.activePokemonView(A).pokemonId();m.evolve(A,active,card(m,A,"stage1").instanceId());m.playTrainer(A,card(m,A,"trainer").instanceId(),null);var q=m.effectPokemonPrompt(A);boolean offered=q.targets().stream().anyMatch(t->t.pokemonId().equals(active));check(offered==id.equals("xy6_en_094"),"Already-evolved host timing");if(offered){host(m,A,active);evolve(m,A,"stage2");check(m.activePokemonView(A).topCardId().equals("stage2"),"Wally did not evolve a second time");}else NonPokemonBatch2Test.settle(m);}
    }
    static void boost(){for(boolean choose:List.of(false,true)){var m=game("swsh7_en_142",true,false,"Mismagius-GX");m.playTrainer(A,card(m,A,"trainer").instanceId(),null);var p=m.effectPokemonPrompt(A);check(!p.canFinish(),"Boost Shake skipped mandatory public host selection");host(m,A,m.activePokemonView(A).pokemonId());if(choose)evolve(m,A,"stage1");else {var q=m.effectCardPrompt(A);m.submitEffectCards(A,q.resolutionId(),q.step(),List.of());NonPokemonBatch2Test.settle(m);}check(m.currentPlayer().equals(B)&&m.activePokemonView(A).topCardId().equals(choose?"stage1":"base"),"Boost Shake failed to end after complete search");}}
    static void breeder(){var m=game("swsh3_en_166",false,false,"Mismagius-GX");var hosts=List.of(m.activePokemonView(A).pokemonId(),m.benchPokemonView(A).getFirst().pokemonId());m.playTrainer(A,card(m,A,"trainer").instanceId(),null);for(UUID id:hosts)host(m,A,id);var q=m.effectCardPrompt(A);var card=q.candidates().stream().filter(c->c.definitionId().equals("stage1")).findFirst().orElseThrow();m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(card.instanceId()));q=m.effectCardPrompt(A);check(q.candidates().stream().noneMatch(c->c.definitionId().equals("stage2")),"Breeder evolved first host twice or skipped stage");evolve(m,A,"stage1");check(m.activePokemonView(A).topCardId().equals("stage1")&&m.benchPokemonView(A).getFirst().topCardId().equals("stage1"),"Breeder failed separate hosts");}
    static void dusk(){for(String name:List.of("Mismagius-GX","Mismagius ex","Other")){var m=game("sm10_en_167",false,false,name);m.playTrainer(A,card(m,A,"trainer").instanceId(),null);host(m,A,m.activePokemonView(A).pokemonId());check(m.hasPendingCardSelection()==name.equals("Mismagius-GX"),"Dusk Stone ignored exact GX-inclusive names");if(name.equals("Mismagius-GX"))evolve(m,A,"stage1");else NonPokemonBatch2Test.settle(m);}}
}
