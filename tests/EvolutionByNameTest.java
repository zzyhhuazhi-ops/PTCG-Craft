import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.engine.effect.EffectRegistry;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.BundledCombatData;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.*;

/** Official Evolution rule: "Evolves from" matches the printed name, so any printing of that Pokémon can evolve. */
public final class EvolutionByNameTest {
    private static final UUID A = new UUID(0, 1501), B = new UUID(0, 1502);
    public static void main(String[] args) {
        var profiles = new LinkedHashMap<String, PokemonProfile>();
        profiles.put("snivy_a", named(60, null, "Snivy", null));
        profiles.put("snivy_b", named(60, null, "Snivy", null));
        profiles.put("tepig", named(60, null, "Tepig", null));
        // Representative printing is snivy_a; the name still admits snivy_b.
        profiles.put("servine", named(80, "snivy_a", "Servine", "Snivy"));
        profiles.put("serperior", named(130, "servine", "Serperior", "Servine"));
        // Legacy data without names keeps the exact-definition match.
        profiles.put("legacy_servine", new PokemonProfile(80, 0, "snivy_a"));
        var field = new BattlefieldState(A, B, profiles, new ServerRandom(1));

        field.beginTurn(A);
        field.placeInitialActive(A, card("snivy_b"));
        field.placeOnBench(A, card("tepig"));
        field.placeOnBench(A, card("snivy_a"));
        field.placeOnBench(A, card("snivy_b"));
        field.endTurn(A);
        field.beginTurn(B); field.placeInitialActive(B, card("tepig", B)); field.endTurn(B);
        field.beginTurn(A);
        UUID active = field.activeView(A).pokemonId();
        var bench = field.benchView(A);
        UUID tepig = bench.get(0).pokemonId(), snivyA = bench.get(1).pokemonId(), snivyB = bench.get(2).pokemonId();

        if (!field.canEvolve(A, active, "servine"))
            throw new AssertionError("同名但非代表卡号的另一版本宝可梦不能进化");
        if (field.canEvolve(A, tepig, "servine")) throw new AssertionError("不同卡名的宝可梦被错误允许进化");
        if (!field.canEvolve(A, snivyA, "legacy_servine")) throw new AssertionError("旧数据的精确卡号进化被破坏");
        if (field.canEvolve(A, snivyB, "legacy_servine")) throw new AssertionError("没有卡名的旧数据错误地按名字放宽");

        var skip = EvolutionPermission.CARD_EFFECT_SKIP_ONE_STAGE_STRICT_TIMING;
        if (!field.canEvolveSkippingOneStage(A, snivyB, "serperior", skip))
            throw new AssertionError("跳阶进化没有按卡名接受非代表版本的基础宝可梦");
        if (field.canEvolveSkippingOneStage(A, tepig, "serperior", skip))
            throw new AssertionError("跳阶进化错误接受了不同名字的基础宝可梦");

        field.evolve(A, active, card("servine"));
        if (!field.activeView(A).topCardId().equals("servine")) throw new AssertionError("按卡名进化后的顶牌不正确");

        rejectsProfile(() -> named(80, null, "Servine", "Snivy"), "按卡名进化缺少代表卡号仍被接受");
        rejectsProfile(() -> named(80, "snivy_a", " ", "Snivy"), "空白卡名被接受");
        rejectsProfile(() -> named(80, "snivy_a", "Servine", ""), "空白进化来源卡名被接受");

        var loaded = BundledCombatData.pokemon(JsonParser.parseString("""
            {"hitPoints":80,"retreatCost":2,"evolvesFromDefinitionId":"snivy_a","prizeCards":1,"attacks":[],
             "pokemonName":"Servine","evolvesFromName":"Snivy"}
            """).getAsJsonObject(), EffectRegistry.standard());
        if (!"Servine".equals(loaded.pokemonName()) || !"Snivy".equals(loaded.evolvesFromName()))
            throw new AssertionError("战斗数据没有读取 pokemonName / evolvesFromName");
        var legacy = BundledCombatData.pokemon(JsonParser.parseString("""
            {"hitPoints":80,"retreatCost":2,"evolvesFromDefinitionId":"snivy_a","prizeCards":1,"attacks":[]}
            """).getAsJsonObject(), EffectRegistry.standard());
        if (legacy.pokemonName() != null || legacy.evolvesFromName() != null)
            throw new AssertionError("没有卡名字段的旧战斗数据不应凭空得到卡名");
        System.out.println("EvolutionByNameTest passed: 按印刷卡名进化（含跳阶）与旧数据精确卡号兼容均已验证");
    }
    private static PokemonProfile named(int hp, String evolvesFromId, String name, String evolvesFromName) {
        return new PokemonProfile(hp, 1, evolvesFromId, null, 0, null, 0, 1, List.of(), List.of(), null, name, evolvesFromName);
    }
    private static CardInstance card(String definitionId) { return card(definitionId, A); }
    private static CardInstance card(String definitionId, UUID owner) { return new CardInstance(UUID.randomUUID(), owner, definitionId); }
    private static void rejectsProfile(Runnable operation, String message) {
        try { operation.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(message);
    }
}
