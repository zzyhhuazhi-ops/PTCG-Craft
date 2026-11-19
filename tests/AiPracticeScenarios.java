import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Focused engine fixtures plus deliberate hidden-zone mutation confined to privacy tests. */
public final class AiPracticeScenarios {
    public static void main(String[] args){AiPracticeTest.cards=BundledRules.loadCardDefinitions();AiPracticeTest.combat=BundledCombatData.load();AiPracticeTest.rules=BundledRules.loadRulesets();run();}
    static void check(boolean b,String m){AiPracticeTest.check(b,m);}
    static Object field(Object instance,String name){try{var f=instance.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(instance);}catch(ReflectiveOperationException e){throw new AssertionError(e);}}
    static AiView view(MatchSession session,UUID seat){var game=session.match();return AiView.filtered(session.view(seat,seat),(CardDatabase)field(game,"definitions"),(CombatCatalog)field(game,"combat"),game.decisionEnergy(seat));}
    static MatchSession.Option choose(MatchSession s,UUID seat){var v=view(s,seat);return v.snapshot().options().get(AiPolicy.choose(v,77,Set.of()).orElseThrow().option());}
    static void run(){privacy();choices();opponentResponse();winningAndPass();strategicEnergy();strategicTurn();noPointlessRetreat();supportBoundary();dampQuery();System.out.println("AI SCENARIOS passed: privacy permutation, RNG, exact/optional/cost/order/target/counter/distribution choices, opponent turn, winning attack, energy/evolution/search, turn preparation/resource preservation, no pointless switch or deck-out draw, Damp query equivalence");}
    static void strategicTurn(){
        UUID a=BattleExperienceTest.A,b=BattleExperienceTest.B;
        for(int damage:List.of(300,600)){
            var game=BattleExperienceTest.fixture(null,null,new AttackDefinition("hit","主攻",Map.of(),damage,EnergyType.FIRE,EffectSupport.SUPPORTED,null));
            var session=MatchSession.multiplayer(game,a,b);var v=view(session,a);
            var active=game.activePokemonView(a);var facts=new HashMap<>(v.cards());
            var energy=new CardInstance(new UUID(83,1),a,"grass");var evolution=new CardInstance(new UUID(83,2),a,"evo");
            facts.put("grass",new AiView.Card(def("grass",CardCategory.ENERGY,true),null,null,EnergyProfile.basic(EnergyType.GRASS)));
            facts.put("evo",new AiView.Card(def("evo",CardCategory.POKEMON,false),new PokemonProfile(550,0,active.topCardId(),null,0,null,0,1,List.of(new AttackDefinition("next","下一回合攻击",Map.of(EnergyType.GRASS,2),400,EnergyType.GRASS,EffectSupport.SUPPORTED,null))),null,null));
            var options=List.of(new MatchSession.Option(0,"附着 草 → 主攻手",List.of("hand:"+energy.instanceId(),"field:"+active.pokemonId())),new MatchSession.Option(1,"攻击：主攻",List.of("field:"+active.pokemonId())),new MatchSession.Option(2,"结束回合"));
            var input=changed(v,List.of(energy,evolution),options,facts,null,null);
            int selected=AiPolicy.choose(input,83,Set.of()).orElseThrow().option();
            check(selected==(damage==600?1:0),"应先准备进化能量，但确定获胜时立即攻击："+damage+" -> "+selected);
        }
        var game=BattleExperienceTest.fixture(null,null,new AttackDefinition("cost","需要能量",Map.of(EnergyType.GRASS,1),40,EnergyType.GRASS,EffectSupport.SUPPORTED,null));
        var v=view(MatchSession.multiplayer(game,a,b),a);var active=game.activePokemonView(a);var facts=new HashMap<>(v.cards());
        var search=new CardInstance(new UUID(84,1),a,"search");var expendable=new CardInstance(new UUID(84,2),a,"junk");var important=new CardInstance(new UUID(84,3),a,"evo");
        facts.put("search",new AiView.Card(def("search",CardCategory.TRAINER,false),null,new TrainerProfile(TrainerCategory.ITEM,new TrainerEffect(TrainerEffect.Opcode.SEARCH_CARD_TO_HAND,1,0,0,2,1)),null));
        facts.put("junk",new AiView.Card(def("junk",CardCategory.ENERGY,true),null,null,EnergyProfile.basic(EnergyType.WATER)));
        facts.put("evo",new AiView.Card(def("evo",CardCategory.POKEMON,false),new PokemonProfile(550,0,active.topCardId(),null,0,null,0,1,List.of()),null,null));
        var options=List.of(new MatchSession.Option(0,"使用：弃二检索",List.of("hand:"+search.instanceId())),new MatchSession.Option(1,"结束回合"));
        check(AiPolicy.choose(changed(v,List.of(search,expendable,important),options,facts,null,null),84,Set.of()).orElseThrow().option()==1,"弃二检索只检查了第一张代价，丢弃唯一进化组件");
        System.out.println("AI STRATEGY passed: prepare before attacking, immediate win, full discard cost");
    }
    static void dampQuery(){
        var registry=dev.hua.ptcglive.engine.effect.EffectRegistry.standard();
        var boom=BundledCombatData.ability(com.google.gson.JsonParser.parseString("{\"id\":\"self-ko\",\"nameZh\":\"测试自爆\",\"kind\":\"ACTIVATED_ONCE_PER_TURN\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"knock_out\",\"target\":\"SELF\"}]}").getAsJsonObject(),registry);
        var damp=BundledCombatData.ability(com.google.gson.JsonParser.parseString("{\"id\":\"damp\",\"nameZh\":\"湿气\",\"kind\":\"PASSIVE\",\"effectSupport\":\"SUPPORTED\",\"effects\":[{\"type\":\"suppress_self_knockout_abilities\",\"target\":\"SELF\"}]}").getAsJsonObject(),registry);
        var draw=new AbilityDefinition("draw","普通抽牌",new AbilityEffect(AbilityEffect.Opcode.DRAW,1),EffectSupport.SUPPORTED,null);
        UUID a=BattleExperienceTest.A,b=BattleExperienceTest.B;
        for(boolean suppressed:List.of(false,true)){
            var db=new CardDatabase(List.of(BattleExperienceTest.pokemon("a"),BattleExperienceTest.pokemon("b")));
            var combat=new CombatCatalog(Map.of("a",new PokemonProfile(100,0,null,null,0,null,0,1,List.of(),List.of(boom,draw)),"b",new PokemonProfile(100,0,null,null,0,null,0,1,List.of(),suppressed?List.of(damp):List.of())),Map.of());
            var game=new ServerMatch(a,b,BattleExperienceTest.deck(a,"a"),BattleExperienceTest.deck(b,"b"),MatchRulesRegressionTest.RULE,db,combat,new ServerRandom(40));OpeningTestSupport.complete(game,a,b,0);
            while(!game.currentPlayer().equals(a))game.endTurn(game.currentPlayer());UUID host=game.activePokemonView(a).pokemonId();
            check(game.canUseAbility(a,host,"self-ko")==!suppressed,"湿气优化改变自我气绝特性的合法性");check(game.canUseAbility(a,host,"draw"),"湿气错误禁止普通抽牌");
        }
    }
    @SuppressWarnings("unchecked") static List<CardInstance> zone(ServerMatch game,UUID seat,String name){return (List<CardInstance>)field(((Map<UUID,?>)field(game,"players")).get(seat),name);}
    static void privacy(){
        var p=AiPresets.all().getFirst();var q=AiPresets.all().getLast();UUID a=AiPracticeTest.A,b=AiPracticeTest.B;
        var game=new ServerMatch(a,b,AiPracticeTest.deck(p,a),AiPracticeTest.deck(q,b),AiPracticeTest.rules.get(p.rule()),AiPracticeTest.cards,AiPracticeTest.combat,new ServerRandom(81));
        OpeningTestSupport.complete(game,a,b,1);var s=MatchSession.multiplayer(game,a,b);s.addSpectator(AiPracticeTest.C);
        var input=view(s,a);var decision=AiPolicy.choose(input,192,Set.of());var rng=game.randomTranscript();
        // Controlled adversarial fixture: change private cards and orders without changing any public count or field.
        var hand=zone(game,b,"hand");var deck=zone(game,b,"deck");int replacement=-1;
        for(int i=0;i<deck.size();i++)if(!deck.get(i).definitionId().equals(hand.getFirst().definitionId())){replacement=i;break;}
        check(replacement>=0,"隐私夹具缺少不同卡面");var old=hand.set(0,deck.get(replacement));deck.set(replacement,old);
        for(UUID seat:List.of(a,b)){Collections.reverse(zone(game,seat,"deck"));Collections.reverse(zone(game,seat,"prizes"));}
        var changed=view(s,a);check(input.equals(changed),"隐藏区域变化泄露进 AI 输入");check(decision.equals(AiPolicy.choose(changed,192,Set.of())),"AI 根据隐藏区域改变决策");
        String encoded=new com.google.gson.Gson().toJson(changed);String publicJson=new com.google.gson.Gson().toJson(s.view(AiPracticeTest.C,null));
        for(UUID seat:List.of(a,b))for(String z:List.of("deck","prizes","hand"))for(var card:zone(game,seat,z)){
            check(!publicJson.contains(card.instanceId().toString()),"观战泄露隐藏实例");
            if(!seat.equals(a)||!z.equals("hand"))check(!encoded.contains(card.instanceId().toString()),"AI 输入泄露隐藏实例");
        }
        check(rng.equals(game.randomTranscript()),"隐私检查／策略消耗真实随机序列");
    }
    static void settle(MatchSession session){
        var game=session.match();int steps=0;
        while(game.hasPendingEffectChoice()||game.triggerOrderPrompt()!=null){
            check(steps++<150,"选择循环");boolean moved=false;
            for(var player:game.clientView(null).players()){
                var v=view(session,player.id());var choice=AiPolicy.choose(v,steps,Set.of());if(choice.isEmpty())continue;
                var d=choice.get();session.submit(player.id(),player.id(),d.revision(),d.option());moved=true;break;
            }check(moved,"强制选择无动作");
        }
    }
    static void choices(){
        var cases=List.of(
            "{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":2,\"minimum\":2,\"card_category\":\"POKEMON\",\"reveal\":true}",
            "{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":2,\"minimum\":2},{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":2,\"minimum\":0,\"card_category\":\"POKEMON\"}",
            "{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":2,\"minimum\":0}",
            "{\"type\":\"discard_reorder_top\",\"target\":\"DECK\",\"amount\":5,\"minimum_discard\":0}",
            "{\"type\":\"choice\",\"options\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}",
            "{\"type\":\"damage_counter\",\"amount\":4,\"distribution\":\"EXACT\",\"target\":\"SELECT_OPPONENT_POKEMON\",\"target_scope\":\"OPPONENT_FIELD\",\"min_targets\":2,\"max_targets\":2}");
        for(String effects:cases){
            var m=ComposedTrainerIntegrationTest.fixture(ComposedTrainerIntegrationTest.item(effects));UUID a=m.currentPlayer(),b=m.clientView(null).players().stream().filter(p->!p.id().equals(a)).findFirst().orElseThrow().id();
            var s=MatchSession.multiplayer(m,a,b);var start=s.view(a,a);var trainer=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("component")).findFirst().orElseThrow();
            var action=start.options().stream().filter(o->o.enabled()&&o.cardKeys().contains("hand:"+trainer.instanceId())).findFirst().orElseThrow(()->new AssertionError("测试卡当前不可使用 "+effects+" / "+start.options()));
            int hand=m.privateView(a).hand().size();s.submit(a,a,start.revision(),action.index());settle(s);
            check(m.privateView(a).discard().contains(trainer),"整卡未结算");
            if(effects.contains("damage_counter"))check(m.activePokemonView(b).damage()>0&&m.benchPokemonView(b).getFirst().damage()>0,"未满足两目标分配约束");
            if(effects.endsWith("\"minimum\":0}"))check(m.privateView(a).hand().size()==hand-1,"可选弃牌没有合法选择零张");
        }
        var m=ComposedEnergyAttachmentTest.fixture("{\"type\":\"attach_energy\",\"target\":\"SELECT_OWN_POKEMON\",\"source\":\"HAND\",\"amount\":2,\"minimum\":2,\"basic_energy_only\":true,\"energy_type\":\"GRASS\",\"target_scope\":\"OWN_FIELD\",\"distribution\":\"ANY\"}",0);
        UUID a=m.currentPlayer(),b=m.clientView(null).players().stream().filter(p->!p.id().equals(a)).findFirst().orElseThrow().id();var s=MatchSession.multiplayer(m,a,b);
        var start=s.view(a,a);var trainer=m.privateView(a).hand().stream().filter(c->c.definitionId().equals("component")).findFirst().orElseThrow();
        s.submit(a,a,start.revision(),start.options().stream().filter(o->o.enabled()&&o.cardKeys().contains("hand:"+trainer.instanceId())).findFirst().orElseThrow().index());settle(s);
        check(m.activePokemonView(a).attachedEnergyIds().size()+m.benchPokemonView(a).stream().mapToInt(p->p.attachedEnergyIds().size()).sum()==2,"实际分配未附着两张");
    }
    static void opponentResponse(){for(var m:List.of(OpponentEnergyAttachmentTest.game(),OpponentEnergyAttachmentTest.eventGame())){UUID a=OpponentEnergyAttachmentTest.A,b=OpponentEnergyAttachmentTest.B;
        var s=MatchSession.multiplayer(m,a,b);var v=s.view(a,a);var attack=v.options().stream().filter(o->o.enabled()&&o.labelZh().startsWith("攻击：")).findFirst().orElseThrow();s.submit(a,a,v.revision(),attack.index());
        check(m.pendingChoicePlayer().equals(b)&&m.currentPlayer().equals(a),"没有进入对手回合回应");settle(s);check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(b),"对手回合回应未续接");
    }}
    static void winningAndPass(){
        var m=BattleExperienceTest.fixture(null,null,new AttackDefinition("win","确定击倒",Map.of(),600,EnergyType.FIRE,EffectSupport.SUPPORTED,null));var s=MatchSession.multiplayer(m,BattleExperienceTest.A,BattleExperienceTest.B);
        var o=choose(s,BattleExperienceTest.A);check(o.labelZh().startsWith("攻击："),"放弃可确认获胜攻击");var v=s.view(BattleExperienceTest.A,BattleExperienceTest.A);s.submit(BattleExperienceTest.A,BattleExperienceTest.A,v.revision(),o.index());
        check(m.result()!=null&&m.result().winner().equals(BattleExperienceTest.A),"确定获胜场景没有获胜");
        var no=BattleExperienceTest.fixture(null,null,new AttackDefinition("cost","无法进攻",Map.of(EnergyType.FIRE,5),60,EnergyType.FIRE,EffectSupport.SUPPORTED,null));var ns=MatchSession.multiplayer(no,BattleExperienceTest.A,BattleExperienceTest.B);
        // Let policy use useful bench actions, then it must end the turn rather than loop.
        for(int i=0;i<10&&no.currentPlayer().equals(BattleExperienceTest.A);i++){var sv=ns.view(BattleExperienceTest.A,BattleExperienceTest.A);var next=choose(ns,BattleExperienceTest.A);ns.submit(BattleExperienceTest.A,BattleExperienceTest.A,sv.revision(),next.index());}
        check(no.currentPlayer().equals(BattleExperienceTest.B),"无可进攻时未结束回合");
    }
    static void strategicEnergy(){
        // Snapshot-only strategic scenarios are separate from the complete real-engine games above.
        var game=BattleExperienceTest.fixture(null,null,new AttackDefinition("cost","草系攻击",Map.of(EnergyType.GRASS,1),40,EnergyType.GRASS,EffectSupport.SUPPORTED,null));
        UUID a=BattleExperienceTest.A,b=BattleExperienceTest.B;var s=MatchSession.multiplayer(game,a,b);var v=view(s,a);var mon=v.snapshot().game().players().stream().filter(p->p.id().equals(a)).findFirst().orElseThrow().active();
        var facts=new HashMap<>(v.cards());CardInstance grass=new CardInstance(new UUID(8,1),a,"grass"),water=new CardInstance(new UUID(8,2),a,"water"),evo=new CardInstance(new UUID(8,3),a,"evo");
        facts.put("grass",new AiView.Card(def("grass",CardCategory.ENERGY,true),null,null,EnergyProfile.basic(EnergyType.GRASS)));
        facts.put("water",new AiView.Card(def("water",CardCategory.ENERGY,true),null,null,EnergyProfile.basic(EnergyType.WATER)));
        facts.put("evo",new AiView.Card(def("evo",CardCategory.POKEMON,false),new PokemonProfile(550,0,mon.topCardId(),null,0,null,0,1,List.of(new AttackDefinition("e","进化攻击",Map.of(EnergyType.GRASS,3),120,EnergyType.GRASS,EffectSupport.SUPPORTED,null))),null,null));
        var options=List.of(new MatchSession.Option(0,"附着 水 → 主攻手",List.of("hand:"+water.instanceId(),"field:"+mon.pokemonId())),new MatchSession.Option(1,"附着 草 → 主攻手",List.of("hand:"+grass.instanceId(),"field:"+mon.pokemonId())),new MatchSession.Option(2,"结束回合"));
        var input=changed(v,List.of(grass,water),options,facts,null,null);
        check(AiPolicy.choose(input,1,Set.of()).orElseThrow().option()==1,"未选择所需能量属性");
        var selection=new EffectCardPrompt(new UUID(9,9),1,"检索所需卡牌",1,1,List.of(water,grass),List.of());
        options=List.of(new MatchSession.Option(0,"选择卡牌：水",List.of("effect:"+water.instanceId())),new MatchSession.Option(1,"选择卡牌：草",List.of("effect:"+grass.instanceId())));
        check(AiPolicy.choose(changed(v,List.of(),options,facts,selection,null),1,Set.of()).orElseThrow().option()==1,"检索没有按攻击所需属性选择");
        var energy=new AiView.Energy(List.of(new EnergyProfile.Unit(Set.of(EnergyType.GRASS))),Map.of("cost",Map.of(EnergyType.GRASS,1)),Map.of("cost",true));
        options=List.of(new MatchSession.Option(0,"进化：主攻手→进化",List.of("hand:"+evo.instanceId(),"field:"+mon.pokemonId())),new MatchSession.Option(1,"攻击：草系攻击",List.of("field:"+mon.pokemonId())),new MatchSession.Option(2,"结束回合"));
        var ev=changed(v,List.of(evo),options,facts,null,null);ev=new AiView(ev.snapshot(),ev.cards(),Map.of(mon.pokemonId(),energy));
        check(AiPolicy.choose(ev,1,Set.of()).orElseThrow().option()==1,"无条件进化导致失去现成攻击");
        options=List.of(new MatchSession.Option(0,"附着 草 → 主攻手",List.of("hand:"+grass.instanceId(),"field:"+mon.pokemonId())),new MatchSession.Option(1,"结束回合"));
        var prepare=changed(v,List.of(evo,grass),options,facts,null,null);prepare=new AiView(prepare.snapshot(),prepare.cards(),Map.of(mon.pokemonId(),energy));
        check(AiPolicy.choose(prepare,1,Set.of()).orElseThrow().option()==0,"没有为手牌中进化卡准备额外能量");
        var noEvolution=changed(v,List.of(grass),options,facts,null,null);noEvolution=new AiView(noEvolution.snapshot(),noEvolution.cards(),Map.of(mon.pokemonId(),energy));
        check(AiPolicy.choose(noEvolution,1,Set.of()).orElseThrow().option()==1,"向已经满足攻击且无进化计划的对象浪费附能");
        var draw=new CardInstance(new UUID(8,4),a,"draw");facts.put("draw",new AiView.Card(def("draw",CardCategory.TRAINER,false),null,new TrainerProfile(TrainerCategory.ITEM,new TrainerEffect(TrainerEffect.Opcode.DRAW,3,0)),null));
        options=List.of(new MatchSession.Option(0,"使用：抽牌",List.of("hand:"+draw.instanceId())),new MatchSession.Option(1,"结束回合"));
        check(AiPolicy.choose(changed(v,List.of(draw),options,facts,null,2),1,Set.of()).orElseThrow().option()==1,"主动无意义抽空牌库");
    }
    static void noPointlessRetreat(){
        var game=BattleExperienceTest.fixture(null,null,new AttackDefinition("hit","直接攻击",Map.of(),60,EnergyType.FIRE,EffectSupport.SUPPORTED,null));
        UUID a=BattleExperienceTest.A,b=BattleExperienceTest.B;
        var card=game.privateView(a).hand().getFirst();game.playBasicPokemonToBench(a,card.instanceId());
        var session=MatchSession.multiplayer(game,a,b);var v=view(session,a);
        check(v.snapshot().options().stream().anyMatch(o->o.enabled()&&o.labelZh().startsWith("撤退")),"换位测试未提供合法撤退");
        var d=AiPolicy.choose(v,15,Set.of()).orElseThrow();check(d.category().equals("攻击"),"同等攻击手之间无意义撤退");
        var retreat=v.snapshot().options().stream().filter(o->o.enabled()&&o.labelZh().startsWith("撤退")).findFirst().orElseThrow();
        var onlyRetreat=changed(v,List.of(),List.of(new MatchSession.Option(0,retreat.labelZh(),retreat.cardKeys()),new MatchSession.Option(1,"结束回合")),v.cards(),null,null);
        check(AiPolicy.choose(onlyRetreat,15,Set.of(AiPolicy.optionalKey(retreat))).orElseThrow().option()==1,"重复执行已尝试换位");
    }
    static CardDefinition def(String id,CardCategory kind,boolean energy){return new CardDefinition(id,id,kind,kind==CardCategory.TRAINER?TrainerCategory.ITEM:TrainerCategory.NONE,"H",Set.of("international"),false,energy,60,Map.of(),EffectSupport.SUPPORTED,null);}
    static AiView changed(AiView v,List<CardInstance> hand,List<MatchSession.Option> options,Map<String,AiView.Card> facts,EffectCardPrompt selection,Integer deckSize){
        var s=v.snapshot();var g=s.game();var ps=g.players().stream().map(p->p.id().equals(g.viewingSeat())?new MatchClientView.Player(p.id(),hand.size(),deckSize==null?p.deckSize():deckSize,p.prizeCount(),p.active(),p.bench(),p.discard(),p.lostZone(),p.gxUsed(),p.vstarUsed(),p.faceUpPrizes()):p).toList();
        var cg=new MatchClientView(g.matchId(),g.viewingSeat(),g.phase(),g.turnNumber(),g.currentPlayer(),g.choicePlayer(),g.result(),ps,hand,g.prizeSlots(),g.setupPlayers(),g.mulliganReveals(),g.stadium(),g.resolvingTrainer(),selection,g.effectReveal(),g.effectAttachment(),g.triggerOrder(),g.damageAllocation(),g.resolvingAbility(),g.resolvingAttack(),g.toolOverflow(),g.resolvingTrainerCards());
        return new AiView(new MatchSession.Snapshot(s.revision(),s.practice(),s.controllableSeats(),cg,options,s.cardNames(),s.publicCues(),s.attackBlockZh(),s.guidance(),s.events()),facts,v.energy());
    }
    static void supportBoundary(){
        var blocked=ComposedTrainerIntegrationTest.item("{\"type\":\"search_deck\",\"target\":\"DECK\",\"amount\":2,\"minimum\":0,\"card_category\":\"ENERGY\",\"basic_energy_only\":true,\"distinct_energy_types\":true}");
        var db=new CardDatabase(List.of(def("restricted",CardCategory.TRAINER,false)));var catalog=new CombatCatalog(Map.of(),Map.of(),Map.of("restricted",blocked));
        check(AiSupport.problems(List.of(new CardInstance(new UUID(1,1),AiPracticeTest.A,"restricted")),db,catalog).isEmpty(),"已验证的不同属性选能仍被拦截");
        check(AiSupport.opponentProblems(List.of(new CardInstance(new UUID(1,1),AiPracticeTest.A,"restricted")),db,catalog).isEmpty(),"把玩家自己完成的选择也禁止了");
    }
}
