package dev.hua.ptcglive.gametest;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import dev.hua.ptcglive.PtcgServices;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.network.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.view.EffectChoicePage;
import io.netty.buffer.Unpooled;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import java.util.*;

/** Real Fabric server/packet-codec regression; these are synthetic cards, not audited real bindings. */
public final class PersistentSourceIntegrationTest implements FabricGameTest {
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void toolAutomaticPackets(TestContext context) { exercise(context, "TOOL"); }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void energyAutomaticPackets(TestContext context) { exercise(context, "ENERGY"); }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void stadiumAutomaticPackets(TestContext context) { exercise(context, "STADIUM"); }
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void toolCapacityCleanupPackets(TestContext context) {exercise(context,"CAPACITY");}
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void dynamicHitPointsPackets(TestContext context) {exercise(context,"HP");}
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void departureContinuationPackets(TestContext context) {exercise(context,"LEAVE");}
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void beforeDamageAttackPackets(TestContext context) {exercise(context,"BEFORE");}
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void copyAttackPackets(TestContext context) {exercise(context,"COPY");}
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void typedEnergyCountPackets(TestContext context) {exercise(context,"TYPED");}
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void specialEnergyAttachmentPackets(TestContext context) {exercise(context,"RESTRICTED");}
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void turnEndEffectPackets(TestContext context) {exercise(context,"END");}
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void delayedPokemonEffectPackets(TestContext context) {exercise(context,"DELAYED");}
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void attackCoinBindingPackets(TestContext context) {exercise(context,"COINS");}
    @GameTest(templateName = EMPTY_STRUCTURE)
    public void attackPokemonBindingPackets(TestContext context) {exercise(context,"NAMED");}

