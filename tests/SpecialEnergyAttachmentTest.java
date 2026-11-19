import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Card attachment restrictions are server data, separate from the amount of Energy supplied. */
public final class SpecialEnergyAttachmentTest {
    static final UUID A=DynamicPassiveModifierTest.A,B=DynamicPassiveModifierTest.B;
    static void check(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static void reject(Runnable call){BeforeDamageAttackTest.reject(call);}
    static EnergyProfile energy(String json){return DynamicEnergyProvisionTest.energy(json);}
    static EnergyProfile restricted(){return energy("""
        {"energyUnits":[["COLORLESS"],["COLORLESS"],["COLORLESS"]],
         "attachmentRule":{"stages":["EVOLUTION"],"discard_if_invalid":true}}
        """);}
    static ServerMatch fixture(EnergyProfile e){return DynamicEnergyProvisionTest.fixture(e,DynamicPassiveModifierTest.attack(20,false,false));}
    static CardInstance card(ServerMatch m,String id){return DynamicPassiveModifierTest.hand(m,A,id);}
    static UUID evolve(ServerMatch m){UUID host=m.activePokemonView(A).pokemonId();m.evolve(A,host,card(m,"evolved").instanceId());return host;}
    public static void main(String[] args){manualAndEffect();devolveAndMove();zeroSupply();stadiumSuppression();selection();typeAndOptionalDiscard();invalidData();
        System.out.println("SpecialEnergyAttachmentTest passed: host restrictions, atomicity, field cleanup, zero supply and private selection");}
    static void manualAndEffect(){
        var m=fixture(restricted());UUID host=m.activePokemonView(A).pokemonId();var e=card(m,"special");
        var hand=m.privateView(A).hand();var rng=m.randomTranscript();
        reject(()->m.attachEnergy(A,e.instanceId(),host));
        check(hand.equals(m.privateView(A).hand())&&!m.energyAttached(A)&&rng.equals(m.randomTranscript()),"非法手动附能留下副作用");
        var basic=card(m,"energy");
        var policy=new ServerMatch.EnergyAttachmentPolicy(2,2,ServerMatch.InPlayTargetScope.OWN_FIELD,false,Set.of());
        boolean unsupported=false;
        try {m.attachEnergyByEffect(A,policy,A,EffectSourceZone.HAND,List.of(basic.instanceId(),e.instanceId()),A,host);}
        catch(IllegalArgumentException|IllegalStateException expected){unsupported=expected.getMessage().contains("尚未核验");}
        check(unsupported,"未核验的效果覆盖没有明确提示不支持");
        check(hand.equals(m.privateView(A).hand())&&m.activePokemonView(A).attachedEnergyIds().isEmpty(),"混合附能失败却先附了合法牌");
        evolve(m);m.attachEnergy(A,e.instanceId(),host);
        check(m.energyAttached(A)&&!m.privateView(A).hand().contains(e),"合法进化宿主不能手动附能");
    }
    static void devolveAndMove(){
        var m=fixture(restricted());var host=evolve(m);var e=card(m,"special");m.attachEnergy(A,e.instanceId(),host);
        m.devolvePokemonToHandByEffect(A,A,host);
        check(m.privateView(A).discard().contains(e)&&m.activePokemonView(A).attachedEnergyIds().isEmpty(),"退化后限制能量未进入所有者弃牌堆");
        var moved=fixture(restricted());var origin=evolve(moved);var energy=card(moved,"special");moved.attachEnergy(A,energy.instanceId(),origin);
        moved.moveAttachedEnergyByEffect(A,A,origin,B,moved.activePokemonView(B).pokemonId(),List.of(energy.instanceId()));
        check(moved.privateView(A).discard().contains(energy)&&!moved.privateView(B).discard().contains(energy),"移动后无效能量归属错误");
    }
    static void zeroSupply(){
        var zero=energy("{\"energyUnits\":[]}");
        check(zero.unitCount()==0&&!EnergyProfile.canPay(List.of(zero),Map.of(EnergyType.COLORLESS,1)),"零供能不能当作一份无色");
        check(!EnergyProfile.isMinimalGenericPayment(List.of(zero),0),"零撤退费允许白白弃置零能量");
        var m=fixture(zero);var host=m.activePokemonView(A).pokemonId();m.attachEnergy(A,card(m,"special").instanceId(),host);
        check(TypedEnergyCountTest.count(m,A,host,0,null,"UNITS",null)&&TypedEnergyCountTest.count(m,A,host,1,null,"CARDS",null),"零供能卡不应消失于实体计数");
    }
    static void stadiumSuppression(){
        var stadium=DynamicPassiveModifierTest.trainer("STADIUM","{"+DynamicEnergyProvisionTest.OVERRIDE+",\"target\":\"ALL_OWN_POKEMON\"}");
        var m=DynamicEnergyProvisionTest.fixture(restricted(),stadium,DynamicPassiveModifierTest.attack(20,false,false));
        var host=evolve(m);var e=card(m,"special");m.attachEnergy(A,e.instanceId(),host);
        m.playTrainer(A,card(m,"stadium").instanceId(),null);m.devolvePokemonToHandByEffect(A,A,host);
        check(!m.privateView(A).discard().contains(e),"场地关闭能量其它效果后仍执行自弃");
        check(TypedEnergyCountTest.count(m,A,host,1,"COLORLESS",null,null),"场地替代供给失效");
        m.discardStadiumByEffect(A);check(m.privateView(A).discard().contains(e),"场地离场后未清理无效能量");
        var invalid=DynamicEnergyProvisionTest.fixture(restricted(),stadium,DynamicPassiveModifierTest.attack(20,false,false));invalid.playTrainer(A,card(invalid,"stadium").instanceId(),null);
        reject(()->invalid.attachEnergy(A,card(invalid,"special").instanceId(),invalid.activePokemonView(A).pokemonId()));
    }
    static void selection(){
        // A parsed multi-step attach program must not offer a restricted card with no valid host.
        var effect=BeforeDamageAttackTest.binding(EffectTrigger.BEFORE_ATTACK,"{\"type\":\"attach_energy\",\"source\":\"HAND\",\"target\":\"OWN_ACTIVE\",\"target_scope\":\"OWN_ACTIVE\",\"amount\":2,\"minimum\":1}");
        var attack=DynamicPassiveModifierTest.attack(20,false,false).withEffectProgram(new EffectProgram(List.of(effect)));
        var m=DynamicEnergyProvisionTest.fixture(restricted(),attack);m.attack(A,"hit");var prompt=m.effectCardPrompt(A);
        check(prompt.candidates().stream().noneMatch(c->c.definitionId().equals("special")),"私有候选仍包括无法附着的限制能量");
        var basic=prompt.candidates().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
        m.submitEffectCards(A,prompt.resolutionId(),prompt.step(),List.of(basic.instanceId()));var target=m.effectAttachmentPrompt(A);
        m.chooseEffectAttachmentTarget(A,target.resolutionId(),target.step(),m.activePokemonView(A).pokemonId());
        check(m.currentPlayer().equals(B),"合法效果附能未完成攻击");
        var none=BeforeDamageAttackTest.binding(EffectTrigger.BEFORE_ATTACK,"{\"type\":\"attach_energy\",\"source\":\"HAND\",\"target\":\"OWN_ACTIVE\",\"target_scope\":\"OWN_ACTIVE\",\"definition_ids\":\"special\",\"amount\":2,\"minimum\":2}");
        var empty=DynamicEnergyProvisionTest.fixture(restricted(),DynamicPassiveModifierTest.attack(20,false,false).withEffectProgram(new EffectProgram(List.of(none))));
        empty.attack(A,"hit");check(empty.currentPlayer().equals(B),"无合法宿主时没有完成尽可能多的效果");
    }
    static void typeAndOptionalDiscard(){
        var typed=energy("{\"energyUnits\":[[\"GRASS\"]],\"attachmentRule\":{\"pokemon_types\":[\"GRASS\"],\"discard_if_invalid\":true}}");
        reject(()->fixture(typed)); // Unknown Pokémon type must fail before the match, not infer from attacks.
        var m=DynamicPassiveModifierTest.fixture(DynamicPassiveModifierTest.plainTool(),DynamicPassiveModifierTest.plainStadium(),typed,
            DynamicPassiveModifierTest.attack(20,false,false),List.of(),0,null,Map.of("basic",EnergyType.GRASS,"evolved",EnergyType.FIRE));
        var host=m.activePokemonView(A).pokemonId();var card=card(m,"special");
        check(m.canAttachEnergy(A,card.instanceId(),host),"正确属性宿主被拒绝");m.attachEnergy(A,card.instanceId(),host);evolve(m);
        check(m.privateView(A).discard().contains(card),"进化后属性不符仍保留能量");
        var withoutDiscard=energy("{\"energyUnits\":[[\"COLORLESS\"]],\"attachmentRule\":{\"stages\":[\"EVOLUTION\"]}}");
        var retained=fixture(withoutDiscard);var evolved=evolve(retained);var e=card(retained,"special");retained.attachEnergy(A,e.instanceId(),evolved);
        retained.devolvePokemonToHandByEffect(A,A,evolved);
        check(retained.activePokemonView(A).attachedEnergyIds().contains(e.instanceId()),"附着前限制被错误当成持续自弃条款");
        var conditional=energy("{\"energyUnits\":[],\"provisionRules\":[{\"conditions\":[{\"type\":\"IS_EVOLVED\"}],\"energyUnits\":[[\"WATER\"],[\"WATER\"]]}]}");
        var changing=fixture(conditional);var source=changing.activePokemonView(A).pokemonId();changing.attachEnergy(A,card(changing,"special").instanceId(),source);
        check(TypedEnergyCountTest.count(changing,A,source,0,null,null,null),"动态零供能未生效");evolve(changing);
        check(TypedEnergyCountTest.count(changing,A,source,2,"WATER",null,null),"进化后未从零供能恢复");
        changing.devolvePokemonToHandByEffect(A,A,source);
        check(TypedEnergyCountTest.count(changing,A,source,0,null,null,null)&&TypedEnergyCountTest.count(changing,A,source,1,null,"CARDS",null),"退化后零供能和实体存在混淆");
        var base=m.cardDefinition("basic");var unknown=new PokemonProfile(300,0,null);
        var catalog=new CombatCatalog(Map.of("basic",unknown),Map.of(),Map.of(),Map.of("special",typed));
        var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(new UUID(857,i+1),A,"basic"));
        var report=new DeckValidator(new CardDatabase(List.of(base)),catalog).validate(BundledRules.loadRulesets().get("standard-international-2026"),deck);
        check(report.issues().stream().noneMatch(i->i.code().equals("宝可梦属性资料缺失")),"未使用目录中的限定属性能量却阻止开局");
        deck.set(0,new CardInstance(new UUID(857,1),A,"special"));
        var restrictedReport=new DeckValidator(new CardDatabase(List.of(base,m.cardDefinition("special"))),catalog)
            .validate(BundledRules.loadRulesets().get("standard-international-2026"),deck);
        check(restrictedReport.issues().stream().anyMatch(i->i.code().equals("宝可梦属性资料缺失")),"实际含有限定属性能量时未提示资料缺失");
        var printed=new CardDefinition(base.id(),base.nameZh(),base.category(),base.trainerCategory(),base.regulationMark(),base.regions(),base.basicPokemon(),base.basicEnergy(),base.copyLimit(),base.specialDeckLimits(),base.effectSupport(),base.unsupportedReasonZh(),base.pokemonFamilies(),Set.of(EnergyType.GRASS));
        var printedReport=new DeckValidator(new CardDatabase(List.of(printed,m.cardDefinition("special"))),catalog)
            .validate(BundledRules.loadRulesets().get("standard-international-2026"),deck);
        check(printedReport.issues().stream().noneMatch(i->i.code().equals("宝可梦属性资料缺失")),"已有印刷属性仍被当作资料缺失");
    }
    static void invalidData(){
        reject(()->energy("{\"energyUnits\":[[\"COLORLESS\"]],\"attachmentRule\":{\"stages\":[\"WRONG\"],\"discard_if_invalid\":true}}"));
        reject(()->energy("{\"energyUnits\":[[\"COLORLESS\"]],\"attachmentRule\":{\"stages\":[\"BASIC\"],\"discard_if_invalid\":\"yes\"}}"));
        reject(()->energy("{\"energyUnits\":[[\"COLORLESS\"]],\"attachmentRule\":{\"stages\":[\"BASIC\"],\"typo\":true}}"));
    }
}
