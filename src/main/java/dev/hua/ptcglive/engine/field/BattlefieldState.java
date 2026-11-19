package dev.hua.ptcglive.engine.field;

import dev.hua.ptcglive.engine.CoinSide;
import dev.hua.ptcglive.engine.RandomAuditEvent;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pure server battlefield authority. It owns no GUI or network state and receives
 * only server-issued card instances. Prize selection and card effects remain in the
 * match resolver; this class prevents the field itself from accepting illegal moves.
 */
public final class BattlefieldState {
    public static final int MAX_BENCH_SIZE = 8;
    private static final AbilityDefinition STAR_CIPHER_ABILITY = new AbilityDefinition(
        "star-cipher-granted", "星之密码", new AbilityEffect(AbilityEffect.Opcode.PASSIVE_STAR_CIPHER, 0),
        dev.hua.ptcglive.rules.EffectSupport.SUPPORTED, null, AbilityDefinition.Kind.PASSIVE);
    public record PokemonView(UUID pokemonId, String topCardId, int hitPoints, int damage,
                              List<UUID> attachedEnergyIds, List<SpecialCondition> conditions,
                              List<String> evolutionCardIds, List<String> attachedEnergyCardIds,
                              List<CardInstance> attachedTools, String continuousEffectZh, EnergyType pokemonType) {
        public PokemonView(UUID pokemonId, String topCardId, int hitPoints, int damage,
                           List<UUID> energyIds, List<SpecialCondition> conditions, List<String> evolution,
                           List<String> energyCards, List<CardInstance> tools, String continuousEffectZh) {
            this(pokemonId, topCardId, hitPoints, damage, energyIds, conditions, evolution, energyCards, tools, continuousEffectZh, null);
        }
        public PokemonView(UUID pokemonId, String topCardId, int hitPoints, int damage,
                           List<UUID> energyIds, List<SpecialCondition> conditions, List<String> evolution, List<String> energyCards) {
            this(pokemonId, topCardId, hitPoints, damage, energyIds, conditions, evolution, energyCards, List.of(), "");
        }
        public PokemonView(UUID pokemonId, String topCardId, int hitPoints, int damage,
                           List<UUID> energyIds, List<SpecialCondition> conditions, List<String> evolution, List<String> energyCards,
                           List<CardInstance> attachedTools) {
            this(pokemonId, topCardId, hitPoints, damage, energyIds, conditions, evolution, energyCards, attachedTools, "");
        }
        public PokemonView {
            attachedEnergyIds = List.copyOf(attachedEnergyIds); conditions = List.copyOf(conditions);
            evolutionCardIds = List.copyOf(evolutionCardIds); attachedEnergyCardIds = List.copyOf(attachedEnergyCardIds);
            attachedTools = List.copyOf(attachedTools);
            continuousEffectZh = continuousEffectZh == null ? "" : continuousEffectZh;
        }
    }
    public record RetreatResult(UUID movedToBench, UUID movedToActive, List<CardInstance> discardedEnergy) { }
    /** A voluntary leave-play result. Unlike a Knock Out, it never awards Prizes. */
    public record LeavePlayResult(UUID pokemonId, boolean wasActive, List<CardInstance> discardedCards) { }
    /** Top evolution removed by a resolved devolution effect; damage and attachments remain in play. */
    public record DevolutionResult(CardInstance removedEvolution, boolean knockedOut) { }
    public record CheckupEvent(UUID player, SpecialCondition condition, int damageAdded, CoinSide coin) { }
    public record CheckupResult(List<CheckupEvent> events, List<PokemonView> knockedOut, List<RandomAuditEvent> randomEvents) { }
    public record DamageResult(int damageAdded, int totalDamage, boolean knockedOut) { }

    private final Map<UUID, PlayerField> players = new LinkedHashMap<>();
    /** Player-wide Roxie's Performance locks; evaluated when retreat is declared so newly Poisoned Pokémon are covered. */
    private final List<PoisonRetreatLock> poisonedRetreatLocks = new ArrayList<>();
    private final Map<String, PokemonProfile> profiles;
    /** Evolution profiles keyed by printed name, for name-based Stage 2 skips (any printing counts). */
    private final Map<String, List<PokemonProfile>> evolutionsByName;
    private final ServerRandom random;
    private java.util.function.Supplier<dev.hua.ptcglive.engine.effect.EffectOrigin.Kind> effectSourceKind =
        () -> dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.RULE;
    public void effectSourceKind(java.util.function.Supplier<dev.hua.ptcglive.engine.effect.EffectOrigin.Kind> source) {
        effectSourceKind=Objects.requireNonNull(source);
    }
    private ContinuousEffect attributed(ContinuousEffect effect) { return effect.withSourceKind(effectSourceKind.get()); }
    public boolean hasAttackEffects(UUID owner,UUID pokemonId) {
        var pokemon=findPokemon(state(owner),pokemonId);
        return pokemon.starCipherAbility || pokemon.weaknessOverride!=null
            &&pokemon.weaknessOverride.sourceKind()==dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.ATTACK
            ||pokemon.continuousEffects.stream().anyMatch(e->e.sourceKind()==dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.ATTACK);
    }
    public void removeAttackEffects(UUID owner,UUID pokemonId) {
        var pokemon=findPokemon(state(owner),pokemonId);
        pokemon.continuousEffects.removeIf(e->e.sourceKind()==dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.ATTACK);
        pruneCardBindings(pokemon);
        if(pokemon.weaknessOverride!=null&&pokemon.weaknessOverride.sourceKind()==dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.ATTACK)
            pokemon.weaknessOverride=null;
        pokemon.starCipherAbility=false;
    }
    public void removeOpponentAbilityEffects(UUID owner,UUID pokemonId) {
        var pokemon=findPokemon(state(owner),pokemonId);
        pokemon.continuousEffects.removeIf(e->e.sourceKind()==dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.ABILITY&&!e.owner().equals(owner));
        pruneCardBindings(pokemon);
        if(pokemon.weaknessOverride!=null&&pokemon.weaknessOverride.sourceKind()==dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.ABILITY
            &&!pokemon.weaknessOverride.owner().equals(owner))pokemon.weaknessOverride=null;
    }
    private UUID currentPlayer;
    private int turnNumber;
    /** A card UUID identifies ownership; this deterministic counter identifies each separate stay in play. */
    private long nextEntrySequence;
    private long nextAttachmentEntrySequence;
    private final Map<UUID,Long> attachmentEntries=new LinkedHashMap<>();
    private final Map<UUID,Integer> energyAttachmentTurns=new LinkedHashMap<>();
    private int energyAttachments;
    private int retreats;
    private final int energyAttachmentLimit;
    private final int retreatLimit;
    private final java.util.function.ToIntFunction<UUID> benchLimit;
    private final java.util.function.ToIntBiFunction<UUID, UUID> retreatReduction;
    private final java.util.function.BiPredicate<UUID, UUID> freeRetreat;
    private final java.util.function.BiPredicate<UUID, UUID> retreatWithStatus;
    private final java.util.function.ToIntBiFunction<UUID, UUID> hitPointsChange;

    public BattlefieldState(UUID firstPlayer, UUID secondPlayer, Map<String, PokemonProfile> profiles, ServerRandom random) {
        this(firstPlayer, secondPlayer, profiles, random, (owner, pokemon) -> 0, 1, 1);
    }
    public BattlefieldState(UUID firstPlayer, UUID secondPlayer, Map<String, PokemonProfile> profiles, ServerRandom random,
                            java.util.function.ToIntBiFunction<UUID, UUID> retreatReduction) {
        this(firstPlayer, secondPlayer, profiles, random, retreatReduction, 1, 1);
    }
    public BattlefieldState(UUID firstPlayer, UUID secondPlayer, Map<String, PokemonProfile> profiles, ServerRandom random,
                            java.util.function.ToIntBiFunction<UUID, UUID> retreatReduction, int energyAttachmentLimit, int retreatLimit) {
        this(firstPlayer, secondPlayer, profiles, random, retreatReduction, energyAttachmentLimit, retreatLimit, 5);
    }
    /** The match passes its frozen ruleset cap; direct callers retain the official five-slot default. */
    public BattlefieldState(UUID firstPlayer, UUID secondPlayer, Map<String, PokemonProfile> profiles, ServerRandom random,
                            java.util.function.ToIntBiFunction<UUID, UUID> retreatReduction, int energyAttachmentLimit, int retreatLimit, int benchLimit) {
        this(firstPlayer,secondPlayer,profiles,random,retreatReduction,energyAttachmentLimit,retreatLimit,benchLimit,(owner,pokemon)->0);
    }
    public BattlefieldState(UUID firstPlayer,UUID secondPlayer,Map<String,PokemonProfile> profiles,ServerRandom random,
                            java.util.function.ToIntBiFunction<UUID,UUID> retreatReduction,int energyAttachmentLimit,int retreatLimit,int benchLimit,
                            java.util.function.ToIntBiFunction<UUID,UUID> hitPointsChange) {
        this(firstPlayer,secondPlayer,profiles,random,retreatReduction,energyAttachmentLimit,retreatLimit,benchLimit,hitPointsChange,(owner,pokemon)->false);
    }
    public BattlefieldState(UUID firstPlayer,UUID secondPlayer,Map<String,PokemonProfile> profiles,ServerRandom random,
                            java.util.function.ToIntBiFunction<UUID,UUID> retreatReduction,int energyAttachmentLimit,int retreatLimit,int benchLimit,
                            java.util.function.ToIntBiFunction<UUID,UUID> hitPointsChange,java.util.function.BiPredicate<UUID,UUID> freeRetreat) {
        this(firstPlayer,secondPlayer,profiles,random,retreatReduction,energyAttachmentLimit,retreatLimit,benchLimit,hitPointsChange,freeRetreat,(owner,pokemon)->false);
    }
    public BattlefieldState(UUID firstPlayer,UUID secondPlayer,Map<String,PokemonProfile> profiles,ServerRandom random,
                            java.util.function.ToIntBiFunction<UUID,UUID> retreatReduction,int energyAttachmentLimit,int retreatLimit,int benchLimit,
                            java.util.function.ToIntBiFunction<UUID,UUID> hitPointsChange,java.util.function.BiPredicate<UUID,UUID> freeRetreat,
                            java.util.function.BiPredicate<UUID,UUID> retreatWithStatus) {
        this(firstPlayer,secondPlayer,profiles,random,retreatReduction,energyAttachmentLimit,retreatLimit,benchLimit,hitPointsChange,freeRetreat,retreatWithStatus,owner->benchLimit);
    }
    public BattlefieldState(UUID firstPlayer,UUID secondPlayer,Map<String,PokemonProfile> profiles,ServerRandom random,
                            java.util.function.ToIntBiFunction<UUID,UUID> retreatReduction,int energyAttachmentLimit,int retreatLimit,int benchLimit,
                            java.util.function.ToIntBiFunction<UUID,UUID> hitPointsChange,java.util.function.BiPredicate<UUID,UUID> freeRetreat,
                            java.util.function.BiPredicate<UUID,UUID> retreatWithStatus,java.util.function.ToIntFunction<UUID> currentBenchLimit) {
        if (Objects.requireNonNull(firstPlayer).equals(Objects.requireNonNull(secondPlayer)))
            throw new IllegalArgumentException("战场需要两名不同玩家");
        players.put(firstPlayer, new PlayerField(firstPlayer));
        players.put(secondPlayer, new PlayerField(secondPlayer));
        this.profiles = Map.copyOf(profiles);
        this.evolutionsByName = this.profiles.values().stream()
            .filter(profile -> profile.isEvolution() && profile.pokemonName() != null)
            .collect(java.util.stream.Collectors.groupingBy(PokemonProfile::pokemonName));
        this.random = Objects.requireNonNull(random);
        this.retreatReduction = Objects.requireNonNull(retreatReduction);
        this.freeRetreat=Objects.requireNonNull(freeRetreat);
        this.retreatWithStatus=Objects.requireNonNull(retreatWithStatus);
        this.hitPointsChange=Objects.requireNonNull(hitPointsChange);
        if (energyAttachmentLimit < 0 || retreatLimit < 0 || benchLimit < 0 || benchLimit > MAX_BENCH_SIZE)
            throw new IllegalArgumentException("场上动作或备战区上限无效");
        this.energyAttachmentLimit = energyAttachmentLimit; this.retreatLimit = retreatLimit;
        this.benchLimit = Objects.requireNonNull(currentBenchLimit);
    }

