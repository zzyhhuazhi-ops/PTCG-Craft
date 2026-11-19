import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual generated cards: conditional draw branches, affordable typed costs and committed discard counts. */
public final class GeneratedRuleBatch35Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final Ruleset RULES=GeneratedRuleBatch19Test.RULES;
    static void check(boolean ok,String why){GeneratedRuleBatch8Test.check(ok,why);}
    static void reject(Runnable r){GeneratedRuleBatch8Test.reject(r);}
    static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
    static UUID bench(ServerMatch m,UUID p){return m.benchPokemonView(p).getFirst().pokemonId();}
    static CardInstance hand(ServerMatch m,UUID p,String id){return GeneratedRuleBatch13Test.hand(m,p,id);}
    static void cards(ServerMatch m,List<UUID> ids){GeneratedRuleBatch23Test.cards(m,ids);}
    static void play(ServerMatch m){GeneratedRuleBatch23Test.play(m);}
    static ServerMatch game(String id){return GeneratedRuleBatch23Test.game(id);}
    static AttackDefinition attack(String id,int slot){var e=GeneratedRuleBatch8Test.entry(id).getAsJsonArray("attacks").get(slot).getAsJsonObject().deepCopy();e.add("energyCost",new JsonObject());e.addProperty("damageType","COLORLESS");return BundledCombatData.attack(e,EffectRegistry.standard());}
    static void trim(ServerMatch m,UUID owner,int n,List<CardInstance> preserved){var keep=new LinkedHashSet<UUID>(preserved.stream().map(CardInstance::instanceId).toList());for(var c:m.privateView(owner).hand())if(keep.size()<n)keep.add(c.instanceId());m.discardHandCardsByEffect(m.currentPlayer(),owner,m.privateView(owner).hand().stream().map(CardInstance::instanceId).filter(x->!keep.contains(x)).toList());check(m.privateView(owner).hand().size()==n,"trim fixture");}
    public static void main(String[] args){drawBranches();lillie();iris();adaman();revavroom();milo();letter();topCard();rest();prizeMill();punctuation();schema();System.out.println("GeneratedRuleBatch35Test passed: real conditional draw, own first turn, typed affordable costs, post-cost draw-until, actual discard counts, private search/inspection, discard-heal, prize gates and retained reminder punctuation");}
    static void drawBranches(){
        for(int prizes:List.of(2,3,4)){var m=game("sv7_en_139");GeneratedRuleBatch9Test.take(m,B,6-prizes);play(m);check(m.privateView(A).hand().size()==(prizes<=3?8:4),"Lacey uses opponent remaining prizes and one branch");}
        for(boolean empty:List.of(false,true)){var m=game("swsh3-5_en_055");if(empty)m.movePokemonByEffect(A,A,bench(m,A),LeavePlayDestination.HAND);play(m);check(m.privateView(A).hand().size()==(empty?8:4),"Kabu only-own-Active branch");}
        for(int taken:List.of(0,1)){var m=game("me1_en_169");GeneratedRuleBatch9Test.take(m,A,taken);play(m);check(m.privateView(A).hand().size()==(taken==0?8:6),"Determination exact own prizes");}
    }
    static void lillie(){
        for(int size:List.of(5,6,7)){var m=game("sm1_en_122");var card=hand(m,A,"trainer");trim(m,A,size,List.of(card));if(size>6){reject(()->play(m));check(m.privateView(A).hand().contains(card),"Full-hand Lillie paid source");}else{play(m);check(m.privateView(A).hand().size()==6,"Lillie counted itself or drew both branches");}}
        for(int size:List.of(7,8,9)){var m=fixture(GeneratedRuleBatch8Test.trainer("sm1_en_122"),List.of(),true);reject(()->play(m));m.endTurn(A);var card=hand(m,B,"trainer");trim(m,B,size,List.of(card));if(size>8){reject(()->m.playTrainer(B,card.instanceId(),null));continue;}m.playTrainer(B,card.instanceId(),null);check(m.privateView(B).hand().size()==8,"Second player's own first turn missing");m.endTurn(B);m.endTurn(A);var next=hand(m,B,"trainer");trim(m,B,7,List.of(next));reject(()->m.playTrainer(B,next.instanceId(),null));}
    }
    static void iris(){for(int size:List.of(1,2,6,7,8))for(int deck:List.of(0,1,10)){
        var m=game("me2-5_en_190");var card=hand(m,A,"trainer");trim(m,A,size,List.of(card));m.discardTopDeckCardsByEffect(A,A,Math.max(0,m.privateView(A).deck().size()-deck));int available=m.privateView(A).deck().size();boolean legal=size>=2&&size<=7&&available>0;
        if(!legal){var before=List.copyOf(m.privateView(A).hand());reject(()->play(m));check(m.privateView(A).hand().equals(before),"Iris rejection changed hand/quota");continue;}
        play(m);var p=m.effectCardPrompt(A);check(p.minimum()==1&&p.maximum()==1&&!p.candidates().contains(card),"Iris cost included played source");reject(()->m.submitEffectCards(B,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId())));var cost=p.candidates().getFirst();cards(m,List.of(cost.instanceId()));check(m.privateView(A).hand().size()==Math.min(6,size-2+available)&&m.privateView(A).discard().containsAll(List.of(card,cost)),"Iris post-cost draw-until");
    }}
    static void adaman(){for(int energyCount:List.of(0,1,2,3))for(int deck:List.of(0,1,10)){
        var m=GeneratedRuleBatch23Test.game("swsh10_en_135",0,EnergyType.METAL);var wanted=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).toList();m.discardHandCardsByEffect(A,A,wanted.stream().skip(energyCount).map(CardInstance::instanceId).toList());m.discardTopDeckCardsByEffect(A,A,Math.max(0,m.privateView(A).deck().size()-deck));var card=hand(m,A,"trainer");
        if(energyCount<2||deck==0){var before=List.copyOf(m.privateView(A).hand());reject(()->play(m));check(m.privateView(A).hand().equals(before),"Adaman paid partial/wrong-type cost");continue;}
        play(m);var p=m.effectCardPrompt(A);check(p.minimum()==2&&p.maximum()==2&&p.candidates().stream().allMatch(c->c.definitionId().equals("energy")),"Adaman used Special provided type or wrong Basic");reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(p.candidates().getFirst().instanceId())));var paid=p.candidates().stream().limit(2).map(CardInstance::instanceId).toList();cards(m,paid);var search=m.effectCardPrompt(A);check(search.minimum()==1&&search.maximum()==2&&m.clientView(B).effectSelection()==null,"Adaman unrestricted search minimum/privacy");reject(()->m.submitEffectCards(A,search.resolutionId(),search.step(),List.of()));var found=search.candidates().stream().limit(2).toList();cards(m,found.stream().map(CardInstance::instanceId).toList());check(m.effectCardReveal()==null&&m.privateView(A).hand().containsAll(found)&&m.privateView(A).discard().stream().filter(c->paid.contains(c.instanceId())).count()==2,"Adaman search/cost physical identity");
    }}
    static void revavroom(){var a=GeneratedRuleBatch8Test.ability("sv1_en_142");for(String paidId:List.of("energy","special","none"))for(int size:List.of(1,6,7))for(boolean empty:List.of(false,true)){
        var m=fixture(GeneratedRuleBatch8Test.trainer("swsh5_en_124"),List.of(a),false);var cost=hand(m,A,paidId.equals("none")?"basic":paidId);if(paidId.equals("none"))m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().filter(c->m.cardDefinition(c.definitionId()).category()==CardCategory.ENERGY).map(CardInstance::instanceId).toList());trim(m,A,size,List.of(cost));if(empty)m.discardTopDeckCardsByEffect(A,A,m.privateView(A).deck().size());boolean legal=!paidId.equals("none")&&size<=6&&!empty;check(m.canUseAbility(A,bench(m,A),a.id())==legal,"Rumbling Engine availability "+paidId+size+empty);
        if(!legal){var before=List.copyOf(m.privateView(A).hand());reject(()->m.useAbility(A,bench(m,A),a.id()));check(m.privateView(A).hand().equals(before),"Rumbling rejection paid Energy");continue;}
        m.useAbility(A,bench(m,A),a.id());var p=m.effectCardPrompt(A);check(p.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).category()==CardCategory.ENERGY),"Rumbling accepts non-Energy");cards(m,List.of(cost.instanceId()));check(m.privateView(A).hand().size()==6&&m.privateView(A).discard().contains(cost)&&!m.canUseAbility(A,bench(m,A),a.id()),"Rumbling reward/quota or special Energy identity");
    }}
    static void milo(){for(int available:List.of(0,1,2,3))for(int selected:List.of(1,2))for(int deck:List.of(0,1,10)){
        if(selected>available&&available>0)continue;var m=game("swsh2_en_161");var card=hand(m,A,"trainer");trim(m,A,available+1,List.of(card));m.discardTopDeckCardsByEffect(A,A,Math.max(0,m.privateView(A).deck().size()-deck));int draw=Math.min(m.privateView(A).deck().size(),selected*2);
        if(available==0){reject(()->play(m));continue;}play(m);var p=m.effectCardPrompt(A);check(p.minimum()==1&&p.maximum()==2&&!p.candidates().contains(card),"Milo count range/source");reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),List.of()));var paid=p.candidates().stream().limit(selected).map(CardInstance::instanceId).toList();cards(m,paid);check(m.privateView(A).hand().size()==available-selected+draw,"Milo used requested capacity instead of committed cards or drew both branches");reject(()->m.submitEffectCards(A,p.resolutionId(),p.step(),paid));
    }}
    static void letter(){var m=game("xy1_en_123");play(m);var p=m.effectCardPrompt(A);check(p.minimum()==0&&p.maximum()==2&&p.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).basicEnergy()),"Letter Basic-only/private search");cards(m,List.of());check(!m.hasPendingEffectChoice(),"Private fail-to-find stalled");}
    static void topCard(){for(boolean discard:List.of(false,true)){var a=attack("rsv10-5_en_016",0);var m=GeneratedRuleBatch19Test.game(a);var top=m.privateView(A).deck().getLast();int deck=m.privateView(A).deck().size();m.attack(A,a.id());var p=m.effectCardPrompt(A);check(p.minimum()==0&&p.maximum()==1&&p.candidates().equals(List.of(top))&&m.clientView(B).effectSelection()==null,"Top inspection leaked or inspected wrong window");cards(m,discard?List.of(top.instanceId()):List.of());check(m.privateView(A).deck().size()==deck-(discard?1:0)&&m.privateView(A).discard().contains(top)==discard,"Top discard/keep changed wrong card");}}
    static void rest(){for(boolean energy:List.of(false,true)){var a=attack("sm4_en_009",0);var m=GeneratedRuleBatch19Test.game(a);m.placeDamageCountersByEffect(A,A,active(m,A),22);CardInstance paid=null;if(energy){paid=hand(m,A,"special");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,paid.instanceId(),A,active(m,A));}m.attack(A,a.id());if(m.hasPendingCardSelection())cards(m,List.of(Objects.requireNonNull(paid).instanceId()));check(m.activePokemonView(A).damage()==0&&m.activePokemonView(A).attachedEnergyIds().isEmpty(),"Accelgor discard-then-heal incorrectly required if-you-do");}}
    static void prizeMill(){for(int remaining:List.of(1,2,3,4,5,6)){var a=attack("sm6_en_080",0);var m=GeneratedRuleBatch19Test.game(a);GeneratedRuleBatch9Test.take(m,A,6-remaining);int size=m.privateView(A).deck().size();m.attack(A,a.id());check(m.privateView(A).deck().size()==size-(remaining%2==0?Math.min(10,size):0),"Guzzlord exact prize OR/owner");}}
    static void punctuation(){
        var a=GeneratedRuleBatch8Test.ability("sm1_en_066");var m=GeneratedRuleBatch8Test.fixture(List.of(a),EnergyType.PSYCHIC,EnergyType.GRASS);var e=hand(m,A,"special");m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,e.instanceId(),A,active(m,A));for(int i=0;i<2;i++){UUID target=i==0?bench(m,A):active(m,A);m.useAbility(A,active(m,A),a.id());cards(m,List.of(e.instanceId()));var p=m.effectAttachmentPrompt(A);m.chooseEffectAttachmentTarget(A,p.resolutionId(),p.step(),target);}check(m.activePokemonView(A).attachedEnergyIds().contains(e.instanceId()),"Lunala reminder comma lost unlimited transfer");
        for(String id:List.of("xy9_en_055","xy11_en_070")){var attack=attack(id,0);var g=fixture(GeneratedRuleBatch8Test.trainer("swsh5_en_124"),List.of(),false,attack);UUID foe=hand(g,B,"rulebox").instanceId();g.attack(A,attack.id());g.playBasicPokemonToBench(B,foe);g.switchActiveByEffect(B,B,foe);g.attack(B,"hit");check(g.activePokemonView(B).damage()==(id.equals("xy9_en_055")?50:100),"Retaliation amount after retained comma "+id);}
    }
    static void schema(){String valid="{\"type\":\"discard\",\"target\":\"HAND\",\"amount\":1,\"minimum\":1,\"as_cost\":true,\"card_category\":\"ENERGY\",\"requires_effect\":true}";var e=EffectRegistry.standard().parse(JsonParser.parseString(valid).getAsJsonObject());var p=new EffectProgram(List.of(new TriggeredEffects(EffectTrigger.ON_CARD_PLAYED,List.of(),List.of(e,new EffectDefinition("draw",EffectTarget.SELF,Map.of("amount","1"),List.of(),List.of())))));TrainerProgramRules.validate(p);reject(()->ActivationCostRules.requireAffordable(p,EffectTrigger.ON_CARD_PLAYED,20L));for(String bad:List.of(valid.replace("ENERGY","POKEMON"),valid.replace("\"requires_effect\":true","\"requires_effect\":false"),valid.replace("\"as_cost\":true,",""),valid.replace("\"minimum\":1","\"minimum\":0"),valid.replace("\"requires_effect\":true","\"energy_type\":\"METAL\""),valid.replace("\"requires_effect\":true","\"source_owner\":\"OPPONENT\""))){reject(()->ZoneSelectionEffect.forProgram(EffectRegistry.standard().parse(JsonParser.parseString(bad).getAsJsonObject())));}}
static ServerMatch fixture(TrainerProfile trainer,List<AbilityDefinition> abilities,boolean first){return fixture(trainer,abilities,first,GeneratedRuleBatch8Test.hit(100,false));}
    static ServerMatch fixture(TrainerProfile trainer,List<AbilityDefinition> abilities,boolean first,AttackDefinition attack){
        int firstSeed=0;var energy=EnergyType.METAL;var type=energy;var tags=Map.<String,Set<EnergyType>>of();
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
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,28);m.drawCardsByEffect(A,B,28);if(!first){m.endTurn(A);m.endTurn(B);}
            if(List.of(A,B).stream().allMatch(p->Arrays.stream(ids).allMatch(id->m.privateView(p).hand().stream().anyMatch(c->c.definitionId().equals(id))))&&
                m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).count()>=3&&m.privateView(A).hand().stream().filter(c->c.definitionId().equals("tool")).count()>=2)return m;
        }throw new AssertionError("No batch 23 fixture");
    }
}


