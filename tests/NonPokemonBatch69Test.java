import com.google.gson.JsonParser;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch69Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final List<String> IDS=List.of("sm5_en_120");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static ServerMatch fixture(UUID p,int bench,Set<EnergyType> types,boolean oldAttack){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer(IDS.getFirst()).orElseThrow();c.trainerLimits=Map.of("棱镜之星",1);if(oldAttack)c.attack=GeneratedRuleBatch28Test.attack("sv5_en_005",0);
        if(p.equals(A))c.ownTypes=types;else c.defenderTypes=types;var m=GeneratedRuleBatch28Test.fixture(c);UUID opp=other(p);if(!m.currentPlayer().equals(opp))m.endTurn(m.currentPlayer());
        if(bench==0)m.movePokemonByEffect(opp,opp,m.benchPokemonView(opp).getFirst().pokemonId(),LeavePlayDestination.HAND);
        while(m.benchPokemonView(opp).size()<bench)m.playBasicPokemonToBench(opp,hand(m,opp,opp.equals(A)?"attacker":"defender").instanceId());m.endTurn(opp);return m;
    }
    static ServerMatch aiGame(String id){return fixture(A,5,Set.of(EnergyType.WATER),false);}
    static void choose(ServerMatch m,UUID chooser,List<UUID> kept){var q=m.effectPokemonPrompt(chooser);for(UUID id:kept)m.chooseEffectPokemon(chooser,q.resolutionId(),q.step(),id);NonPokemonBatch67Test.settle(m);}
    public static void main(String[] args){gates();returnCards();legacy();schema();System.out.println("NonPokemonBatch69Test passed: live Active Water/Metal gate, both players, exactly two opponent-selected survivors, full stacks and mixed-owner attachments shuffled once per owner, player-effect immunity bypass, no Prizes, invalid/stale/duplicate selection rejection; original attack keeps three with original chooser");}
    static void gates(){for(UUID p:List.of(A,B))for(int bench:List.of(0,1,2,3,5))for(Set<EnergyType> types:List.of(Set.of(EnergyType.FIRE),Set.of(EnergyType.WATER),Set.of(EnergyType.METAL),Set.of(EnergyType.FIRE,EnergyType.WATER))){
        var m=fixture(p,bench,types,false);var source=hand(m,p,"trainer");boolean allowed=bench>2&&(types.contains(EnergyType.WATER)||types.contains(EnergyType.METAL));var before=m.privateView(p);check(m.canPlayTrainer(p,source.instanceId(),null)==allowed,"Cyrus gate wrong "+bench+"/"+types);if(!allowed){GeneratedRuleBatch8Test.reject(()->m.playTrainer(p,source.instanceId(),null));check(m.privateView(p).equals(before)&&m.mayPlaySupporter(p),"Rejected Cyrus consumed source");continue;}
        var kept=m.benchPokemonView(other(p)).stream().limit(2).map(x->x.pokemonId()).toList();m.playTrainer(p,source.instanceId(),null);choose(m,other(p),kept);check(m.benchPokemonView(other(p)).size()==2&&m.privateView(p).lostZone().contains(source),"Cyrus did not keep two or banish Prism");
    }
        var m=fixture(A,3,Set.of(EnergyType.FIRE),false);m.evolveByEffect(A,m.benchPokemonView(A).getFirst().pokemonId(),hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);var source=hand(m,A,"trainer");check(!m.canPlayTrainer(A,source.instanceId(),null),"Water on Bench incorrectly qualified");m.switchActiveByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId());check(m.canPlayTrainer(A,source.instanceId(),null),"Live switched Water Active failed gate");
    }
    static void returnCards(){for(UUID p:List.of(A,B))for(boolean shield:List.of(false,true)){
        var m=fixture(p,5,Set.of(EnergyType.WATER),false);UUID opp=other(p),host=m.benchPokemonView(opp).getLast().pokemonId();m.endTurn(p);var evolution=hand(m,opp,opp.equals(A)?"evolved":"defender_evolved");m.evolveByEffect(opp,host,evolution.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.endTurn(opp);
        var ownEnergy=hand(m,p,"energy");var oppEnergy=hand(m,opp,"energy");m.attachEnergyByEffect(p,p,EffectSourceZone.HAND,ownEnergy.instanceId(),opp,host);m.attachEnergyByEffect(p,opp,EffectSourceZone.HAND,oppEnergy.instanceId(),opp,host);m.placeDamageCountersByEffect(p,opp,host,3);
        var field=EventTargetBindingTest.field(m);if(shield)for(var pokemon:m.benchPokemonView(opp))field.addProtection(opp,opp,pokemon.pokemonId(),DiscardPokemonReplacementTest.protection(false,"TRAINER","OPPONENT"));
        var before=m.benchPokemonView(opp);var kept=before.stream().limit(2).map(x->x.pokemonId()).toList();var returned=new HashSet<CardInstance>();for(var view:before)if(!kept.contains(view.pokemonId())){returned.addAll(field.previousEvolutionCards(opp,view.pokemonId()));returned.add(field.topCard(opp,view.pokemonId()));returned.addAll(field.attachedEnergy(opp,view.pokemonId()));}
        var prizes=m.privateView(p).prizes();var oppPrizes=m.privateView(opp).prizes();var source=hand(m,p,"trainer");var oldDeck=m.privateView(opp).deck();var oldOwn=m.privateView(p).deck();int random=m.randomTranscript().size();m.playTrainer(p,source.instanceId(),null);var q=m.effectPokemonPrompt(opp);
        GeneratedRuleBatch8Test.reject(()->m.effectPokemonPrompt(p));GeneratedRuleBatch8Test.reject(()->m.chooseEffectPokemon(p,q.resolutionId(),q.step(),kept.getFirst()));GeneratedRuleBatch8Test.reject(()->m.chooseEffectPokemon(opp,q.resolutionId(),q.step(),m.activePokemonView(opp).pokemonId()));GeneratedRuleBatch8Test.reject(()->m.finishEffectPokemonSelection(opp,q.resolutionId(),q.step()));
        check(m.benchPokemonView(opp).equals(before),"Invalid selection moved field");m.chooseEffectPokemon(opp,q.resolutionId(),q.step(),kept.getFirst());check(m.benchPokemonView(opp).equals(before),"Cyrus returned cards before two choices");GeneratedRuleBatch8Test.reject(()->m.chooseEffectPokemon(opp,q.resolutionId(),q.step(),kept.getFirst()));GeneratedRuleBatch8Test.reject(()->m.finishEffectPokemonSelection(opp,q.resolutionId(),q.step()));m.chooseEffectPokemon(opp,q.resolutionId(),q.step(),kept.getLast());NonPokemonBatch67Test.settle(m);GeneratedRuleBatch8Test.reject(()->m.chooseEffectPokemon(opp,q.resolutionId(),q.step(),kept.getLast()));
        var expected=new HashSet<>(oldDeck);returned.stream().filter(x->x.ownerId().equals(opp)).forEach(expected::add);var ownExpected=new HashSet<>(oldOwn);ownExpected.add(ownEnergy);
        check(new HashSet<>(m.privateView(opp).deck()).equals(expected)&&new HashSet<>(m.privateView(p).deck()).equals(ownExpected),"Cyrus lost stack or mixed-owner attachments");check(m.benchPokemonView(opp).stream().map(x->x.pokemonId()).toList().equals(kept)&&m.privateView(p).prizes().equals(prizes)&&m.privateView(opp).prizes().equals(oppPrizes),"Cyrus immunity blocked return or awarded Prizes");
        check(m.randomTranscript().size()-random==expected.size()-1+ownExpected.size()-1,"Cyrus should shuffle each owner's combined returned pool once");
    }}
    static void legacy(){var m=fixture(A,5,Set.of(EnergyType.WATER),true);var kept=m.benchPokemonView(B).stream().limit(3).map(x->x.pokemonId()).toList();m.attack(A,GeneratedRuleBatch28Test.attack("sv5_en_005",0).id());choose(m,A,kept);check(m.benchPokemonView(B).size()==3,"Existing attack lost keep-three controller choice");}
    static void schema(){String valid="{\"type\":\"shuffle_unchosen_opponent_bench\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"keep\":2,\"chooser\":\"OPPONENT\"}";for(String bad:List.of(valid.replace("\"keep\":2","\"keep\":3"),valid.replace("\"keep\":2","\"keep\":1"),valid.replace("\"chooser\":\"OPPONENT\"","\"chooser\":\"SELF\""),valid.replace("SELECT_OPPONENT_POKEMON","SELECT_OWN_POKEMON")))GeneratedRuleBatch8Test.reject(()->PokemonEffectSelection.validate(EffectRegistry.standard().parse(JsonParser.parseString(bad).getAsJsonObject())));}
}
