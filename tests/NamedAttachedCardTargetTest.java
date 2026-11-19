import dev.hua.ptcglive.engine.ServerMatch;
import dev.hua.ptcglive.rules.CardInstance;
import java.util.List;
import java.util.UUID;

/**
 * A named Pokémon selection must survive into the later public attached-card
 * selection without giving the client a replacement host or a hidden card ID.
 */
public final class NamedAttachedCardTargetTest {
    private static final UUID A = new UUID(0, 8821);
    private static final UUID B = new UUID(0, 8822);

    public static void main(String[] args) {
        attachEnergyToBoundBenchPokemon();
        attachToolToBoundBenchPokemon();
        discardEnergyFromBoundBenchPokemon();
        moveEnergyFromBoundBenchPokemon();
        returnToolFromBoundOpponentPokemon();
        System.out.println("NamedAttachedCardTargetTest passed: named Pokémon hosts persist through public Energy/Tool selection");
    }

    private static void discardEnergyFromBoundBenchPokemon() {
        ServerMatch match = ComposedEnergyAttachmentTest.fixture("""
            {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","position":"BENCH","bind":"ally","effects":[]},
            {"type":"with_pokemon","ref":"ally","position":"BENCH","effects":[
              {"type":"discard_energy","target":"CHOSEN_POKEMON","amount":1,"minimum":1}
            ]}
            """, 0);
        UUID bench = match.benchPokemonView(A).getFirst().pokemonId();
        CardInstance energy = hand(match, A, "grass");
        match.attachEnergy(A, energy.instanceId(), bench);

        match.playTrainer(A, hand(match, A, "component").instanceId(), null);
        var selection = match.effectPokemonPrompt(A);
        check(selection.targets().stream().anyMatch(target -> target.pokemonId().equals(bench)),
            "服务器没有签发已绑定备战宝可梦候选");
        match.chooseEffectPokemon(A, selection.resolutionId(), selection.step(), bench);

        var prompt = match.effectCardPrompt(A);
        check(prompt.candidates().equals(List.of(energy)), "命名备战目标泄漏或混入了其它宝可梦的能量");
        match.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(energy.instanceId()));
        check(match.benchPokemonView(A).stream().filter(view -> view.pokemonId().equals(bench)).findFirst().orElseThrow().attachedEnergyIds().isEmpty(),
            "绑定目标上的能量未被弃置");
        check(match.privateView(A).discard().contains(energy), "弃置能量未回到实际拥有者弃牌区");
    }

    private static void attachEnergyToBoundBenchPokemon() {
        ServerMatch match = ComposedEnergyAttachmentTest.fixture("""
            {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","position":"BENCH","bind":"ally","effects":[]},
            {"type":"with_pokemon","ref":"ally","position":"BENCH","effects":[
              {"type":"attach_energy","target":"CHOSEN_POKEMON","source":"HAND","amount":1,"minimum":1,
               "basic_energy_only":true,"energy_type":"GRASS","target_scope":"OWN_BENCH"}
            ]}
            """, 0);
        UUID bench = match.benchPokemonView(A).getFirst().pokemonId();
        CardInstance energy = hand(match, A, "grass");
        match.playTrainer(A, hand(match, A, "component").instanceId(), null);
        var selection = match.effectPokemonPrompt(A);
        match.chooseEffectPokemon(A, selection.resolutionId(), selection.step(), bench);
        var cards = match.effectCardPrompt(A);
        check(cards.candidates().stream().anyMatch(card -> card.instanceId().equals(energy.instanceId())),
            "已绑定宝可梦没有获得私有能量候选");
        match.submitEffectCards(A, cards.resolutionId(), cards.step(), List.of(energy.instanceId()));
        check(!match.hasPendingAttachmentTarget() && !match.hasPendingCardSelection(),
            "命名目标附能错误要求客户端再次选择宿主");
        check(!match.hasPendingEffectChoice(), "命名目标附能后训练家结算没有结束");
        check(match.benchPokemonView(A).stream().filter(view -> view.pokemonId().equals(bench)).findFirst().orElseThrow()
                .attachedEnergyIds().contains(energy.instanceId()), "能量没有附着到服务器已绑定的备战宝可梦");
    }

    private static void returnToolFromBoundOpponentPokemon() {
        ServerMatch match = ComposedEnergyAttachmentTest.fixture("""
            {"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","position":"ACTIVE","bind":"foe","effects":[]},
            {"type":"with_pokemon","ref":"foe","position":"ACTIVE","effects":[
              {"type":"return_tool","target":"CHOSEN_POKEMON","amount":1,"minimum":1}
            ]}
            """, 10);
        match.endTurn(A);
        match.drawCardsByEffect(B, B, 60);
        UUID opponentActive = match.activePokemonView(B).pokemonId();
        CardInstance tool = hand(match, B, "tool");
        match.playTrainer(B, tool.instanceId(), opponentActive);
        match.endTurn(B);
        int handBefore = match.privateView(B).hand().size();

        match.playTrainer(A, hand(match, A, "component").instanceId(), null);
        var selection = match.effectPokemonPrompt(A);
        check(selection.targets().size() == 1 && selection.targets().getFirst().pokemonId().equals(opponentActive),
            "服务器没有将对方战斗位保持为命名目标");
        match.chooseEffectPokemon(A, selection.resolutionId(), selection.step(), opponentActive);

        var prompt = match.effectCardPrompt(A);
        check(prompt.candidates().stream().map(CardInstance::instanceId).toList().equals(List.of(tool.instanceId())),
            "命名对方目标没有只列出其公开附着工具");
        match.submitEffectCards(A, prompt.resolutionId(), prompt.step(), List.of(tool.instanceId()));
        check(match.activePokemonView(B).attachedTools().isEmpty() && match.privateView(B).hand().contains(tool)
                && match.privateView(B).hand().size() == handBefore + 1,
            "工具没有从绑定对方目标回到工具实际拥有者手牌");
    }

    private static void moveEnergyFromBoundBenchPokemon() {
        ServerMatch match = ComposedEnergyAttachmentTest.fixture("""
            {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","position":"BENCH","bind":"source","effects":[]},
            {"type":"with_pokemon","ref":"source","position":"BENCH","effects":[
              {"type":"move_energy","target":"CHOSEN_POKEMON","amount":1,"minimum":1,"destination_scope":"OWN_ACTIVE"}
            ]}
            """, 20);
        UUID bench = match.benchPokemonView(A).getFirst().pokemonId();
        UUID active = match.activePokemonView(A).pokemonId();
        CardInstance energy = hand(match, A, "grass");
        match.attachEnergy(A, energy.instanceId(), bench);

        match.playTrainer(A, hand(match, A, "component").instanceId(), null);
        var selection = match.effectPokemonPrompt(A);
        match.chooseEffectPokemon(A, selection.resolutionId(), selection.step(), bench);
        var cards = match.effectCardPrompt(A);
        check(cards.candidates().equals(List.of(energy)), "绑定来源没有限制移动能量的公开候选");
        match.submitEffectCards(A, cards.resolutionId(), cards.step(), List.of(energy.instanceId()));
        var target = match.effectAttachmentPrompt(A);
        check(target.targets().equals(List.of(active)), "命名来源的能量移动未按服务器目标范围签发目的地");
        match.chooseEffectAttachmentTarget(A, target.resolutionId(), target.step(), active);
        check(match.activePokemonView(A).attachedEnergyIds().contains(energy.instanceId())
                && match.benchPokemonView(A).stream().filter(view -> view.pokemonId().equals(bench)).findFirst().orElseThrow().attachedEnergyIds().isEmpty(),
            "能量没有从绑定来源移动到服务器确认的目的地");
    }

    private static void attachToolToBoundBenchPokemon() {
        ServerMatch match = ComposedEnergyAttachmentTest.fixture("""
            {"type":"select_pokemon","target":"SELECT_OWN_POKEMON","position":"BENCH","bind":"ally","effects":[]},
            {"type":"with_pokemon","ref":"ally","position":"BENCH","effects":[
              {"type":"attach_tool","target":"CHOSEN_POKEMON","source":"HAND","amount":1,"minimum":1,"target_scope":"OWN_BENCH"}
            ]}
            """, 30);
        match.drawCardsByEffect(A, A, 60);
        UUID bench = match.benchPokemonView(A).getFirst().pokemonId();
        CardInstance tool = hand(match, A, "tool");
        match.playTrainer(A, hand(match, A, "component").instanceId(), null);
        var selection = match.effectPokemonPrompt(A);
        match.chooseEffectPokemon(A, selection.resolutionId(), selection.step(), bench);
        var cards = match.effectCardPrompt(A);
        check(cards.candidates().stream().anyMatch(card -> card.instanceId().equals(tool.instanceId())),
            "已绑定宝可梦没有获得私有工具候选");
        match.submitEffectCards(A, cards.resolutionId(), cards.step(), List.of(tool.instanceId()));
        check(!match.hasPendingAttachmentTarget() && match.benchPokemonView(A).stream()
                .filter(view -> view.pokemonId().equals(bench)).findFirst().orElseThrow().attachedTools().contains(tool),
            "工具没有直接附着到服务器已绑定的备战宝可梦");
        check(!match.hasPendingEffectChoice(), "命名目标附着工具后训练家结算没有结束");
    }

    private static CardInstance hand(ServerMatch match, UUID owner, String definitionId) {
        return match.privateView(owner).hand().stream().filter(card -> card.definitionId().equals(definitionId)).findFirst().orElseThrow();
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
