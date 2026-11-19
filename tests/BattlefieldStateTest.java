import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.BattlefieldState;
import dev.hua.ptcglive.engine.field.PokemonProfile;
import dev.hua.ptcglive.engine.field.SpecialCondition;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Test-first coverage for the transport-free battlefield state layer. */
public final class BattlefieldStateTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000000081");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000000082");

    public static void main(String[] args) {
        Map<String, PokemonProfile> profiles = new LinkedHashMap<>();
        profiles.put("base", new PokemonProfile(100, 2, null));
        profiles.put("evolution", new PokemonProfile(160, 1, "base"));
        BattlefieldState field = new BattlefieldState(A, B, profiles, new ServerRandom(17));
        CardInstance activeA = card(A, "base"), activeB = card(B, "base");
        field.placeInitialActive(A, activeA); field.placeInitialActive(B, activeB);
        for (int index = 0; index < 5; index++) field.placeOnBench(A, card(A, "base"));
        try { field.placeOnBench(A, card(A, "base")); throw new AssertionError("第六个备战位被允许"); }
        catch (IllegalStateException expected) { }

        field.beginTurn(A);
        field.attachEnergy(A, activeA.instanceId(), card(A, "energy"));
        try { field.attachEnergy(A, activeA.instanceId(), card(A, "energy")); throw new AssertionError("同回合第二次能量附着被允许"); }
        catch (IllegalStateException expected) { }
        field.applySpecialCondition(A, SpecialCondition.POISONED);
        field.applySpecialCondition(A, SpecialCondition.BURNED);
        field.applySpecialCondition(A, SpecialCondition.ASLEEP);
        field.applySpecialCondition(A, SpecialCondition.CONFUSED);
        var conditions = field.activeView(A).conditions();
        if (!conditions.containsAll(List.of(SpecialCondition.POISONED, SpecialCondition.BURNED, SpecialCondition.CONFUSED))
            || conditions.contains(SpecialCondition.ASLEEP)) throw new AssertionError("特殊状态的叠加/互斥规则错误");
        try { field.evolve(A, activeA.instanceId(), card(A, "evolution")); throw new AssertionError("首回合进化被允许"); }
        catch (IllegalStateException expected) { }

        field.beginTurn(B); field.beginTurn(A);
        field.evolve(A, activeA.instanceId(), card(A, "evolution"));
        if (!field.activeView(A).topCardId().equals("evolution") || !field.activeView(A).conditions().isEmpty())
            throw new AssertionError("进化未替换顶牌或未清除特殊状态");
        field.applySpecialCondition(A, SpecialCondition.PARALYZED);
        try { field.retreat(A, field.benchView(A).getFirst().pokemonId(), List.of(card(A, "fake").instanceId())); throw new AssertionError("麻痹宝可梦被允许撤退"); }
        catch (IllegalStateException expected) { }
        field.applySpecialCondition(A, SpecialCondition.CONFUSED);
        List<UUID> retreatEnergy = field.activeView(A).attachedEnergyIds();
        var retreat = field.retreat(A, field.benchView(A).getFirst().pokemonId(), retreatEnergy);
        if (retreat.discardedEnergy().size() != 1 || !field.activeView(A).conditions().isEmpty())
            throw new AssertionError("撤退费用或特殊状态清除错误");

        field.applySpecialCondition(A, SpecialCondition.POISONED);
        field.applySpecialCondition(A, SpecialCondition.BURNED);
        field.applySpecialCondition(A, SpecialCondition.ASLEEP);
        var checkup = field.pokemonCheckup();
        if (field.activeView(A).damage() != 30 || checkup.randomEvents().size() < 2 || field.randomTranscript().isEmpty())
            throw new AssertionError("Pokémon Checkup 的中毒、灼伤和睡眠结算或随机复盘错误");
        System.out.println("BattlefieldStateTest passed: 5 个备战位、能量、进化、撤退、特殊状态与 Pokémon Checkup 均已验证");
    }

    private static CardInstance card(UUID owner, String definition) { return new CardInstance(UUID.randomUUID(), owner, definition); }
}
