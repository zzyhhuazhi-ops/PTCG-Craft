import com.google.gson.JsonParser;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.engine.*;
import java.util.*;

/** Generic field-copy contract; these synthetic cards are not audited real-card bindings. */
public final class CopyAttackTest {
    static final UUID A=new UUID(0,8401),B=new UUID(0,8402),WATCH=new UUID(0,8403);
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    static void reject(Runnable r){BeforeDamageAttackTest.reject(r);}
    static AttackDefinition copier() {
        return copier("");
    }
    static AttackDefinition copier(String extra) {
        return BundledCombatData.attack(JsonParser.parseString("""
            {"id":"copy","nameZh":"复制测试","energyCost":{"COLORLESS":1},"baseDamage":0,
             "damageType":"PSYCHIC","effectSupport":"SUPPORTED","triggers":[
              {"trigger":"ON_ATTACK","effects":[{"type":"copy_attack","target":"OPPONENT_ACTIVE",
               "cost_policy":"DECLARED_ATTACK"}]}]
            """+extra+"}").getAsJsonObject(),EffectRegistry.standard());
    }
    public static void main(String[] args) {
        AttackProgramRules.validate(copier());
        copiedMixedTargets(); costsTypeAndForgery(); phasesAndSession(); copiedPreDamageTarget(); copiedDiscardsAndBench(); sharedUsage(); confusion(); copiedCooldownNameRestriction(); copiedOpponentAttackLock(); invalidPrograms(); sourceScopes(); jsonAndDeckGate();
        System.out.println("CopyAttackTest passed: server field choices, declared cost, actual attacker, staged text and usage");
    }
    static void copiedMixedTargets() {
        for(String scope:List.of("OPPONENT_ANY","ALL_OPPONENT_POKEMON","ALL_POKEMON"))for(boolean bench:List.of(false,true)) {
            var m=fixture(selected(",\"damageTarget\":\""+scope+"\",\"targetCount\":"+(scope.equals("OPPONENT_ANY")?1:0)));
            pay(m);var c=choice(m);check(c.effectiveAttack()!=null,"copy rejected mixed targets");
            UUID target=bench?m.benchPokemonView(B).getFirst().pokemonId():m.activePokemonView(B).pokemonId();
            var before=m.clientView(A);var rng=m.randomTranscript();reject(()->copy(m,c,List.of(m.activePokemonView(A).pokemonId()),List.of()));
            check(before.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"copied target forgery mutated state");
            copy(m,c,scope.equals("OPPONENT_ANY")?List.of(target):List.of(),List.of());
            if(scope.equals("ALL_POKEMON"))check(m.activePokemonView(A).damage()==40&&m.benchPokemonView(A).getFirst().damage()==40,"both-field copy did not damage actual attacker and own Bench");
            check(m.activePokemonView(B).damage()==(!scope.equals("OPPONENT_ANY")||!bench?80:0),"copied Active weakness used wrong attacker type");
            check(m.benchPokemonView(B).getFirst().damage()==(!scope.equals("OPPONENT_ANY")||bench?40:0),"copied Bench took weakness or wrong targets");
        }
    }
    static AttackDefinition selected(String extra) {
        return BundledCombatData.attack(JsonParser.parseString("""
            {"id":"hit","nameZh":"被复制招式","energyCost":{"FIRE":4},"baseDamage":40,
             "damageType":"FIRE","effectSupport":"SUPPORTED"
            """+extra+"}").getAsJsonObject(),EffectRegistry.standard());
    }
    static ServerMatch fixture(AttackDefinition copy,AttackDefinition selected,EnergyType type,int seedStart) {
        return fixture(copy,List.of(copy,selected),selected,type,seedStart);
    }
    static ServerMatch fixture(AttackDefinition copy,List<AttackDefinition> alphaAttacks,AttackDefinition selected,EnergyType type,int seedStart) {
        var db=new CardDatabase(List.of(card("alpha",true),card("beta",true),card("energy",false)));
        var combat=new CombatCatalog(Map.of(
            "alpha",new PokemonProfile(300,0,null,EnergyType.FIRE,2,null,0,1,alphaAttacks,List.of(),type),
            "beta",new PokemonProfile(300,0,null,EnergyType.PSYCHIC,2,null,0,1,List.of(selected),List.of(),EnergyType.FIRE)),Map.of("energy",EnergyType.GRASS));
        for(int seed=seedStart;seed<seedStart+100;seed++) {
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID owner:List.of(A,B)) {var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,i%3==0?(owner.equals(A)?"alpha":"beta"):"energy"));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),db,combat,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->!c.definitionId().equals("energy")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,20);m.drawCardsByEffect(A,B,20);m.endTurn(A);m.endTurn(B);return m;
        }
        throw new AssertionError("复制攻击夹具生成失败");
    }
    static CardDefinition card(String id,boolean pokemon) {
        return new CardDefinition(id,id,pokemon?CardCategory.POKEMON:CardCategory.ENERGY,TrainerCategory.NONE,"H",Set.of("international"),pokemon,!pokemon,60,Map.of(),EffectSupport.SUPPORTED,null);
    }
    static ServerMatch fixture(AttackDefinition attack){return fixture(copier(),attack,EnergyType.PSYCHIC,0);}
    static void pay(ServerMatch m){m.attachEnergy(A,DynamicPassiveModifierTest.hand(m,A,"energy").instanceId(),m.activePokemonView(A).pokemonId());}
    static ServerMatch.CopyAttackOption choice(ServerMatch m){return m.copyAttackOptions(A,"copy").stream().filter(c->c.attackId().equals("hit")).findFirst().orElseThrow();}
    static void copy(ServerMatch m,ServerMatch.CopyAttackOption c,List<UUID> targets,List<UUID> energy){m.copyAttackWithEnergyDiscards(A,"copy",c.pokemonId(),c.entrySequence(),c.attackId(),targets,energy);}
    static void costsTypeAndForgery() {
        var m=fixture(selected(""));var c=choice(m);var view=m.clientView(A);var rng=m.randomTranscript();
        reject(()->copy(m,c,List.of(),List.of()));check(view.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"费用不足仍复制攻击");
        pay(m);view=m.clientView(A);rng=m.randomTranscript();
        reject(()->m.attack(A,"copy"));
        reject(()->m.copyAttackWithEnergyDiscards(A,"copy",c.pokemonId(),c.entrySequence()+1,"hit",List.of(),List.of()));
        reject(()->m.copyAttackWithEnergyDiscards(A,"copy",m.benchPokemonView(B).getFirst().pokemonId(),c.entrySequence(),"hit",List.of(),List.of()));
        reject(()->m.copyAttackWithEnergyDiscards(A,"copy",c.pokemonId(),c.entrySequence(),"forged",List.of(),List.of()));
        check(view.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"伪造选择改变场面或随机数");
        copy(m,c,List.of(),List.of());
        check(m.activePokemonView(B).damage()==80&&m.activePokemonView(A).damage()==0,"复制没有按实际攻击者超属性计算弱点");
        check(m.currentPlayer().equals(B)&&m.lastAttack().attacker().equals(A),"复制没有归属实际攻击者／结束一次回合");
    }
    static void phasesAndSession() {
        var m=fixture(selected("""
            ,"triggers":[{"trigger":"BEFORE_ATTACK","effects":[{"type":"search_deck","target":"DECK","amount":1,"minimum":1}]},
              {"trigger":"AFTER_ATTACK","effects":[{"type":"damage_counter","target":"SELF","amount":2},
                {"type":"choice","effects":[{"type":"draw","amount":1},{"type":"draw","amount":2}]}]}]
            """));pay(m);var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);var v=session.view(A,A);
        var option=v.options().stream().filter(o->o.labelZh().startsWith("复制攻击：")).findFirst().orElseThrow();
        check(dev.hua.ptcglive.view.MatchActionMatcher.isAttackOption(option),"复制攻击未接入对方战斗位拖拽筛选");
        check(dev.hua.ptcglive.view.MatchActionMatcher.forCards(v,List.of("field:"+m.activePokemonView(A).pokemonId(),
            "field:"+m.activePokemonView(B).pokemonId())).contains(option),"拖拽双方战斗卡面无法找到复制招式");
        reject(()->session.submit(B,A,v.revision(),option.index()));reject(()->session.submit(WATCH,A,v.revision(),option.index()));
        session.submit(A,A,v.revision(),option.index());reject(()->session.submit(A,A,v.revision(),option.index()));
        check(m.activePokemonView(B).damage()==0&&m.effectCardPrompt(A)!=null,"复制没有进入伤害前选择");
        var rng=m.randomTranscript();
        for(UUID viewer:List.of(B,WATCH)) {
            var dto=session.view(viewer,viewer.equals(WATCH)?null:viewer);var json=new com.google.gson.Gson().toJson(dto);
            check(dto.game().effectSelection()==null,"复制选牌泄露给他人");
            for(var card:m.privateView(A).deck())check(!json.contains(card.instanceId().toString()),"复制消息泄露牌库顺序");
            check(!json.contains("effectProgram")&&!json.contains("effectiveAttack"),"可执行效果外发给客户端");
        }
        check(rng.equals(m.randomTranscript()),"刷新复制候选改变随机数");
        BeforeDamageAttackTest.selectFirst(m,A);
        check(m.activePokemonView(B).damage()==80&&m.activePokemonView(A).damage()==20&&m.effectChoicePrompt(A)!=null,"SELF错误指向被复制宝可梦");
        var prompt=m.effectChoicePrompt(A);int turn=m.turnNumber();m.chooseEffectOption(A,prompt.resolutionId(),prompt.step(),0);
        check(m.currentPlayer().equals(B)&&m.turnNumber()==turn+1&&m.activePokemonView(B).damage()==80,"复制续接重复伤害或回合");
    }
    static void copiedDiscardsAndBench() {
        var m=fixture(selected(",\"discardSelectedAttachedEnergyAfterAttack\":1,\"damageTarget\":\"OPPONENT_BENCH\""));pay(m);var c=choice(m);
        var energy=m.activePokemonView(A).attachedEnergyIds().getFirst();var target=m.benchPokemonView(B).getFirst().pokemonId();
        var view=m.clientView(A);reject(()->copy(m,c,List.of(target),List.of(new UUID(1,2))));check(view.equals(m.clientView(A)),"非法弃能仍伤害");
        copy(m,c,List.of(target),List.of(energy));
        check(m.benchPokemonView(B).getFirst().damage()==40&&m.activePokemonView(B).damage()==0,"复制备战伤害套用了弱点或打错目标");
        check(m.activePokemonView(A).attachedEnergyIds().isEmpty()&&m.privateView(A).discard().stream().anyMatch(card->card.instanceId().equals(energy)),"复制弃能没有作用于实际攻击者");
    }
    /** The copied program, not the copier, owns its separate declared BEFORE_ATTACK target. */
    static void copiedPreDamageTarget() {
        var selected=selected("""
            ,"triggers":[{"trigger":"BEFORE_ATTACK","effects":[{"type":"heal","target":"SELECT_OWN_POKEMON","amount":10}]}]
            """);
        var m=fixture(selected);pay(m);var choice=choice(m);UUID bench=m.benchPokemonView(A).getFirst().pokemonId();
        m.placeDamageCountersByEffect(A,A,bench,2);var view=m.clientView(A);var rng=m.randomTranscript();
        reject(()->copy(m,choice,List.of(),List.of()));
        reject(()->m.copyAttackWithSelectedEffectTargetAndEnergyDiscards(A,"copy",choice.pokemonId(),choice.entrySequence(),choice.attackId(),
            m.activePokemonView(B).pokemonId(),List.of(),List.of()));
        check(view.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"复制攻击的非法伤害前预选目标改变了场面或随机数");
        m.copyAttackWithSelectedEffectTargetAndEnergyDiscards(A,"copy",choice.pokemonId(),choice.entrySequence(),choice.attackId(),bench,List.of(),List.of());
        check(m.benchPokemonView(A).getFirst().damage()==10&&m.activePokemonView(B).damage()==80,
            "复制攻击没有使用被复制招式的伤害前预选目标或伤害阶段");
    }
    static void sharedUsage() {
        var gx=new SharedUsageLimit(SharedUsageLimit.GX,SharedUsageLimit.Period.GAME);
        String usage=",\"usageLimit\":{\"group\":\"gx-attack\",\"period\":\"GAME\"}";
        var m=fixture(copier(usage),selected(usage),EnergyType.PSYCHIC,0);pay(m);copy(m,choice(m),List.of(),List.of());
        check(m.sharedUsageExhausted(A,gx)&&!m.sharedUsageExhausted(B,gx),"复制消耗对手或漏掉自己的共享资格");
        m.endTurn(B);var before=m.clientView(A);reject(()->copy(m,choice(m),List.of(),List.of()));check(before.equals(m.clientView(A)),"复制绕过整局共享资格");
    }
    static void confusion() {
        boolean success=false,failure=false;var gx=new SharedUsageLimit(SharedUsageLimit.GX,SharedUsageLimit.Period.GAME);
        for(int seed=0;seed<30&&!(success&&failure);seed++) {
            var m=fixture(copier(),selected(",\"usageLimit\":{\"group\":\"gx-attack\",\"period\":\"GAME\"}"),EnergyType.PSYCHIC,seed);pay(m);
            m.applySpecialConditionByEffect(A,A,SpecialCondition.CONFUSED);
            copy(m,choice(m),List.of(),List.of());
            if(m.activePokemonView(A).damage()==30) {failure=true;check(m.activePokemonView(B).damage()==0&&!m.sharedUsageExhausted(A,gx),"混乱失败仍执行或消耗复制招式");}
            else {success=true;check(m.activePokemonView(B).damage()==80&&m.sharedUsageExhausted(A,gx),"混乱成功重复检查或漏结算");}
        }
        check(success&&failure,"未覆盖混乱两面");
    }
    /** A copied move's “cannot use this attack” clause keys the printed name, including a different local id. */
    static void copiedCooldownNameRestriction() {
        var cooldown=selected(",\"cannotUseSameAttackDuringNextTurn\":true");
        var alias=new AttackDefinition("same-name-alias",cooldown.nameZh(),Map.of(EnergyType.COLORLESS,1),10,
            cooldown.damageType(),cooldown.effectSupport(),cooldown.unsupportedReasonZh(),List.of(),null,null,List.of(),
            AttackDefinition.DamageTarget.OPPONENT_ACTIVE,0,1,false,0,0,false,0,false,false,EffectProgram.EMPTY,null);
        var m=fixture(copier(),List.of(copier(),cooldown,alias),cooldown,EnergyType.PSYCHIC,0);
        pay(m);copy(m,choice(m),List.of(),List.of());m.endTurn(B);
        check(m.activeAttacks(A).stream().noneMatch(attack->attack.nameZh().equals(cooldown.nameZh())),
            "复制后的同名招式冷却只锁定了内部编号");
        check(m.activeAttacks(A).stream().anyMatch(attack->attack.id().equals("copy")),
            "同名冷却错误禁止了复制招式本身");
        check(m.copyAttackOptions(A,"copy").stream().filter(option->option.attackId().equals(cooldown.id()))
            .allMatch(option->option.unsupportedReasonZh()!=null),"复制入口绕过了同名招式冷却");
    }
    static void copiedOpponentAttackLock() {
        var m=fixture(selected("""
            ,"triggers":[{"trigger":"AFTER_ATTACK","effects":[{"type":"lock_opponent_attack","target":"OPPONENT_ACTIVE"}]}]
            """));
        pay(m);
        var choice=choice(m);
        var before=m.clientView(A);var rng=m.randomTranscript();
        reject(()->m.copyAttackWithSelectedEffectTargetAndEnergyDiscards(A,"copy",choice.pokemonId(),choice.entrySequence(),
            choice.attackId(),null,List.of(),List.of(),"forged"));
        check(before.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"伪造复制锁招选择改变状态或随机数");
        var session=MatchSession.multiplayer(m,A,B);
        var view=session.view(A,A);
        var option=view.options().stream().filter(o->o.labelZh().startsWith("复制攻击：")&&o.labelZh().contains("锁定对方招式："))
            .findFirst().orElseThrow(()->new AssertionError("复制锁招攻击没有可提交的锁定选择"));
        session.submit(A,A,view.revision(),option.index());
        check(m.activePokemonView(B).damage()==80,"复制锁招攻击未完成伤害结算");
        check(m.currentPlayer().equals(B)&&m.activeAttacks(B).isEmpty(),"复制锁招攻击没有锁定对方招式");
        reject(()->session.submit(A,A,view.revision(),option.index()));
    }
    static void invalidPrograms() {
        var unknown=fixture(copier(),selected(""),null,0);check(choice(unknown).unsupportedReasonZh().contains("pokemonType"),"缺失属性仍猜测复制类型");
        var nested=fixture(copier(),selected("").withEffectProgram(new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.AFTER_ATTACK,"{\"type\":\"copy_attack\",\"target\":\"OPPONENT_ACTIVE\",\"cost_policy\":\"DECLARED_ATTACK\"}")))),EnergyType.PSYCHIC,0);
        check(nested.copyAttackOptions(A,"copy").getFirst().unsupportedReasonZh()!=null,"嵌套复制被静默允许");
        reject(()->AttackProgramRules.validate(copier().withEffectProgram(new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_ATTACK,"{\"type\":\"copy_attack\",\"target\":\"OPPONENT_ACTIVE\",\"cost_policy\":\"DECLARED_ATTACK\",\"filter\":\"fusion_strike\"}"))))));
        var cooldown=fixture(selected(",\"cannotUseSameAttackDuringNextTurn\":true"));check(choice(cooldown).unsupportedReasonZh()==null,"可结算的同名招式冷却被错误拒绝");
        var invalid=fixture(selected("""
            ,"triggers":[{"trigger":"BEFORE_ATTACK","effects":[{"type":"draw","amount":1}]},
              {"trigger":"AFTER_ATTACK","effects":[{"type":"heal","target":"SELF","amount":11}]}]
            """));pay(invalid);var before=invalid.clientView(A);var rng=invalid.randomTranscript();
        reject(()->copy(invalid,choice(invalid),List.of(),List.of()));
        check(before.equals(invalid.clientView(A))&&rng.equals(invalid.randomTranscript()),"复制未支持后阶段仍执行前缀");
    }
    static void sourceScopes() {
        var own=copier().withEffectProgram(new EffectProgram(List.of(BeforeDamageAttackTest.binding(EffectTrigger.ON_ATTACK,"{\"type\":\"copy_attack\",\"target\":\"OWN_BENCH\",\"cost_policy\":\"DECLARED_ATTACK\"}"))));
        var m=fixture(own,selected(""),EnergyType.PSYCHIC,0);pay(m);var choices=m.copyAttackOptions(A,"copy");
        check(choices.stream().allMatch(c->c.owner().equals(A)&&!c.pokemonId().equals(m.activePokemonView(A).pokemonId())),"OWN_BENCH越界");
        check(choices.stream().filter(c->c.attackId().equals("copy")).allMatch(c->c.unsupportedReasonZh()!=null),"循环复制未拒绝");
        copy(m,choice(m),List.of(),List.of());check(m.activePokemonView(B).damage()==80,"复制自己的备战招式没有攻击对方");
    }
    static void jsonAndDeckGate() {
        var json=JsonParser.parseString("""
            {"hitPoints":100,"retreatCost":1,"prizeCards":1,"pokemonType":"PSYCHIC","attacks":[
              {"id":"copy","nameZh":"复制","energyCost":{"COLORLESS":1},"baseDamage":0,"effectSupport":"SUPPORTED",
               "triggers":[{"trigger":"ON_ATTACK","effects":[{"type":"copy_attack","target":"OPPONENT_ACTIVE","cost_policy":"DECLARED_ATTACK"}]}]}]}
            """).getAsJsonObject();
        var loaded=BundledCombatData.pokemon(json,EffectRegistry.standard());check(loaded.pokemonType()==EnergyType.PSYCHIC,"JSON属性未载入");
        var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(new UUID(84,i+1),A,"alpha"));
        var rules=BundledRules.loadRulesets().get("standard-international-2026");var db=new CardDatabase(List.of(card("alpha",true)));
        var valid=new DeckValidator(db,new CombatCatalog(Map.of("alpha",loaded),Map.of())).validate(rules,deck);
        check(valid.issues().stream().noneMatch(i->i.code().equals("宝可梦属性资料缺失")),"完整复制属性被卡组检查器拒绝");
        json.remove("pokemonType");var unknown=BundledCombatData.pokemon(json,EffectRegistry.standard());check(unknown.pokemonType()==null,"旧数据静默猜测属性");
        var invalid=new DeckValidator(db,new CombatCatalog(Map.of("alpha",unknown),Map.of())).validate(rules,deck);
        check(invalid.issues().stream().anyMatch(i->i.code().equals("宝可梦属性资料缺失")),"缺复制属性仍允许正式卡组开局");
        json.addProperty("pokemonType","FAKE_TYPE");reject(()->BundledCombatData.pokemon(json,EffectRegistry.standard()));
    }
}
