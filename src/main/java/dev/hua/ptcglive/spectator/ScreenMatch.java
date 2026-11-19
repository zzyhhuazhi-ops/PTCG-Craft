package dev.hua.ptcglive.spectator;

import dev.hua.ptcglive.engine.*;
import java.util.*;

/** Wire allowlist: no hands, deck contents, face-down prizes, private prompts, actions or AI data. */
public record ScreenMatch(UUID id,long revision,MatchPhase phase,int turn,UUID currentPlayer,MatchResult result,
        List<MatchClientView.Player> players,String stadium,List<String> resolvingCards,List<BattleEventJournal.Event> events) {
    public ScreenMatch{players=List.copyOf(players);resolvingCards=List.copyOf(resolvingCards);events=List.copyOf(events);}
    public static ScreenMatch capture(long revision,MatchClientView publicView,List<BattleEventJournal.Event> publicEvents){
        if(publicView.viewingSeat()!=null||!publicView.hand().isEmpty()||!publicView.prizeSlots().isEmpty())
            throw new IllegalArgumentException("A screen requires the engine's spectator view");
        return new ScreenMatch(publicView.matchId(),revision,publicView.phase(),publicView.turnNumber(),publicView.currentPlayer(),publicView.result(),
            publicView.players(),publicView.stadium()==null?null:publicView.stadium().definitionId(),
            publicView.resolvingTrainerCards().stream().map(c->c.definitionId()).toList(),publicEvents);
    }
}
