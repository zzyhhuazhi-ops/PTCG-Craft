package dev.hua.ptcglive.engine.effect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Collections;
import java.util.function.Consumer;

/**
 * A short-lived, server-issued selection contract for a single Effect step.
 *
 * <p>The network is never asked to describe legal targets.  The authoritative
 * engine creates the candidate list, gives the entitled seat a prompt, and
 * accepts only a non-duplicated subset of that exact list at the matching
 * session revision.  Hidden-zone card identities can be included only in an
 * {@link Visibility#ENTITLED_PLAYER_ONLY} transaction and are never returned
 * to a different player or spectator.</p>
 */
public final class EffectSelectionTransaction {
    public enum Visibility { PUBLIC_FIELD, ENTITLED_PLAYER_ONLY }

    public record Candidate(UUID id, EffectTargetResolution.TargetRef reference) {
        public Candidate {
            Objects.requireNonNull(id, "效果选择候选编号");
            Objects.requireNonNull(reference, "效果选择候选引用");
        }
    }

    /** The DTO suitable for exactly the entitled connection; it contains no resolver callback. */
    public record Prompt(UUID transactionId, long revision, int minimum, int maximum,
                         Visibility visibility, List<UUID> candidateIds) {
        public Prompt {
            Objects.requireNonNull(transactionId, "效果选择事务编号");
            Objects.requireNonNull(visibility, "效果选择可见性");
            candidateIds = List.copyOf(Objects.requireNonNull(candidateIds, "效果选择候选编号"));
        }
    }

    private final UUID transactionId;
    private final UUID entitledPlayer;
    private final long revision;
    private final int minimum;
    private final int maximum;
    private final Visibility visibility;
    private final EffectTarget selectionTarget;
    private final Map<UUID, Candidate> candidates;
    private boolean consumed;
    private boolean committing;
    private boolean invalidated;

    private EffectSelectionTransaction(UUID transactionId, UUID entitledPlayer, long revision, int minimum, int maximum,
                                       Visibility visibility, EffectTarget selectionTarget, List<Candidate> candidates) {
        this(transactionId,entitledPlayer,revision,minimum,maximum,visibility,selectionTarget,candidates,60);
    }
    private EffectSelectionTransaction(UUID transactionId, UUID entitledPlayer, long revision, int minimum, int maximum,
                                       Visibility visibility, EffectTarget selectionTarget, List<Candidate> candidates, int capacity) {
        this.transactionId = Objects.requireNonNull(transactionId, "效果选择事务编号");
        this.entitledPlayer = Objects.requireNonNull(entitledPlayer, "效果选择操作者");
        if (revision < 0) throw new IllegalArgumentException("效果选择版本不能为负数");
        if (minimum < 0 || maximum < minimum || maximum > capacity)
            throw new IllegalArgumentException("效果选择数量范围无效");
        this.revision = revision;
        this.minimum = minimum;
        this.maximum = maximum;
        this.visibility = Objects.requireNonNull(visibility, "效果选择可见性");
        this.selectionTarget = selectionTarget;
        var indexed = new LinkedHashMap<UUID, Candidate>();
        for (Candidate candidate : List.copyOf(Objects.requireNonNull(candidates, "效果选择候选"))) {
            if (indexed.putIfAbsent(candidate.id(), candidate) != null)
                throw new IllegalArgumentException("效果选择候选不能重复");
        }
        if (indexed.size() < minimum)
            throw new IllegalArgumentException("效果选择候选少于规则要求的最小数量");
        // Candidate order is part of the prompt contract; Map.copyOf does not preserve it.
        this.candidates = Collections.unmodifiableMap(indexed);
    }

    public static EffectSelectionTransaction open(UUID entitledPlayer, long revision, int minimum, int maximum,
                                                  Visibility visibility, List<Candidate> candidates) {
        return new EffectSelectionTransaction(UUID.randomUUID(), entitledPlayer, revision, minimum, maximum, visibility, null, candidates);
    }
    /** Creates a public target selection bound to the exact Effect target declaration. */
    public static EffectSelectionTransaction openForTarget(UUID entitledPlayer, long revision, int minimum, int maximum,
                                                           EffectTarget selectionTarget, List<Candidate> candidates) {
        if (selectionTarget != EffectTarget.SELECT_OWN_POKEMON && selectionTarget != EffectTarget.SELECT_OPPONENT_POKEMON
            && selectionTarget != EffectTarget.SELECT_ANY_POKEMON)
            throw new IllegalArgumentException("只有 SELECT_* 宝可梦目标可以绑定公开选择事务");
        return new EffectSelectionTransaction(UUID.randomUUID(), entitledPlayer, revision, minimum, maximum,
            Visibility.PUBLIC_FIELD, selectionTarget, candidates);
    }