    public int turnNumber() { return turnNumber; }
    public UUID currentPlayer() { return currentPlayer; }
    public List<RandomAuditEvent> randomTranscript() { return random.transcript(); }
    public PokemonView activeView(UUID player) { return view(state(player).active()); }
    public List<PokemonView> benchView(UUID player) { return state(player).bench().stream().map(this::view).toList(); }
    /** Source enumeration / dependency facts only; never send these printed-HP views to clients. */
    public PokemonView printedActiveView(UUID player) {return view(state(player).active(),false);}
    public List<PokemonView> printedBenchView(UUID player) {return state(player).bench().stream().map(p->view(p,false)).toList();}
    public PokemonView printedPokemonView(UUID player,UUID pokemon) {return view(findPokemon(state(player),pokemon),false);}
    public PokemonProfile activeProfile(UUID player) { return state(player).active().profile; }
    /** Returns the profile for a server-validated public in-play Pokémon identity. */
    public PokemonProfile pokemonProfile(UUID player, UUID pokemonId) { return findPokemon(state(player), pokemonId).profile; }
    public long pokemonEntrySequence(UUID player, UUID pokemonId) { return findPokemon(state(player), pokemonId).entrySequence; }
    /** Server match bridge records only actual attack damage during an opponent turn, never placed counters/checkup. */
    public void recordOpponentAttackDamage(UUID player,UUID pokemonId,int opponentTurn,int amount) {
        if(opponentTurn<=0||amount<=0)throw new IllegalArgumentException("受击历史须为实际回合中的正数招式伤害");
        var pokemon=findPokemon(state(player),pokemonId);
        int prior=pokemon.attackDamageTurn==opponentTurn?pokemon.attackDamageReceived:0;
        int total=Math.addExact(prior,amount);
        pokemon.attackDamageTurn=opponentTurn;pokemon.attackDamageReceived=total;
    }
    /** Historical fact follows this in-play object through switches/healing, but not a changed evolution form. */
    public int attackDamageReceivedInTurn(UUID player,UUID pokemonId,int opponentTurn) {
        var pokemon=findPokemon(state(player),pokemonId);
        return opponentTurn>0&&pokemon.attackDamageTurn==opponentTurn?pokemon.attackDamageReceived:0;
    }
    private static void clearAttackDamageHistory(PokemonInPlay pokemon) {pokemon.attackDamageTurn=-1;pokemon.attackDamageReceived=0;}
    public long attachmentEntrySequence(UUID player,UUID pokemonId,UUID cardId) {
        var host=findPokemon(state(player),pokemonId);
        if(java.util.stream.Stream.concat(host.attachedEnergy.stream(),host.attachedTools.stream()).noneMatch(c->c.instanceId().equals(cardId)))
            throw new IllegalArgumentException("来源卡已不再附着此宝可梦");
        return Objects.requireNonNull(attachmentEntries.get(cardId),"附着来源缺少入场身份");
    }
    public List<AbilityDefinition> abilities(UUID player, UUID pokemonId) {
        return possessedAbilities(findPokemon(state(player), pokemonId));
    }
    private static List<AbilityDefinition> possessedAbilities(PokemonInPlay pokemon) {
        if (!pokemon.starCipherAbility) return pokemon.profile.abilities();
        var abilities = new ArrayList<>(pokemon.profile.abilities());
        abilities.add(STAR_CIPHER_ABILITY);
        return List.copyOf(abilities);
    }
    /** Returns only currently effective Abilities; the printed card data is never altered by a temporary lock. */
    public List<AbilityDefinition> activeAbilities(UUID player, UUID pokemonId) {
        PokemonInPlay pokemon = findPokemon(state(player), pokemonId);
        return pokemon.continuousEffects.stream().anyMatch(ContinuousEffect::disablesAbilityEffects)
            ? List.of() : possessedAbilities(pokemon);
    }
    /** The granted Ability belongs to this exact in-play Pokémon; leaving play discards it with this field entry. */
    public void grantStarCipherAbility(UUID owner, UUID pokemonId) {
        requireTurn(owner);
        findPokemon(state(owner), pokemonId).starCipherAbility = true;
    }
    public AbilityDefinition ability(UUID player, UUID pokemonId, String abilityId) {
        return findPokemon(state(player), pokemonId).profile.ability(abilityId)
            .orElseThrow(() -> new IllegalArgumentException("该宝可梦没有此特性：" + abilityId));
    }
    public boolean abilityUsed(UUID player, UUID pokemonId, String abilityId) {
        return findPokemon(state(player), pokemonId).usedAbilityIds.contains(abilityId);
    }
    /** Validates the current player, in-play identity and per-instance usage before the match resolves the effect. */
    public AbilityDefinition beginAbility(UUID player, UUID pokemonId, String abilityId) {
        requireTurn(player);
        PokemonInPlay pokemon = findPokemon(state(player), pokemonId);
        if (pokemon.continuousEffects.stream().anyMatch(ContinuousEffect::disablesAbilityEffects))
            throw new IllegalStateException("该宝可梦的特性当前失效");
        AbilityDefinition ability = pokemon.profile.ability(abilityId)
            .orElseThrow(() -> new IllegalArgumentException("该宝可梦没有此特性：" + abilityId));
        if (ability.oncePerTurn() && pokemon.usedAbilityIds.contains(abilityId))
            throw new IllegalStateException("此宝可梦本回合已经使用过该特性");
        if (ability.oncePerTurn()) pokemon.usedAbilityIds.add(abilityId);
        return ability;
    }
    public List<CardInstance> attachedEnergy(UUID player, UUID pokemonId) {
        return List.copyOf(findPokemon(state(player), pokemonId).attachedEnergy);
    }
    public boolean hasBench(UUID player) { return !state(player).bench.isEmpty(); }
    public boolean hasActive(UUID player) { return state(player).active != null; }
    public boolean retreated() { return retreats >= retreatLimit; }
    /** Compatibility predicate: true for either kind of protection from this attacker. */
    public boolean preventsAttack(UUID affectedPlayer, UUID attackingPlayer) {
        PokemonInPlay active = state(affectedPlayer).active;
        return attackProtection(affectedPlayer, attackingPlayer) != null;
    }
    /** Shared source/impact gate. Attack-only legacy shields remain compatible and cannot block Abilities. */
    public boolean preventsImpact(UUID affectedPlayer, UUID pokemonId,
        dev.hua.ptcglive.engine.effect.EffectOrigin origin, dev.hua.ptcglive.engine.effect.EffectProtection.Impact impact) {
        return preventsImpact(affectedPlayer,pokemonId,origin,impact,filter->filter.equals(dev.hua.ptcglive.engine.effect.AttackSourceFilter.ANY));
    }
    public boolean preventsImpact(UUID affectedPlayer, UUID pokemonId,
        dev.hua.ptcglive.engine.effect.EffectOrigin origin, dev.hua.ptcglive.engine.effect.EffectProtection.Impact impact,
        java.util.function.Predicate<dev.hua.ptcglive.engine.effect.AttackSourceFilter> matchesAttacker) {
        var pokemon = findPokemon(state(affectedPlayer),pokemonId);
        for (var effect : pokemon.continuousEffects) {
            if (effect.effectProtection() != null && effect.effectProtection().blocks(origin,affectedPlayer,impact,matchesAttacker)) return true;
            if (origin.kind() == dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.ATTACK && effect.appliesToAttackFrom(origin.controller())
                && (impact == dev.hua.ptcglive.engine.effect.EffectProtection.Impact.DAMAGE
                    || effect.protectionScope() == AttackDefinition.ProtectionScope.DAMAGE_AND_EFFECTS)) return true;
        }
        return false;
    }
    public void addProtection(UUID sourceController, UUID targetOwner, UUID pokemonId,
        dev.hua.ptcglive.engine.effect.ProtectionEffect protection) {
        state(sourceController); var pokemon=findPokemon(state(targetOwner),pokemonId);
        var value=attributed(ContinuousEffect.protection(sourceController,protection));
        if(protection.boundToTopCard()) {
            UUID top=pokemon.topCard().instanceId();
            if(pokemon.continuousEffects.stream().noneMatch(e->e.equals(value)&&top.equals(pokemon.cardBoundEffects.get(e)))) {
                pokemon.continuousEffects.add(value);pokemon.cardBoundEffects.put(value,top);
            }
        } else if(pokemon.continuousEffects.stream().noneMatch(e->e.equals(value)&&!pokemon.cardBoundEffects.containsKey(e)))pokemon.continuousEffects.add(value);
    }
    /** The scope is server-owned and expires at the start of the protected Pokémon owner's next turn. */
    public AttackDefinition.ProtectionScope attackProtection(UUID affectedPlayer, UUID attackingPlayer) {
        PokemonInPlay active = state(affectedPlayer).active;
        if (active == null) return null;
        return attackProtection(affectedPlayer, active.pokemonId, attackingPlayer);
    }
    /** Target-specific protection is required for attacks that select or hit Benched Pokémon. */
    public AttackDefinition.ProtectionScope attackProtection(UUID affectedPlayer, UUID pokemonId, UUID attackingPlayer) {
        PokemonInPlay pokemon = findPokemon(state(affectedPlayer), pokemonId);
        return pokemon.continuousEffects.stream().filter(effect -> effect.appliesToAttackFrom(attackingPlayer))
            .map(ContinuousEffect::protectionScope)
            .max(java.util.Comparator.comparingInt(scope -> scope == AttackDefinition.ProtectionScope.DAMAGE_AND_EFFECTS ? 1 : 0))
            .orElse(null);
    }
    public void protectFromOpponentNextTurn(UUID owner, UUID pokemonId, UUID opponent) {
        protectFromOpponentNextTurn(owner, pokemonId, opponent, AttackDefinition.ProtectionScope.DAMAGE_AND_EFFECTS);
    }
    public void protectFromOpponentNextTurn(UUID owner, UUID pokemonId, UUID opponent, AttackDefinition.ProtectionScope scope) {
        requireTurn(owner);
        PokemonInPlay pokemon = findPokemon(state(owner), pokemonId);
        if (!players.containsKey(opponent) || opponent.equals(owner)) throw new IllegalArgumentException("攻击免疫的对手无效");
        Objects.requireNonNull(scope, "攻击保护范围");
        pokemon.continuousEffects.removeIf(effect -> effect.sourceKind()==effectSourceKind.get()&&effect.kind() == ContinuousEffect.Kind.ATTACK_PROTECTION
            && effect.opposingPlayer().equals(opponent));
        pokemon.continuousEffects.add(attributed(ContinuousEffect.attackProtection(owner, opponent, scope)));
    }
    /** Adds a server-owned, attack-only reduction which expires at the owner's next turn start. */
    public void reduceDamageFromOpponentNextTurn(UUID owner, UUID pokemonId, UUID opponent, int amount) {
        requireTurn(owner);
        PokemonInPlay pokemon = findPokemon(state(owner), pokemonId);
        if (!players.containsKey(opponent) || opponent.equals(owner)) throw new IllegalArgumentException("减伤效果的对手无效");
        pokemon.continuousEffects.add(attributed(ContinuousEffect.damageReduction(owner, opponent, amount)));
    }
    /** Source-bound attack effects clear with normal switch/evolution/leave cleanup, and expire at the next opposing turn end. */
    public void modifyDamageReceivedNextOpponentTurn(UUID owner,UUID pokemonId,UUID opponent,
        dev.hua.ptcglive.engine.effect.AttackDamageReceivedModifier modifier) {
        requireTurn(owner);state(opponent);
        findPokemon(state(owner),pokemonId).continuousEffects.add(attributed(ContinuousEffect.receivedModifier(owner,opponent,modifier)));
    }
    public int attackDamageReceivedChange(UUID affectedPlayer,UUID pokemonId,UUID attackingPlayer,
        dev.hua.ptcglive.engine.effect.AttackSourceFilter.Facts source) {
        if(!attackingPlayer.equals(currentPlayer))return 0;
        return findPokemon(state(affectedPlayer),pokemonId).continuousEffects.stream()
            .filter(e->e.kind()==ContinuousEffect.Kind.ATTACK_DAMAGE_RECEIVED_MODIFIER&&e.opposingPlayer().equals(attackingPlayer)
                &&e.receivedModifier().attackers().matches(source))
            .mapToInt(e->e.receivedModifier().amount()).sum();
    }
    /** The affected Pokémon's attacks do less damage until the effect owner's next turn starts; switching or evolving clears it. */
    public void penalizeAttackDamageUntilOwnerNextTurn(UUID effectOwner, UUID affectedPlayer, UUID pokemonId, int amount) {
        state(effectOwner);
        PokemonInPlay pokemon = findPokemon(state(affectedPlayer), pokemonId);
        pokemon.continuousEffects.add(attributed(ContinuousEffect.attackDamagePenalty(effectOwner, amount)));
    }
    /** Total “this Pokémon's attacks do N less damage” currently on an attacking Pokémon (before Weakness and Resistance). */
    public int attackDamagePenalty(UUID player, UUID pokemonId) {
        return findPokemon(state(player), pokemonId).continuousEffects.stream()
            .filter(ContinuousEffect::penalizesAttackDamage).mapToInt(ContinuousEffect::damageReduction).sum();
    }
    /**
     * Weakness changes immediately after the applying attack's damage and lasts through the effect owner's next turn.
     * The effect belongs to its target, so that target switching or evolving clears it; the attacker's movement does not.
     * Reapplication replaces the old expiry. The printed Pokémon profile remains unchanged.
     */
    public void overwriteWeaknessThroughOwnerNextTurn(UUID effectOwner,UUID affectedPlayer,UUID pokemonId,
                                                       EnergyType type,boolean untilOwnerTurnEnd) {
        var pokemon=findPokemon(state(affectedPlayer),pokemonId);
        pokemon.weaknessOverride=new TimedWeakness(effectOwner,state(effectOwner).turnsStarted+1,type,untilOwnerTurnEnd,type==null?0:2,effectSourceKind.get());
    }
    public void overwriteWeaknessUntilLeavesActive(UUID effectOwner,UUID affectedPlayer,UUID pokemonId,EnergyType type) {
        var pokemon=findPokemon(state(affectedPlayer),pokemonId);
        int multiplier=effectiveWeaknessMultiplier(affectedPlayer,pokemonId);
        pokemon.weaknessOverride=new TimedWeakness(effectOwner,Integer.MAX_VALUE,type,true,multiplier,effectSourceKind.get());
    }
    public EnergyType effectiveWeaknessType(UUID owner,UUID pokemonId) {
        var pokemon=findPokemon(state(owner),pokemonId);
        return pokemon.weaknessOverride==null?pokemon.profile.weaknessType():pokemon.weaknessOverride.type();
    }
    public int effectiveWeaknessMultiplier(UUID owner,UUID pokemonId) {
        var pokemon=findPokemon(state(owner),pokemonId);
        return pokemon.weaknessOverride==null?pokemon.profile.weaknessMultiplier():pokemon.weaknessOverride.multiplier();
    }
    public void boostAttackDamageThroughOwnerNextTurn(UUID effectOwner, UUID affectedPlayer, UUID pokemonId, int amount) {
        boostAttackDamageThroughOwnerNextTurn(effectOwner, affectedPlayer, pokemonId, amount, null);
    }
    public void boostAttackDamageThroughOwnerNextTurn(UUID effectOwner, UUID affectedPlayer, UUID pokemonId, int amount, String attackId) {
        PokemonInPlay pokemon = findPokemon(state(affectedPlayer), pokemonId);
        pokemon.continuousEffects.add(attributed(ContinuousEffect.attackDamageBonus(effectOwner, amount, state(effectOwner).turnsStarted + 1, attackId)));
    }
    public void increaseDamageTakenThroughOwnerNextTurn(UUID effectOwner, UUID affectedPlayer, UUID pokemonId, int amount) {
        PokemonInPlay pokemon = findPokemon(state(affectedPlayer), pokemonId);
        pokemon.continuousEffects.add(attributed(ContinuousEffect.damageTakenIncrease(effectOwner, amount, state(effectOwner).turnsStarted + 1)));
    }
    /** “If the Defending Pokémon tries to attack during your opponent's next turn, your opponent flips a coin …”; switching or evolving clears it. */
    public void gateAttackWithCoinUntilOwnerNextTurn(UUID effectOwner,UUID affectedPlayer,UUID pokemonId) {
        gateAttackWithCoinUntilOwnerNextTurn(effectOwner,affectedPlayer,pokemonId,1);
    }
    public void gateAttackWithCoinUntilOwnerNextTurn(UUID effectOwner,UUID affectedPlayer,UUID pokemonId,int flips) {
        state(effectOwner);
        var gate=attributed(ContinuousEffect.attackCoinGate(effectOwner,flips));
        PokemonInPlay pokemon=findPokemon(state(affectedPlayer),pokemonId);
        pokemon.continuousEffects.removeIf(effect->effect.gatesAttackWithCoin()&&effect.owner().equals(effectOwner));
        pokemon.continuousEffects.add(gate);
    }
    public boolean attackCoinGated(UUID player,UUID pokemonId) {return attackGateFlips(player,pokemonId)>0;}
    public int attackGateFlips(UUID player,UUID pokemonId) {
        return findPokemon(state(player),pokemonId).continuousEffects.stream().mapToInt(ContinuousEffect::attackGateFlips).max().orElse(0);
    }
    /** “This Pokémon's attacks do N more damage” bonuses that are live now (the owner's next turn has started). */
    public int attackDamageBonus(UUID player, UUID pokemonId) {
        return attackDamageBonus(player, pokemonId, null);
    }
    /** Uses the attack actually declared and paid for, not the body of an attack copied from another Pokémon. */
    public int attackDamageBonus(UUID player, UUID pokemonId, String declaredAttackId) {
        return liveNextTurnAmount(player, pokemonId, effect -> effect.boostsAttackDamage(declaredAttackId));
    }
    /** “This Pokémon takes N more damage from attacks” increases that are live now (the owner's next turn has started). */
    public int damageTakenIncrease(UUID player, UUID pokemonId) {
        return liveNextTurnAmount(player, pokemonId, ContinuousEffect::increasesDamageTaken);
    }
    public void guardSmallAttackDamageUntilOwnerNextTurn(UUID owner,UUID affectedPlayer,UUID pokemonId,int maximum) {
        var pokemon=findPokemon(state(affectedPlayer),pokemonId);
        pokemon.continuousEffects.removeIf(effect->effect.sourceKind()==effectSourceKind.get()&&effect.kind()==ContinuousEffect.Kind.ATTACK_DAMAGE_MAX_GUARD&&effect.owner().equals(owner));
        pokemon.continuousEffects.add(attributed(ContinuousEffect.attackDamageMaximumGuard(owner,maximum)));
    }
    public int attackDamageMaximumGuard(UUID player,UUID pokemonId,UUID attacker) {
        return findPokemon(state(player),pokemonId).continuousEffects.stream().filter(effect->effect.guardsAttackDamageMaximum(attacker))
            .mapToInt(ContinuousEffect::damageReduction).max().orElse(0);
    }
    public void overrideAttackBaseThroughOwnerNextTurn(UUID owner,UUID affectedPlayer,UUID pokemonId,String attackId,int amount) {
        var pokemon=findPokemon(state(affectedPlayer),pokemonId);
        pokemon.continuousEffects.removeIf(effect->effect.sourceKind()==effectSourceKind.get()&&effect.kind()==ContinuousEffect.Kind.NEXT_TURN_ATTACK_BASE
            &&effect.owner().equals(owner)&&effect.overridesAttackBase(attackId));
        pokemon.continuousEffects.add(attributed(ContinuousEffect.nextTurnAttackBase(owner,attackId,amount,state(owner).turnsStarted+1)));
    }
    public java.util.OptionalInt nextTurnAttackBase(UUID player,UUID pokemonId,String attackId) {
        return findPokemon(state(player),pokemonId).continuousEffects.stream()
            .filter(effect->effect.overridesAttackBase(attackId)&&effect.owner().equals(currentPlayer)
                &&state(effect.owner()).turnsStarted==effect.expiresAfterOwnerTurn())
            .mapToInt(ContinuousEffect::damageReduction).max();
    }
    private int liveNextTurnAmount(UUID player, UUID pokemonId, java.util.function.Predicate<ContinuousEffect> kind) {
        return findPokemon(state(player), pokemonId).continuousEffects.stream().filter(kind)
            .filter(effect -> effect.owner().equals(currentPlayer) && state(effect.owner()).turnsStarted == effect.expiresAfterOwnerTurn())
            .mapToInt(ContinuousEffect::damageReduction).sum();
    }
    /**
     * “During your opponent's next turn, if this Pokémon is damaged by an attack, put N damage counters on the Attacking
     * Pokémon.” Lives on this Pokémon until the effect owner's next turn starts; switching or evolving clears it.
     */
    public void retaliateUntilOwnerNextTurn(UUID effectOwner, UUID opposingPlayer, UUID affectedPlayer, UUID pokemonId, int counters) {
        state(effectOwner); state(opposingPlayer);
        PokemonInPlay pokemon = findPokemon(state(affectedPlayer), pokemonId);
        pokemon.continuousEffects.add(attributed(ContinuousEffect.retaliation(effectOwner, opposingPlayer, counters)));
    }
    /** The retaliation effects on this Pokémon that answer an attack from {@code attacker}. */
    public List<ContinuousEffect> retaliations(UUID player, UUID pokemonId, UUID attacker) {
        return findPokemon(state(player), pokemonId).continuousEffects.stream().filter(effect -> effect.retaliatesAgainst(attacker)).toList();
    }
    /** Applies a server-owned effect that blocks only an ordinary retreat, not a forced switch. */
    public void preventRetreatUntilOwnerNextTurn(UUID effectOwner, UUID affectedPlayer, UUID pokemonId) {
        state(effectOwner);
        PokemonInPlay pokemon = findPokemon(state(affectedPlayer), pokemonId);
        pokemon.continuousEffects.removeIf(effect -> effect.sourceKind()==effectSourceKind.get()&&effect.kind() == ContinuousEffect.Kind.RETREAT_LOCK
            && effect.owner().equals(effectOwner));
        pokemon.continuousEffects.add(attributed(ContinuousEffect.retreatLock(effectOwner)));
    }
    /** Applies a player-wide lock covering all current and future Poisoned Pokémon of the affected player. */
    public void preventPoisonedRetreatUntilOwnerNextTurn(UUID effectOwner, UUID affectedPlayer) {
        state(effectOwner); state(affectedPlayer);
        poisonedRetreatLocks.removeIf(lock -> lock.effectOwner().equals(effectOwner) && lock.affectedPlayer().equals(affectedPlayer));
        poisonedRetreatLocks.add(new PoisonRetreatLock(effectOwner, affectedPlayer));
    }
    /** Applies a server-owned effect that blocks attacks while the affected Pokémon remains Active. */
    public void preventAttackUntilOwnerNextTurn(UUID effectOwner, UUID affectedPlayer, UUID pokemonId) {
        state(effectOwner);
        PokemonInPlay pokemon = findPokemon(state(affectedPlayer), pokemonId);
        pokemon.continuousEffects.removeIf(effect -> effect.sourceKind()==effectSourceKind.get()&&effect.kind() == ContinuousEffect.Kind.ATTACK_LOCK
            && effect.owner().equals(effectOwner));
        pokemon.continuousEffects.add(attributed(ContinuousEffect.attackLock(effectOwner)));
    }
    /** Blocks attacks through the effect owner's next turn; moving to the Bench or evolving clears it as usual. */
    public void preventAttackThroughOwnerNextTurn(UUID effectOwner, UUID affectedPlayer, UUID pokemonId) {
        state(effectOwner);
        PokemonInPlay pokemon = findPokemon(state(affectedPlayer), pokemonId);
        pokemon.continuousEffects.removeIf(effect -> effect.sourceKind()==effectSourceKind.get()&&effect.kind() == ContinuousEffect.Kind.ATTACK_LOCK
            && effect.owner().equals(effectOwner) && effect.expiry() == ContinuousEffect.Expiry.OWNER_TURN_END);
        pokemon.continuousEffects.add(attributed(ContinuousEffect.attackLockThroughOwnerNextTurn(effectOwner, state(effectOwner).turnsStarted + 1)));
    }
    /** Applies a server-owned effect that prevents this public Pokémon from evolving until the effect owner's next turn. */
    public void preventEvolutionUntilOwnerNextTurn(UUID effectOwner, UUID affectedPlayer, UUID pokemonId) {
        state(effectOwner);
        PokemonInPlay pokemon = findPokemon(state(affectedPlayer), pokemonId);
        pokemon.continuousEffects.removeIf(effect -> effect.sourceKind()==effectSourceKind.get()&&effect.kind() == ContinuousEffect.Kind.EVOLUTION_LOCK
            && effect.owner().equals(effectOwner));
        pokemon.continuousEffects.add(attributed(ContinuousEffect.evolutionLock(effectOwner)));
    }
    public void preventHandEvolutionUntilOwnerNextTurn(UUID effectOwner,UUID affectedPlayer,UUID pokemonId) {
        state(effectOwner);var pokemon=findPokemon(state(affectedPlayer),pokemonId);
        pokemon.continuousEffects.removeIf(e->e.sourceKind()==effectSourceKind.get()&&e.kind()==ContinuousEffect.Kind.HAND_EVOLUTION_LOCK&&e.owner().equals(effectOwner));
        pokemon.continuousEffects.add(attributed(ContinuousEffect.handEvolutionLock(effectOwner)));
    }
    public void preventHandEnergy(UUID sourceOwner,UUID owner,UUID pokemonId) {
        var p=findPokemon(state(owner),pokemonId);var lock=attributed(ContinuousEffect.handEnergyLock(sourceOwner));
        if(!p.continuousEffects.contains(lock))p.continuousEffects.add(lock);
    }
    public boolean energyFromHandAllowed(UUID owner,UUID pokemonId) {
        return findPokemon(state(owner),pokemonId).continuousEffects.stream().noneMatch(e->e.kind()==ContinuousEffect.Kind.HAND_ENERGY_LOCK);
    }
    public boolean evolutionFromHandAllowed(UUID owner,UUID pokemonId) {
        return findPokemon(state(owner),pokemonId).continuousEffects.stream().noneMatch(e->e.kind()==ContinuousEffect.Kind.HAND_EVOLUTION_LOCK);
    }
    public boolean activeAttackLocked(UUID player) {
        PokemonInPlay active = state(player).active;
        return active != null && active.continuousEffects.stream().anyMatch(ContinuousEffect::preventsAttack);
    }
    /** A named-attack cooldown never prevents the Pokémon's other declared attacks. */
    public boolean activeAttackLocked(UUID player, AttackDefinition attack) {
        PokemonInPlay active = state(player).active;
        return active != null && active.continuousEffects.stream().anyMatch(effect -> effect.preventsAttackNamed(attack.nameZh()));
    }
    public boolean pokemonAttackLocked(UUID player,UUID pokemonId) {
        return findPokemon(state(player),pokemonId).continuousEffects.stream().anyMatch(ContinuousEffect::preventsAttack);
    }
    public boolean pokemonAttackLocked(UUID player,UUID pokemonId,AttackDefinition attack) {
        return findPokemon(state(player),pokemonId).continuousEffects.stream().anyMatch(e->e.preventsAttackNamed(attack.nameZh()));
    }
    /** Locks every currently available attack with the same printed name until the end of this Pokémon owner's next turn. */
    public void lockAttackUntilAfterOwnerNextTurn(UUID owner, UUID pokemonId, AttackDefinition attack) {
        requireTurn(owner);
        PokemonInPlay pokemon = findPokemon(state(owner), pokemonId);
        pokemon.continuousEffects.removeIf(effect -> effect.sourceKind()==effectSourceKind.get()&&effect.kind() == ContinuousEffect.Kind.ATTACK_ID_LOCK
            && effect.owner().equals(owner) && effect.preventsAttackNamed(attack.nameZh()));
        pokemon.continuousEffects.add(attributed(ContinuousEffect.attackCooldown(owner, attack.nameZh(),
            state(owner).turnsStarted + 1)));
    }
    /** Locks one printed attack name of another player's Pokémon until the effect owner's next turn starts (switching or evolving clears it). */
    public void lockAttackUntilOwnerNextTurnStart(UUID effectOwner, UUID affectedPlayer, UUID pokemonId, String attackNameZh) {
        state(effectOwner);
        PokemonInPlay pokemon = findPokemon(state(affectedPlayer), pokemonId);
        var lock = attributed(ContinuousEffect.opponentAttackLock(effectOwner, attackNameZh));
        if (!pokemon.continuousEffects.contains(lock)) pokemon.continuousEffects.add(lock);
    }
    /** Locks the printed attack name until this Pokémon leaves the Active Spot (switching or evolving clears it). */
    public void lockAttackUntilLeavesActive(UUID owner, UUID pokemonId, String attackNameZh) {
        PokemonInPlay pokemon = findPokemon(state(owner), pokemonId);
        var lock = attributed(ContinuousEffect.attackLockUntilLeavesActive(owner, attackNameZh));
        if (!pokemon.continuousEffects.contains(lock)) pokemon.continuousEffects.add(lock);
    }
    /** Applies an expiring retreat-cost increase or reduction to either public field. */
    public void modifyRetreatCostUntilOwnerNextTurn(UUID effectOwner, UUID affectedPlayer, UUID pokemonId, int change) {
        state(effectOwner);
        PokemonInPlay pokemon = findPokemon(state(affectedPlayer), pokemonId);
        pokemon.continuousEffects.removeIf(effect -> effect.sourceKind()==effectSourceKind.get()&&effect.kind() == ContinuousEffect.Kind.RETREAT_COST_MODIFIER
            && effect.owner().equals(effectOwner));
        pokemon.continuousEffects.add(attributed(ContinuousEffect.retreatCostModifier(effectOwner, change)));
    }
    public void increaseAttackCostUntilOwnerNextTurn(UUID effectOwner,UUID affectedPlayer,UUID pokemonId,int amount) {
        state(effectOwner);var effect=attributed(ContinuousEffect.attackCostIncrease(effectOwner,amount));
        var pokemon=findPokemon(state(affectedPlayer),pokemonId);
        if(!pokemon.continuousEffects.contains(effect))pokemon.continuousEffects.add(effect);
    }
    public int attackCostIncrease(UUID player,UUID pokemonId) {
        return findPokemon(state(player),pokemonId).continuousEffects.stream()
            .filter(e->e.kind()==ContinuousEffect.Kind.ATTACK_COST_MODIFIER).mapToInt(ContinuousEffect::retreatCostChange).sum();
    }
    /** Keeps Tools attached while temporarily preventing their printed effects from applying. */
    public void disableToolEffectsUntilOwnerNextTurn(UUID effectOwner, UUID affectedPlayer, UUID pokemonId) {
        state(effectOwner);
        PokemonInPlay pokemon = findPokemon(state(affectedPlayer), pokemonId);
        pokemon.continuousEffects.removeIf(effect -> effect.sourceKind()==effectSourceKind.get()&&effect.kind() == ContinuousEffect.Kind.TOOL_EFFECTS_DISABLED
            && effect.owner().equals(effectOwner));
        pokemon.continuousEffects.add(attributed(ContinuousEffect.toolEffectsDisabled(effectOwner)));
    }
    /** Public-field predicate; it reveals no Tool identity beyond already public attachments. */
    public boolean toolEffectsDisabled(UUID player, UUID pokemonId) {
        return toolEffectsDisabled(player,pokemonId,false);
    }
    public boolean toolEffectsDisabled(UUID player, UUID pokemonId,boolean ignoreOpposingAbilities) {
        return findPokemon(state(player), pokemonId).continuousEffects.stream().anyMatch(e->e.disablesToolEffects()
            &&(!ignoreOpposingAbilities||e.sourceKind()!=dev.hua.ptcglive.engine.effect.EffectOrigin.Kind.ABILITY||e.owner().equals(player)));
    }
    /** Public-field predicate; the continuous-effect label is included in the visible in-play state. */
    public boolean abilityEffectsDisabled(UUID player, UUID pokemonId) {
        return findPokemon(state(player), pokemonId).continuousEffects.stream().anyMatch(ContinuousEffect::disablesAbilityEffects);
    }
    /** Keeps the printed Ability data but suppresses every activated and passive server resolution until expiry. */
    public void disableAbilityEffectsUntilOwnerNextTurn(UUID effectOwner, UUID affectedPlayer, UUID pokemonId) {
        state(effectOwner);
        PokemonInPlay pokemon = findPokemon(state(affectedPlayer), pokemonId);
        pokemon.continuousEffects.removeIf(effect -> effect.sourceKind()==effectSourceKind.get()&&effect.kind() == ContinuousEffect.Kind.ABILITY_EFFECTS_DISABLED
            && effect.owner().equals(effectOwner));
        pokemon.continuousEffects.add(attributed(ContinuousEffect.abilityEffectsDisabled(effectOwner)));
    }
    /** The reduction is resolved from the target's server state, never an item supplied by the attacker. */
    public int attackDamageReduction(UUID affectedPlayer, UUID pokemonId, UUID attackingPlayer) {
        return findPokemon(state(affectedPlayer), pokemonId).continuousEffects.stream()
            .filter(effect -> effect.reducesDamageFrom(attackingPlayer)).mapToInt(ContinuousEffect::damageReduction).sum();
    }
    public int activeRetreatCost(UUID player) {
        return retreatCost(player, state(player).active().pokemonId);
    }
    public int retreatCost(UUID player, UUID pokemonId) {
        var active = findPokemon(state(player), pokemonId);
        // These Item forms have no printed retreat cost; cost increases cannot create one.
        if(active.profile.printedRules().isItemPokemon())return 0;
        // Removing the cost overrides every printed, passive and temporary cost increase.
        if(freeRetreat.test(player,active.pokemonId))return 0;
        int temporary = active.continuousEffects.stream().filter(ContinuousEffect::modifiesRetreatCost)
            .mapToInt(ContinuousEffect::retreatCostChange).sum();
        return Math.max(0, active.profile.retreatCost() - retreatReduction.applyAsInt(player, active.pokemonId) + temporary);
    }
    public void attachTool(UUID player, UUID targetId, CardInstance tool, int maximumTools) {
        requireTurn(player);
        attachToolByEffect(player, targetId, tool, maximumTools);
    }
    /** A resolved effect may attach a Tool to either public field; it never becomes a client action. */
    public void attachToolByEffect(UUID owner, UUID targetId, CardInstance tool, int maximumTools) {
        var target = findPokemon(state(owner), targetId);
        if (maximumTools < 1 || maximumTools > 4) throw new IllegalArgumentException("工具数量例外参数无效");
        if (target.attachedTools.size() >= maximumTools) throw new IllegalStateException("此宝可梦已达到当前场上效果允许的宝可梦工具数量上限");
        target.attachedTools.add(Objects.requireNonNull(tool));
        attachmentEntries.put(tool.instanceId(),++nextAttachmentEntrySequence);
    }
    /** Removes one public attached Tool. The match resolver owns the destination discard zone. */
    public CardInstance detachTool(UUID owner, UUID targetId, UUID toolId) {
        PokemonInPlay target = findPokemon(state(owner), targetId);
        CardInstance tool = target.attachedTools.stream().filter(card -> card.instanceId().equals(toolId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("指定卡牌不是该宝可梦附着的工具"));
        target.attachedTools.remove(tool);
        return tool;
    }
    public boolean canEvolve(UUID player, UUID targetId, String definitionId) {
        return canEvolve(player, targetId, definitionId, EvolutionPermission.STRICT);
    }
    /** Card effects can use a declared permission; callers never infer timing exceptions. */
    public boolean canEvolve(UUID player, UUID targetId, String definitionId, EvolutionPermission permission) {
        PokemonInPlay target = findPokemon(state(player), targetId);
        PokemonProfile evolved = profiles.get(definitionId);
        Objects.requireNonNull(permission, "进化时点许可");
        String targetDefinitionId = target.topCard().definitionId();
        return evolutionTimingAllowed(player, target, permission) && evolved != null
            && (evolved.evolvesFrom(targetDefinitionId, profiles.get(targetDefinitionId))
                || permission.allowSkipOneEvolutionStage() && skipsExactlyOneEvolutionStage(target, evolved));
    }
    /** Printed name-alias evolution (for example Rainbow DNA) still obeys every ordinary timing limit. */
    public boolean canEvolveFromNameAlias(UUID player,UUID targetId,String definitionId,
                                          EvolutionPermission permission,String sourceName) {
        PokemonInPlay target=findPokemon(state(player),targetId);
        PokemonProfile evolved=profiles.get(definitionId);
        Objects.requireNonNull(permission,"进化时点许可");
        return evolved!=null&&evolved.evolvesFromName()!=null&&evolved.evolvesFromName().equals(sourceName)
            &&evolutionTimingAllowed(player,target,permission);
    }
    /** True only for a reviewed one-stage skip, never for an ordinary direct evolution. */
    public boolean canEvolveSkippingOneStage(UUID player, UUID targetId, String definitionId, EvolutionPermission permission) {
        PokemonInPlay target = findPokemon(state(player), targetId);
        PokemonProfile evolved = profiles.get(definitionId);
        Objects.requireNonNull(permission, "进化时点许可");
        return permission.allowSkipOneEvolutionStage() && evolved != null && evolutionTimingAllowed(player, target, permission)
            && skipsExactlyOneEvolutionStage(target, evolved);
    }
    public boolean evolutionTimingAllowed(UUID player,UUID pokemonId,EvolutionPermission permission) {
        return evolutionTimingAllowed(player,findPokemon(state(player),pokemonId),Objects.requireNonNull(permission));
    }
    private boolean evolutionTimingAllowed(UUID player, PokemonInPlay target, EvolutionPermission permission) {
        return player.equals(currentPlayer) && (permission.allowOwnerFirstTurn() || state(player).turnsStarted > 1)
            && (permission.allowJustEnteredThisTurn() || target.enteredTurn < turnNumber && target.lastDevolvedTurn != turnNumber)
            && (permission.allowAnotherEvolutionThisTurn() || target.lastEvolvedTurn != turnNumber)
            && target.continuousEffects.stream().noneMatch(ContinuousEffect::preventsEvolution);
    }
    private boolean skipsExactlyOneEvolutionStage(PokemonInPlay target, PokemonProfile evolved) {
        if (evolved.evolvesFromDefinitionId() == null) return false;
        String targetId = target.topCard().definitionId();
        PokemonProfile targetProfile = profiles.get(targetId);
        if (evolved.evolvesFromName() != null) {
            // By name: some Stage 1 printing named like the Stage 2's "Evolves from" must itself evolve from this
            // Pokémon; which printings the players own does not matter for the rule.
            return evolutionsByName.getOrDefault(evolved.evolvesFromName(), List.of()).stream()
                .anyMatch(intermediate -> intermediate.evolvesFrom(targetId, targetProfile));
        }
        PokemonProfile intermediate = profiles.get(evolved.evolvesFromDefinitionId());
        return intermediate != null && intermediate.evolvesFromDefinitionId() != null
            && intermediate.evolvesFrom(targetId, targetProfile);
    }
    public CardInstance topCard(UUID player, UUID pokemonId) { return findPokemon(state(player), pokemonId).topCard(); }
    /** Physical cards currently below the top card, in evolution order. */
    public List<CardInstance> previousEvolutionCards(UUID player,UUID pokemonId) {
        var stack=findPokemon(state(player),pokemonId).stack;
        return List.copyOf(stack.subList(0,stack.size()-1));
    }
    /** Server-only physical root; logical in-play identity must never be used as a card-zone ID. */
    public CardInstance baseCard(UUID player,UUID pokemonId) {return findPokemon(state(player),pokemonId).stack.getFirst();}
    /**
     * A card swap is the same in-play Pokémon, not evolution, departure or a new entry.
     * Keep its logical field identity, complete state and lower evolution cards; the
     * caller owns the validated source-zone transfer and destination of the old top card.
     */
    public CardInstance replaceTopCardByEffect(UUID owner,UUID pokemonId,CardInstance replacement) {
        var target=findPokemon(state(owner),pokemonId);
        var next=requireProfile(Objects.requireNonNull(replacement,"替换宝可梦卡"));
        requirePhysicalCardNotInPlay(replacement.instanceId());
        var previous=target.stack.set(target.stack.size()-1,replacement);
        target.profile=next;
        target.continuousEffects.removeIf(e->target.cardBoundEffects.containsKey(e)&&!replacement.instanceId().equals(target.cardBoundEffects.get(e)));
        pruneCardBindings(target);
        return previous;
    }

    /** Replaces all Pokemon cards, preserving the same stay, attachments, counters and effects. */
    public List<CardInstance> replacePokemonCardsByEffect(UUID owner,UUID pokemonId,CardInstance replacement) {
        var target=findPokemon(state(owner),pokemonId);
        var previous=new ArrayList<>(target.stack);
        replaceTopCardByEffect(owner,pokemonId,replacement);
        target.stack.clear();target.stack.add(replacement);
        return List.copyOf(previous);
    }

    /** Replaces the entire in-play Pokemon in the same slot; every old card leaves play and no state is inherited. */
    public LeavePlayResult replacePokemonAndDiscardStateByEffect(UUID owner,UUID pokemonId,CardInstance replacement) {
        PlayerField field=state(owner);
        PokemonInPlay old=findPokemon(field,pokemonId);
        boolean active=field.active==old;
        int benchIndex=active?-1:field.bench.indexOf(old);
        if(!active&&benchIndex<0)throw new IllegalStateException("待替换宝可梦不在场上");
        PokemonInPlay next=newPokemon(Objects.requireNonNull(replacement,"替换基础宝可梦"));
        if(active)field.active=next;else field.bench.set(benchIndex,next);
        List<CardInstance> discarded=new ArrayList<>(old.stack);
        discarded.addAll(old.attachedEnergy);discarded.addAll(old.attachedTools);
        return new LeavePlayResult(old.pokemonId,active,List.copyOf(discarded));
    }
    private boolean logicalIdInPlay(UUID id) {
        return players.values().stream().anyMatch(f->f.active!=null&&f.active.pokemonId.equals(id)
            ||f.bench.stream().anyMatch(p->p.pokemonId.equals(id)));
    }
    private void requirePhysicalCardNotInPlay(UUID id) {
        for(var field:players.values()) {
            var pokemon=new ArrayList<>(field.bench);if(field.active!=null)pokemon.add(field.active);
            for(var p:pokemon)if(java.util.stream.Stream.of(p.stack,p.attachedEnergy,p.attachedTools).flatMap(List::stream)
                .anyMatch(card->card.instanceId().equals(id)))throw new IllegalArgumentException("物理卡已在场，不能再次上场或替换");
        }
    }
    private PokemonInPlay newPokemon(CardInstance card) {
        var profile=requireProfile(card);requirePhysicalCardNotInPlay(card.instanceId());
        long entry=++nextEntrySequence;UUID logical=UUID.nameUUIDFromBytes(
            ("ptcglive-pokemon:"+card.instanceId()+":"+entry+":opaque").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        // Allocate an opaque identity per stay without spending game RNG.
        // A top-card exchange keeps it; any card that leaves and reenters gets a new stay.
        for(int salt=0;logicalIdInPlay(logical);salt++)logical=UUID.nameUUIDFromBytes(
            ("ptcglive-pokemon:"+card.instanceId()+":"+entry+":"+salt).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return new PokemonInPlay(profile,card,turnNumber,entry,logical);
    }

    /** “If this Pokémon moved from your Bench to the Active Spot this turn”: a promotion between turns is not this turn. */
    public boolean becameActiveThisTurn(UUID player, UUID pokemonId) { return findPokemon(state(player), pokemonId).becameActiveTurn == turnNumber; }
    public boolean becameActiveOnTurn(UUID player, UUID pokemonId, int checkedTurn) { return findPokemon(state(player), pokemonId).becameActiveTurn == checkedTurn; }
    /** A successful voluntary retreat is distinct from a switch or a failed retreat attempt. */
    public boolean retreatedOnTurn(UUID player, UUID pokemonId, int checkedTurn) {
        return checkedTurn>0 && findPokemon(state(player), pokemonId).retreatedTurn==checkedTurn;
    }
    /** “If this Pokémon was healed during this turn”: damage was actually removed by a heal during the current turn. */
    public boolean healedThisTurn(UUID player, UUID pokemonId) { return findPokemon(state(player), pokemonId).healedTurn == turnNumber; }
    public PokemonView pokemonView(UUID player, UUID pokemonId) { return view(findPokemon(state(player), pokemonId)); }
    public int heal(UUID player, UUID pokemonId, int amount) {
        requireTurn(player);
        return healByEffect(player, pokemonId, amount);
    }
    /** A resolved effect may heal either field; the match resolver establishes turn authority. */
    public int healByEffect(UUID owner, UUID pokemonId, int amount) {
        int healed = removeDamage(owner, pokemonId, amount);
        if (healed > 0) findPokemon(state(owner), pokemonId).healedTurn = turnNumber;
        return healed;
    }
    /** Moving damage counters off a Pokémon removes its damage without healing it. */
    public int removeDamageForMove(UUID owner, UUID pokemonId, int amount) { return removeDamage(owner, pokemonId, amount); }
    private int removeDamage(UUID owner, UUID pokemonId, int amount) {
        if (amount <= 0 || amount % 10 != 0) throw new IllegalArgumentException("回复量无效");
        PokemonInPlay pokemon = findPokemon(state(owner), pokemonId);
        int healed = Math.min(pokemon.damage, amount); pokemon.damage -= healed; return healed;
    }
    /** Switching by an effect is not retreating: no cost, no condition block, no once-per-turn consumption. */
    public void switchActive(UUID player, UUID replacementId) {
        requireTurn(player);
        switchActiveByEffect(player, replacementId);
    }
    /** A resolved effect may switch either field; the caller has already established effect authority. */
    public void switchActiveByEffect(UUID owner, UUID replacementId) {
        PlayerField field = state(owner);
        PokemonInPlay active = field.active(), replacement = findBench(field, replacementId);
        field.bench.remove(replacement); field.bench.add(active); field.active = replacement;
        replacement.becameActiveTurn = turnNumber;
        active.conditions.clear(); clearProtection(active);
    }

    /** Used during setup only. The higher-level match service verifies Basic Pokémon legality. */
    public void placeInitialActive(UUID player, CardInstance card) {
        PlayerField field = state(player);
        if (field.active != null) throw new IllegalStateException("已经选择战斗宝可梦");
        field.active = newPokemon(card);
    }

    public void placeOnBench(UUID player, CardInstance card) {
        PlayerField field = state(player);
        int limit=benchLimit.applyAsInt(player);
        if(limit<0||limit>MAX_BENCH_SIZE)throw new IllegalStateException("当前备战区上限无效");
        if (field.bench.size() >= limit) throw new IllegalStateException("备战区已达到当前上限");
        field.bench.add(newPokemon(card));
    }

    /** Begins a turn and resets only per-turn field actions. Turn order is enforced by the caller's match phase. */
    public void beginTurn(UUID player) {
        state(player);
        currentPlayer = player;
        poisonedRetreatLocks.removeIf(lock -> lock.effectOwner().equals(player));
        PlayerField field = state(player);
        field.turnsStarted++;
        if (field.active != null) field.active.usedAbilityIds.clear();
        for (PokemonInPlay pokemon : field.bench) pokemon.usedAbilityIds.clear();
        // An effect can target either side of the board. Expiry follows the
        // effect controller, so clear it across both fields at that owner's turn start.
        for (PlayerField board : players.values()) {
            if (board.active != null) expireTurnStartEffects(board.active, player);
            for (PokemonInPlay pokemon : board.bench) expireTurnStartEffects(pokemon, player);
        }
        turnNumber++;
        energyAttachments = 0;
        retreats = 0;
    }
    /** Called exactly as a real turn changes; cooldowns survive the creating turn and expire after the next owner turn. */
    public void endTurn(UUID player) {
        PlayerField owner = state(player);
        for (PlayerField board : players.values()) {
            if (board.active != null) expireTurnEndEffects(board.active, player, owner.turnsStarted);
            for (PokemonInPlay pokemon : board.bench) expireTurnEndEffects(pokemon, player, owner.turnsStarted);
        }
    }

    public void attachEnergy(UUID player, UUID targetPokemonId, CardInstance energy) {
        requireTurn(player);
        if (energyAttachments >= energyAttachmentLimit) throw new IllegalStateException("本规则包的每回合能量附着次数已用完");
        PokemonInPlay target = findPokemon(state(player), targetPokemonId);
        target.attachedEnergy.add(Objects.requireNonNull(energy));
        attachmentEntries.put(energy.instanceId(),++nextAttachmentEntrySequence);
        energyAttachmentTurns.put(energy.instanceId(),turnNumber);
        energyAttachments++;
    }
    /** Attaches Energy as a resolved card effect; it never consumes the normal hand-attachment counter. */
    public void attachEnergyByEffect(UUID owner, UUID targetPokemonId, CardInstance energy) {
        findPokemon(state(owner), targetPokemonId).attachedEnergy.add(Objects.requireNonNull(energy));
        attachmentEntries.put(energy.instanceId(),++nextAttachmentEntrySequence);
        energyAttachmentTurns.put(energy.instanceId(),turnNumber);
    }
    /** Moving an attached card preserves its original attachment turn; reattaching starts a new one. */
    public boolean energyAttachedThisTurn(UUID owner,UUID host,UUID card) {
        return findPokemon(state(owner),host).attachedEnergy.stream().anyMatch(c->c.instanceId().equals(card))
            &&Objects.equals(energyAttachmentTurns.get(card),turnNumber);
    }
    /** Removes selected attached Energy cards; the match resolver owns their destination zone. */
    public List<CardInstance> detachEnergy(UUID owner, UUID targetPokemonId, List<UUID> energyIds) {
        if (energyIds == null || energyIds.isEmpty()) throw new IllegalArgumentException("至少选择一张附着能量");
        if (new java.util.LinkedHashSet<>(energyIds).size() != energyIds.size()) throw new IllegalArgumentException("同一张能量不能重复选择");
        PokemonInPlay target = findPokemon(state(owner), targetPokemonId);
        List<CardInstance> removed = new ArrayList<>();
        for (UUID id : energyIds) removed.add(target.attachedEnergy.stream().filter(card -> card.instanceId().equals(id)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("指定卡牌不是该宝可梦附着的能量")));
        target.attachedEnergy.removeAll(removed);
        return List.copyOf(removed);
    }
    /** Moves selected attached Energy between public in-play Pokémon without using the manual attachment counter. */
    public List<CardInstance> moveAttachedEnergyByEffect(UUID sourceOwner, UUID sourcePokemonId,
                                                          UUID targetOwner, UUID targetPokemonId, List<UUID> energyIds) {
        // Validate the destination before removing any source energy, preserving atomic rejection.
        findPokemon(state(targetOwner), targetPokemonId);
        List<CardInstance> moved = detachEnergy(sourceOwner, sourcePokemonId, energyIds);
        findPokemon(state(targetOwner), targetPokemonId).attachedEnergy.addAll(moved);
        return moved;
    }

    /** Replaces the top card while retaining damage and attached cards, then cures all Special Conditions. */
    public void evolve(UUID player, UUID targetPokemonId, CardInstance evolution) {
        evolve(player, targetPokemonId, evolution, EvolutionPermission.STRICT);
    }
    public boolean evolvedThisTurn(UUID owner,UUID pokemonId) {return findPokemon(state(owner),pokemonId).lastEvolvedTurn==turnNumber;}
    /** Printed evolution-source conditions refer to the actual previous Pokémon, including stage-skip effects. */
    public boolean evolvedThisTurnFrom(UUID owner,UUID pokemonId,String sourceName) {
        PokemonInPlay target=findPokemon(state(owner),pokemonId);
        return target.lastEvolvedTurn==turnNumber && target.topCard().instanceId().equals(target.lastEvolutionCardId)
            && sourceName.equalsIgnoreCase(target.lastEvolutionFromName);
    }
    /** Only a server card-effect resolver may pass a non-STRICT permission. */
    public void evolve(UUID player, UUID targetPokemonId, CardInstance evolution, EvolutionPermission permission) {
        evolve(player,targetPokemonId,evolution,permission,false);
    }
    public void evolve(UUID player, UUID targetPokemonId, CardInstance evolution, EvolutionPermission permission,boolean retainConfusion) {
        evolve(player,targetPokemonId,evolution,permission,retainConfusion?Set.of(SpecialCondition.CONFUSED):Set.of());
    }
    public void evolve(UUID player, UUID targetPokemonId, CardInstance evolution, EvolutionPermission permission,Set<SpecialCondition> retainedConditions) {
        requireTurn(player);
        PokemonInPlay target = findPokemon(state(player), targetPokemonId);
        PokemonProfile evolved = requireProfile(evolution);
        if (!evolved.isEvolution()) throw new IllegalStateException("该宝可梦没有进化来源，不能作为进化牌使用");
        Objects.requireNonNull(permission, "进化时点许可");
        if (!canEvolve(player, targetPokemonId, evolution.definitionId(), permission))
            throw new IllegalStateException("该进化牌不符合当前宝可梦的进化时点、来源或已审核的阶段许可");
        target.lastEvolutionFromName = target.profile.pokemonName();
        target.lastEvolutionCardId = evolution.instanceId();
        target.stack.add(evolution);
        target.profile = evolved;
        target.lastEvolvedTurn = turnNumber;
        clearAttackDamageHistory(target);
        var retained=EnumSet.noneOf(SpecialCondition.class);retained.addAll(target.conditions);retained.retainAll(retainedConditions);
        target.conditions.clear();target.conditions.addAll(retained);clearProtection(target);
    }
    /**
     * Removes only the highest Evolution card. The Rulebook's devolution rule
     * clears Special Conditions and other effects; damage counters and attached
     * cards remain with the resulting Pokémon.
     */
    public DevolutionResult devolveByEffect(UUID owner, UUID pokemonId) {
        return devolveByEffect(owner,pokemonId,false);
    }
    public DevolutionResult devolveByEffect(UUID owner, UUID pokemonId,boolean retainConfusion) {
        return devolveByEffect(owner,pokemonId,retainConfusion?Set.of(SpecialCondition.CONFUSED):Set.of());
    }
    public DevolutionResult devolveByEffect(UUID owner, UUID pokemonId,Set<SpecialCondition> retainedConditions) {
        PokemonInPlay target = findPokemon(state(owner), pokemonId);
        if (target.stack.size() < 2) throw new IllegalStateException("该宝可梦没有可退化的进化牌");
        CardInstance removed = target.stack.removeLast();
        // The advanced rulebook treats a devolved Pokemon as newly played for evolution timing.
        target.lastDevolvedTurn = turnNumber;
        target.profile = requireProfile(target.stack.getLast());
        clearAttackDamageHistory(target);
        var retained=EnumSet.noneOf(SpecialCondition.class);retained.addAll(target.conditions);retained.retainAll(retainedConditions);
        target.conditions.clear();target.conditions.addAll(retained);clearProtection(target);
        return new DevolutionResult(removed, target.damage >= hitPoints(target));
    }

    /** Pays the server-computed retreat cost (including continuous effects), then switches and cures conditions. */
    public RetreatResult retreat(UUID player, UUID replacementPokemonId, List<UUID> paidEnergyIds) {
        return retreat(player, replacementPokemonId, paidEnergyIds,
            paidEnergyIds == null ? -1 : paidEnergyIds.size());
    }
    /**
     * Completes a retreat after the match resolver has verified the selected
     * Energy profiles. The battlefield still verifies all public attachment
     * identities and the server-computed minimum; it deliberately has no card
     * definition catalogue, so it cannot infer Special Energy unit counts.
     */
    public RetreatResult retreat(UUID player, UUID replacementPokemonId, List<UUID> paidEnergyIds, int paidEnergyUnits) {
        List<CardInstance> paid = validateRetreat(player,replacementPokemonId,paidEnergyIds,paidEnergyUnits);
        PlayerField field=state(player);
        PokemonInPlay active=field.active(),replacement=findBench(field,replacementPokemonId);
        active.attachedEnergy.removeAll(paid);
        active.conditions.clear(); clearProtection(active);
        field.bench.remove(replacement);
        field.bench.add(active);
        field.active = replacement;
        active.retreatedTurn = turnNumber;
        replacement.becameActiveTurn = turnNumber;
        retreats++;
        return new RetreatResult(active.pokemonId, replacement.pokemonId, paid);
    }

    /** Read-only public-state validation; the server validates Energy provision before rolling any retreat coin. */
    public List<CardInstance> validateRetreat(UUID player, UUID replacementPokemonId, List<UUID> paidEnergyIds, int paidEnergyUnits) {
        requireTurn(player);
        if (retreats >= retreatLimit) throw new IllegalStateException("本规则包的每回合撤退次数已用完");
        PlayerField field = state(player);
        PokemonInPlay active = field.active();
        if (active.profile.printedRules().preventsRetreat())
            throw new IllegalStateException("该宝可梦的卡面规则禁止撤退");
        if ((active.conditions.contains(SpecialCondition.ASLEEP) || active.conditions.contains(SpecialCondition.PARALYZED))
            &&!retreatWithStatus.test(player,active.pokemonId))
            throw new IllegalStateException("睡眠或麻痹的宝可梦不能撤退");
        if (active.continuousEffects.stream().anyMatch(ContinuousEffect::preventsRetreat)
            || active.conditions.contains(SpecialCondition.POISONED)
                && poisonedRetreatLocks.stream().anyMatch(lock -> lock.affectedPlayer().equals(player)))
            throw new IllegalStateException("该宝可梦正受效果影响，不能撤退");
        findBench(field, replacementPokemonId);
        if (paidEnergyIds == null || paidEnergyUnits < activeRetreatCost(player))
            throw new IllegalStateException("撤退支付的能量不足以满足撤退费用");
        List<CardInstance> paid = new ArrayList<>();
        for (UUID energyId : paidEnergyIds) {
            CardInstance energy = active.attachedEnergy.stream().filter(card -> card.instanceId().equals(energyId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("撤退费用包含未附着在战斗宝可梦上的能量"));
            if (paid.contains(energy)) throw new IllegalStateException("同一张能量不能重复支付撤退费用");
            paid.add(energy);
        }
        return List.copyOf(paid);
    }

    /** Server-resolved tails consumes the manual retreat allowance without moving cards or clearing conditions. */
    public void consumeFailedRetreatAttempt(UUID player, UUID replacementPokemonId, List<UUID> paidEnergyIds, int paidEnergyUnits) {
        validateRetreat(player,replacementPokemonId,paidEnergyIds,paidEnergyUnits);
        retreats++;
    }

    /** Only a server-resolved card effect should call this; conditions can affect Active Pokémon only. */
    /** @return true if the active Pokémon's condition set actually changed. */
    public boolean applySpecialCondition(UUID affectedPlayer, SpecialCondition condition) {
        return applySpecialCondition(affectedPlayer, condition, defaultCheckupCounters(condition));
    }
    /**
     * “… Poisoned. Put N damage counters instead of 1 on that Pokémon between turns.” Every application sets this
     * condition's strength, so a new Poisoned/Burned replaces the old one together with its damage-counter count.
     */
    public boolean applySpecialCondition(UUID affectedPlayer, SpecialCondition condition, int checkupCounters) {
        PokemonInPlay active = state(affectedPlayer).active();
        Objects.requireNonNull(condition, "异常状态");
        if (checkupCounters != defaultCheckupCounters(condition)
            && (condition != SpecialCondition.POISONED && condition != SpecialCondition.BURNED && condition != SpecialCondition.CONFUSED || checkupCounters < 1 || checkupCounters > 60))
            throw new IllegalArgumentException("只有中毒、灼伤或混乱可以指定伤害指示物数量（1–60）");
        if (active.profile.printedRules().preventsSpecialConditions()) return false;
        boolean changed = condition.positional() && active.conditions.removeIf(SpecialCondition::positional);
        changed |= active.conditions.add(condition);
        if (condition == SpecialCondition.PARALYZED) active.paralyzedThroughOwnerTurn = state(affectedPlayer).turnsStarted + 1;
        if (condition == SpecialCondition.POISONED) { changed |= active.poisonCounters != checkupCounters; active.poisonCounters = checkupCounters; }
        if (condition == SpecialCondition.BURNED) { changed |= active.burnCounters != checkupCounters; active.burnCounters = checkupCounters; }
        if (condition == SpecialCondition.CONFUSED) { changed |= active.confusionCounters != checkupCounters; active.confusionCounters = checkupCounters; }
        return changed;
    }
    /** Rulebook defaults: Poisoned 1 and Burned 2 during Checkup; Confusion deals 3 on a failed attack check. */
    public static int defaultCheckupCounters(SpecialCondition condition) {
        return condition == SpecialCondition.BURNED ? 2
            : condition == SpecialCondition.POISONED ? 1
            : condition == SpecialCondition.CONFUSED ? 3 : 0;
    }
    /** Current counter value for Poisoned, Burned, or the failed-attack Confusion penalty. */
    public int checkupCounters(UUID player, SpecialCondition condition) {
        PokemonInPlay active = state(player).active();
        return condition == SpecialCondition.POISONED ? active.poisonCounters
            : condition == SpecialCondition.BURNED ? active.burnCounters
            : condition == SpecialCondition.CONFUSED ? active.confusionCounters : 0;
    }
    public int confusionCounters(UUID player) {return state(player).active().confusionCounters;}
    /** Removes only the declared conditions; damage and all non-condition attachments remain untouched. */
    public List<SpecialCondition> clearSpecialConditions(UUID owner, UUID pokemonId, java.util.Set<SpecialCondition> conditions) {
        if (conditions == null || conditions.isEmpty()) throw new IllegalArgumentException("至少指定一种要移除的异常状态");
        PokemonInPlay pokemon = findPokemon(state(owner), pokemonId);
        List<SpecialCondition> removed = pokemon.conditions.stream().filter(conditions::contains).toList();
        if (removed.isEmpty()) throw new IllegalStateException("指定宝可梦没有可移除的异常状态");
        pokemon.conditions.removeAll(removed);
        if (conditions.contains(SpecialCondition.PARALYZED)) pokemon.paralyzedThroughOwnerTurn = 0;
        return List.copyOf(removed);
    }

    /** Adds resolved damage to the Active Pokémon. The match resolver owns the resulting KO procedure. */
    public DamageResult damageActive(UUID affectedPlayer, int damage) {
        return damagePokemon(affectedPlayer, state(affectedPlayer).active().pokemonId, damage);
    }
    /** Adds damage to a public in-play Pokémon identity; clients cannot name a hidden card. */
    public DamageResult damagePokemon(UUID affectedPlayer, UUID pokemonId, int damage) {
        if (damage < 0 || damage % 10 != 0) throw new IllegalArgumentException("伤害必须是非负的 10 的倍数");
        PokemonInPlay target = findPokemon(state(affectedPlayer), pokemonId);
        target.damage += damage;
        return new DamageResult(damage, target.damage, target.damage >= hitPoints(target));
    }
    /**
     * Effect damage-counter placement is deliberately separate from attack
     * damage: callers must not apply Weakness, Resistance, attack protection
     * or attack-only reduction to this operation.
     */
    public boolean surviveAttackDamage(UUID owner,UUID pokemonId) {
        PokemonInPlay target=findPokemon(state(owner),pokemonId);
        int hp=hitPoints(target);
        if(target.damage<hp)return false;
        target.damage=hp-10;
        return true;
    }
    public DamageResult placeDamageCounters(UUID affectedPlayer, UUID pokemonId, int counters) {
        if (counters < 1 || counters > 60) throw new IllegalArgumentException("伤害指示物数量无效");
        return damagePokemon(affectedPlayer, pokemonId, counters * 10);
    }
    /**
     * Marks an existing public in-play Pokémon as Knocked Out without treating
     * the event as attack damage or damage-counter placement. The match resolver
     * still owns the ordinary Prize, replacement and loss procedure.
     */
    public DamageResult knockOutPokemonByEffect(UUID affectedPlayer, UUID pokemonId) {
        PokemonInPlay target = findPokemon(state(affectedPlayer), pokemonId);
        target.damage = Math.max(target.damage, hitPoints(target));
        return new DamageResult(0, target.damage, true);
    }

    /** Removes a confirmed Knocked Out Active Pokémon and returns every card that must enter its owner's discard pile. */
    public List<CardInstance> takeKnockedOutActive(UUID owner) {
        return takeKnockedOut(owner, state(owner).active().pokemonId).discardedCards();
    }
    /** Removes a confirmed Knocked Out Active or Benched Pokémon. */
    public LeavePlayResult takeKnockedOut(UUID owner, UUID pokemonId) {
        PlayerField field = state(owner);
        PokemonInPlay target;
        boolean wasActive;
        if (field.active != null && field.active.pokemonId.equals(pokemonId)) { target = field.active; wasActive = true; }
        else { target = findBench(field, pokemonId); wasActive = false; }
        if (target.damage < hitPoints(target)) throw new IllegalStateException("该宝可梦尚未被击倒");
        if (wasActive) field.active = null; else field.bench.remove(target);
        List<CardInstance> discarded = new ArrayList<>(target.stack);
        discarded.addAll(target.attachedEnergy);
        discarded.addAll(target.attachedTools);
        return new LeavePlayResult(target.pokemonId, wasActive, List.copyOf(discarded));
    }

    /**
     * Removes an in-play Pokémon as a card effect cost. This intentionally has no
     * Knock Out side effect and cannot award Prizes; ServerMatch handles any
     * required replacement or loss after the effect has resolved.
     */
    public LeavePlayResult discardPokemon(UUID owner, UUID pokemonId) {
        return removePokemonByEffect(owner, pokemonId);
    }

    /**
     * Removes a public in-play Pokémon without treating it as a Knock Out.
     * The caller owns the destination and the replacement/loss procedure.
     */
    public LeavePlayResult removePokemonByEffect(UUID owner, UUID pokemonId) {
        PlayerField field = state(owner);
        PokemonInPlay pokemon;
        boolean active;
        if (field.active != null && field.active.pokemonId.equals(pokemonId)) {
            pokemon = field.active;
            field.active = null;
            active = true;
        } else {
            pokemon = findBench(field, pokemonId);
            field.bench.remove(pokemon);
            active = false;
        }
        List<CardInstance> discarded = new ArrayList<>(pokemon.stack);
        discarded.addAll(pokemon.attachedEnergy);
        discarded.addAll(pokemon.attachedTools);
        return new LeavePlayResult(pokemon.pokemonId, active, List.copyOf(discarded));
    }

    /** Promotes one Benched Pokémon after a Knock Out. It intentionally does not consume a retreat action. */
    public void promote(UUID owner, UUID benchPokemonId) {
        PlayerField field = state(owner);
        if (field.active != null) throw new IllegalStateException("战斗位尚未空出，不能选择替补");
        PokemonInPlay replacement = findBench(field, benchPokemonId);
        field.bench.remove(replacement);
        field.active = replacement;
        replacement.becameActiveTurn = turnNumber;
    }

    /** Applies the Rulebook's Pokémon Checkup order: Poisoned, Burned, Asleep, then Paralyzed. */
    public CheckupResult pokemonCheckup() {
        return pokemonCheckup((owner,status)->false);
    }
    public CheckupResult pokemonCheckup(java.util.function.BiPredicate<UUID,SpecialCondition> preventsRecovery) {
        return pokemonCheckup(preventsRecovery,owner->0);
    }
    public CheckupResult pokemonCheckup(java.util.function.BiPredicate<UUID,SpecialCondition> preventsRecovery,
                                        java.util.function.ToIntFunction<UUID> poisonBonusCounters) {
        return pokemonCheckup(preventsRecovery,poisonBonusCounters,owner->1);
    }
    public CheckupResult pokemonCheckup(java.util.function.BiPredicate<UUID,SpecialCondition> preventsRecovery,
                                        java.util.function.ToIntFunction<UUID> poisonBonusCounters,
                                        java.util.function.ToIntFunction<UUID> sleepRecoveryCoins) {
        List<CheckupEvent> events = new ArrayList<>();
        int randomBefore = random.transcript().size();
        for (SpecialCondition step : List.of(SpecialCondition.POISONED, SpecialCondition.BURNED, SpecialCondition.ASLEEP, SpecialCondition.PARALYZED))
        for (PlayerField field : players.values()) {
            PokemonInPlay active = field.active;
            if (active == null) continue;
            if (step == SpecialCondition.POISONED && active.conditions.contains(SpecialCondition.POISONED)) {
                int added = 10 * Math.addExact(active.poisonCounters,Math.max(0,poisonBonusCounters.applyAsInt(field.player)));
                active.damage += added;
                events.add(new CheckupEvent(field.player, SpecialCondition.POISONED, added, null));
            }
            if (step == SpecialCondition.BURNED && active.conditions.contains(SpecialCondition.BURNED)) {
                int added = 10 * active.burnCounters;
                active.damage += added;
                CoinSide coin = flip("checkup-burn:" + field.player);
                if (coin == CoinSide.HEADS && !preventsRecovery.test(field.player,SpecialCondition.BURNED)) active.conditions.remove(SpecialCondition.BURNED);
                events.add(new CheckupEvent(field.player, SpecialCondition.BURNED, added, coin));
            }
            if (step == SpecialCondition.ASLEEP && active.conditions.contains(SpecialCondition.ASLEEP)) {
                int flips=Math.max(1,sleepRecoveryCoins.applyAsInt(field.player));
                CoinSide coin=CoinSide.HEADS;
                for(int index=0;index<flips;index++)if(flip("checkup-asleep:" + field.player + ":" + index)==CoinSide.TAILS)coin=CoinSide.TAILS;
                if (coin == CoinSide.HEADS) active.conditions.remove(SpecialCondition.ASLEEP);
                events.add(new CheckupEvent(field.player, SpecialCondition.ASLEEP, 0, coin));
            }
            if (step == SpecialCondition.PARALYZED && active.conditions.contains(SpecialCondition.PARALYZED) && field.player.equals(currentPlayer)
                && field.turnsStarted >= active.paralyzedThroughOwnerTurn) {
                active.conditions.remove(SpecialCondition.PARALYZED);
                events.add(new CheckupEvent(field.player, SpecialCondition.PARALYZED, 0, null));
            }
        }
        List<PokemonView> knockedOut = players.values().stream().map(field -> field.active).filter(Objects::nonNull)
            .filter(pokemon -> pokemon.damage >= hitPoints(pokemon)).map(this::view).toList();
        List<RandomAuditEvent> all = random.transcript();
        return new CheckupResult(List.copyOf(events), knockedOut, List.copyOf(all.subList(randomBefore, all.size())));
    }

    private CoinSide flip(String operation) { return random.nextInt(operation, 2) == 0 ? CoinSide.HEADS : CoinSide.TAILS; }
    private PlayerField state(UUID player) {
        PlayerField value = players.get(player);
        if (value == null) throw new IllegalArgumentException("玩家不属于此战场");
        return value;
    }
    private PokemonProfile requireProfile(CardInstance card) {
        PokemonProfile profile = profiles.get(Objects.requireNonNull(card).definitionId());
        if (profile == null) throw new IllegalStateException("此卡没有已支持的宝可梦战斗数据：" + card.definitionId());
        return profile;
    }
    private PokemonInPlay findPokemon(PlayerField field, UUID pokemonId) {
        if (field.active != null && (field.active.pokemonId.equals(pokemonId)||field.active.stack.getFirst().instanceId().equals(pokemonId))) return field.active;
        return findBench(field, pokemonId);
    }
    private PokemonInPlay findBench(PlayerField field, UUID pokemonId) {
        return field.bench.stream().filter(pokemon -> pokemon.pokemonId.equals(pokemonId)||pokemon.stack.getFirst().instanceId().equals(pokemonId)).findFirst()
            .orElseThrow(() -> new IllegalStateException("目标不在你的备战区或战斗位"));
    }
    private PokemonView view(PokemonInPlay pokemon) {
        return view(pokemon,true);
    }
    private int hitPoints(PokemonInPlay pokemon) {
        UUID controller=players.entrySet().stream().filter(e->e.getValue().active==pokemon||e.getValue().bench.contains(pokemon))
            .map(Map.Entry::getKey).findFirst().orElseThrow(()->new IllegalStateException("HP 查询目标已离场"));
        return Math.max(0,Math.addExact(pokemon.profile.hitPoints(),hitPointsChange.applyAsInt(controller,pokemon.pokemonId)));
    }
    private PokemonView view(PokemonInPlay pokemon,boolean liveHitPoints) {
        if (pokemon == null) throw new IllegalStateException("尚未选择战斗宝可梦");
        return new PokemonView(pokemon.pokemonId, pokemon.topCard().definitionId(), liveHitPoints?hitPoints(pokemon):pokemon.profile.hitPoints(), pokemon.damage,
            pokemon.attachedEnergy.stream().map(CardInstance::instanceId).toList(), List.copyOf(pokemon.conditions),
            pokemon.stack.stream().map(CardInstance::definitionId).toList(),
            pokemon.attachedEnergy.stream().map(CardInstance::definitionId).toList(), pokemon.attachedTools,
            java.util.stream.Stream.concat(pokemon.continuousEffects.stream().map(this::effectStatusLabel),
                java.util.stream.Stream.concat(pokemon.delayedMarkers.values().stream(),pokemon.weaknessOverride==null?java.util.stream.Stream.empty():java.util.stream.Stream.of(
                    pokemon.weaknessOverride.type()==null?"无弱点，至效果使用者下回合开始":energyTypeZh(pokemon.weaknessOverride.type())+"弱点×"+pokemon.weaknessOverride.multiplier()+(pokemon.weaknessOverride.expiresAfterOwnerTurn()==Integer.MAX_VALUE?"，至离开战斗位或进化":"，至效果使用者下回合结束")))).distinct().collect(java.util.stream.Collectors.joining("；")), pokemon.profile.pokemonType());
    }
    private String effectStatusLabel(ContinuousEffect effect) {
        String expiry=switch(effect.expiry()) {
            case OWNER_NEXT_TURN_START -> "效果所属方下个回合开始时到期";
            case OPPONENT_TURN_END -> "效果所属方的对手回合结束时到期";
            case LEAVES_PLAY -> "离场时到期";
            case OWNER_TURN_END -> {
                int remaining=effect.expiresAfterOwnerTurn()-state(effect.owner()).turnsStarted;
                yield remaining<=0?"效果所属方本回合结束时到期":"效果所属方再经过 "+remaining+" 次回合结束时到期";
            }
        };
        return effect.publicLabelZh()+"（"+expiry+"）";
    }
    private static String energyTypeZh(EnergyType type) {
        return switch(type) {
            case GRASS -> "草"; case FIRE -> "火"; case WATER -> "水"; case LIGHTNING -> "雷"; case PSYCHIC -> "超";
            case FIGHTING -> "斗"; case DARKNESS -> "恶"; case METAL -> "钢"; case DRAGON -> "龙"; case COLORLESS -> "无色"; case FAIRY -> "妖精";
        };
    }
    private void requireTurn(UUID player) {
        if (!player.equals(currentPlayer)) throw new IllegalStateException("尚未轮到你操作战场");
    }
    public void addDelayedMarker(UUID owner,UUID pokemonId,UUID token,String label) {
        findPokemon(state(owner),pokemonId).delayedMarkers.put(token,label);
    }
    public boolean hasDelayedMarker(UUID owner,UUID pokemonId,UUID token) {return findPokemon(state(owner),pokemonId).delayedMarkers.containsKey(token);}
    public void removeDelayedMarker(UUID owner,UUID pokemonId,UUID token) {findPokemon(state(owner),pokemonId).delayedMarkers.remove(token);}
    private static void pruneCardBindings(PokemonInPlay pokemon) {
        pokemon.cardBoundEffects.keySet().removeIf(bound->pokemon.continuousEffects.stream().noneMatch(e->e==bound));
    }
    private static void clearProtection(PokemonInPlay pokemon) {
        // Card-specific protection survives a position change, but the protected physical card must still be the top.
        pokemon.continuousEffects.removeIf(e->!pokemon.topCard().instanceId().equals(pokemon.cardBoundEffects.get(e)));
        pruneCardBindings(pokemon);pokemon.delayedMarkers.clear();pokemon.weaknessOverride=null;
    }
    private static void expireTurnStartEffects(PokemonInPlay pokemon, UUID player) {
        if(pokemon.weaknessOverride!=null&&!pokemon.weaknessOverride.untilOwnerTurnEnd()
            &&pokemon.weaknessOverride.owner().equals(player))pokemon.weaknessOverride=null;
        pokemon.continuousEffects.removeIf(effect -> effect.owner().equals(player)
            && effect.expiry() == ContinuousEffect.Expiry.OWNER_NEXT_TURN_START);
        pruneCardBindings(pokemon);
    }
    private static void expireTurnEndEffects(PokemonInPlay pokemon, UUID player, int ownerTurnsStarted) {
        if(pokemon.weaknessOverride!=null&&pokemon.weaknessOverride.untilOwnerTurnEnd()&&pokemon.weaknessOverride.owner().equals(player)
            &&pokemon.weaknessOverride.expiresAfterOwnerTurn()<=ownerTurnsStarted) pokemon.weaknessOverride=null;
        pokemon.continuousEffects.removeIf(effect -> effect.expiry() == ContinuousEffect.Expiry.OPPONENT_TURN_END && !effect.owner().equals(player)
            || effect.owner().equals(player) && effect.expiry() == ContinuousEffect.Expiry.OWNER_TURN_END
            && effect.expiresAfterOwnerTurn() <= ownerTurnsStarted);
        pruneCardBindings(pokemon);
    }

    private static final class PlayerField {
        final UUID player;
        PokemonInPlay active;
        int turnsStarted;
        final List<PokemonInPlay> bench = new ArrayList<>();
        PlayerField(UUID player) { this.player = player; }
        PokemonInPlay active() { if (active == null) throw new IllegalStateException("尚未选择战斗宝可梦"); return active; }
        List<PokemonInPlay> bench() { return bench; }
    }
    private record PoisonRetreatLock(UUID effectOwner, UUID affectedPlayer) { }
    private record TimedWeakness(UUID owner,int expiresAfterOwnerTurn,EnergyType type,boolean untilOwnerTurnEnd,int multiplier,dev.hua.ptcglive.engine.effect.EffectOrigin.Kind sourceKind) { }
    private static final class PokemonInPlay {
        final UUID pokemonId;
        final List<CardInstance> stack = new ArrayList<>();
        final List<CardInstance> attachedEnergy = new ArrayList<>();
        final List<CardInstance> attachedTools = new ArrayList<>();
        final java.util.Set<String> usedAbilityIds = new java.util.LinkedHashSet<>();
        final EnumSet<SpecialCondition> conditions = EnumSet.noneOf(SpecialCondition.class);
        final int enteredTurn;
        final long entrySequence;
        PokemonProfile profile;
        TimedWeakness weaknessOverride;
        int damage;
        int paralyzedThroughOwnerTurn = -1;
        /** Damage counters this Pokémon's Poisoned / Burned puts during Pokémon Checkup; set on every application. */
        int poisonCounters = 1, burnCounters = 2, confusionCounters = 3;
        int lastEvolvedTurn = -1;
        String lastEvolutionFromName;
        UUID lastEvolutionCardId;
        int lastDevolvedTurn = -1;
        /** Turn number in which this Pokémon last moved from the Bench to the Active Spot (switch, retreat or promotion). */
        int becameActiveTurn = -1;
        int retreatedTurn = -1;
        /** Turn number in which a heal last removed damage from this Pokémon (moving damage counters is not healing). */
        int healedTurn = -1;
        int attackDamageTurn = -1, attackDamageReceived;
        boolean starCipherAbility;
        final List<ContinuousEffect> continuousEffects = new ArrayList<>();
        final Map<ContinuousEffect,UUID> cardBoundEffects=new java.util.IdentityHashMap<>();
        final Map<UUID,String> delayedMarkers = new java.util.LinkedHashMap<>();
        PokemonInPlay(PokemonProfile profile, CardInstance base, int enteredTurn, long entrySequence,UUID logicalId) {
            this.profile = profile;
            this.pokemonId = logicalId;
            this.stack.add(base);
            this.enteredTurn = enteredTurn;
            this.entrySequence = entrySequence;
        }
        CardInstance topCard() { return stack.getLast(); }
    }
}
