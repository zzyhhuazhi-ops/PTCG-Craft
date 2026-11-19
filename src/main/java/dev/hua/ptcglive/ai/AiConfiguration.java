package dev.hua.ptcglive.ai;
import dev.hua.ptcglive.rules.DeckValidationReport;
import java.util.*;

/** Owner-visible setup report; never part of the bot's decision context. */
public record AiConfiguration(String token,UUID playerBox,UUID computerBox,String ruleset,
                              String playerName,String computerName,int playerCount,int computerCount,
                              DeckValidationReport playerRules,DeckValidationReport computerRules,
                              List<String> operations,AiDeckAnalysis analysis,AiDifficulty difficulty) {
    public AiConfiguration {operations=List.copyOf(operations);Objects.requireNonNull(difficulty);}
    public boolean allowed(){return playerRules.canStartMatch()&&computerRules.canStartMatch()&&operations.isEmpty();}
}