    /** Only this exact public-field contract may select across the physical cards of both decks. */
    public static EffectSelectionTransaction openForOtherOwnMetalEnergy(UUID entitledPlayer, long revision,
                                                                       EffectDefinition effect, List<Candidate> candidates) {
        if (!AttachedEnergyEffect.otherOwnMetalToSelf(effect) || candidates.isEmpty() || candidates.size()>120
            || candidates.stream().anyMatch(c -> !(c.reference() instanceof EffectTargetResolution.PokemonTarget target)
                || !target.owner().equals(entitledPlayer)))
            throw new IllegalArgumentException("其他己方属性能量选择需要精确合同及1至120张公开附着候选");
        return new EffectSelectionTransaction(UUID.randomUUID(),entitledPlayer,revision,1,candidates.size(),
            Visibility.PUBLIC_FIELD,null,candidates,120);
    }

    /** Final coin result selects public Energy on a single opposing host, up to both conserved decks. */
    public static EffectSelectionTransaction openForCoinEnergy(UUID entitledPlayer,long revision,int minimum,int maximum,
                                                               EffectDefinition effect,List<Candidate> candidates) {
        if(!AttachedEnergyEffect.coinHeads(effect)||candidates.size()>120
            ||candidates.stream().anyMatch(c->!(c.reference() instanceof EffectTargetResolution.PokemonTarget t)
                ||t.owner().equals(entitledPlayer))
            ||candidates.stream().map(Candidate::reference).distinct().count()>1)
            throw new IllegalArgumentException("硬币选能必须来自同一对方宝可梦，且不能超过双方实体卡总量");
        AttachedEnergyEffect.from(effect);
        return new EffectSelectionTransaction(UUID.randomUUID(),entitledPlayer,revision,minimum,maximum,
            Visibility.PUBLIC_FIELD,null,candidates,120);
    }

    public UUID transactionId() { return transactionId; }
    public UUID entitledPlayer() { return entitledPlayer; }
    public long revision() { return revision; }
    public synchronized boolean consumed() { return consumed; }
    public synchronized void invalidate() { invalidated = true; }
    /** Internal rule resolvers use this to prevent a transaction from being replayed for another target declaration. */
    public synchronized void requireTarget(EffectTarget target) {
        if (selectionTarget == null || selectionTarget != target)
            throw new IllegalArgumentException("效果选择事务与卡牌目标声明不匹配");
    }

    /** Only the entitled seat can obtain a candidate list, including an owner-private zone. */
    public synchronized Prompt promptFor(UUID recipient) {
        if (!entitledPlayer.equals(recipient)) throw new IllegalArgumentException("你无权查看此效果选择");
        requireOpen();
        return new Prompt(transactionId, revision, minimum, maximum, visibility, new ArrayList<>(candidates.keySet()));
    }

    /**
     * Validates and consumes the transaction.  Callers must additionally
     * revalidate the returned public references against current match state
     * immediately before applying the effect.
     */
    public synchronized List<Candidate> submit(UUID recipient, UUID suppliedTransactionId, long suppliedRevision,
                                                List<UUID> selectedIds) {
        return commit(recipient, suppliedTransactionId, suppliedRevision, selectedIds, selected -> { });
    }

    /** The engine must validate live state and apply an atomic primitive before consuming the choice. */
    public synchronized List<Candidate> commit(UUID recipient, UUID suppliedTransactionId, long suppliedRevision,
                                               List<UUID> selectedIds, Consumer<List<Candidate>> resolver) {
        Objects.requireNonNull(resolver, "服务器选择结算器");
        List<Candidate> selected = validate(recipient, suppliedTransactionId, suppliedRevision, selectedIds);
        committing = true;
        try {
            resolver.accept(selected);
            consumed = true;
            return selected;
        } finally { committing = false; }
    }

    public synchronized List<Candidate> validate(UUID recipient, UUID suppliedTransactionId, long suppliedRevision,
                                                 List<UUID> selectedIds) {
        if (!entitledPlayer.equals(recipient)) throw new IllegalArgumentException("你无权提交此效果选择");
        if (!transactionId.equals(suppliedTransactionId)) throw new IllegalArgumentException("效果选择事务不匹配");
        if (revision != suppliedRevision) throw new IllegalStateException("对局状态已更新，请按最新界面重新选择");
        requireOpen();
        List<UUID> selected = List.copyOf(selectedIds == null ? List.of() : selectedIds);
        if (selected.size() < minimum || selected.size() > maximum)
            throw new IllegalArgumentException("本效果必须选择 " + minimum + " 至 " + maximum + " 个目标");
        if (new LinkedHashSet<>(selected).size() != selected.size())
            throw new IllegalArgumentException("同一效果目标不能重复选择");
        var resolved = new ArrayList<Candidate>();
        for (UUID selectedId : selected) {
            Candidate candidate = candidates.get(selectedId);
            if (candidate == null) throw new IllegalArgumentException("所选目标不在服务器给出的候选范围内");
            resolved.add(candidate);
        }
        return List.copyOf(resolved);
    }

    private void requireOpen() {
        if (consumed || invalidated || committing) throw new IllegalStateException("此效果选择已完成、正在结算或失效");
    }
}
