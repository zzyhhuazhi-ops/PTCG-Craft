import com.google.gson.*;
import dev.hua.ptcglive.ai.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.rules.*;
import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;

/** Reuses reviewed test boards, but only after verifying the exact requested printed program. */
final class AiOperationFixtures {
    static final CombatCatalog DATA=BundledCombatData.load();
    static final CardDatabase CARDS=BundledRules.loadCardDefinitions();
    record Factory(String owner,String method,String parameter,Set<String> ids) {}
    record Attempt(Factory factory,String sourceId,int preparation) {}
    record Prepared(MatchSession session,UUID actor,MatchSession.Option option,String fixture) {}
    static final List<Factory> FACTORIES=load();
    static final Map<String,Attempt> FOUND=new HashMap<>();
    static final Set<String> MISSING=new HashSet<>();
    static final Map<String,List<String>> ALIASES=new HashMap<>();
    static final Map<String,String> DIAGNOSTICS=new HashMap<>();
    static List<Factory> load(){
        try {
            var result=new ArrayList<Factory>();
            for(var value:JsonParser.parseString(Files.readString(Path.of("tests/fixtures/ai-operation-factories.json"))).getAsJsonArray()){
                var item=value.getAsJsonObject();var ids=new HashSet<String>();
                item.getAsJsonArray("referencedCardIds").forEach(id->ids.add(id.getAsString()));
                result.add(new Factory(item.get("class").getAsString(),item.get("method").getAsString(),item.get("parameter").getAsString(),ids));
            }
            result.sort(Comparator.comparingInt(f->f.method().equals("aiGame")?0:1));return result;
        }catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    static Object profile(String id){
        if(DATA.trainerProfiles().containsKey(id))return DATA.trainerProfiles().get(id);
        if(DATA.energyProfiles().containsKey(id))return DATA.energyProfiles().get(id);
        return DATA.pokemonProfiles().get(id);
    }
    static List<String> aliases(String id){return ALIASES.computeIfAbsent(id,key->{
        var result=new ArrayList<String>();result.add(id);Object expected=profile(id);
        var keys=new TreeSet<String>();keys.addAll(DATA.trainerProfiles().keySet());keys.addAll(DATA.energyProfiles().keySet());keys.addAll(DATA.pokemonProfiles().keySet());
        if(expected!=null)for(String other:keys)if(!other.equals(id)&&expected.equals(profile(other)))result.add(other);
        return result;
    });}
    static ActionType type(String id,String operation){
        if(operation.equals("trainer"))return ActionType.TRAINER;
        if(operation.equals("energy-attachment"))return ActionType.ATTACH;
        if(operation.equals("stadium-activation"))return ActionType.STADIUM;
        var p=DATA.pokemonProfiles().get(id);
        if(p!=null&&p.attacks().stream().anyMatch(a->a.id().equals(operation)))return ActionType.ATTACK;
        if(p!=null&&p.abilities().stream().anyMatch(a->a.id().equals(operation)))return ActionType.ABILITY;
        return null;
    }
    static MatchSession session(ServerMatch m){var seats=m.clientView(null).players();return MatchSession.multiplayer(m,seats.get(0).id(),seats.get(1).id());}
    static Optional<MatchSession.Option> exactOption(MatchSession session,String id,String operation,ActionType type){
        var m=session.match();UUID actor=m.currentPlayer();var view=AiPracticeScenarios.view(session,actor);
        var expected=profile(id);
        return view.snapshot().options().stream().filter(o->o.enabled()&&o.type()==type)
            .filter(o->AiCardOperationTest.matchesDeclaredOperation(m,actor,operation,o)).filter(o->{
                if(type==ActionType.TRAINER||type==ActionType.ATTACH)return m.privateView(actor).hand().stream().anyMatch(c->{
                    var card=view.cards().get(c.definitionId());return card!=null&&o.cardKeys().contains("hand:"+c.instanceId())
                        &&Objects.equals(expected,type==ActionType.TRAINER?card.trainer():card.energy());
                });
                if(type==ActionType.STADIUM){var stadium=m.clientView(actor).stadium();return stadium!=null&&view.cards().get(stadium.definitionId())!=null&&Objects.equals(expected,view.cards().get(stadium.definitionId()).trainer());}
                if(type==ActionType.ATTACK){var actual=DATA.pokemonProfiles().get(id).attacks().stream().filter(a->a.id().equals(operation)).findFirst().orElseThrow();return m.activeAttacks(actor).contains(actual);}
                if(type==ActionType.ABILITY){
                    var actual=DATA.pokemonProfiles().get(id).abilities().stream().filter(a->a.id().equals(operation)).findFirst().orElseThrow();
                    var hosts=new ArrayList<>(m.benchPokemonView(actor));hosts.add(m.activePokemonView(actor));
                    return hosts.stream().filter(h->o.cardKeys().contains("field:"+h.pokemonId())).anyMatch(h->{
                        var card=view.cards().get(h.topCardId());return card!=null&&card.pokemon()!=null&&card.pokemon().abilities().contains(actual);
                    });
                }
                return false;
            }).findFirst();
    }
    static Prepared find(String id,String operation,AiDifficulty level){
        ActionType type=type(id,operation);if(type==null)return null;String key=id+"/"+operation;
        if(MISSING.contains(key))return null;
        var cached=FOUND.get(key);if(cached!=null){var ready=attempt(cached,id,operation,type,level);if(ready!=null)return ready;}
        for(var factory:FACTORIES){
            var ids=factory.parameter().equals("String")?aliases(id).stream().filter(factory.ids()::contains).toList():List.of(id);
            for(String source:ids)for(int preparation=0;preparation<2;preparation++){
                var trial=new Attempt(factory,source,preparation);var ready=attempt(trial,id,operation,type,level);
                if(ready!=null){FOUND.put(key,trial);return ready;}
            }
        }
        for(int preparation=0;preparation<9;preparation++){
            var trial=new Attempt(null,id,preparation);var ready=attempt(trial,id,operation,type,level);
            if(ready!=null){FOUND.put(key,trial);return ready;}
        }
        MISSING.add(key);return null;
    }
    static Prepared attempt(Attempt trial,String id,String operation,ActionType type,AiDifficulty level){
        try {
            var f=trial.factory();Class<?> parameter;Object argument;ServerMatch m;
            var pokemon=DATA.pokemonProfiles().get(id);
            if(f==null)m=generic(id,operation,type,trial.preparation());
            else {switch(f.parameter()){
                case "String" -> {parameter=String.class;argument=trial.sourceId();}
                case "AttackDefinition" -> {if(type!=ActionType.ATTACK)return null;parameter=AttackDefinition.class;argument=pokemon.attacks().stream().filter(a->a.id().equals(operation)).findFirst().orElseThrow();}
                case "AbilityDefinition" -> {if(type!=ActionType.ABILITY)return null;parameter=AbilityDefinition.class;argument=pokemon.abilities().stream().filter(a->a.id().equals(operation)).findFirst().orElseThrow();}
                case "TrainerProfile" -> {if(type!=ActionType.TRAINER&&type!=ActionType.STADIUM)return null;parameter=TrainerProfile.class;argument=DATA.trainerProfiles().get(id);}
                case "EnergyProfile" -> {if(type!=ActionType.ATTACH)return null;parameter=EnergyProfile.class;argument=DATA.energyProfiles().get(id);}
                default -> {return null;}
            }
            var method=Class.forName(f.owner()).getDeclaredMethod(f.method(),parameter);method.setAccessible(true);
            m=(ServerMatch)method.invoke(null,argument);}
            var session=session(m);
            if(f!=null&&trial.preparation()==1)AiCardOperationTest.preparePublicResources(m);
            if(type==ActionType.STADIUM&&m.clientView(m.currentPlayer()).stadium()==null){
                var play=exactOption(session,id,"trainer",ActionType.TRAINER);if(play.isEmpty())return null;
                UUID actor=m.currentPlayer();session.submit(actor,actor,session.view(actor,actor).revision(),play.get().index());AiCardOperationTest.settle(session,level);
            }
            var option=exactOption(session,id,operation,type);
            return option.map(o->new Prepared(session,m.currentPlayer(),o,(f==null?"typed-resource-board":f.owner()+"."+f.method())+"("+trial.sourceId()+"), preparation="+trial.preparation())).orElse(null);
        }catch(ReflectiveOperationException|RuntimeException|AssertionError e){
            if(trial.factory()==null)DIAGNOSTICS.put(id+"/"+operation,e.toString());
            return null;
        }
    }
    static void arguments(ConditionDefinition c,List<Map<String,String>> result){result.add(c.arguments());c.children().forEach(x->arguments(x,result));}
    static void arguments(EffectDefinition e,List<Map<String,String>> result){result.add(e.arguments());e.conditions().forEach(x->arguments(x,result));e.effects().forEach(x->arguments(x,result));}
    static void conditions(EffectDefinition e,List<ConditionDefinition> result){result.addAll(e.conditions());e.effects().forEach(x->conditions(x,result));}
    static void conditions(ConditionDefinition c,List<ConditionDefinition> result){result.add(c);c.children().forEach(x->conditions(x,result));}
    static boolean compare(int value,String comparison,int target){return switch(comparison){case "<="->value<=target;case ">="->value>=target;case "<"->value<target;case ">"->value>target;default->value==target;};}
    static boolean prizeMatches(ConditionDefinition c,int count){
        if(c.type()==ConditionType.OR)return c.children().stream().anyMatch(x->prizeMatches(x,count));
        if(c.type()==ConditionType.AND)return c.children().stream().allMatch(x->prizeMatches(x,count));
        if(c.type()!=ConditionType.PRIZE_COUNT||!c.arguments().containsKey("value")||!"OPPONENT_ACTIVE".equals(c.arguments().get("target")))return true;
        return compare(count,c.arguments().getOrDefault("comparison",">="),Integer.parseInt(c.arguments().get("value")));
    }
    static String first(List<Map<String,String>> args,String... keys){for(String key:keys)for(var a:args)if(a.containsKey(key))return a.get(key).split("\\|")[0];return null;}
    static ServerMatch generic(String id,String operation,ActionType type,int preparation){
        boolean resources=preparation>=5;int mode=resources?preparation-5:preparation;
        var c=new GeneratedRuleBatch28Test.Config();var args=new ArrayList<Map<String,String>>();EffectProgram program=EffectProgram.EMPTY;
        if(type==ActionType.TRAINER||type==ActionType.STADIUM){c.trainer=DATA.trainerProfiles().get(id);program=type==ActionType.STADIUM?c.trainer.activation().program():c.trainer.effectProgram();}
        if(type==ActionType.ABILITY){c.ownAbility=DATA.pokemonProfiles().get(id).abilities().stream().filter(a->a.id().equals(operation)).findFirst().orElseThrow();c.source=c.ownAbility;program=c.ownAbility.effectProgram();}
        if(type==ActionType.ATTACK){c.attack=DATA.pokemonProfiles().get(id).attacks().stream().filter(a->a.id().equals(operation)).findFirst().orElseThrow();program=c.attack.effectProgram();c.specialEnergy=AiCardOperationTest.rainbow();c.defenderFamilies=Set.of(PokemonFamily.V);}
        for(var t:program.triggers()){t.conditions().forEach(x->arguments(x,args));t.effects().forEach(x->arguments(x,args));}
        String rules=new Gson().toJson(program);
        String playedCard=rules.contains("CARD_PLAYED_THIS_TURN")?first(args,"definition_ids"):null;
        if(resources&&playedCard!=null&&DATA.trainerProfiles().containsKey(playedCard))for(var t:DATA.trainerProfiles().get(playedCard).effectProgram().triggers())t.effects().forEach(x->arguments(x,args));
        if(rules.contains("SECOND_PLAYER_FIRST_TURN")){c.later=false;c.second=true;}
        else if(rules.contains("OWN_FIRST_TURN")&&!rules.contains("NOT"))c.later=false;
        boolean previousKo=rules.contains("KNOCKED_OUT_LAST_TURN")||rules.contains("KNOCKED_OUT_BY_ATTACK_LAST_TURN");
        boolean opponentPrizes=rules.contains("PRIZE_COUNT");
        int koRounds=1;
        if(opponentPrizes){
            var conditions=new ArrayList<ConditionDefinition>();for(var t:program.triggers()){conditions.addAll(t.conditions());t.effects().forEach(x->conditions(x,conditions));}
            int remaining=2;
            for(int candidate=5;candidate>=2;candidate--){final int count=candidate;
                if(conditions.stream().allMatch(x->prizeMatches(x,count))){remaining=candidate;break;}}
            c.ownPrizes=6-remaining==4?2:6-remaining;koRounds=(6-remaining)/c.ownPrizes;
        }
        if(previousKo||opponentPrizes)c.defenderAttack=GeneratedRuleBatch8Test.hit(1000,false);
        String energy=first(args,"energy_type","pokemon_or_basic_energy_type");if(energy!=null)c.energyType=EnergyType.valueOf(energy);
        String host=first(args,"target_pokemon_type","pokemon_types","pokemon_or_basic_energy_type","pokemon_type");if(host!=null)c.ownTypes=Set.of(EnergyType.valueOf(host));
        String family=first(args,"target_pokemon_families","pokemon_families","pokemon_family","battle_style");if(family!=null){c.ownFamilies=Set.of(PokemonFamily.valueOf(family));c.defenderFamilies=c.ownFamilies;}
        if(rules.contains("IS_GX"))c.ownFamilies=Set.of(PokemonFamily.GX);
        c.ownName=first(args,"target_name_contains","pokemon_name_contains","pokemon_names","name_contains");
        var allConditions=new ArrayList<ConditionDefinition>();for(var t:program.triggers()){t.conditions().forEach(x->conditions(x,allConditions));t.effects().forEach(x->conditions(x,allConditions));}
        for(var condition:allConditions)if(condition.type()==ConditionType.NAME_CONTAINS)c.ownName=condition.arguments().get("value");
        if(rules.contains("festival-lead"))c.ownAbility=DATA.pokemonProfiles().values().stream().flatMap(p->p.abilities().stream()).filter(a->a.id().equals("festival-lead")).findFirst().orElseThrow();
        var names=args.stream().filter(a->a.containsKey("pokemon_names")).map(a->a.get("pokemon_names").split("\\|")[0]).distinct().toList();
        if(names.size()>1){var n=new HashMap<String,String>();for(int i=0;i<Math.min(3,names.size());i++)n.put(List.of("attacker","source","new").get(i),names.get(i));c.names=n;}
        boolean stadium=type==ActionType.ABILITY&&rules.contains("STADIUM_IN_PLAY");if(stadium)c.trainer=DynamicPassiveModifierTest.plainStadium();
        String hp=first(args,"hp_max");if(hp!=null)c.ownHp=Integer.parseInt(hp);
        if(type==ActionType.ATTACH){c.specialEnergy=DATA.energyProfiles().get(id);var rule=c.specialEnergy.attachmentRule();if(!rule.pokemonTypes().isEmpty())c.ownTypes=rule.pokemonTypes();c.ownFamilies=rule.families();c.ownName=rule.pokemonNameContains();}
        c.evolvedTypes=c.ownTypes;c.evolvedAbility=c.ownAbility;var template=GeneratedRuleBatch28Test.fixture(c);
        final var m=resources?resourceBoard(template,c,args,names):template;
        UUID actor=m.currentPlayer();
        if(names.size()>3)for(var bench:m.benchPokemonView(actor))m.movePokemonByEffect(actor,actor,bench.pokemonId(),LeavePlayDestination.HAND);
        if(type==ActionType.ABILITY||names.size()>1||opponentPrizes)m.playBasicPokemonToBench(actor,GeneratedRuleBatch28Test.hand(m,actor,"source").instanceId());
        if(names.size()>2)m.playBasicPokemonToBench(actor,GeneratedRuleBatch28Test.hand(m,actor,"new").instanceId());
        if(resources&&names.size()>3)for(int i=3;i<names.size();i++)m.playBasicPokemonToBench(actor,GeneratedRuleBatch28Test.hand(m,actor,"aux-partner-"+i).instanceId());
        if(stadium)m.playTrainer(actor,GeneratedRuleBatch28Test.hand(m,actor,"trainer").instanceId(),null);
        if(mode==4||resources&&mode==3){
            m.evolveByEffect(actor,m.activePokemonView(actor).pokemonId(),GeneratedRuleBatch28Test.hand(m,actor,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            if(resources)m.evolveByEffect(actor,m.activePokemonView(actor).pokemonId(),GeneratedRuleBatch28Test.hand(m,actor,"aux-stage2").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            m.endTurn(actor);UUID opponent=m.currentPlayer();
            m.evolveByEffect(opponent,m.activePokemonView(opponent).pokemonId(),GeneratedRuleBatch28Test.hand(m,opponent,"defender_evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            if(resources)m.evolveByEffect(opponent,m.activePokemonView(opponent).pokemonId(),GeneratedRuleBatch28Test.hand(m,opponent,"aux-defender-stage2").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
            m.endTurn(opponent);
        }
        if(type==ActionType.ATTACH&&c.specialEnergy.attachmentRule().stages().contains(EnergyAttachmentRule.Stage.EVOLUTION))m.evolveByEffect(actor,m.activePokemonView(actor).pokemonId(),GeneratedRuleBatch28Test.hand(m,actor,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        if(resources){
            if(type!=ActionType.STADIUM&&m.clientView(actor).stadium()==null)m.playTrainer(actor,GeneratedRuleBatch28Test.hand(m,actor,"aux-stadium").instanceId(),null);
            for(var seat:m.clientView(null).players()){
                String tool=seat.id().equals(actor)&&rules.contains("HAS_TOOL_DEFINITION")?first(args,"definition_ids"):"aux-tool";
                m.attachToolByEffect(actor,seat.id(),EffectSourceZone.HAND,GeneratedRuleBatch28Test.hand(m,seat.id(),tool).instanceId(),seat.id(),m.activePokemonView(seat.id()).pokemonId());
            }
            if(rules.contains("CARD_PLAYED_THIS_TURN")&&!rules.contains("definition_ids"))m.playTrainer(actor,GeneratedRuleBatch28Test.hand(m,actor,"aux-supporter").instanceId(),null);
            if(playedCard!=null){m.playTrainer(actor,GeneratedRuleBatch28Test.hand(m,actor,playedCard).instanceId(),null);AiCardOperationTest.settle(session(m),AiDifficulty.NORMAL);}
        }
        if(mode>0){
            for(var seat:m.clientView(null).players()){
                var hosts=new ArrayList<>(m.benchPokemonView(seat.id()));hosts.add(m.activePokemonView(seat.id()));
                for(var mon:hosts){
                    int counters=Math.min(5,(mon.hitPoints()-10)/10);if(rules.contains("HP_BELOW")||rules.contains("remaining_hp_max"))counters=(mon.hitPoints()-30)/10;
                    while(counters>0){int part=Math.min(60,counters);m.placeDamageCountersByEffect(actor,seat.id(),mon.pokemonId(),part);counters-=part;}
                    if(type!=ActionType.ATTACH&&!rules.contains("DISCARD"))for(var card:m.privateView(seat.id()).hand().stream().filter(x->x.definitionId().equals("energy")).limit(1).toList())m.attachEnergyByEffect(actor,seat.id(),EffectSourceZone.HAND,card.instanceId(),seat.id(),mon.pokemonId());
                }
                if(rules.contains("DISCARD")&&!rules.contains("as_cost"))m.discardHandCardsByEffect(actor,seat.id(),m.privateView(seat.id()).hand().stream().filter(x->x.definitionId().equals("energy")).limit(3).map(CardInstance::instanceId).toList());
                if(resources)m.discardHandCardsByEffect(actor,seat.id(),m.privateView(seat.id()).hand().stream().filter(x->!Set.of("trainer","energy","evolved","defender_evolved").contains(x.definitionId())).map(CardInstance::instanceId).toList());
                var cards=m.privateView(seat.id()).hand().stream().filter(x->!x.definitionId().equals("trainer")).limit(8).map(CardInstance::instanceId).toList();m.discardHandCardsByEffect(actor,seat.id(),cards);
            }
        }
        if(mode==2||mode==3&&!resources){
            var keep=new HashSet<UUID>();m.privateView(actor).hand().stream().filter(x->x.definitionId().equals("trainer")).limit(type==ActionType.TRAINER||type==ActionType.STADIUM?1:0).forEach(x->keep.add(x.instanceId()));
            if(mode==2&&!rules.contains("HAND_SIZE"))m.privateView(actor).hand().stream().filter(x->x.definitionId().equals("energy")).limit(2).forEach(x->keep.add(x.instanceId()));
            var discard=m.privateView(actor).hand().stream().filter(x->!keep.contains(x.instanceId())).map(CardInstance::instanceId).toList();if(!discard.isEmpty())m.discardHandCardsByEffect(actor,actor,discard);
        }
        if(rules.contains("BECAME_ACTIVE_THIS_TURN"))m.switchActiveByEffect(actor,actor,m.benchPokemonView(actor).getFirst().pokemonId());
        if(previousKo||opponentPrizes)for(int round=0;round<koRounds;round++){m.endTurn(actor);UUID opponent=m.currentPlayer();m.attack(opponent,c.defenderAttack.id());AiCardOperationTest.settle(session(m),AiDifficulty.NORMAL);}
        if(rules.contains("LOST_ZONE_COUNT"))for(var card:m.privateView(actor).hand().stream().filter(x->!x.definitionId().equals("trainer")).limit(10).toList())m.sendToLostZoneByEffect(actor,EffectSourceZone.HAND,card.instanceId());
        if(rules.contains("HAS_STATUS")&&rules.contains("POISONED"))m.applySpecialConditionByEffect(actor,m.clientView(null).players().stream().map(MatchClientView.Player::id).filter(p->!p.equals(actor)).findFirst().orElseThrow(),SpecialCondition.POISONED);
        return m;
    }
    static ServerMatch resourceBoard(ServerMatch template,GeneratedRuleBatch28Test.Config c,List<Map<String,String>> args,List<String> names){
        var original=(CombatCatalog)AiPracticeScenarios.field(template,"combat");
        var definitions=new LinkedHashMap<String,CardDefinition>();((CardDatabase)AiPracticeScenarios.field(template,"definitions")).all().forEach(d->definitions.put(d.id(),d));
        var pokemon=new HashMap<>(original.pokemonProfiles());var trainers=new HashMap<>(original.trainerProfiles());var energies=new HashMap<>(original.energyProfiles());
        trainers.put("aux-supporter",new TrainerProfile(TrainerCategory.SUPPORTER,new TrainerEffect(TrainerEffect.Opcode.DRAW,1,0)));
        trainers.put("aux-tool",DynamicPassiveModifierTest.plainTool());trainers.put("aux-stadium",DynamicPassiveModifierTest.plainStadium());energies.put("aux-special",EnergyProfile.fixed(EnergyType.COLORLESS,2));
        var extra=new LinkedHashSet<>(List.of("aux-supporter","aux-tool","aux-stadium","aux-special","aux-stage2","aux-defender-stage2"));
        pokemon.put("aux-stage2",new PokemonProfile(1000,0,"evolved",null,0,null,0,1,List.of(c.attack),c.ownAbility==null?List.of():List.of(c.ownAbility),c.ownTypes.iterator().next(),names.isEmpty()?null:names.getFirst(),null));
        pokemon.put("aux-defender-stage2",new PokemonProfile(1000,0,"defender_evolved",null,0,null,0,1,List.of(c.defenderAttack),List.of(),c.defenderFallback));
        for(int i=3;i<names.size();i++){String key="aux-partner-"+i;extra.add(key);pokemon.put(key,new PokemonProfile(1000,0,null,null,0,null,0,1,List.of(c.attack),List.of(),c.ownTypes.iterator().next(),names.get(i),null));}
        for(var a:args)if(a.containsKey("definition_ids")){
            String key=a.get("definition_ids").split("\\|")[0];var definition=CARDS.find(key).orElseThrow();definitions.put(key,definition);extra.add(key);
            if(DATA.trainerProfiles().containsKey(key))trainers.put(key,DATA.trainerProfiles().get(key));
            if(DATA.energyProfiles().containsKey(key))energies.put(key,DATA.energyProfiles().get(key));
            if(DATA.pokemonProfiles().containsKey(key))pokemon.put(key,DATA.pokemonProfiles().get(key));
        }
        for(String key:extra)if(!definitions.containsKey(key)){
            var trainer=trainers.get(key);boolean mon=pokemon.containsKey(key);var category=trainer==null?TrainerCategory.NONE:trainer.category();
            definitions.put(key,new CardDefinition(key,key.equals("aux-supporter")?"火箭队支援者":key,mon?CardCategory.POKEMON:trainer==null?CardCategory.ENERGY:CardCategory.TRAINER,category,"H",Set.of("international"),mon&&key.startsWith("aux-partner"),false,60,Map.of(),EffectSupport.SUPPORTED,null,mon?c.ownFamilies:Set.of(),mon?c.ownTypes:Set.of(),Set.of(BattleStyle.SINGLE_STRIKE)));
        }
        var combat=new CombatCatalog(pokemon,Map.of(),trainers,energies);UUID a=GeneratedRuleBatch28Test.A,b=GeneratedRuleBatch28Test.B;
        var decks=new ArrayList<List<CardInstance>>();
        for(UUID owner:List.of(a,b)){
            var ids=new ArrayList<String>();String base=owner.equals(a)?"attacker":"defender";
            for(String key:List.of(base,"source","new","energy","trainer",owner.equals(a)?"evolved":"defender_evolved"))for(int i=0;i<(key.equals(base)?10:key.equals("energy")?8:2);i++)ids.add(key);
            for(String key:extra){ids.add(key);ids.add(key);}while(ids.size()<60)ids.add(base);if(ids.size()!=60)throw new AssertionError("Resource fixture exceeds deck size");
            var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids.get(i)));decks.add(deck);
        }
        for(int seed=0;seed<500;seed++){
            var m=new ServerMatch(a,b,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(definitions.values()),combat,new ServerRandom(seed));
            m.callCoin(a,CoinSide.HEADS);UUID winner=m.publicCoinResult()==CoinSide.HEADS?a:b;m.chooseWhoGoesFirst(winner,winner.equals(c.second?b:a));
            if(List.of(a,b).stream().anyMatch(p->m.privateView(p).hand().stream().filter(x->x.definitionId().equals(p.equals(a)?"attacker":"defender")).count()<2))continue;
            for(UUID p:List.of(a,b)){var basics=m.privateView(p).hand().stream().filter(x->x.definitionId().equals(p.equals(a)?"attacker":"defender")).toList();m.chooseOpeningActive(p,basics.get(0).instanceId());m.placeOpeningBench(p,basics.get(1).instanceId());m.startAfterOpening(p);}OpeningTestSupport.complete(m,a,b,1);
            for(UUID p:List.of(a,b))m.drawCardsByEffect(m.currentPlayer(),p,32);
            if(c.later){m.endTurn(a);m.endTurn(b);}else if(c.second)m.endTurn(b);
            var required=new HashSet<>(extra);required.addAll(List.of("trainer","source","new","evolved"));
            if(required.stream().allMatch(key->m.privateView(a).hand().stream().anyMatch(card->card.definitionId().equals(key)))&&List.of("defender_evolved","aux-defender-stage2","aux-tool").stream().allMatch(key->m.privateView(b).hand().stream().anyMatch(card->card.definitionId().equals(key))))return m;
        }
        throw new AssertionError("No resource fixture opening");
    }
}
