import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/**
 * “Switch this Pokémon with 1 of your Benched Pokémon. If you do, …”, “Draw a card. If you do, …” and
 * “Discard an Energy from this Pokémon. If you do, …”: the first after-damage step records under bind what it
 * actually did (switched, cards drawn, Energy discarded); the dependent text runs only when that count is at least 1.
 * A step that could do nothing (empty Bench, empty deck, no Energy) records nothing and reads 0.
 */
public final class ChainedIfYouDoTest {
    static final UUID A=BeforeDamageAttackTest.A,B=BeforeDamageAttackTest.B;
    static void check(boolean b,String reason){BeforeDamageAttackTest.check(b,reason);}
    static String ifDone(String effect){
        return "{\"type\":\"conditional\",\"conditions\":[{\"type\":\"BOUND_COUNT\",\"bind\":\"done\",\"value\":1}],\"effects\":["+effect+"]}";
    }
    static final String SWITCH_SELF="{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\",\"bind\":\"done\"}";
    static final String OPPONENT_SWITCHES="{\"type\":\"force_switch\",\"target\":\"SELECT_OPPONENT_POKEMON\"}";
    public static void main(String[] args){
        switchThenOpponentSwitches(); noBenchNothingHappens(); gustThenSwitchSelf(); drawThenAsleep(); discardThenDiscard(); invalidData();
        System.out.println("ChainedIfYouDoTest passed: 换位／抽牌／弃能量记录“做成了没有”，“If you do” 的后半句只在做成时结算");
    }
    /** Palkia “Wormhole”: switch this Pokémon, and if you do, the opponent switches their Active Pokémon. */
    static void switchThenOpponentSwitches(){
        var a=BeforeDamageAttackTest.attack("","",SWITCH_SELF+","+ifDone(OPPONENT_SWITCHES));
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"换位后 “If you do” 被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        UUID mine=m.benchPokemonView(A).getFirst().pokemonId(),theirs=m.benchPokemonView(B).getFirst().pokemonId(),oldActive=m.activePokemonView(B).pokemonId();
        m.attack(A,a.id());
        var own=m.effectPokemonPrompt(A);
        m.chooseEffectPokemon(A,own.resolutionId(),own.step(),mine);
        check(m.activePokemonView(A).pokemonId().equals(mine),"自己没有换位");
        var opponent=m.effectPokemonPrompt(B);
        check(opponent!=null,"换位成功后对手没有得到选择新战斗宝可梦的提示");
        m.chooseEffectPokemon(B,opponent.resolutionId(),opponent.step(),theirs);
        check(m.activePokemonView(B).pokemonId().equals(theirs),"对手没有换位");
        check(m.benchPokemonView(B).stream().anyMatch(p->p.pokemonId().equals(oldActive)&&p.damage()==80),"伤害应先打在原战斗宝可梦上");
        check(m.currentPlayer().equals(B),"攻击没有结束");
        check(m.replayTranscript().stream().anyMatch(e->e.operation().equals("攻击计数绑定：done；数量=1")),"回放没有记录换位成功");
    }
    /** No Benched Pokémon: the switch cannot happen, so neither does the dependent text. */
    static void noBenchNothingHappens(){
        var a=BeforeDamageAttackTest.attack("","",SWITCH_SELF+","+ifDone(OPPONENT_SWITCHES));
        var m=fixtureWithoutBench(a);
        UUID active=m.activePokemonView(A).pokemonId(),opponent=m.activePokemonView(B).pokemonId();
        m.attack(A,a.id());
        check(!m.hasPendingPokemonSelection(),"没有备战宝可梦时仍要求选择");
        check(m.activePokemonView(A).pokemonId().equals(active)&&m.activePokemonView(B).pokemonId().equals(opponent),"没有换位时不应结算后半句");
        check(m.activePokemonView(B).damage()==80&&m.currentPlayer().equals(B),"攻击伤害或回合结束不对");
    }
    /** Floette “Swirling Petals”: switch in the opponent's Benched Pokémon; if you do, switch this Pokémon. */
    static void gustThenSwitchSelf(){
        var a=BeforeDamageAttackTest.attack("","","{\"type\":\"gust\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"bind\":\"done\"},"
            +ifDone("{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"}"));
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"对手换位后 “If you do” 被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);
        UUID mine=m.benchPokemonView(A).getFirst().pokemonId(),theirs=m.benchPokemonView(B).getFirst().pokemonId();
        m.attack(A,a.id());
        var gust=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,gust.resolutionId(),gust.step(),theirs);
        var own=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,own.resolutionId(),own.step(),mine);
        check(m.activePokemonView(B).pokemonId().equals(theirs)&&m.activePokemonView(A).pokemonId().equals(mine),"两次换位没有都完成");
    }
    /** “Caturday”: draw a card; if you do, this Pokémon is now Asleep. (A damage counter stands in for Asleep: Pokémon Checkup may wake it.) */
    static void drawThenAsleep(){
        var a=BeforeDamageAttackTest.attack("","","{\"type\":\"draw\",\"amount\":1,\"bind\":\"done\"},"
            +ifDone("{\"type\":\"damage_counter\",\"target\":\"SELF\",\"amount\":1}"));
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"抽牌后 “If you do” 被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        var m=BeforeDamageAttackTest.fixture(a);int hand=m.privateView(A).hand().size();
        m.attack(A,a.id());
        check(m.privateView(A).hand().size()==hand+1&&m.activePokemonView(A).damage()==10,"抽到牌后没有结算后半句");
        var empty=BeforeDamageAttackTest.fixture(a);
        empty.drawCardsByEffect(A,A,60);
        check(empty.privateView(A).deck().isEmpty(),"夹具牌库没有抽空");
        empty.attack(A,a.id());
        check(empty.activePokemonView(A).damage()==0,"牌库为空没抽到牌，不应结算后半句");
    }
    /** Kleavor V “Axe Slash”: discard an Energy from this Pokémon; if you do, discard an Energy from the opponent's Active. */
    static void discardThenDiscard(){
        var a=BeforeDamageAttackTest.attack("","","{\"type\":\"discard_energy\",\"target\":\"OWN_ACTIVE\",\"amount\":1,\"minimum\":1,\"bind\":\"done\"},"
            +ifDone("{\"type\":\"discard_energy\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":1,\"minimum\":1}"));
        check(AttackProgramRules.unsupportedReason(a).isEmpty(),"弃能量后 “If you do” 被拒绝："+AttackProgramRules.unsupportedReason(a).orElse(""));
        for(boolean own:List.of(true,false)){
            var m=BeforeDamageAttackTest.fixture(a);
            if(own)AttackCostBindingTest.giveEnergy(m,1);
            UUID defender=m.activePokemonView(B).pokemonId();
            var energy=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
            m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,energy.instanceId(),B,defender);
            m.attack(A,a.id());
            for(int i=0;i<3&&m.hasPendingCardSelection();i++){
                var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId()));
            }
            check(m.activePokemonView(A).attachedEnergyIds().isEmpty(),"自己的能量没有弃掉");
            check(m.activePokemonView(B).attachedEnergyIds().size()==(own?0:1),
                own?"弃掉自己的能量后没有弃对手的能量":"自己没有能量可弃时不应弃对手的能量");
            check(m.currentPlayer().equals(B),"攻击没有结束");
        }
    }
    static void invalidData(){
        for(String[] bad:List.<String[]>of(
            // 伤害计算不能读伤害后才记录的计数
            new String[]{"",ifDone("{\"type\":\"damage\",\"target\":\"OPPONENT_ACTIVE\",\"amount\":30}"),SWITCH_SELF},
            // 读取在它之后才记录的计数
            new String[]{"","",ifDone("{\"type\":\"draw\",\"amount\":1}")+","+SWITCH_SELF},
            // 撤退效果、未审核参数
            new String[]{"","","{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\",\"bind\":\"done\",\"amount\":1},"+ifDone(OPPONENT_SWITCHES)},
            new String[]{"","","{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\",\"bind\":\"Done\"},"+ifDone(OPPONENT_SWITCHES)}))
            BeforeDamageAttackTest.reject(()->AttackProgramRules.validate(BeforeDamageAttackTest.attack(bad[0],bad[1],bad[2])));
        // Manual continuations now own separate memories for successful switches.
        var trainer=new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_CARD_PLAYED,
            SWITCH_SELF+","+ifDone("{\"type\":\"draw\",\"amount\":1}"))));
        check(ResumableProgramRules.unsupportedReason(trainer,false).isEmpty(),"训练家换位后的实际动作计数未接入");
        var item=ComposedTrainerIntegrationTest.item(SWITCH_SELF+","+ifDone("{\"type\":\"draw\",\"amount\":1}"));
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),EnergyProfile.basic(EnergyType.GRASS),DynamicPassiveModifierTest.attack(40,false,false),List.of(),0,item);
        UUID actor=DynamicPassiveModifierTest.A;
        for(int i=0;i<2;i++) {
            var card=DynamicPassiveModifierTest.hand(m,actor,"item");int count=m.privateView(actor).hand().size();
            m.playTrainer(actor,card.instanceId(),null);var p=m.effectPokemonPrompt(actor);
            m.chooseEffectPokemon(actor,p.resolutionId(),p.step(),m.benchPokemonView(actor).getFirst().pokemonId());
            check(m.privateView(actor).hand().size()==count,"不同训练家续接的换位计数泄漏或丢失");
        }
    }
    /** The usual dynamic-passive fixture, but both players open with only an Active Pokémon. */
    static ServerMatch fixtureWithoutBench(AttackDefinition attack){
        var tool=DynamicPassiveModifierTest.plainTool();var stadium=DynamicPassiveModifierTest.plainStadium();
        String[] ids={"basic","basic","evolved","tool","stadium","energy","special"};
        var defs=new ArrayList<CardDefinition>();
        for(String id:Arrays.stream(ids).distinct().toList()) {
            boolean p=id.equals("basic")||id.equals("evolved"),e=id.equals("energy")||id.equals("special");
            var category=p||e?TrainerCategory.NONE:id.equals("tool")?TrainerCategory.TOOL:TrainerCategory.STADIUM;
            defs.add(new CardDefinition(id,id,p?CardCategory.POKEMON:e?CardCategory.ENERGY:CardCategory.TRAINER,category,"H",Set.of("international"),id.equals("basic"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
        }
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(300,3,null,EnergyType.GRASS,2,null,0,1,List.of(attack),List.of(),null),
            "evolved",new PokemonProfile(350,3,"basic",EnergyType.GRASS,2,null,0,1,List.of(attack),List.of(),null)),
            Map.of("energy",EnergyType.GRASS),Map.of("tool",tool,"stadium",stadium),Map.of("special",EnergyProfile.basic(EnergyType.COLORLESS)));
        for(int seed=0;seed<1000;seed++) {
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID owner:List.of(A,B)) {var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),combat,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().noneMatch(c->c.definitionId().equals("basic"))))continue;
            OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,30);m.drawCardsByEffect(A,B,30);m.endTurn(A);m.endTurn(B);
            if(m.benchPokemonView(A).isEmpty()&&m.benchPokemonView(B).isEmpty())return m;
        }
        throw new AssertionError("没有备战宝可梦的夹具未生成");
    }
}
