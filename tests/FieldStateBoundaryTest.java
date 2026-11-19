import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Whole-card and turn boundaries, not client refreshes, own state-based correction. */
public final class FieldStateBoundaryTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static ServerMatch fixture(AbilityDefinition... abilities) {
        return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),DynamicPassiveModifierTest.attack(40,false,false),List.of(abilities));
    }
    public static void main(String[] args) {
        cardTextAfterDeparture(); twoEmptyActives(); auraDeparture(); startExpiry(); endExpiry();
        departedTargets(); trainerDeparture(); attackDeparture(); promotionLosesHp(); directKoDepartureRejected(); directKoAndOppositeDeparture(); absentConditionLogic();
        System.out.println("FieldStateBoundaryTest passed: whole-card departure and turn expiry checkpoints");
    }
    static void cardTextAfterDeparture() {
        for(String effect:List.of("return_to_hand","shuffle_into_deck")) {
            var a=ResumableAbilityTest.ability("leave","{\"type\":\""+effect+"\",\"target\":\"SELF\"},{\"type\":\"choice\",\"effects\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}");
            var m=fixture(a);UUID source=m.activePokemonView(A).pokemonId();int before=m.privateView(A).hand().size(),turn=m.turnNumber();
            m.useAbility(A,source,"leave");
            check(m.phase()==MatchPhase.TURN&&m.hasPendingEffectChoice(),"离场提前打断同张卡后续选择："+effect);
            check(m.clientView(B).resolvingAbility().pokemonId()==null&&m.clientView(null).resolvingAbility().pokemonId()==null,"公开效果提示仍跟踪离场卡的私有实体身份");
            var p=m.effectChoicePrompt(A);int rng=m.randomTranscript().size();var session=MatchSession.multiplayer(m,A,B);
            session.view(A,A);session.view(B,B);check(rng==m.randomTranscript().size(),"刷新重洗离场来源");
            m.chooseEffectOption(A,p.resolutionId(),p.step(),1);
            check(m.privateView(A).hand().size()==before+2+(effect.equals("return_to_hand")?1:0),"离场后抽牌被跳过或重复");
            check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT&&m.pendingChoicePlayer().equals(A),"完成整张卡后未请求替补");
            m.chooseReplacement(A,m.benchPokemonView(A).getFirst().pokemonId());
            check(m.phase()==MatchPhase.TURN&&m.turnNumber()==turn&&m.currentPlayer().equals(A),"离场替补错误推进回合");
        }
    }
    static void twoEmptyActives() {
        var a=ResumableAbilityTest.ability("both","{\"type\":\"return_to_hand\",\"target\":\"OPPONENT_ACTIVE\"},{\"type\":\"return_to_hand\",\"target\":\"SELF\"},{\"type\":\"draw\",\"amount\":1}");
        var m=fixture(a);int turn=m.turnNumber();m.useAbility(A,m.activePokemonView(A).pokemonId(),"both");
        for(int i=0;i<2;i++) {
            check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT,"第二个空战斗位被覆盖");UUID owner=m.pendingChoicePlayer();
            m.chooseReplacement(owner,m.benchPokemonView(owner).getFirst().pokemonId());
        }
        check(m.phase()==MatchPhase.TURN&&m.turnNumber()==turn,"双方替补未回到同一回合");
    }
    static TrainerProfile aura(){return DynamicPassiveModifierTest.trainer("TOOL","{\"type\":\"hit_points_modifier\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":100}");}
    static void promotionLosesHp() {
        var stadium=DynamicPassiveModifierTest.trainer("STADIUM","{\"type\":\"hit_points_modifier\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":100,\"conditions\":[{\"type\":\"NOT\",\"conditions\":[{\"type\":\"IS_ACTIVE\"}]}]}");
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),stadium);
        m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);
        UUID bench=m.benchPokemonView(A).getFirst().pokemonId();m.placeDamageCountersByEffect(A,A,bench,32);
        m.knockOutPokemonByEffect(A,A,m.activePokemonView(A).pokemonId());
        m.takePrizeAt(B,0);check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT,"没有首次替补选择");
        m.chooseReplacement(A,bench);
        check(m.phase()==MatchPhase.FINISHED&&m.result().winner().equals(B)
            &&m.result().reason()==MatchResult.Reason.NO_POKEMON,
            "替补失去备战 HP 加成后未在无替补时立即判负");
        check(m.privateView(B).prizes().size()==5,"无宝可梦自动败北错误额外发放连续击倒奖赏");
    }
    static void directKoDepartureRejected() {
        var a=ResumableAbilityTest.ability("ambiguous","{\"type\":\"draw\",\"amount\":1},{\"type\":\"knock_out\",\"target\":\"OPPONENT_ACTIVE\"},{\"type\":\"return_to_hand\",\"target\":\"OPPONENT_ACTIVE\"}");
        var m=fixture(a);var before=m.clientView(A);var rng=m.randomTranscript();boolean denied=false;
        try {m.useAbility(A,m.activePokemonView(A).pokemonId(),"ambiguous");}catch(IllegalStateException expected){denied=true;}
        check(denied&&m.clientView(A).equals(before)&&m.randomTranscript().equals(rng),"未核验的直接击倒与离场组合执行了前缀");
    }
    /** Opposite sides cannot be the same Pokémon, so both outcomes share the normal KO/replacement checkpoint. */
    static void directKoAndOppositeDeparture() {
        var a=ResumableAbilityTest.ability("split","""
            {"type":"knock_out","target":"OPPONENT_ACTIVE"},
            {"type":"return_to_hand","target":"SELF"},
            {"type":"draw","amount":1}
            """);
        var m=fixture(a);UUID source=m.activePokemonView(A).pokemonId();int hand=m.privateView(A).hand().size(),turn=m.turnNumber();
        m.useAbility(A,source,"split");
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&m.privateView(A).hand().size()==hand+2,
            "异侧击倒与离场没有完成文本、回手或统一奖赏检查点");
        m.takePrizeAt(A,0);
        for(int i=0;i<2;i++) {
            check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT,"异侧离场／击倒没有依次要求双方替补");
            UUID owner=m.pendingChoicePlayer();m.chooseReplacement(owner,m.benchPokemonView(owner).getFirst().pokemonId());
        }
        check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(A)&&m.turnNumber()==turn&&m.privateView(A).prizes().size()==5,
            "异侧离场／击倒错误推进回合或奖赏数量");
    }
    static void auraDeparture() {
        for(boolean active:List.of(true,false))for(var destination:LeavePlayDestination.values()) {
            var m=DynamicPassiveModifierTest.fixture(aura(),DynamicPassiveModifierTest.plainStadium());
            UUID source=(active?m.activePokemonView(B):m.benchPokemonView(B).getFirst()).pokemonId();
            UUID affected=(active?m.benchPokemonView(B).getFirst():m.activePokemonView(B)).pokemonId();
            UUID physical=PokemonIdentityTestSupport.physical(m,B,affected);
            m.attachToolByEffect(A,B,EffectSourceZone.HAND,hand(m,B,"tool").instanceId(),B,source);m.placeDamageCountersByEffect(A,B,affected,32);
            m.movePokemonByEffect(A,B,source,destination);
            // The last remaining Pokémon is Knocked Out after the aura source
            // leaves.  No replacement exists, so the current rules finish the
            // game immediately instead of exposing a cosmetic Prize prompt.
            check(m.phase()==MatchPhase.FINISHED&&m.result().winner().equals(A)
                &&m.result().reason()==MatchResult.Reason.NO_POKEMON,
                "光环离场后的最后宝可梦没有立即按无宝可梦判负："+active+destination);
            check(m.privateView(B).discard().stream().anyMatch(c->c.instanceId().equals(physical)),"受影响宝可梦未弃置");
            check(m.privateView(A).prizes().size()==6,"无宝可梦立即判负错误发放了奖赏");
        }
    }
    static String shield(String duration) {return EffectOriginProtectionTest.shield("TRAINER","EFFECTS_ONLY","SELF",duration);}
    static ServerMatch negativeAura(AbilityDefinition guard) {
        var stadium=DynamicPassiveModifierTest.trainer("STADIUM","{\"type\":\"hit_points_modifier\",\"target\":\"ALL_OPPONENT_POKEMON\",\"amount\":-100}");
        return DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),stadium,EnergyProfile.basic(EnergyType.COLORLESS),DynamicPassiveModifierTest.attack(40,false,false),List.of(guard));
    }
    static void startExpiry() {
        var m=negativeAura(ResumableAbilityTest.ability("guard",shield("SOURCE_NEXT_TURN_START")));
        m.endTurn(A);UUID host=m.activePokemonView(B).pokemonId();m.useAbility(B,host,"guard");m.endTurn(B);
        m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);m.placeDamageCountersByEffect(A,B,host,25);
        int deck=m.privateView(B).deck().size(),turn=m.turnNumber();m.endTurn(A);
        check(m.phase()==MatchPhase.PRIZE_SELECTION,"回合开始防护到期未检查HP击倒");
        m.takePrizeAt(A,0);m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());
        check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(B)&&m.turnNumber()==turn+1,"回合开始击倒后回合错位");
        check(m.privateView(B).deck().size()==deck-1,"回合开始击倒续接重复或丢失必抽牌");
    }
    static void endExpiry() {
        var m=negativeAura(ResumableAbilityTest.ability("guard",shield("SOURCE_TURN_END")));
        m.playTrainer(A,hand(m,A,"stadium").instanceId(),null);m.endTurn(A);
        UUID host=m.activePokemonView(B).pokemonId();m.useAbility(B,host,"guard");m.placeDamageCountersByEffect(B,B,host,25);
        int deck=m.privateView(A).deck().size(),turn=m.turnNumber();m.endTurn(B);
        check(m.phase()==MatchPhase.PRIZE_SELECTION,"回合结束防护到期未检查HP击倒");
        m.takePrizeAt(A,0);m.chooseReplacement(B,m.benchPokemonView(B).getFirst().pokemonId());
        check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(A)&&m.turnNumber()==turn+1,"回合结束击倒续接回合错误");
        check(m.privateView(A).deck().size()==deck-1,"回合结束击倒续接重复或丢失必抽牌");
    }
    static void departedTargets() {
        var ability=ResumableAbilityTest.ability("leave","""
            {"type":"return_to_hand","target":"SELF"},
            {"type":"heal","target":"SELF","amount":10},
            {"type":"draw","amount":3,"conditions":[{"type":"HP_BELOW","value":100}]},
            {"type":"search_deck","target":"DECK","amount":1,"minimum":1},
            {"type":"draw","target":"OWN_ACTIVE","amount":1}
            """);
        var m=fixture(ability);int hand=m.privateView(A).hand().size();UUID host=m.activePokemonView(A).pokemonId();
        m.placeDamageCountersByEffect(A,A,host,1);m.useAbility(A,host,"leave");
        var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));
        check(m.privateView(A).hand().size()==hand+3&&m.phase()==MatchPhase.CHOOSE_REPLACEMENT,"离场后仍读取旧宝可梦，或玩家抽牌被空战斗位阻断");
    }
    static void absentConditionLogic() {
        var ability=ResumableAbilityTest.ability("logic","""
            {"type":"return_to_hand","target":"SELF"},
            {"type":"draw","amount":1,"conditions":[{"type":"OR","conditions":[{"type":"HP_BELOW","value":10},{"type":"HAND_SIZE","value":0}]}]},
            {"type":"draw","amount":2,"conditions":[{"type":"NOT","conditions":[{"type":"HP_BELOW","value":10}]}]},
            {"type":"draw","amount":1}
            """);
        var m=fixture(ability);int before=m.privateView(A).hand().size();m.useAbility(A,m.activePokemonView(A).pokemonId(),"logic");
        check(m.privateView(A).hand().size()==before+3,"缺失目标吞掉有效 OR 分支，或被 NOT 反转成成功");
    }
    static void trainerDeparture() {
        var item=ComposedTrainerIntegrationTest.item("{\"type\":\"return_to_hand\",\"target\":\"OWN_ACTIVE\"},{\"type\":\"draw\",\"amount\":1}");
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),DynamicPassiveModifierTest.attack(40,false,false),List.of(),0,item);
        var c=hand(m,A,"item");int before=m.privateView(A).hand().size();m.playTrainer(A,c.instanceId(),null);
        check(m.privateView(A).hand().size()==before+1&&m.privateView(A).discard().contains(c)&&m.phase()==MatchPhase.CHOOSE_REPLACEMENT,"训练家离场后抽牌和弃置未完整完成");
    }
    static void attackDeparture() {
        for(String target:List.of("SELF","OPPONENT_ACTIVE")) {
            var after=ResumableAttackTest.attack("{\"type\":\"return_to_hand\",\"target\":\""+target+"\"},{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":1},{\"type\":\"draw\",\"amount\":1}");
            var attack=DynamicPassiveModifierTest.attack(40,false,false).withEffectProgram(after.effectProgram());
            var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.COLORLESS),attack);
            if(target.equals("OPPONENT_ACTIVE"))m.placeDamageCountersByEffect(A,B,m.activePokemonView(B).pokemonId(),23);
            int turn=m.turnNumber();m.attack(A,attack.id());check(m.phase()==MatchPhase.TURN&&m.hasPendingCardSelection(),"攻击离场没有保留后续私有选择");
            if(target.equals("SELF"))check(m.clientView(B).resolvingAttack().pokemonId()==null,"攻击提示仍发送回手来源实体 ID");
            var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));
            check(m.phase()==MatchPhase.CHOOSE_REPLACEMENT,"攻击离场未要求替补");UUID owner=m.pendingChoicePlayer();
            check(owner.equals(target.equals("SELF")?A:B),"攻击替补席位错误");
            m.chooseReplacement(owner,m.benchPokemonView(owner).getFirst().pokemonId());
            check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(B)&&m.turnNumber()==turn+1,"攻击离场后回合未恰好推进一次");
            check(m.privateView(A).prizes().size()==6&&m.privateView(B).prizes().size()==6,"攻击文字完成前离场的目标被错误给予击倒奖赏");
        }
    }
}
