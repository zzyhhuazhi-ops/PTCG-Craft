import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.*;

/** Normal evolution remains strict; only an explicit server effect permission can override timing. */
public final class EvolutionPermissionTest {
    private static final UUID A = new UUID(0, 1401), B = new UUID(0, 1402);
    public static void main(String[] args) {
        var profiles = Map.of(
            "base", new PokemonProfile(60, 0, null),
            "stage1", new PokemonProfile(90, 0, "base"),
            "stage2", new PokemonProfile(120, 0, "stage1"));
        var field = new BattlefieldState(A, B, profiles, new ServerRandom(1));
        CardInstance base = new CardInstance(UUID.randomUUID(), A, "base");
        CardInstance stage1 = new CardInstance(UUID.randomUUID(), A, "stage1");
        CardInstance stage2 = new CardInstance(UUID.randomUUID(), A, "stage2");
        field.beginTurn(A); field.placeInitialActive(A, base);
        UUID target = field.activeView(A).pokemonId();
        if (field.canEvolve(A, target, "stage1")) throw new AssertionError("普通首回合登场宝可梦不能进化");
        rejects(() -> field.evolve(A, target, stage1));
        if (!field.canEvolve(A, target, "stage1", EvolutionPermission.CARD_EFFECT_ALL_TIMING))
            throw new AssertionError("已声明的服务器卡牌效果许可没有开放对应时点");
        field.evolve(A, target, stage1, EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        if (field.canEvolve(A, target, "stage2")) throw new AssertionError("普通操作不能在同一回合再次进化");
        field.evolve(A, target, stage2, EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        if (!field.activeView(A).topCardId().equals("stage2")) throw new AssertionError("服务器许可进化没有保留同一宝可梦身份");
        System.out.println("EvolutionPermissionTest passed: 严格进化时点与服务器卡牌效果例外均已验证");
    }
    private static void rejects(Runnable operation) {
        try { operation.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; }
        throw new AssertionError("非法普通进化被接受");
    }
}