    private void exercise(TestContext context, String kind) {
        UUID a=UUID.randomUUID(), b=UUID.randomUUID(), watcher=UUID.randomUUID();
        var registry=EffectRegistry.standard();
        String policy=switch(kind) {
            case "END" -> "{\"trigger\":\"ON_TURN_END\",\"turn\":\"OWN\",\"optional\":true,\"usage\":\"NONE\"}";
            case "TOOL" -> "{\"trigger\":\"ON_DAMAGE\",\"from\":\"OPPONENT_ATTACK\",\"activeOnly\":true,\"lethalOnly\":false,\"optional\":true,\"usage\":\"NONE\"}";
            case "ENERGY" -> "{\"trigger\":\"ON_ENERGY_ATTACHED\",\"target\":\"SELF\",\"source\":\"HAND\",\"cause\":\"MANUAL\",\"optional\":true,\"usage\":\"NONE\"}";
            default -> "{\"trigger\":\"ON_POKEMON_CHECKUP\",\"optional\":true,\"usage\":\"NONE\"}";
        };
        String automatic="{\"id\":\"card-test\",\"nameZh\":\"自动来源协议验证\",\"eventPolicy\":"+policy
            +(kind.equals("ENERGY")?",\"sourceCardOnly\":true":"")
            +",\"effects\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"choice\",\"effects\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}]}";
        if(kind.equals("END"))automatic=automatic.replace("{\"type\":\"draw\",\"amount\":2}","{\"type\":\"discard_source\"}");
        var tool=BundledCombatData.trainer(JsonParser.parseString(kind.equals("TOOL")
            ? "{\"trainerCategory\":\"TOOL\",\"automaticEffects\":["+automatic+"]}"
            : kind.equals("HP") ? "{\"trainerCategory\":\"TOOL\",\"effects\":[{\"type\":\"hit_points_modifier\",\"amount\":100}]}"
            : kind.equals("CAPACITY") ? "{\"trainerCategory\":\"TOOL\",\"effects\":[{\"type\":\"tool_capacity\",\"amount\":2,\"conditions\":[{\"type\":\"HP_ABOVE\",\"value\":290}]}]}"
            : "{\"trainerCategory\":\"TOOL\",\"effects\":[{\"type\":\"retreat_cost_modifier\",\"amount\":-1}]}").getAsJsonObject(),registry);
        var stadium=BundledCombatData.trainer(JsonParser.parseString(kind.equals("STADIUM")
            ? "{\"trainerCategory\":\"STADIUM\",\"automaticEffects\":["+automatic+"]}"
            : Set.of("TYPED","RESTRICTED").contains(kind) ? "{\"trainerCategory\":\"STADIUM\",\"effects\":[{\"type\":\"special_energy_override\",\"target\":\"ALL_OWN_POKEMON\",\"energy_type\":\"COLORLESS\",\"amount\":1,\"suppress_other_effects\":true}]}"
            : kind.equals("HP") ? "{\"trainerCategory\":\"STADIUM\",\"effects\":[{\"type\":\"hit_points_modifier\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":-100}]}"
            : "{\"trainerCategory\":\"STADIUM\",\"effects\":[{\"type\":\"retreat_cost_modifier\",\"target\":\"ALL_OWN_POKEMON\",\"amount\":-1}]}").getAsJsonObject(),registry);
        var energy=BundledCombatData.specialEnergy(JsonParser.parseString((kind.equals("TYPED")?"{\"energyUnits\":[[\"WATER\",\"FIGHTING\"],[\"WATER\",\"FIGHTING\"]]":"{\"energyUnits\":[[\"COLORLESS\"]]")
            +(kind.equals("RESTRICTED")?",\"attachmentRule\":{\"stages\":[\"EVOLUTION\"],\"discard_if_invalid\":true}":"")
            +(Set.of("ENERGY","END").contains(kind)?",\"automaticEffects\":["+automatic+"]":"")+"}").getAsJsonObject(),registry);
        var attack=BundledCombatData.attack(JsonParser.parseString("{\"id\":\"hit\",\"nameZh\":\"协议测试攻击\",\"energyCost\":{},\"baseDamage\":"+(kind.equals("HP")?320:40)+",\"effectSupport\":\"SUPPORTED\""
            +(kind.equals("NAMED")?"""
                ,"triggers":[
                  {"trigger":"BEFORE_ATTACK","effects":[{"type":"select_pokemon","target":"SELECT_OPPONENT_POKEMON","position":"BENCH","bind":"victim",
                    "effects":[{"type":"damage_counter","target":"CHOSEN_POKEMON","amount":2}]}]},
                  {"trigger":"ON_ATTACK","effects":[{"type":"with_pokemon","ref":"victim","effects":[
                    {"type":"damage_per_damage_counter","target":"CHOSEN_POKEMON","amount":10}]}]},
                  {"trigger":"AFTER_ATTACK","effects":[{"type":"search_deck","target":"DECK","amount":1,"minimum":1},
                    {"type":"with_pokemon","ref":"victim","effects":[{"type":"damage_counter","target":"CHOSEN_POKEMON","amount":3}]},
                    {"type":"draw","amount":1}]}]
                """:"")
            +(kind.equals("COINS")?"""
                ,"triggers":[
                  {"trigger":"BEFORE_ATTACK","effects":[{"type":"coin_flip","flips":1,"bind":"punch","effects":[]},
                    {"type":"draw","amount":1},{"type":"search_deck","target":"DECK","amount":1,"minimum":1}]},
                  {"trigger":"ON_ATTACK","effects":[{"type":"conditional","conditions":[{"type":"COIN_RESULT","coin":"punch","value":"HEADS"}],
                    "effects":[{"type":"damage","target":"OPPONENT_ACTIVE","amount":20}]}]},
                  {"trigger":"AFTER_ATTACK","effects":[{"type":"choice","effects":[{"type":"draw","amount":1},{"type":"draw","amount":2}]},
                    {"type":"conditional","conditions":[{"type":"COIN_RESULT","coin":"punch","value":"HEADS"}],"effects":[{"type":"confuse","target":"OPPONENT_ACTIVE"}]}]}]
                """:"")
            +(kind.equals("DELAYED")?"""
                ,"triggers":[{"trigger":"AFTER_ATTACK","effects":[{"type":"repeat","count":2,"effects":[
                  {"type":"delayed_effect","target":"OPPONENT_ACTIVE","when":"OPPONENT_NEXT_TURN_END","clear":"BENCH_EVOLVE_LEAVE","effects":[
                    {"type":"draw","amount":1},{"type":"choice","effects":[{"type":"damage_counter","target":"TRIGGER_POKEMON","amount":3},{"type":"draw","amount":1}]}]}]}]}]
                """:"")
            +(kind.equals("TYPED")?",\"effects\":[{\"type\":\"damage_per_energy\",\"target\":\"ALL_OWN_POKEMON\",\"energy_type\":\"WATER\",\"amount\":30}]":"")
            +(Set.of("BEFORE","COPY").contains(kind)?"""
              ,"triggers":[
                {"trigger":"BEFORE_ATTACK","effects":[{"type":"draw","amount":1},{"type":"search_deck","target":"DECK","amount":1,"minimum":1}]},
                {"trigger":"AFTER_ATTACK","effects":[{"type":"choice","effects":[{"type":"draw","amount":1},{"type":"draw","amount":2}]}]}]
              """:"")+"}").getAsJsonObject(),registry);
        var definitions=new CardDatabase(List.of(
            card("evolved",CardCategory.POKEMON,TrainerCategory.NONE,false),
            card("basic",CardCategory.POKEMON,TrainerCategory.NONE,true),card("tool",CardCategory.TRAINER,TrainerCategory.TOOL,false),
            card("stadium",CardCategory.TRAINER,TrainerCategory.STADIUM,false),card("energy",CardCategory.ENERGY,TrainerCategory.NONE,false)));
        var leave=BundledCombatData.ability(JsonParser.parseString("""
            {"id":"leave","nameZh":"离场续接协议测试","effectSupport":"SUPPORTED","effects":[
              {"type":"return_to_hand","target":"SELF"},
              {"type":"search_deck","target":"DECK","amount":1,"minimum":1},
              {"type":"draw","amount":1}
            ]}
            """).getAsJsonObject(),registry);
        var attacks=new ArrayList<AttackDefinition>();attacks.add(attack);
        if(kind.equals("COPY"))attacks.add(BundledCombatData.attack(JsonParser.parseString("""
            {"id":"copy","nameZh":"复制协议测试","energyCost":{"COLORLESS":1},"baseDamage":0,"effectSupport":"SUPPORTED",
             "triggers":[{"trigger":"ON_ATTACK","effects":[{"type":"copy_attack","target":"OPPONENT_ACTIVE","cost_policy":"DECLARED_ATTACK"}]}]}
            """).getAsJsonObject(),registry));
        var profiles=new HashMap<String,PokemonProfile>();
        profiles.put("basic",new PokemonProfile(300,0,null,kind.equals("COPY")?EnergyType.PSYCHIC:null,
            kind.equals("COPY")?2:0,null,0,1,attacks,kind.equals("LEAVE")?List.of(leave):List.of(),kind.equals("COPY")?EnergyType.PSYCHIC:null));
        if(kind.equals("RESTRICTED"))profiles.put("evolved",new PokemonProfile(350,0,"basic",null,0,null,0,1,attacks));
        var combat=new CombatCatalog(profiles,Map.of(),Map.of("tool",tool,"stadium",stadium),Map.of("energy",energy));
        var decks=new ArrayList<List<CardInstance>>();
        for(UUID owner:List.of(a,b)) {var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(UUID.randomUUID(),owner,kind.equals("RESTRICTED")&&i>=10&&i<20?"evolved":i<30?"basic":i<40?"tool":i<50?"stadium":"energy"));decks.add(deck);}
        ServerMatch selected=null;
        for(int seed=0;seed<1000;seed++) {
            var m=new ServerMatch(a,b,decks.get(0),decks.get(1),PtcgServices.rulesets().get("standard-international-2026"),definitions,combat,new ServerRandom(seed));
            m.callCoin(a,CoinSide.HEADS);UUID winner=m.publicCoinResult()==CoinSide.HEADS?a:b;m.chooseWhoGoesFirst(winner,winner.equals(a));
            if(List.of(a,b).stream().allMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).count()>=2)) {selected=m;break;}
        }
        context.assertTrue(selected!=null,"无法生成自动来源夹具");var m=selected;
        for(UUID owner:List.of(a,b)) {var basics=m.privateView(owner).hand().stream().filter(c->c.definitionId().equals("basic")).toList();m.chooseOpeningActive(owner,basics.getFirst().instanceId());m.placeOpeningBench(owner,basics.get(1).instanceId());}
        var session=MatchSession.multiplayer(m,a,b);session.addSpectator(watcher);
        int setup=10;
        while(m.phase()!=MatchPhase.TURN&&setup-->0)for(UUID owner:List.of(a,b)) {
            var snapshot=session.view(owner,owner);var confirm=snapshot.options().stream().filter(o->o.labelZh().equals("确认开局设置")).findFirst();
            if(confirm.isPresent())session.submit(owner,owner,snapshot.revision(),confirm.get().index());
        }
        context.assertTrue(m.phase()==MatchPhase.TURN,"开局未进入回合");
        m.drawCardsByEffect(a,a,25);m.endTurn(a);m.endTurn(b);
        if(kind.equals("LEAVE")) {departure(context,m,session,a,b,watcher);return;}
        if(Set.of("BEFORE","COPY","COINS").contains(kind)) {beforeDamage(context,m,session,a,b,watcher,kind.equals("COPY"),kind.equals("COINS"));return;}
        if(kind.equals("TYPED")) {typedEnergy(context,m,session,a,b,watcher);return;}
        if(kind.equals("RESTRICTED")) {restrictedEnergy(context,m,session,a,b,watcher);return;}
        if(kind.equals("END")) {turnEnd(context,m,session,a,b,watcher);return;}
        if(kind.equals("DELAYED")) {delayed(context,m,session,a,b,watcher);return;}
        if(kind.equals("NAMED")) {namedPokemon(context,m,session,a,b,watcher);return;}
        var source=m.privateView(a).hand().stream().filter(c->c.definitionId().equals(Set.of("CAPACITY","HP").contains(kind)?"tool":kind.toLowerCase(Locale.ROOT))).findFirst().orElseThrow();
        UUID host=m.activePokemonView(a).pokemonId();var wire=new RegistryByteBuf(Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            var snapshot=session.view(a,a);var play=snapshot.options().stream().filter(o->o.cardKeys().contains("hand:"+source.instanceId())
                &&(kind.equals("STADIUM")||o.cardKeys().contains("field:"+host))).findFirst().orElseThrow();
            int before=m.privateView(a).hand().size();submit(context,wire,session,a,b,play.index());
            if(kind.equals("HP")) {
                submitLabel(context,wire,session,a,b,"结束回合");submitLabel(context,wire,session,b,a,"攻击：");
                context.assertTrue(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(a)&&m.activePokemonView(a).damage()==320,"动态 HP 未阻止错误提前击倒");
                var rng=m.randomTranscript();
                for(UUID viewer:List.of(a,b,watcher)) {
                    wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(viewer,viewer.equals(watcher)?null:viewer)),"动态 HP"));
                    String json=MatchStatePayload.CODEC.decode(wire).snapshotJson();var received=new Gson().fromJson(json,MatchSession.Snapshot.class);
                    var publicHost=received.game().players().stream().filter(p->p.id().equals(a)).findFirst().orElseThrow().active();
                    context.assertTrue(publicHost.hitPoints()==400&&publicHost.damage()==320,"双方或观战收到印刷 HP");
                    for(UUID owner:List.of(a,b)) {
                        var hidden=new ArrayList<CardInstance>(m.privateView(owner).deck());hidden.addAll(m.privateView(owner).prizes());if(!owner.equals(viewer))hidden.addAll(m.privateView(owner).hand());
                        for(var card:hidden)context.assertTrue(!json.contains(card.instanceId().toString()),"HP 同步泄露隐藏牌");
                    }
                }
                context.assertTrue(rng.equals(m.randomTranscript()),"HP 查询改变随机数");
                var stadiumCard=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("stadium")).findFirst().orElseThrow();
                var view=session.view(a,a);int turn=m.turnNumber();
                submit(context,wire,session,a,b,view.options().stream().filter(o->o.cardKeys().contains("hand:"+stadiumCard.instanceId())).findFirst().orElseThrow().index());
                context.assertTrue(m.phase()==MatchPhase.PRIZE_SELECTION&&m.pendingChoicePlayer().equals(b),"HP 下降未进入拿奖");
                submitLabel(context,wire,session,b,a,"拿取背面朝上的奖赏卡：");
                var replacement=session.view(a,a);submit(context,wire,session,a,b,replacement.options().stream().filter(o->o.cardKeys().stream().anyMatch(k->k.startsWith("field:"))).findFirst().orElseThrow().index());
                context.assertTrue(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(a)&&m.turnNumber()==turn,"非攻击 HP 击倒错误结束回合");
                context.complete();return;
            }
            if(kind.equals("CAPACITY")) {
                var second=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("tool")).findFirst().orElseThrow();
                var view=session.view(a,a);var attach=view.options().stream().filter(o->o.cardKeys().contains("hand:"+second.instanceId())&&o.cardKeys().contains("field:"+host)).findFirst().orElseThrow();
                submit(context,wire,session,a,b,attach.index());submitLabel(context,wire,session,a,b,"结束回合");int turn=m.turnNumber();
                submitLabel(context,wire,session,b,a,"攻击：");var prompt=m.toolOverflowPrompt();
                context.assertTrue(prompt!=null&&prompt.owner().equals(a)&&m.currentPlayer().equals(b),"攻击后没有暂停在受伤方工具选择");
                var rng=m.randomTranscript();
                for(UUID viewer:List.of(a,b,watcher)) {
                    wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(viewer,viewer.equals(watcher)?null:viewer)),"超量工具选择"));
                    String json=MatchStatePayload.CODEC.decode(wire).snapshotJson();var received=new Gson().fromJson(json,MatchSession.Snapshot.class);
                    context.assertTrue(prompt.equals(received.game().toolOverflow())&&received.game().choicePlayer().equals(a),"公开容量提示或拥有者丢失");
                    var page=dev.hua.ptcglive.view.EffectCardPage.from(received,0);
                    context.assertTrue(page.cards().size()==2&&page.cards().stream().allMatch(c->c.key().startsWith("tool-overflow:")),"卡图选择区域缺失");
                    if(!viewer.equals(a))context.assertTrue(received.options().stream().noneMatch(o->o.labelZh().startsWith("弃置超量工具：")),"非拥有者获得选择权限");
                    for(UUID owner:List.of(a,b)) {
                        var hidden=new ArrayList<CardInstance>(m.privateView(owner).deck());hidden.addAll(m.privateView(owner).prizes());if(!owner.equals(viewer))hidden.addAll(m.privateView(owner).hand());
                        for(var card:hidden)context.assertTrue(!json.contains(card.instanceId().toString()),"工具清理包泄露隐藏牌");
                    }
                }
                context.assertTrue(rng.equals(m.randomTranscript()),"刷新清理提示消耗随机");
                submitLabel(context,wire,session,a,b,"弃置超量工具：");
                context.assertTrue(m.toolOverflowPrompt()==null&&m.activePokemonView(a).attachedTools().size()==1&&m.privateView(a).discard().contains(source),"实体工具没有正确弃置");
                context.assertTrue(m.currentPlayer().equals(a)&&m.turnNumber()==turn+1,"清理后攻击回合未恰好完成一次");
                context.complete();return;
            }
            if(kind.equals("TOOL")) {
                submitLabel(context,wire,session,a,b,"结束回合");submitLabel(context,wire,session,b,a,"攻击：");
            } else if(kind.equals("STADIUM"))submitLabel(context,wire,session,a,b,"结束回合");
            int ordering=10;
            while(m.triggerOrderPrompt()!=null&&!m.triggerOrderPrompt().optionalDecision()&&ordering-->0) {
                UUID chooser=m.triggerOrderPrompt().choosingPlayer();var view=session.view(chooser,chooser);
                var option=view.options().stream().filter(o->o.labelZh().startsWith("优先结算触发：")&&o.labelZh().contains("自动来源协议验证")).findFirst().orElseThrow();
                submit(context,wire,session,chooser,chooser.equals(a)?b:a,option.index());
            }
            context.assertTrue(m.triggerOrderPrompt()!=null&&m.triggerOrderPrompt().optionalDecision()&&m.triggerOrderPrompt().choosingPlayer().equals(a),"可选效果未交还卡牌拥有者");
            var view=session.view(a,a);var page=EffectChoicePage.from(view,0);
            context.assertTrue(page.options().size()==2&&page.options().getFirst().labelZh().startsWith("使用触发效果："),"卡桌漏显示非特性的确认选项");
            var publicSource=m.triggerOrderPrompt().entries().getFirst();
            context.assertTrue(publicSource.sourceCardId().equals(source.instanceId())&&publicSource.sourceKind()==(kind.equals("ENERGY")?EffectOrigin.Kind.ENERGY:EffectOrigin.Kind.TRAINER),"公开来源被冒充为宝可梦特性");
            submitLabel(context,wire,session,a,b,"使用触发效果：");
            context.assertTrue(m.hasPendingEffectChoice()&&m.pendingChoicePlayer().equals(a)&&m.privateView(a).hand().size()==before,"多步选择前缀执行次数错误");
            var rng=m.randomTranscript();
            for(UUID viewer:List.of(a,b,watcher)) {
                wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(viewer,viewer.equals(watcher)?null:viewer)),"自动来源选择"));
                String json=MatchStatePayload.CODEC.decode(wire).snapshotJson();var decoded=new Gson().fromJson(json,MatchSession.Snapshot.class);
                context.assertTrue(decoded.game().resolvingAbility().sourceKind()==publicSource.sourceKind(),"选择续接状态丢失来源类别");
                context.assertTrue(!json.contains("automaticEffects")&&!json.contains("effectProgram"),"服务端规则程序进入公开包");
                if(!viewer.equals(a))context.assertTrue(decoded.options().stream().noneMatch(o->o.labelZh().startsWith("选择效果 ")),"对手或观众收到私有选择");
                for(UUID owner:List.of(a,b)) {
                    var hidden=new ArrayList<CardInstance>(m.privateView(owner).deck());hidden.addAll(m.privateView(owner).prizes());
                    if(!viewer.equals(owner))hidden.addAll(m.privateView(owner).hand());
                    for(var card:hidden)context.assertTrue(!json.contains(card.instanceId().toString()),"自动来源包泄露隐藏牌");
                }
            }
            context.assertTrue(rng.equals(m.randomTranscript()),"刷新状态改变随机序列");
            var choice=session.view(a,a).options().stream().filter(o->o.labelZh().startsWith("选择效果 ")).toList();
            context.assertTrue(choice.size()==2,"组合选择选项缺失");submit(context,wire,session,a,b,choice.get(1).index());
            int remaining=10;
            while(m.triggerOrderPrompt()!=null&&remaining-->0) {
                UUID chooser=m.triggerOrderPrompt().choosingPlayer();submitLabel(context,wire,session,chooser,chooser.equals(a)?b:a,"优先结算触发：");
            }
            context.assertTrue(m.phase()==MatchPhase.TURN&&!m.hasPendingEffectChoice()&&m.triggerOrderPrompt()==null,"续接未正确完成");
            context.assertTrue(m.privateView(a).hand().size()==before+2+(kind.equals("TOOL")?1:0),"抽牌前缀或新回合抽牌重复／丢失");
            context.assertTrue(m.currentPlayer().equals(kind.equals("STADIUM")?b:a),"自动效果错误推进当前玩家");
        } finally {wire.release();}
        context.complete();
    }
    private static void departure(TestContext context,ServerMatch m,MatchSession session,UUID a,UUID b,UUID watcher) {
        var wire=new RegistryByteBuf(Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            int hand=m.privateView(a).hand().size(),turn=m.turnNumber();UUID host=m.activePokemonView(a).pokemonId();
            var view=session.view(a,a);var start=view.options().stream().filter(o->o.labelZh().startsWith("使用特性")&&o.cardKeys().contains("field:"+host)).findFirst().orElseThrow();
            submit(context,wire,session,a,b,start.index());
            context.assertTrue(m.phase()==MatchPhase.TURN&&m.effectCardPrompt(a)!=null,"离场提前进入替补，未保留同张卡选牌");
            var rng=m.randomTranscript();
            for(UUID viewer:List.of(a,b,watcher)) {
                wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(viewer,viewer.equals(watcher)?null:viewer)),"离场续接"));
                String json=MatchStatePayload.CODEC.decode(wire).snapshotJson();var snapshot=new Gson().fromJson(json,MatchSession.Snapshot.class);
                context.assertTrue(viewer.equals(a)==(snapshot.game().effectSelection()!=null),"离场后的私有选牌权限错误");
                for(UUID owner:List.of(a,b)) {
                    var hidden=new ArrayList<CardInstance>(m.privateView(owner).prizes());
                    if(!viewer.equals(a)||!owner.equals(a))hidden.addAll(m.privateView(owner).deck());
                    if(!owner.equals(viewer))hidden.addAll(m.privateView(owner).hand());
                    for(var card:hidden)context.assertTrue(!json.contains(card.instanceId().toString()),"离场后的状态包泄露隐藏牌");
                }
            }
            context.assertTrue(rng.equals(m.randomTranscript()),"离场等待期间刷新状态消耗随机");
            submitLabel(context,wire,session,a,b,"选择卡牌");submitLabel(context,wire,session,a,b,"确认选牌");
            context.assertTrue(m.phase()==MatchPhase.CHOOSE_REPLACEMENT&&m.pendingChoicePlayer().equals(a)&&m.privateView(a).hand().size()==hand+3,"整张卡文本、抽牌或替补边界错误");
            var replacement=session.view(a,a);submit(context,wire,session,a,b,replacement.options().stream().filter(o->o.cardKeys().stream().anyMatch(k->k.startsWith("field:"))).findFirst().orElseThrow().index());
            context.assertTrue(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(a)&&m.turnNumber()==turn,"离场替补错误推进回合");
        } finally {wire.release();}
        context.complete();
    }
    private static void beforeDamage(TestContext context,ServerMatch m,MatchSession session,UUID a,UUID b,UUID watcher,boolean copy,boolean coins) {
        var wire=new RegistryByteBuf(Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            if(copy) {
                var energy=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
                var view=session.view(a,a);submit(context,wire,session,a,b,view.options().stream().filter(o->o.cardKeys().contains("hand:"+energy.instanceId())
                    &&o.cardKeys().contains("field:"+m.activePokemonView(a).pokemonId())).findFirst().orElseThrow().index());
            }
            int hand=m.privateView(a).hand().size(),turn=m.turnNumber(),damage=copy?80:40;
            int randomBefore=m.randomTranscript().size();
            if(copy) {
                var view=session.view(a,a);var option=view.options().stream().filter(o->o.labelZh().startsWith("复制攻击：")&&o.labelZh().endsWith("／协议测试攻击")).findFirst().orElseThrow();
                context.assertTrue(option.cardKeys().contains("field:"+m.activePokemonView(b).pokemonId()),"复制消息没有绑定公开招式来源");
                submit(context,wire,session,a,b,option.index());
            } else submitLabel(context,wire,session,a,b,"攻击：");
            boolean heads=coins&&m.randomTranscript().get(randomBefore).result()==0;
            if(coins) {
                context.assertTrue(m.randomTranscript().size()==randomBefore+1,"攻击命名硬币未恰好掷一次");
                if(heads)damage+=20;
            }
            context.assertTrue(m.activePokemonView(b).damage()==0&&m.effectCardPrompt(a)!=null,"伤害前选择未暂停攻击");
            var rng=m.randomTranscript();
            for(UUID viewer:List.of(a,b,watcher)) {
                wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(viewer,viewer.equals(watcher)?null:viewer)),"伤害前结算"));
                String json=MatchStatePayload.CODEC.decode(wire).snapshotJson();var view=new Gson().fromJson(json,MatchSession.Snapshot.class);
                context.assertTrue(!json.contains("effectiveAttack")&&!json.contains("effectProgram"),"服务器可执行攻击定义外发");
                context.assertTrue(!json.contains("attackCoins")&&!json.contains("attackCheckpoint"),"服务器攻击可写检查点外发");
                context.assertTrue(view.game().resolvingAttack().phase()==EffectTrigger.BEFORE_ATTACK,"伤害前阶段在消息往返时丢失");
                context.assertTrue(viewer.equals(a)==(view.game().effectSelection()!=null),"伤害前候选选牌权限错误");
                for(UUID owner:List.of(a,b)) {
                    var hidden=new ArrayList<CardInstance>(m.privateView(owner).prizes());
                    if(!viewer.equals(a)||!owner.equals(a))hidden.addAll(m.privateView(owner).deck());
                    if(!owner.equals(viewer))hidden.addAll(m.privateView(owner).hand());
                    for(var card:hidden)context.assertTrue(!json.contains(card.instanceId().toString()),"伤害前消息泄露隐藏卡");
                }
            }
            context.assertTrue(rng.equals(m.randomTranscript()),"伤害前刷新改变随机数");
            submitLabel(context,wire,session,a,b,"选择卡牌");submitLabel(context,wire,session,a,b,"确认选牌");
            context.assertTrue(m.activePokemonView(b).damage()==damage&&m.clientView(b).resolvingAttack().phase()==EffectTrigger.AFTER_ATTACK&&m.turnNumber()==turn,"选牌后未恰好造成一次伤害并暂停于伤害后");
            submitLabel(context,wire,session,a,b,"选择效果 1");
            context.assertTrue(m.currentPlayer().equals(b)&&m.turnNumber()==turn+1&&m.activePokemonView(b).damage()==damage&&m.privateView(a).hand().size()==hand+3,"两个攻击阶段重复前缀／伤害／回合");
            if(coins) {
                context.assertTrue(m.activePokemonView(b).conditions().contains(SpecialCondition.CONFUSED)==heads,"消息暂停后伤害与混乱未共用硬币");
                context.assertTrue(m.randomTranscript().stream().filter(e->e.operation().contains("coin")).count()==rng.stream().filter(e->e.operation().contains("coin")).count(),"消息重放或恢复重复掷币");
            }
        } finally {wire.release();}
        context.complete();
    }
    private static void namedPokemon(TestContext context,ServerMatch m,MatchSession session,UUID a,UUID b,UUID watcher) {
        var wire=new RegistryByteBuf(Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            int hand=m.privateView(a).hand().size(),turn=m.turnNumber();UUID target=m.benchPokemonView(b).getFirst().pokemonId();
            submitLabel(context,wire,session,a,b,"攻击：");
            for(int stage=0;stage<2;stage++) {
                var rng=m.randomTranscript();
                for(UUID viewer:List.of(a,b,watcher)) {
                    wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(viewer,viewer.equals(watcher)?null:viewer)),"命名目标结算"));
                    String json=MatchStatePayload.CODEC.decode(wire).snapshotJson();var received=new Gson().fromJson(json,MatchSession.Snapshot.class);
                    context.assertTrue(received.game().resolvingAttack().phase()==(stage==0?EffectTrigger.BEFORE_ATTACK:EffectTrigger.AFTER_ATTACK),"命名目标阶段在消息中丢失");
                    context.assertTrue(!json.contains("pokemonBindings")&&!json.contains("attackPokemon")&&!json.contains("effectProgram"),"可写目标绑定或执行程序外发");
                    if(!viewer.equals(a))context.assertTrue(received.options().stream().noneMatch(o->o.labelZh().startsWith("选择效果目标")||o.labelZh().startsWith("选择卡牌")),"非控制者获取目标选择权限");
                    for(UUID owner:List.of(a,b)) {
                        var hidden=new ArrayList<CardInstance>(m.privateView(owner).prizes());
                        if(stage==0||!viewer.equals(a)||!owner.equals(a))hidden.addAll(m.privateView(owner).deck());
                        if(!owner.equals(viewer))hidden.addAll(m.privateView(owner).hand());
                        for(var card:hidden)context.assertTrue(!json.contains(card.instanceId().toString()),"命名目标同步泄露隐藏卡");
                    }
                }
                context.assertTrue(rng.equals(m.randomTranscript()),"读取命名目标状态改变随机序列");
                if(stage==0) {
                    var view=session.view(a,a);var option=view.options().stream().filter(o->o.labelZh().startsWith("选择效果目标")&&o.cardKeys().contains("field:"+target)).findFirst().orElseThrow();
                    boolean denied=false;try{session.submit(watcher,a,view.revision(),option.index());}catch(IllegalArgumentException expected){denied=true;}
                    context.assertTrue(denied,"旁观者冒用目标选择席位");submit(context,wire,session,a,b,option.index());
                    context.assertTrue(m.activePokemonView(b).damage()==60&&m.benchPokemonView(b).getFirst().damage()==20,"伤害阶段没读取前一步所选对象的指示物");
                }
            }
            submitLabel(context,wire,session,a,b,"选择卡牌");submitLabel(context,wire,session,a,b,"确认选牌");
            context.assertTrue(m.benchPokemonView(b).getFirst().pokemonId().equals(target)&&m.benchPokemonView(b).getFirst().damage()==50
                &&m.activePokemonView(b).damage()==60&&m.privateView(a).hand().size()==hand+2&&m.currentPlayer().equals(b)&&m.turnNumber()==turn+1,
                "引用续接串目标、重复伤害或重复回合推进");
        } finally {wire.release();}
        context.complete();
    }
    private static void typedEnergy(TestContext context,ServerMatch m,MatchSession session,UUID a,UUID b,UUID watcher) {
        var wire=new RegistryByteBuf(Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            var energy=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
            var view=session.view(a,a);submit(context,wire,session,a,b,view.options().stream().filter(o->o.cardKeys().contains("hand:"+energy.instanceId())
                &&o.cardKeys().contains("field:"+m.activePokemonView(a).pokemonId())).findFirst().orElseThrow().index());
            UUID host=m.activePokemonView(a).pokemonId();
            context.assertTrue(m.matchesEffectCondition(a,host,new ConditionDefinition(ConditionType.ENERGY_COUNT,
                Map.of("energy_type","WATER","value","2","comparison","="),List.of())),"正式附能未提供两个水能量单位");
            context.assertTrue(m.matchesEffectCondition(a,host,new ConditionDefinition(ConditionType.ENERGY_COUNT,
                Map.of("energy_type","WATER","count_mode","CARDS","value","1","comparison","="),List.of())),"一个双份能量被当作两张卡");
            // Server-only fixture setup: a vetted effect attaches a second card to Bench; no client chosen outcomes.
            var extra=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
            m.attachEnergyByEffect(a,a,EffectSourceZone.HAND,extra.instanceId(),a,m.benchPokemonView(a).getFirst().pokemonId());
            context.assertTrue(m.matchesEffectCondition(a,host,new ConditionDefinition(ConditionType.ENERGY_COUNT,
                Map.of("target","ALL_OWN_POKEMON","energy_type","WATER","value","4","comparison","="),List.of())),"全场条件未汇总四个供能单位");
            var stadium=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("stadium")).findFirst().orElseThrow();
            view=session.view(a,a);submit(context,wire,session,a,b,view.options().stream().filter(o->o.cardKeys().contains("hand:"+stadium.instanceId())).findFirst().orElseThrow().index());
            context.assertTrue(!m.matchesEffectCondition(a,host,new ConditionDefinition(ConditionType.HAS_ENERGY,Map.of("energy_type","WATER"),List.of())),"场地覆盖仍留下水能量条件");
            context.assertTrue(!m.matchesEffectCondition(a,host,new ConditionDefinition(ConditionType.HAS_ENERGY,
                Map.of("target","ALL_OWN_POKEMON","energy_type","WATER"),List.of())),"场地覆盖没有更新全场条件");
            var rng=m.randomTranscript();
            for(UUID viewer:List.of(a,b,watcher)) {
                wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(viewer,viewer.equals(watcher)?null:viewer)),"属性能量计数"));
                String json=MatchStatePayload.CODEC.decode(wire).snapshotJson();
                for(UUID owner:List.of(a,b)) {
                    var hidden=new ArrayList<CardInstance>(m.privateView(owner).deck());hidden.addAll(m.privateView(owner).prizes());
                    if(!owner.equals(viewer))hidden.addAll(m.privateView(owner).hand());
                    for(var card:hidden)context.assertTrue(!json.contains(card.instanceId().toString()),"属性计数消息泄露隐藏卡");
                }
                context.assertTrue(!json.contains("effectProgram"),"计数效果程序外发");
            }
            context.assertTrue(rng.equals(m.randomTranscript()),"属性查询改变随机数");
            submitLabel(context,wire,session,a,b,"攻击：");context.assertTrue(m.activePokemonView(b).damage()==40,"场地覆盖后仍按原水能量增伤");
            submitLabel(context,wire,session,b,a,"结束回合");m.discardStadiumByEffect(a);
            submitLabel(context,wire,session,a,b,"攻击：");context.assertTrue(m.activePokemonView(b).damage()==200&&m.currentPlayer().equals(b),"场地离场后全场属性计数未恢复或重复攻击");
        } finally {wire.release();}
        context.complete();
    }
    private static void restrictedEnergy(TestContext context,ServerMatch m,MatchSession session,UUID a,UUID b,UUID watcher) {
        var wire=new RegistryByteBuf(Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            var energy=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
            UUID host=m.activePokemonView(a).pokemonId();var hand=m.privateView(a).hand();var rng=m.randomTranscript();
            context.assertTrue(session.view(a,a).options().stream().noneMatch(o->o.cardKeys().contains("hand:"+energy.instanceId())),"服务器提供了非法特殊能量附着选项");
            boolean rejected=false;try {m.attachEnergy(a,energy.instanceId(),host);}catch(IllegalArgumentException|IllegalStateException expected){rejected=true;}
            context.assertTrue(rejected&&hand.equals(m.privateView(a).hand())&&!m.energyAttached(a),"非法附能修改卡牌或次数");
            context.assertTrue(rng.equals(m.randomTranscript()),"附能校验消耗随机数");
            var evolve=session.view(a,a).options().stream().filter(o->o.labelZh().startsWith("进化：")&&o.cardKeys().contains("field:"+host)).findFirst().orElseThrow();
            submit(context,wire,session,a,b,evolve.index());
            var attach=session.view(a,a).options().stream().filter(o->o.cardKeys().contains("hand:"+energy.instanceId())&&o.cardKeys().contains("field:"+host)).findFirst().orElseThrow();
            submit(context,wire,session,a,b,attach.index());
            var stadium=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("stadium")).findFirst().orElseThrow();
            submit(context,wire,session,a,b,session.view(a,a).options().stream().filter(o->o.cardKeys().contains("hand:"+stadium.instanceId())).findFirst().orElseThrow().index());
            // Server-only fixture hooks exercise host changes and source removal; not client actions.
            m.devolvePokemonToHandByEffect(a,a,host);
            context.assertTrue(m.activePokemonView(a).attachedEnergyIds().contains(energy.instanceId()),"场地关闭效果期间误弃能量");
            m.discardStadiumByEffect(a);
            context.assertTrue(m.privateView(a).discard().contains(energy)&&!m.activePokemonView(a).attachedEnergyIds().contains(energy.instanceId()),"场地离场没有恢复自弃限制");
            rng=m.randomTranscript();
            for(UUID viewer:List.of(a,b,watcher)) {
                wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(viewer,viewer.equals(watcher)?null:viewer)),"特殊能量附着规则"));
                String json=MatchStatePayload.CODEC.decode(wire).snapshotJson();
                for(UUID owner:List.of(a,b)) {
                    var hidden=new ArrayList<CardInstance>(m.privateView(owner).deck());hidden.addAll(m.privateView(owner).prizes());if(!owner.equals(viewer))hidden.addAll(m.privateView(owner).hand());
                    for(var card:hidden)context.assertTrue(!json.contains(card.instanceId().toString()),"附着限制消息泄露隐藏卡");
                }
                context.assertTrue(!json.contains("attachmentRule")&&!json.contains("effectProgram"),"服务器规则程序外发");
            }
            context.assertTrue(rng.equals(m.randomTranscript()),"附着候选查询修改随机结果");
        } finally {wire.release();}
        context.complete();
    }
    private static void turnEnd(TestContext context,ServerMatch m,MatchSession session,UUID a,UUID b,UUID watcher) {
        var wire=new RegistryByteBuf(Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            var energy=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();UUID host=m.activePokemonView(a).pokemonId();
            var attach=session.view(a,a).options().stream().filter(o->o.cardKeys().contains("hand:"+energy.instanceId())&&o.cardKeys().contains("field:"+host)).findFirst().orElseThrow();
            submit(context,wire,session,a,b,attach.index());int before=m.privateView(a).hand().size(),turn=m.turnNumber();
            submitLabel(context,wire,session,a,b,"结束回合");
            context.assertTrue(m.phase()==MatchPhase.TURN_END&&m.triggerOrderPrompt()!=null&&m.triggerOrderPrompt().optionalDecision(),"回合末未进入可选效果等待");
            var view=session.view(a,a);var use=view.options().stream().filter(o->o.labelZh().startsWith("使用")&&!o.labelZh().contains("放弃")).findFirst().orElseThrow();
            submit(context,wire,session,a,b,use.index());context.assertTrue(m.hasPendingEffectChoice()&&m.privateView(a).hand().size()==before+1,"回合末续接未保留已执行前缀");
            var rng=m.randomTranscript();
            for(UUID viewer:List.of(a,b,watcher)) {
                wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(viewer,viewer.equals(watcher)?null:viewer)),"回合末结算"));
                String json=MatchStatePayload.CODEC.decode(wire).snapshotJson();var received=new Gson().fromJson(json,MatchSession.Snapshot.class);
                context.assertTrue(received.game().phase()==MatchPhase.TURN_END&&received.game().choicePlayer().equals(a),"回合末阶段或选择权外发错误");
                context.assertTrue(!json.contains("effectProgram")&&!json.contains("automaticEffects"),"回合末外发可执行规则");
                context.assertTrue(received.options().stream().noneMatch(o->o.labelZh().equals("结束回合")||o.labelZh().startsWith("攻击：")),"回合末仍暴露普通回合动作");
                for(UUID owner:List.of(a,b)) {
                    var hidden=new ArrayList<CardInstance>(m.privateView(owner).deck());hidden.addAll(m.privateView(owner).prizes());if(!owner.equals(viewer))hidden.addAll(m.privateView(owner).hand());
                    for(var card:hidden)context.assertTrue(!json.contains(card.instanceId().toString()),"回合末同步泄露隐藏卡");
                }
            }
            context.assertTrue(rng.equals(m.randomTranscript()),"回合末刷新消耗随机");
            var choice=session.view(a,a);submit(context,wire,session,a,b,choice.options().stream().filter(o->o.labelZh().contains("来源卡放入")).findFirst().orElseThrow().index());
            context.assertTrue(m.currentPlayer().equals(b)&&m.turnNumber()==turn+1&&m.phase()==MatchPhase.TURN&&m.privateView(a).hand().size()==before+1,"回合末选择重做前缀或重复推进");
            context.assertTrue(m.privateView(a).discard().contains(energy)&&!m.activePokemonView(a).attachedEnergyIds().contains(energy.instanceId()),"来源实体卡没有自弃");
        } finally {wire.release();}
        context.complete();
    }
    private static void delayed(TestContext context,ServerMatch m,MatchSession session,UUID a,UUID b,UUID watcher) {
        var wire=new RegistryByteBuf(Unpooled.buffer(),context.getWorld().getRegistryManager());
        try {
            UUID original=m.activePokemonView(a).pokemonId();submitLabel(context,wire,session,a,b,"攻击：");
            context.assertTrue(m.activePokemonView(b).damage()==40&&m.activePokemonView(b).continuousEffectZh().contains("延迟"),"正式攻击未施加延迟或提前结算");
            // Server fixture departs the original source; packet privacy must not re-identify the returned card.
            m.movePokemonByEffect(b,a,original,LeavePlayDestination.HAND);m.chooseReplacement(a,m.benchPokemonView(a).getFirst().pokemonId());
            int before=m.privateView(a).hand().size(),turn=m.turnNumber();submitLabel(context,wire,session,b,a,"结束回合");
            context.assertTrue(m.triggerOrderPrompt()!=null&&m.pendingChoicePlayer().equals(b),"同一回合末的多个延迟没有进入排序");
            for(UUID viewer:List.of(b,watcher)) {
                wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(viewer,viewer.equals(watcher)?null:viewer)),"延迟排序"));
                String json=MatchStatePayload.CODEC.decode(wire).snapshotJson();
                // Historical public field IDs may remain in presentation cues. Physical hidden IDs must not.
                for(var card:m.privateView(a).hand())context.assertTrue(!json.contains(card.instanceId().toString()),"延迟排序包泄露实际隐藏手牌实例");
            }
            submitLabel(context,wire,session,b,a,"优先结算触发：");
            context.assertTrue(m.phase()==MatchPhase.TURN_END&&m.pendingChoicePlayer().equals(a)&&m.privateView(a).hand().size()==before+1,"延迟选择未交给原控制者");
            var rng=m.randomTranscript();
            for(UUID viewer:List.of(a,b,watcher)) {
                wire.clear();MatchStatePayload.CODEC.encode(wire,new MatchStatePayload(false,new Gson().toJson(session.view(viewer,viewer.equals(watcher)?null:viewer)),"延迟效果"));
                String json=MatchStatePayload.CODEC.decode(wire).snapshotJson();var snapshot=new Gson().fromJson(json,MatchSession.Snapshot.class);
                context.assertTrue(snapshot.game().phase()==MatchPhase.TURN_END&&!json.contains("effectProgram"),"延迟状态包丢失阶段或外发程序");
                if(!viewer.equals(a))context.assertTrue(snapshot.options().stream().noneMatch(o->o.labelZh().startsWith("选择效果 ")),"非控制者收到延迟选择权限");
                for(UUID owner:List.of(a,b)) {
                    var hidden=new ArrayList<CardInstance>(m.privateView(owner).deck());hidden.addAll(m.privateView(owner).prizes());if(!owner.equals(viewer))hidden.addAll(m.privateView(owner).hand());
                    for(var card:hidden)context.assertTrue(!json.contains(card.instanceId().toString()),"延迟效果泄露隐藏身份");
                }
            }
            context.assertTrue(rng.equals(m.randomTranscript()),"查看延迟结算改变随机序列");submitLabel(context,wire,session,a,b,"选择效果 1");
            context.assertTrue(m.phase()==MatchPhase.TURN_END&&m.pendingChoicePlayer().equals(a)&&m.activePokemonView(b).damage()==70,"第一个延迟完成后漏掉第二个程序");
            submitLabel(context,wire,session,a,b,"选择效果 1");
            context.assertTrue(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(a)&&m.turnNumber()==turn+1&&m.privateView(a).hand().size()==before+3
                &&m.activePokemonView(b).damage()==100&&!m.activePokemonView(b).continuousEffectZh().contains("延迟"),"延迟续接重复执行、拿错对象或漏推进");
        } finally {wire.release();}
        context.complete();
    }
    private static CardDefinition card(String id,CardCategory category,TrainerCategory trainer,boolean basic) {
        return new CardDefinition(id,"协议测试"+id,category,trainer,"H",Set.of("international"),basic,false,60,Map.of(),EffectSupport.SUPPORTED,null);
    }
    private static void submitLabel(TestContext context,RegistryByteBuf wire,MatchSession session,UUID owner,UUID other,String label) {
        var view=session.view(owner,owner);submit(context,wire,session,owner,other,view.options().stream().filter(o->o.labelZh().startsWith(label)).findFirst().orElseThrow().index());
    }
    private static void submit(TestContext context,RegistryByteBuf wire,MatchSession session,UUID owner,UUID other,int option) {
        var view=session.view(owner,owner);wire.clear();MatchIntentPayload.CODEC.encode(wire,new MatchIntentPayload(view.game().matchId().toString(),owner.toString(),Math.toIntExact(view.revision()),option));
        var intent=MatchIntentPayload.CODEC.decode(wire);boolean denied=false;
        try {session.submit(other,owner,intent.revision(),intent.option());}catch(IllegalArgumentException expected){denied=true;}
        context.assertTrue(denied,"对手冒用自动效果席位成功");session.submit(owner,owner,intent.revision(),intent.option());denied=false;
        try {session.submit(owner,owner,intent.revision(),intent.option());}catch(IllegalStateException expected){denied=true;}
        context.assertTrue(denied,"重复意图被执行");
    }
}
