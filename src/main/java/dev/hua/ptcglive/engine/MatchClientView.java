package dev.hua.ptcglive.engine;

import dev.hua.ptcglive.engine.field.BattlefieldState.PokemonView;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.List;
import java.util.UUID;

/** The only match DTO allowed on the wire. Even the owner gets no deck order or Prize faces/IDs. */
public record MatchClientView(UUID matchId, UUID viewingSeat, MatchPhase phase, int turnNumber,
    UUID currentPlayer, UUID choicePlayer, MatchResult result, List<Player> players,
    List<CardInstance> hand, List<Integer> prizeSlots,
    List<SetupPlayer> setupPlayers, List<MulliganReveal> mulliganReveals, CardInstance stadium, CardInstance resolvingTrainer,
    EffectCardPrompt effectSelection, EffectCardReveal effectReveal, EffectAttachmentPrompt effectAttachment, TriggerOrderPrompt triggerOrder,
    DamageAllocationPrompt damageAllocation, AbilityResolution resolvingAbility, AttackResolution resolvingAttack, ToolOverflowPrompt toolOverflow,
    List<CardInstance> resolvingTrainerCards, CardInstance stadiumCompanion) {
    public List<CardInstance> stadiumCards() {return stadium==null?List.of():stadiumCompanion==null?List.of(stadium):List.of(stadium,stadiumCompanion);}
    public MatchClientView(UUID matchId, UUID viewingSeat, MatchPhase phase, int turnNumber,
        UUID currentPlayer, UUID choicePlayer, MatchResult result, List<Player> players,
        List<CardInstance> hand, List<Integer> prizeSlots, List<SetupPlayer> setupPlayers, List<MulliganReveal> mulliganReveals,
        CardInstance stadium, CardInstance resolvingTrainer, EffectCardPrompt effectSelection, EffectCardReveal effectReveal,
        EffectAttachmentPrompt effectAttachment, TriggerOrderPrompt triggerOrder, DamageAllocationPrompt damageAllocation,
        AbilityResolution resolvingAbility, AttackResolution resolvingAttack, ToolOverflowPrompt toolOverflow, List<CardInstance> resolvingTrainerCards) {
        this(matchId,viewingSeat,phase,turnNumber,currentPlayer,choicePlayer,result,players,hand,prizeSlots,setupPlayers,mulliganReveals,
            stadium,resolvingTrainer,effectSelection,effectReveal,effectAttachment,triggerOrder,damageAllocation,resolvingAbility,resolvingAttack,toolOverflow,resolvingTrainerCards,null);
    }
    /** Old snapshots and constructors keep the single representative source contract. */
    public MatchClientView(UUID matchId,UUID viewingSeat,MatchPhase phase,int turnNumber,
            UUID currentPlayer,UUID choicePlayer,MatchResult result,List<Player> players,
            List<CardInstance> hand,List<Integer> prizeSlots,List<SetupPlayer> setupPlayers,List<MulliganReveal> mulliganReveals,
            CardInstance stadium,CardInstance resolvingTrainer,EffectCardPrompt effectSelection,EffectCardReveal effectReveal,
            EffectAttachmentPrompt effectAttachment,TriggerOrderPrompt triggerOrder,DamageAllocationPrompt damageAllocation,
            AbilityResolution resolvingAbility,AttackResolution resolvingAttack,ToolOverflowPrompt toolOverflow) {
        this(matchId,viewingSeat,phase,turnNumber,currentPlayer,choicePlayer,result,players,hand,prizeSlots,setupPlayers,mulliganReveals,
            stadium,resolvingTrainer,effectSelection,effectReveal,effectAttachment,triggerOrder,damageAllocation,resolvingAbility,resolvingAttack,
            toolOverflow,resolvingTrainer==null?List.of():List.of(resolvingTrainer));
    }
    /** Public source facts only; pokemonId is null once the source leaves play, even while its text continues. */
    public record AbilityResolution(UUID pokemonId, String abilityId, String nameZh,dev.hua.ptcglive.engine.effect.EffectOrigin.Kind sourceKind) {
        public AbilityResolution(UUID pokemonId,String abilityId,String nameZh) {this(pokemonId,abilityId,nameZh,dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.ABILITY);}
    }
    /** pokemonId is deliberately nullable after departure; private engine identity is never erased. */
    public record AttackResolution(UUID pokemonId, String attackId, String nameZh, dev.hua.ptcglive.engine.effect.EffectTrigger phase) {
        public AttackResolution(UUID pokemonId,String attackId,String nameZh) {
            this(pokemonId,attackId,nameZh,dev.hua.ptcglive.engine.effect.EffectTrigger.AFTER_ATTACK);
        }
    }
    public boolean resolvingCardEffect() { return effectSelection!=null || resolvingTrainer != null || resolvingAbility != null || resolvingAttack != null || toolOverflow!=null; }
    public MatchClientView(UUID matchId, UUID viewingSeat, MatchPhase phase, int turnNumber,
        UUID currentPlayer, UUID choicePlayer, MatchResult result, List<Player> players,
        List<CardInstance> hand, List<Integer> prizeSlots, List<SetupPlayer> setupPlayers, List<MulliganReveal> mulliganReveals,
        CardInstance stadium, CardInstance resolvingTrainer, EffectCardPrompt effectSelection, EffectCardReveal effectReveal,
        EffectAttachmentPrompt effectAttachment, TriggerOrderPrompt triggerOrder, DamageAllocationPrompt damageAllocation,
        AbilityResolution resolvingAbility, AttackResolution resolvingAttack) {
        this(matchId,viewingSeat,phase,turnNumber,currentPlayer,choicePlayer,result,players,hand,prizeSlots,setupPlayers,mulliganReveals,
            stadium,resolvingTrainer,effectSelection,effectReveal,effectAttachment,triggerOrder,damageAllocation,resolvingAbility,resolvingAttack,null);
    }
    public MatchClientView(UUID matchId, UUID viewingSeat, MatchPhase phase, int turnNumber,
        UUID currentPlayer, UUID choicePlayer, MatchResult result, List<Player> players,
        List<CardInstance> hand, List<Integer> prizeSlots, List<SetupPlayer> setupPlayers, List<MulliganReveal> mulliganReveals,
        CardInstance stadium, CardInstance resolvingTrainer, EffectCardPrompt effectSelection, EffectCardReveal effectReveal,
        EffectAttachmentPrompt effectAttachment, TriggerOrderPrompt triggerOrder, DamageAllocationPrompt damageAllocation,
        AbilityResolution resolvingAbility) {
        this(matchId,viewingSeat,phase,turnNumber,currentPlayer,choicePlayer,result,players,hand,prizeSlots,setupPlayers,
            mulliganReveals,stadium,resolvingTrainer,effectSelection,effectReveal,effectAttachment,triggerOrder,damageAllocation,resolvingAbility,null);
    }
    public MatchClientView(UUID matchId, UUID viewingSeat, MatchPhase phase, int turnNumber,
        UUID currentPlayer, UUID choicePlayer, MatchResult result, List<Player> players,
        List<CardInstance> hand, List<Integer> prizeSlots, List<SetupPlayer> setupPlayers, List<MulliganReveal> mulliganReveals,
        CardInstance stadium, CardInstance resolvingTrainer, EffectCardPrompt effectSelection, EffectCardReveal effectReveal,
        EffectAttachmentPrompt effectAttachment, TriggerOrderPrompt triggerOrder, DamageAllocationPrompt damageAllocation) {
        this(matchId,viewingSeat,phase,turnNumber,currentPlayer,choicePlayer,result,players,hand,prizeSlots,setupPlayers,
            mulliganReveals,stadium,resolvingTrainer,effectSelection,effectReveal,effectAttachment,triggerOrder,damageAllocation,null);
    }
    public MatchClientView(UUID matchId, UUID viewingSeat, MatchPhase phase, int turnNumber,
        UUID currentPlayer, UUID choicePlayer, MatchResult result, List<Player> players,
        List<CardInstance> hand, List<Integer> prizeSlots, List<SetupPlayer> setupPlayers, List<MulliganReveal> mulliganReveals,
        CardInstance stadium, CardInstance resolvingTrainer, EffectCardPrompt effectSelection, EffectCardReveal effectReveal,
        EffectAttachmentPrompt effectAttachment, TriggerOrderPrompt triggerOrder) {
        this(matchId,viewingSeat,phase,turnNumber,currentPlayer,choicePlayer,result,players,hand,prizeSlots,setupPlayers,
            mulliganReveals,stadium,resolvingTrainer,effectSelection,effectReveal,effectAttachment,triggerOrder,null);
    }
    public MatchClientView(UUID matchId, UUID viewingSeat, MatchPhase phase, int turnNumber,
        UUID currentPlayer, UUID choicePlayer, MatchResult result, List<Player> players,
        List<CardInstance> hand, List<Integer> prizeSlots, List<SetupPlayer> setupPlayers, List<MulliganReveal> mulliganReveals,
        CardInstance stadium, CardInstance resolvingTrainer, EffectCardPrompt effectSelection, EffectCardReveal effectReveal,
        EffectAttachmentPrompt effectAttachment) {
        this(matchId, viewingSeat, phase, turnNumber, currentPlayer, choicePlayer, result, players, hand, prizeSlots,
            setupPlayers, mulliganReveals, stadium, resolvingTrainer, effectSelection, effectReveal, effectAttachment, null);
    }
    public MatchClientView(UUID matchId, UUID viewingSeat, MatchPhase phase, int turnNumber,
        UUID currentPlayer, UUID choicePlayer, MatchResult result, List<Player> players,
        List<CardInstance> hand, List<Integer> prizeSlots, List<SetupPlayer> setupPlayers, List<MulliganReveal> mulliganReveals,
        CardInstance stadium, CardInstance resolvingTrainer, EffectCardPrompt effectSelection, EffectCardReveal effectReveal) {
        this(matchId, viewingSeat, phase, turnNumber, currentPlayer, choicePlayer, result, players, hand, prizeSlots,
            setupPlayers, mulliganReveals, stadium, resolvingTrainer, effectSelection, effectReveal, null);
    }
    public MatchClientView(UUID matchId, UUID viewingSeat, MatchPhase phase, int turnNumber,
        UUID currentPlayer, UUID choicePlayer, MatchResult result, List<Player> players,
        List<CardInstance> hand, List<Integer> prizeSlots, List<SetupPlayer> setupPlayers, List<MulliganReveal> mulliganReveals,
        CardInstance stadium, CardInstance resolvingTrainer, EffectCardPrompt effectSelection) {
        this(matchId, viewingSeat, phase, turnNumber, currentPlayer, choicePlayer, result, players, hand, prizeSlots,
            setupPlayers, mulliganReveals, stadium, resolvingTrainer, effectSelection, null);
    }
    public MatchClientView(UUID matchId, UUID viewingSeat, MatchPhase phase, int turnNumber,
        UUID currentPlayer, UUID choicePlayer, MatchResult result, List<Player> players,
        List<CardInstance> hand, List<Integer> prizeSlots, List<SetupPlayer> setupPlayers, List<MulliganReveal> mulliganReveals,
        CardInstance stadium, CardInstance resolvingTrainer) {
        this(matchId, viewingSeat, phase, turnNumber, currentPlayer, choicePlayer, result, players, hand, prizeSlots,
            setupPlayers, mulliganReveals, stadium, resolvingTrainer, null);
    }
    public MatchClientView(UUID matchId, UUID viewingSeat, MatchPhase phase, int turnNumber,
        UUID currentPlayer, UUID choicePlayer, MatchResult result, List<Player> players,
        List<CardInstance> hand, List<Integer> prizeSlots, List<SetupPlayer> setupPlayers, List<MulliganReveal> mulliganReveals, CardInstance stadium) {
        this(matchId, viewingSeat, phase, turnNumber, currentPlayer, choicePlayer, result, players, hand, prizeSlots, setupPlayers, mulliganReveals, stadium, null);
    }
    public MatchClientView(UUID matchId, UUID viewingSeat, MatchPhase phase, int turnNumber,
        UUID currentPlayer, UUID choicePlayer, MatchResult result, List<Player> players,
        List<CardInstance> hand, List<Integer> prizeSlots, List<SetupPlayer> setupPlayers, List<MulliganReveal> mulliganReveals) {
        this(matchId, viewingSeat, phase, turnNumber, currentPlayer, choicePlayer, result, players, hand, prizeSlots, setupPlayers, mulliganReveals, null);
    }
    public MatchClientView(UUID matchId, UUID viewingSeat, MatchPhase phase, int turnNumber,
        UUID currentPlayer, UUID choicePlayer, MatchResult result, List<Player> players,
        List<CardInstance> hand, List<Integer> prizeSlots) {
        this(matchId, viewingSeat, phase, turnNumber, currentPlayer, choicePlayer, result, players, hand, prizeSlots, List.of(), List.of());
    }
    public MatchClientView {
        players = List.copyOf(players); hand = List.copyOf(hand); prizeSlots = List.copyOf(prizeSlots);
        setupPlayers = List.copyOf(setupPlayers); mulliganReveals = List.copyOf(mulliganReveals);
        resolvingTrainerCards=List.copyOf(resolvingTrainerCards==null?resolvingTrainer==null?List.of():List.of(resolvingTrainer):resolvingTrainerCards);
    }
    public record SetupPlayer(UUID id, int mulligans, boolean needsMulligan, boolean ready, boolean revealAcknowledged) { }
    /** Only the failed seven-card hand, with no physical IDs that could track shuffled hidden cards. */
    public record MulliganReveal(UUID playerId, int attempt, List<String> cardIds) {
        public MulliganReveal { cardIds = List.copyOf(cardIds); }
    }
    /** Discard and Lost Zone are public face-up zones; deck, hand and prizes remain filtered above. */
    public record Player(UUID id, int handSize, int deckSize, int prizeCount, PokemonView active,
                         List<PokemonView> bench, List<CardInstance> discard, List<CardInstance> lostZone,
                         boolean gxUsed, boolean vstarUsed, List<FaceUpPrize> faceUpPrizes) {
        public Player(UUID id,int handSize,int deckSize,int prizeCount,PokemonView active,List<PokemonView> bench,
            List<CardInstance> discard,List<CardInstance> lostZone) {
            this(id,handSize,deckSize,prizeCount,active,bench,discard,lostZone,false,false,List.of());
        }
        public Player(UUID id,int handSize,int deckSize,int prizeCount,PokemonView active,List<PokemonView> bench,
            List<CardInstance> discard,List<CardInstance> lostZone,boolean gxUsed,boolean vstarUsed) {
            this(id,handSize,deckSize,prizeCount,active,bench,discard,lostZone,gxUsed,vstarUsed,List.of());
        }
        public Player { bench=List.copyOf(bench); discard = List.copyOf(discard); lostZone = List.copyOf(lostZone); faceUpPrizes=List.copyOf(faceUpPrizes); }
        public record FaceUpPrize(int slot,String cardId) { }
    }
}
