import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import dev.hua.ptcglive.table.*;
import java.util.*;
import java.time.Clock;

/** Player-configurable decks, not runtime presets. Synthetic combination fixtures are explicitly separate. */
public final class AiCustomDeckTest {
    static final UUID A=AiPracticeTest.A,B=AiPracticeTest.B;
    static final String RULE="custom-open-all";
    static final List<Map<String,Object>> combinations=new ArrayList<>();
    static void check(boolean b,String m){AiPracticeTest.check(b,m);}
    static void rejects(Runnable r){AiPracticeTest.rejects(r);}
    public static void main(String[] args)throws Exception{
        AiPracticeTest.cards=BundledRules.loadCardDefinitions();AiPracticeTest.combat=BundledCombatData.load();AiPracticeTest.rules=BundledRules.loadRulesets();
        snapshots();analysisAndPrivacy();combinations();
        for(var p:configurations()){
            var deck=AiPracticeTest.deck(p,A);var rules=new DeckValidator(AiPracticeTest.cards,AiPracticeTest.combat).validate(AiPracticeTest.rules.get(RULE),deck);
            check(rules.canStartMatch(),p.name()+rules.issues());check(AiSupport.problems(deck,AiPracticeTest.cards,AiPracticeTest.combat).isEmpty(),p.name()+"强制选择未支持");
            for(long seed:new long[]{7,19,43})for(boolean first:List.of(true,false))AiPracticeTest.play(p,AiPresets.all().get(1),seed,first);
        }
        var report=new LinkedHashMap<String,Object>();report.put("configurations",configurations());report.put("games",AiPracticeTest.games);report.put("coverage",AiPracticeTest.coverage);
        report.put("combinations",combinations);report.put("policyCalls",AiPracticeTest.policyCalls);report.put("averagePolicyMs",AiPracticeTest.policyNanos/1_000_000.0/AiPracticeTest.policyCalls);report.put("maxPolicyMs",AiPracticeTest.maxPolicyNanos/1_000_000.0);report.put("minecraftClientVerified",false);
        java.nio.file.Files.writeString(java.nio.file.Path.of("outputs/ai-custom-0.7.15-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        System.out.println("AiCustomDeckTest passed: changing decks/names/energy, mirror isolation, stale and one-use confirmations, independent locks, fixed-seed hidden-information isolation, short combinations and 18 full games");
    }
    static AiPresets.Preset config(String id,String name,Map<String,Integer> list){return new AiPresets.Preset(id,name,RULE,"本模组自定义测试构筑；不是官方预组","仅用于自动验收，运行时任意合法卡盒",new TreeMap<>(list));}
    static List<AiPresets.Preset> configurations(){
        return List.of(config("fire-evolution","火系进化与主动抽牌",Map.ofEntries(e("049",4),e("050",4),e("056",4),e("399",4),e("367",4),e("373",4),e("370",4),e("365",4),e("377",4),e("fire_energy",24))),
            config("water-evolution","水系二阶进化与附能",Map.ofEntries(e("093",4),e("094",4),e("095",4),e("092",4),e("399",4),e("367",4),e("373",4),e("370",4),e("365",4),e("381",2),e("water_energy",22))),
            config("mixed-energy","火水雷三属性资源调度",Map.ofEntries(e("056",4),e("092",4),e("137",4),e("399",4),e("367",4),e("373",4),e("366",4),e("365",4),e("377",4),e("fire_energy",8),e("water_energy",8),e("lightning_energy",8))));
    }
    static Map.Entry<String,Integer> e(String id,int n){return Map.entry("sd100_cn_"+id,n);}
    static AiDeckAnalysis analyze(List<CardInstance> d){return AiDeckAnalysis.analyze(d,AiPracticeTest.cards,AiPracticeTest.combat);}
    static Map<String,Long> counts(List<CardInstance> list){return list.stream().collect(java.util.stream.Collectors.groupingBy(CardInstance::definitionId,TreeMap::new,java.util.stream.Collectors.counting()));}
    static void snapshots(){
        var time=new BattleExperienceTest.Time();var boxes=new CardBoxService(time);var tables=CardTableManager.restore(AiPracticeTest.rules,boxes,AiPracticeTest.cards,AiPracticeTest.combat,()->new ServerRandom(19),time,new CardTableManager.Snapshot(List.of(),List.of(),List.of()));
        var p=AiPracticeLifecycle.box(boxes,A,configurations().get(0));var q=AiPracticeLifecycle.box(boxes,A,configurations().get(1));var other=AiPracticeLifecycle.box(boxes,B,configurations().get(2));
        var key=new CardTableKey("minecraft:overworld",7515);tables.open(key,A,RULE);
        rejects(()->tables.checkComputer(key,A,p.id(),other.id(),RULE));
        var checked=tables.checkComputer(key,A,p.id(),q.id(),RULE);check(checked.allowed(),"真实卡组检查失败"+checked);
        boxes.rename(A,q.id(),"新名称");rejects(()->tables.startComputer(key,A,p.id(),q.id(),RULE,checked.token()));
        var expired=tables.checkComputer(key,A,p.id(),q.id(),RULE);time.now+=300_001;rejects(()->tables.startComputer(key,A,p.id(),q.id(),RULE,expired.token()));
        var modified=tables.checkComputer(key,A,p.id(),q.id(),RULE);UUID old=q.contents().getFirst();boxes.take(A,q.id(),old);boxes.put(A,q.id(),boxes.mint(A,"sd100_cn_water_energy").instanceId());
        rejects(()->tables.startComputer(key,A,p.id(),q.id(),RULE,modified.token()));
        var current=tables.checkComputer(key,A,p.id(),q.id(),RULE);check(current.allowed(),"源卡盒编辑后应能重新检查");var before=boxes.snapshot();
        UUID id=tables.startComputer(key,A,p.id(),q.id(),RULE,current.token());UUID bot=tables.matchView(id,A,A).computer().seat();var engine=tables.session(id).match();
        var after=boxes.snapshot();
        check(before.cards().equals(after.cards())&&before.boxes().equals(after.boxes())&&before.discoveries().equals(after.discoveries()),"AI 创建了收藏副本");check(boxes.isLocked(A,p.id())&&!boxes.isLocked(A,q.id()),"AI 源卡盒被错误锁定");
        var original=List.copyOf(engine.privateView(bot).deck());check(counts(original).equals(counts(boxes.contentsForValidation(A,q.id()))),"临时快照卡表错误");
        var physical=new HashSet<UUID>();boxes.contentsForValidation(A,p.id()).forEach(c->physical.add(c.instanceId()));boxes.contentsForValidation(A,q.id()).forEach(c->physical.add(c.instanceId()));
        check(original.stream().noneMatch(c->physical.contains(c.instanceId()))&&original.stream().allMatch(c->c.ownerId().equals(bot)),"临时实例共享真实 ID/归属");
        rejects(()->tables.startComputer(key,A,p.id(),q.id(),RULE,current.token()));
        boxes.take(A,q.id(),q.contents().getFirst());check(original.equals(engine.privateView(bot).deck()),"开局后的源卡盒修改改变快照");
        UUID independent=UUID.randomUUID();boxes.snapshotForMatch(A,q.id(),independent);tables.endMatch(id);
        check(!boxes.isLocked(A,p.id())&&boxes.isLocked(A,q.id()),"AI 结束误解锁其他对局");boxes.unlock(independent);
        tables.open(key,A,RULE);var mirror=tables.checkComputer(key,A,p.id(),p.id(),RULE);UUID mir=tables.startComputer(key,A,p.id(),p.id(),RULE,mirror.token());
        var session=tables.session(mir);UUID mbot=tables.matchView(mir,A,A).computer().seat();var human=session.match().privateView(A).deck();var computer=session.match().privateView(mbot).deck();
        check(counts(human).equals(counts(computer)),"镜像卡表不同");check(human.stream().noneMatch(c->computer.stream().anyMatch(d->d.instanceId().equals(c.instanceId()))),"镜像实例冲突");
        tables.disconnectPlayer(A);check(tables.activeComputerCount()==0&&!tables.hasMatch(mir)&&!boxes.isLocked(A,p.id()),"退出未清理镜像任务/锁");
        tables.open(key,A,RULE);var stale=tables.checkComputer(key,A,p.id(),p.id(),RULE);tables.endTable(key,A);tables.open(key,A,RULE);rejects(()->tables.startComputer(key,A,p.id(),p.id(),RULE,stale.token()));
        tables.requireBoxPossession((owner,box)->!box.equals(p.id()));rejects(()->tables.checkComputer(key,A,p.id(),p.id(),RULE));
        System.out.println("CUSTOM LIFECYCLE passed");
    }
    static void analysisAndPrivacy(){
        var one=configurations().get(0);var two=configurations().get(1);var a=analyze(AiPracticeTest.deck(one,A));var b=analyze(AiPracticeTest.deck(two,A));
        check(a.energyTypes().equals(Set.of(EnergyType.FIRE))&&b.energyTypes().equals(Set.of(EnergyType.WATER)),"未按真实供能结构分析");check(!a.attackers().equals(b.attackers())&&!a.evolutionLines().equals(b.evolutionLines()),"修改主攻和进化线后分析没有改变");
        var renamed=config(one.id(),"名字不构成策略脚本",one.cards());check(a.equals(analyze(AiPracticeTest.deck(renamed,A))),"改名改变分析");
        var game=new ServerMatch(A,B,AiPracticeTest.deck(one,A),AiPracticeTest.deck(two,B),AiPracticeTest.rules.get(RULE),AiPracticeTest.cards,AiPracticeTest.combat,new ServerRandom(311));OpeningTestSupport.complete(game,A,B,0);var s=MatchSession.multiplayer(game,A,B);
        var input=view(s,A,a);var d=AiPolicy.choose(input,515,Set.of());var rng=game.randomTranscript();
        var hand=AiPracticeScenarios.zone(game,B,"hand");var deck=AiPracticeScenarios.zone(game,B,"deck");var swap=hand.set(0,deck.getFirst());deck.set(0,swap);
        for(UUID seat:List.of(A,B)){Collections.reverse(AiPracticeScenarios.zone(game,seat,"deck"));Collections.reverse(AiPracticeScenarios.zone(game,seat,"prizes"));}
        var changed=view(s,A,a);check(input.equals(changed)&&d.equals(AiPolicy.choose(changed,515,Set.of())),"已知自身卡表时仍泄露隐藏牌序/手牌");check(rng.equals(game.randomTranscript()),"规划消耗对局随机数");
        var renamedInput=view(s,A,analyze(AiPracticeTest.deck(renamed,A)));check(d.equals(AiPolicy.choose(renamedInput,515,Set.of())),"改名改变决策");
        check(input.deck().initialCards().equals(one.cards()),"决策初始卡表并非仅自己的卡表");
        System.out.println("CUSTOM ANALYSIS AND PRIVACY passed");
    }
    static AiView view(MatchSession session,UUID seat,AiDeckAnalysis analysis){
        var game=session.match();var snapshot=session.view(seat,seat);var own=snapshot.game().players().stream().filter(p->p.id().equals(seat)).findFirst().orElseThrow().active();
        return AiView.filtered(snapshot,(CardDatabase)AiPracticeScenarios.field(game,"definitions"),(CombatCatalog)AiPracticeScenarios.field(game,"combat"),game.decisionEnergy(seat),analysis,
            new AiView.OwnTurn(game.mayAttack(seat),game.energyAttached(seat),own==null?0:game.activeRetreatCost(seat)));
    }
    static void combinations(){
        combo("search-attach-attack",ComposedTrainerIntegrationTest.item("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":1,\"minimum\":0,\"card_category\":\"ENERGY\",\"basic_energy_only\":true,\"reveal\":true}"),false);
        combo("recover-attach-attack",ComposedTrainerIntegrationTest.item("{\"type\":\"search_discard_pile\",\"target\":\"DISCARD\",\"amount\":1,\"minimum\":1,\"card_category\":\"ENERGY\",\"basic_energy_only\":true,\"destination\":\"HAND\"}"),true);
        combo("tool-boost-attack",parse("{\"trainerCategory\":\"TOOL\",\"effect\":{\"opcode\":\"PASSIVE_TOOL\",\"amount\":0},\"triggers\":[{\"trigger\":\"PASSIVE\",\"effects\":[{\"type\":\"attack_damage_modifier\",\"target\":\"SELF\",\"amount\":20,\"direction\":\"DEALT\"}]}]}"),false);
    }
    static TrainerProfile parse(String json){return BundledCombatData.trainer(com.google.gson.JsonParser.parseString(json).getAsJsonObject(),dev.hua.ptcglive.engine.effect.EffectRegistry.standard());}
    static void combo(String name,TrainerProfile trainer,boolean recovery){
        boolean boost=name.startsWith("tool");
        var attack=new AttackDefinition("hit","检验攻击",boost?Map.of():Map.of(EnergyType.GRASS,1),40,EnergyType.GRASS,EffectSupport.SUPPORTED,null);
        var db=new CardDatabase(List.of(BattleExperienceTest.pokemon("attacker"),BattleExperienceTest.pokemon("defender"),
            new CardDefinition("energy","测试草能量",CardCategory.ENERGY,TrainerCategory.NONE,"H",Set.of("international"),false,true,60,Map.of(),EffectSupport.SUPPORTED,null),
            new CardDefinition("trainer","测试训练家",CardCategory.TRAINER,trainer.category(),"H",Set.of("international"),false,false,60,Map.of(),EffectSupport.SUPPORTED,null)));
        var catalog=new CombatCatalog(Map.of("attacker",new PokemonProfile(500,0,null,null,0,null,0,1,List.of(attack)),"defender",new PokemonProfile(500,0,null,null,0,null,0,1,List.of())),Map.of("energy",EnergyType.GRASS),Map.of("trainer",trainer));
        var own=new ArrayList<CardInstance>();for(int i=0;i<60;i++)own.add(new CardInstance(new UUID(715,i),A,i<10?"attacker":i<35?"energy":"trainer"));
        var game=new ServerMatch(A,B,own,BattleExperienceTest.deck(B,"defender"),AiPracticeTest.rules.get(RULE),db,catalog,new ServerRandom(711));OpeningTestSupport.complete(game,A,B,0);
        while(!game.currentPlayer().equals(A)||!game.mayAttack(A))game.endTurn(game.currentPlayer());
        // Deliberate test fixture preparation only. All measured actions below use the production service.
        var hand=AiPracticeScenarios.zone(game,A,"hand");var deck=AiPracticeScenarios.zone(game,A,"deck");deck.addAll(hand);hand.clear();
        var card=deck.stream().filter(c->c.definitionId().equals("trainer")).findFirst().orElseThrow();deck.remove(card);hand.add(card);
        if(recovery){var energy=deck.stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();deck.remove(energy);AiPracticeScenarios.zone(game,A,"discard").add(energy);}
        if(boost)game.placeDamageCountersByEffect(A,B,game.activePokemonView(B).pokemonId(),44);
        var s=MatchSession.multiplayer(game,A,B);var analysis=AiDeckAnalysis.analyze(own,db,catalog);var trace=new ArrayList<String>();var used=new HashSet<String>();
        for(int step=0;step<20&&game.result()==null&&game.currentPlayer().equals(A);step++){
            UUID actor=A;var v=view(s,A,analysis);var candidate=AiPolicy.choose(v,715+step,used);
            if(candidate.isEmpty()){actor=B;v=view(s,B,AiDeckAnalysis.EMPTY);candidate=AiPolicy.choose(v,715+step,Set.of());}
            var decision=candidate.orElseThrow();var option=v.snapshot().options().get(decision.option());trace.add(option.labelZh());
            s.submit(actor,actor,decision.revision(),decision.option());String remembered=AiPolicy.optionalKey(option);if(actor.equals(A)&&!remembered.isEmpty())used.add(remembered);
            if(decision.category().equals("攻击"))break;
        }
        check(trace.stream().anyMatch(t->t.startsWith("使用")),name+"没有使用关键训练家："+trace);
        check(trace.stream().anyMatch(t->t.startsWith("攻击：")),name+"没有完成后续攻击："+trace);
        if(!boost)check(trace.stream().anyMatch(t->t.startsWith("附着 ")),name+"没有完成附能："+trace);
        if(boost)check(game.lastAttack().finalDamage()==60,name+"增伤后伤害错误");
        combinations.add(Map.of("case",name,"actions",trace));System.out.println("COMBINATION "+name+" "+trace);
    }
}
