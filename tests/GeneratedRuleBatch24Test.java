import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Generated once-per-turn Abilities retain source identity and exact player choices. */
public final class GeneratedRuleBatch24Test {
    static final UUID A=GeneratedRuleBatch19Test.A,B=GeneratedRuleBatch19Test.B;
    static final Ruleset RULES=GeneratedRuleBatch19Test.RULES;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static AbilityDefinition ability(String id){return GeneratedRuleBatch8Test.ability(id);}
    static ServerMatch game(String id){return game(id,0,EnergyType.GRASS);}
    static ServerMatch game(String id,int seed,EnergyType energy){return fixture(List.of(ability(id)),seed,energy);}
    static void use(ServerMatch m,String id){m.useAbility(A,bench(m,A),ability(id).id());}
    static void cards(ServerMatch m,List<UUID> ids){GeneratedRuleBatch23Test.cards(m,ids);}
    static boolean heads(ServerMatch m,int before){return GeneratedRuleBatch23Test.coinResult(m,before)==0;}
    static void spent(ServerMatch m,UUID source,String id){check(!m.canUseAbility(A,source,ability(id).id()),"Ability usage not recorded for source "+id);reject(()->m.useAbility(A,source,ability(id).id()));}
    public static void main(String[] args){searches();heal();handAttach();forcedSwitch();drawUsage();coinActions();selfShuffle();selfEvolution();selfEnergy();System.out.println("GeneratedRuleBatch24Test passed: real Ability source/usage, typed private search, healing scopes, coin branches, physical Energy, self shuffle and self evolution");}
    static void seedDeck(ServerMatch m,List<String> ids){m.drawCardsByEffect(A,A,m.privateView(A).deck().size());var chosen=ids.stream().map(id->hand(m,A,id).instanceId()).toList();m.discardHandCardsByEffect(A,A,chosen);m.shuffleDiscardIntoDeckByEffect(A,A);}
    static void searches(){for(String id:List.of("me3_en_036","sm1_en_017","sm5_en_093","sm3_en_096","sm9_en_119","sv5_en_017","sv9_en_079","swsh2_en_152")){
        EnergyType type=id.equals("me3_en_036")?EnergyType.PSYCHIC:id.equals("sm5_en_093")?EnergyType.FAIRY:EnergyType.GRASS;
        var m=game(id,0,type);seedDeck(m,List.of("energy","wrong","special","basic","evolved","tool","supporter","stadium","item"));UUID source=bench(m,A);use(m,id);var p=m.effectCardPrompt(A);
        Set<String> actual=new HashSet<>(p.candidates().stream().map(CardInstance::definitionId).toList());Set<String> expected=id.equals("me3_en_036")?Set.of("energy"):id.equals("sm3_en_096")?Set.of("energy","wrong"):id.equals("sm9_en_119")?Set.of("supporter"):id.equals("sv5_en_017")?Set.of("stadium"):id.equals("swsh2_en_152")?Set.of("tool"):id.equals("sv9_en_079")?Set.of("basic","evolved"):Set.of("basic");
        check(actual.equals(expected)&&p.minimum()==0&&m.clientView(B).effectSelection()==null,"Ability search filter/privacy "+id+" "+actual);var selected=p.candidates().getFirst();cards(m,List.of(selected.instanceId()));check(m.privateView(A).hand().contains(selected),"Search did not return selected card");spent(m,source,id);
    }
        var m=game("swsh7_en_016");use(m,"swsh7_en_016");if(m.hasPendingCardSelection())cards(m,List.of());spent(m,bench(m,A),"swsh7_en_016");
    }
    static void heal(){for(String id:List.of("me2-5_en_003","sm10_en_138","swsh6_en_008","sm12_en_030")){
        var m=game(id,0,id.equals("sm10_en_138")?EnergyType.FAIRY:EnergyType.GRASS);m.playBasicPokemonToBench(A,hand(m,A,"basic").instanceId());UUID wrong=m.benchPokemonView(A).getLast().pokemonId();m.evolveByEffect(A,wrong,hand(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        for(UUID host:List.of(active(m,A),bench(m,A),wrong))m.placeDamageCountersByEffect(A,A,host,5);m.placeDamageCountersByEffect(A,B,bench(m,B),5);UUID source=bench(m,A);use(m,id);
        if(id.equals("sm12_en_030")){var p=m.effectPokemonPrompt(A);check(p.targets().stream().noneMatch(t->t.pokemonId().equals(active(m,A))),"Bench heal included Active");reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),active(m,A)));m.chooseEffectPokemon(A,p.resolutionId(),p.step(),source);}
        int amount=id.equals("swsh6_en_008")||id.equals("sm12_en_030")?20:30;
        check(m.benchPokemonView(A).getFirst().damage()==50-amount&&m.activePokemonView(A).damage()==(id.equals("sm12_en_030")?50:50-amount)&&m.benchPokemonView(A).getLast().damage()==(id.equals("me2-5_en_003")?20:50)&&m.benchPokemonView(B).getFirst().damage()==50,"Ability heal scope/type/owner "+id);spent(m,source,id);
    }}
    static void handAttach(){for(String id:List.of("sm3_en_093","sv1_en_054")){
        var m=game(id,0,EnergyType.FAIRY);UUID source=bench(m,A);use(m,id);var p=m.effectCardPrompt(A);check(p.minimum()==1&&p.candidates().stream().allMatch(c->id.equals("sm3_en_093")?c.definitionId().equals("energy"):m.cardDefinition(c.definitionId()).basicEnergy()),"Hand attachment admitted wrong Energy");reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(hand(m,A,"special").instanceId())));UUID card=p.candidates().getFirst().instanceId();cards(m,List.of(card));var q=m.effectAttachmentPrompt(A);m.chooseEffectAttachmentTarget(A,q.resolutionId(),q.step(),source);check(m.benchPokemonView(A).getFirst().attachedEnergyIds().contains(card)&&!m.energyAttached(A),"Ability attachment source/manual quota");spent(m,source,id);
    }}
    static void forcedSwitch(){String id="sv1_en_137";var m=game(id);UUID source=bench(m,A),replacement=bench(m,B);use(m,id);var p=m.effectPokemonPrompt(B);reject(()->m.chooseEffectPokemon(A,p.resolutionId(),p.step(),replacement));m.chooseEffectPokemon(B,p.resolutionId(),p.step(),replacement);check(active(m,B).equals(replacement),"Mabosstiff switch did not resolve");spent(m,source,id);}
    static void drawUsage(){String id="me5_en_068";var m=game(id);UUID source=bench(m,A),other=active(m,A);int hand=m.privateView(A).hand().size();use(m,id);check(m.privateView(A).hand().size()==hand+1,"New wording failed to draw");spent(m,source,id);m.switchActiveByEffect(A,A,source);spent(m,source,id);m.useAbility(A,other,ability(id).id());check(m.privateView(A).hand().size()==hand+2,"Two copies incorrectly share once-per-turn use");m.endTurn(A);m.endTurn(B);check(m.canUseAbility(A,source,ability(id).id()),"Once-per-turn use did not reset");m.disableAbilityEffectsByEffect(A,A,source);check(!m.canUseAbility(A,source,ability(id).id()),"Suppressed source could activate");}
    static void coinActions(){for(String id:List.of("me2-5_en_015","me3_en_014","me5-5_en_008","sm7_en_003","xy8_en_015")){
        boolean h=false,t=false;for(int seed=0;seed<50&&!(h&&t);seed++){var m=game(id,seed,EnergyType.GRASS);UUID source=bench(m,A),old=active(m,B);var special=hand(m,B,"special");m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,special.instanceId(),B,old);if(id.equals("me5-5_en_008"))seedDeck(m,List.of("basic","tool"));if(id.equals("xy8_en_015"))seedDeck(m,List.of("energy","special"));int before=m.randomTranscript().size(),opponentHand=m.privateView(B).hand().size();use(m,id);boolean head=heads(m,before);h|=head;t|=!head;
            if(head){if(id.equals("me2-5_en_015")){var p=m.effectCardPrompt(A);check(p.candidates().equals(List.of(special)),"Return Energy selects physical card");cards(m,List.of(special.instanceId()));}
                else if(id.equals("sm7_en_003")){var p=m.effectPokemonPrompt(A);reject(()->m.chooseEffectPokemon(B,p.resolutionId(),p.step(),bench(m,B)));m.chooseEffectPokemon(A,p.resolutionId(),p.step(),bench(m,B));}
                else if(id.equals("me5-5_en_008")||id.equals("xy8_en_015")){var p=m.effectCardPrompt(A);check(p.candidates().size()==1&&p.candidates().getFirst().definitionId().equals(id.equals("xy8_en_015")?"energy":"basic"),"Coin deck search filter");cards(m,List.of(p.candidates().getFirst().instanceId()));if(m.hasPendingAttachmentTarget()){var q=m.effectAttachmentPrompt(A);m.chooseEffectAttachmentTarget(A,q.resolutionId(),q.step(),source);}}
            }
            check(!m.hasPendingEffectChoice(),"Unresolved Ability coin continuation");if(id.equals("me2-5_en_015"))check(m.privateView(B).hand().contains(special)==head,"Return Energy heads/tails");if(id.equals("me3_en_014"))check(m.privateView(B).hand().size()==opponentHand-(head?1:0),"Random discard heads/tails");if(id.equals("sm7_en_003"))check(active(m,B).equals(old)!=head,"Gust heads/tails");if(id.equals("xy8_en_015"))check(m.benchPokemonView(A).getFirst().attachedEnergyIds().size()==(head?1:0),"Deck attach heads/tails");spent(m,source,id);
        }check(h&&t,"Both coin branches not covered "+id);
    }}
    static void selfShuffle(){for(boolean activeSource:List.of(false,true)){String id="sm4_en_016";var m=game(id);UUID source=activeSource?active(m,A):bench(m,A),other=activeSource?bench(m,A):active(m,A);var evolution=hand(m,A,"evolved");m.evolveByEffect(A,source,evolution.instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);CardInstance special=hand(m,A,"special"),tool=hand(m,A,"tool");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,special.instanceId(),A,source);m.playTrainer(A,tool.instanceId(),source);int deck=m.privateView(A).deck().size();m.useAbility(A,source,ability(id).id());if(activeSource)m.chooseReplacement(A,other);check(m.privateView(A).deck().size()==deck+4&&m.privateView(A).deck().containsAll(List.of(evolution,special,tool))&&active(m,A).equals(other)&&m.privateView(B).prizes().size()==6&&!m.hasPendingEffectChoice(),"Self shuffle lost evolution/attachments, wrong source or prize boundary");}}
    static void selfEvolution(){String id="sm10_en_002";boolean h=false,t=false;for(int seed=0;seed<50&&!(h&&t);seed++){var m=game(id,seed,EnergyType.GRASS);seedDeck(m,List.of("evolved","basic"));UUID source=bench(m,A);int before=m.randomTranscript().size();use(m,id);boolean head=heads(m,before);h|=head;t|=!head;if(head){var p=m.effectCardPrompt(A);check(p.candidates().size()==1&&p.candidates().getFirst().definitionId().equals("evolved"),"Self evolution lineage");cards(m,List.of(p.candidates().getFirst().instanceId()));}check(m.benchPokemonView(A).getFirst().topCardId().equals(head?"evolved":"basic")&&m.activePokemonView(A).topCardId().equals("basic"),"Ability evolved Active instead of source");}check(h&&t,"Evolution coin outcomes incomplete");}
    static void selfEnergy(){String id="sv4_en_144";boolean h=false,t=false;for(int seed=0;seed<50&&!(h&&t);seed++){var m=game(id,seed,EnergyType.GRASS);UUID source=bench(m,A);var special=hand(m,A,"special");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,special.instanceId(),A,source);m.drawCardsByEffect(A,A,m.privateView(A).deck().size());var basics=m.privateView(A).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicEnergy()).limit(5).map(CardInstance::instanceId).toList();m.discardHandCardsByEffect(A,A,basics);int before=m.randomTranscript().size();use(m,id);boolean head=heads(m,before);h|=head;t|=!head;var p=m.effectCardPrompt(A);if(head){check(p.maximum()==4&&p.minimum()==1&&p.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).basicEnergy()),"Buggy Turbo Basic/up-to limits");reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),basics));cards(m,basics.subList(0,4));check(m.benchPokemonView(A).getFirst().attachedEnergyIds().size()==5,"Buggy Turbo did not attach to its Benched source");}else{check(p.candidates().equals(List.of(special)),"Buggy Turbo tails must discard source's physical Energy");cards(m,List.of(special.instanceId()));check(m.privateView(A).discard().contains(special),"Tails did not discard Energy");}check(m.activePokemonView(A).attachedEnergyIds().isEmpty()&&!m.hasPendingEffectChoice()&&!m.energyAttached(A),"Self Energy touched Active/manual attachment");spent(m,source,id);}check(h&&t,"Self Energy coin outcomes incomplete");}
    static ServerMatch fixture(List<AbilityDefinition> abilities,int firstSeed,EnergyType energy){
        var trainer=GeneratedRuleBatch8Test.trainer("swsh5_en_124");var type=energy;var tags=Map.<String,Set<EnergyType>>of();var attack=GeneratedRuleBatch8Test.hit(100,false);
        String[] ids={"basic","basic","basic","basic","energy","energy","wrong","special","tool","supporter","stadium","item","trainer","evolved","rulebox"};
        var defs=new ArrayList<CardDefinition>();
        for(String id:Arrays.stream(ids).distinct().toList()){
            boolean pokemon=Set.of("basic","evolved","rulebox").contains(id),isEnergy=Set.of("energy","wrong","special").contains(id);
            defs.add(new CardDefinition(id,id,pokemon?CardCategory.POKEMON:isEnergy?CardCategory.ENERGY:CardCategory.TRAINER,
                id.equals("trainer")?trainer.category():id.equals("tool")?TrainerCategory.TOOL:id.equals("supporter")?TrainerCategory.SUPPORTER:id.equals("stadium")?TrainerCategory.STADIUM:id.equals("item")?TrainerCategory.ITEM:TrainerCategory.NONE,"H",Set.of("international"),
                id.equals("basic")||id.equals("rulebox"),id.equals("energy")||id.equals("wrong"),60,Map.of(),EffectSupport.SUPPORTED,null,
                id.equals("rulebox")?Set.of(PokemonFamily.EX,PokemonFamily.RULE_BOX):Set.of(),pokemon?tags.getOrDefault(id,Set.of(id.equals("evolved")?EnergyType.PSYCHIC:type)):Set.of()));
        }
        var pokemon=new HashMap<String,PokemonProfile>();
        for(String id:List.of("basic","evolved","rulebox"))pokemon.put(id,new PokemonProfile(500,1,id.equals("evolved")?"basic":null,null,0,null,0,
            id.equals("rulebox")?2:1,List.of(id.equals("rulebox")?GeneratedRuleBatch8Test.hit(100,false):attack),id.equals("rulebox")?List.of():abilities,id.equals("evolved")?EnergyType.PSYCHIC:type));
        var combat=new CombatCatalog(pokemon,Map.of("energy",energy,"wrong",energy==EnergyType.FIRE?EnergyType.WATER:EnergyType.FIRE),
            Map.of("trainer",trainer,"tool",DynamicPassiveModifierTest.plainTool(),"supporter",GeneratedRuleBatch8Test.trainer("xy2_en_093"),"stadium",GeneratedRuleBatch8Test.trainer("swsh5_en_138"),"item",GeneratedRuleBatch8Test.trainer("swsh5_en_124")),Map.of("special",EnergyProfile.fixed(energy,2)));
        for(int seed=firstSeed;seed<firstSeed+500;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,ids[i%ids.length]));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->{var basics=m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).toList();return basics.size()<2||basics.subList(0,2).stream().anyMatch(c->!c.definitionId().equals("basic"));}))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,28);m.drawCardsByEffect(A,B,28);m.endTurn(A);m.endTurn(B);
            if(List.of(A,B).stream().allMatch(p->Arrays.stream(ids).allMatch(id->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(id))))&&
                m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).count()>=3&&m.privateView(A).hand().stream().filter(c->c.definitionId().equals("tool")).count()>=2)return m;
        }throw new AssertionError("No batch 24 fixture");
    }


}
