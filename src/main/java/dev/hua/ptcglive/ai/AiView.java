package dev.hua.ptcglive.ai;

import dev.hua.ptcglive.engine.MatchSession;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Immutable decision input. No ServerMatch, world, deck order, seeds or opponent deck list. */
public record AiView(MatchSession.Snapshot snapshot,Map<String,Card> cards,Map<UUID,Energy> energy,AiDeckAnalysis deck,OwnTurn turn) {
    public record OwnTurn(boolean mayAttack,boolean energyAttached,int retreatCost){}
    public record Card(CardDefinition definition,PokemonProfile pokemon,TrainerProfile trainer,EnergyProfile energy){}
    public record Energy(List<EnergyProfile.Unit> units,Map<String,Map<EnergyType,Integer>> costs,Map<String,Boolean> payable) {
        public Energy {units=List.copyOf(units);var copy=new HashMap<String,Map<EnergyType,Integer>>();costs.forEach((k,v)->copy.put(k,Map.copyOf(v)));costs=Map.copyOf(copy);payable=Map.copyOf(payable);}
    }
    public AiView {cards=Map.copyOf(cards);energy=Map.copyOf(energy);Objects.requireNonNull(deck);Objects.requireNonNull(turn);}
    public AiView(MatchSession.Snapshot snapshot,Map<String,Card> cards,Map<UUID,Energy> energy){this(snapshot,cards,energy,AiDeckAnalysis.EMPTY,new OwnTurn(true,false,0));}
    public AiView(MatchSession.Snapshot snapshot,Map<String,Card> cards,Map<UUID,Energy> energy,AiDeckAnalysis deck){this(snapshot,cards,energy,deck,new OwnTurn(true,false,0));}
    public static AiView filtered(MatchSession.Snapshot snapshot,CardDatabase cards,CombatCatalog combat,Map<UUID,Energy> energy) {
        return filtered(snapshot,cards,combat,energy,AiDeckAnalysis.EMPTY);
    }
    public static AiView filtered(MatchSession.Snapshot snapshot,CardDatabase cards,CombatCatalog combat,Map<UUID,Energy> energy,AiDeckAnalysis ownDeck) {
        return filtered(snapshot,cards,combat,energy,ownDeck,new OwnTurn(true,false,0));
    }
    public static AiView filtered(MatchSession.Snapshot snapshot,CardDatabase cards,CombatCatalog combat,Map<UUID,Energy> energy,AiDeckAnalysis ownDeck,OwnTurn turn) {
        var visible=new HashMap<String,Card>();
        // cardNames was populated exclusively from the authenticated recipient view.
        var ids=new HashSet<>(snapshot.cardNames().keySet());ids.addAll(ownDeck.initialCards().keySet());
        for(String id:ids)visible.put(id,new Card(cards.find(id).orElseThrow(),combat.fieldPokemon(id).orElse(null),combat.trainer(id).orElse(null),combat.energyProfile(id).orElse(null)));
        return new AiView(snapshot,visible,energy,ownDeck,turn);
    }
}
