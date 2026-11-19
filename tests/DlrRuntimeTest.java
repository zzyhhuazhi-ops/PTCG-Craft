import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;
import java.nio.file.*;

public final class DlrRuntimeTest {
 static final UUID A=ModernRuleRepair2Test.A,B=ModernRuleRepair2Test.B;
 static final CombatCatalog DATA=BundledCombatData.load();
 static int checks;
 static void check(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static CardInstance hand(ServerMatch m,String id){return ModernRuleRepair2Test.hand(m,A,id);}
 static UUID active(ServerMatch m,UUID p){return m.activePokemonView(p).pokemonId();}
 static String id(int n){return "me6_en_"+String.format("%03d",n);}
 static CardDefinition def(String id,CardCategory c,TrainerCategory t,boolean basic,Set<PokemonFamily> families,Set<EnergyType> types,String name){
  return new CardDefinition(id,name,c,t,"J",Set.of("international"),basic,c==CardCategory.ENERGY&&id.startsWith("energy"),60,Map.of(),EffectSupport.SUPPORTED,null,families,types);
 }
 static ServerMatch game(AttackDefinition attack,EnergyType type,int... required)throws Exception {
  var defs=new ArrayList<CardDefinition>();var pokemon=new HashMap<String,PokemonProfile>();var trainers=new HashMap<String,TrainerProfile>();var energy=new HashMap<String,EnergyProfile>();
  var root=JsonParser.parseString(Files.readString(Path.of("src/main/resources/data/ptcglive/cards/dlr.json"))).getAsJsonObject();
  for(var e:root.getAsJsonArray("cards")){
   var c=e.getAsJsonObject();String id=c.get("id").getAsString();var cat=CardCategory.valueOf(c.get("category").getAsString());var tc=TrainerCategory.valueOf(c.get("trainerCategory").getAsString());
   var fam=EnumSet.noneOf(PokemonFamily.class);if(c.has("pokemonFamilies"))for(var f:c.getAsJsonArray("pokemonFamilies"))fam.add(PokemonFamily.valueOf(f.getAsString()));
   var types=EnumSet.noneOf(EnergyType.class);if(c.has("pokemonTypes"))for(var f:c.getAsJsonArray("pokemonTypes"))types.add(EnergyType.valueOf(f.getAsString()));
   defs.add(def(id,cat,tc,c.get("basicPokemon").getAsBoolean(),fam,types,c.get("nameZh").getAsString()));
   switch(cat){case POKEMON->pokemon.put(id,DATA.pokemon(id).orElseThrow());case TRAINER->trainers.put(id,DATA.trainer(id).orElseThrow());case ENERGY->energy.put(id,DATA.energyProfile(id).orElseThrow());}
  }
  defs.add(def("basic",CardCategory.POKEMON,TrainerCategory.NONE,true,Set.of(),Set.of(type),"Fixture"));
  defs.add(def("mega",CardCategory.POKEMON,TrainerCategory.NONE,true,Set.of(PokemonFamily.EX,PokemonFamily.MEGA_EVOLUTION_EX,PokemonFamily.RULE_BOX),Set.of(EnergyType.COLORLESS),"Mega fixture ex"));
  defs.add(def("energy",CardCategory.ENERGY,TrainerCategory.NONE,false,Set.of(),Set.of(),"Basic energy"));
  defs.add(def("energy-wrong",CardCategory.ENERGY,TrainerCategory.NONE,false,Set.of(),Set.of(),"Wrong energy"));
  var profile=new PokemonProfile(500,0,null,null,0,null,0,1,List.of(attack),List.of(),type,"Fixture",null);
  pokemon.put("basic",profile);pokemon.put("mega",new PokemonProfile(500,0,null,null,0,null,0,3,List.of(attack),List.of(),EnergyType.COLORLESS,"Mega fixture ex",null));
  var combat=new CombatCatalog(pokemon,Map.of("energy",type,"energy-wrong",EnergyType.WATER),trainers,energy);
  var pattern=new ArrayList<String>(List.of("basic","basic","basic","basic","energy","energy","energy","energy-wrong","mega"));for(int n:required){pattern.add(id(n));pattern.add(id(n));}
  for(int seed=0;seed<2000;seed++){
   var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,pattern.get(i%pattern.size())));decks.add(cards);}
   var m=new ServerMatch(A,B,decks.get(0),decks.get(1),GeneratedRuleBatch8Test.RULES,new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
   if(List.of(A,B).stream().anyMatch(p->{var basics=m.privateView(p).hand().stream().filter(c->m.cardDefinition(c.definitionId()).basicPokemon()).limit(2).toList();return basics.size()<2||basics.stream().anyMatch(c->!c.definitionId().equals("basic"));}))continue;
   OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,27);m.endTurn(A);m.endTurn(B);
   if(Arrays.stream(required).allMatch(n->m.privateView(A).hand().stream().anyMatch(c->c.definitionId().equals(id(n))))&&m.privateView(A).hand().stream().filter(c->c.definitionId().equals("energy")).count()>=3&&m.privateView(A).hand().stream().anyMatch(c->c.definitionId().equals("mega")))return m;
  }throw new AssertionError("DLR fixture seed");
 }
 static AttackDefinition hit(){return GeneratedRuleBatch8Test.hit(40,false);}
 static void stadiums()throws Exception {
  for(int left:List.of(94,96,98)){
   var m=game(hit(),EnergyType.COLORLESS,left,left+1);var l=hand(m,id(left));var right=hand(m,id(left+1));int before=m.privateView(A).hand().size();
   m.playTrainer(A,l.instanceId(),null);check(m.stadiumCards().size()==2,"both halves in play");check(m.clientView(A).stadiumCards().size()==2&&m.clientView(null).stadiumCards().size()==2,"both halves publicly visible");check(m.privateView(A).hand().size()==before-2,"both halves leave hand");
   check(!m.canPlayTrainer(A,m.privateView(A).hand().stream().filter(c->c.definitionId().equals(id(left))).findFirst().orElse(l).instanceId(),null),"second stadium blocked");
   m.discardStadiumByEffect(A);check(m.stadiumCards().isEmpty(),"both halves removed");check(m.privateView(A).discard().containsAll(List.of(l,right)),"both halves discarded exactly");
   var one=game(hit(),EnergyType.COLORLESS,left);check(!one.canPlayTrainer(A,hand(one,id(left)).instanceId(),null),"single half illegal");
  }
 }
 static void healing()throws Exception {
  var m=game(hit(),EnergyType.COLORLESS,98,99,101);var a=active(m,A);m.placeDamageCountersByEffect(A,A,a,15);m.playTrainer(A,hand(m,id(98)).instanceId(),null);
  m.healDamageByEffect(A,A,a,30);check(m.activePokemonView(A).damage()==90,"Trench doubles healing");
  m.moveDamageCountersByEffect(A,A,a,A,m.benchPokemonView(A).getFirst().pokemonId(),2);check(m.activePokemonView(A).damage()==70&&m.benchPokemonView(A).getFirst().damage()==20,"counter movement must not double");
  m.discardStadiumByEffect(A);m.healDamageByEffect(A,A,a,30);check(m.activePokemonView(A).damage()==40,"healing reverts after stadium leaves");
 }
 static void dive()throws Exception {
  for(int n:List.of(88,124)){
   var m=game(hit(),EnergyType.COLORLESS,n);var talon=hand(m,id(n));check(!m.canUseSwellingFlash(A,talon.instanceId()),"Dive requires colorless Mega ex");
   m.playBasicPokemonToBench(A,hand(m,"mega").instanceId());check(m.canUseSwellingFlash(A,talon.instanceId()),"Dive enabled");int count=m.benchPokemonView(A).size();m.useSwellingFlash(A,talon.instanceId());
   check(m.benchPokemonView(A).size()==count+1&&m.benchPokemonView(A).stream().anyMatch(p->p.topCardId().equals(id(n))),"Stage 2 placed directly from hand");check(!m.privateView(A).hand().contains(talon),"Dive physical card removed");
  }
 }
 static void cap()throws Exception {
  var m=game(hit(),EnergyType.FIRE,100);UUID a=active(m,A),b=m.benchPokemonView(A).getFirst().pokemonId();
  var caps=m.privateView(A).hand().stream().filter(c->c.definitionId().equals(id(100))).limit(2).toList();check(caps.size()==2,"cap fixture");
  m.playTrainer(A,caps.get(0).instanceId(),a);m.playTrainer(A,caps.get(1).instanceId(),b);ModernRuleRepair2Test.pay(m,A,a,"energy",1);var o=m.borrowedAttackOptions(A).stream().filter(x->x.attackId().equals("delta-gift")).findFirst().orElseThrow(); check(o.attack()!=null,"Cap materializes: "+o.unsupportedReasonZh()); m.attackBorrowed(A,o.holderId(),o.holderEntry(),o.holderTop(),o.abilityId(),o.providerId(),o.providerEntry(),o.providerTop(),o.attackId(),null,List.of(),List.of(),null,-1,null,null);
  var p=m.effectCardPrompt(A);check(p!=null&&p.candidates().stream().allMatch(c->m.cardDefinition(c.definitionId()).basicEnergy()),"Cap searches only basic Energy");
  var selected=p.candidates().stream().limit(2).toList();check(selected.size()==2,"Cap deck fixture");m.submitEffectCards(A,p.resolutionId(),p.step(),selected.stream().map(CardInstance::instanceId).toList());
  var q=m.effectAttachmentPrompt(A);check(new HashSet<>(q.targets()).equals(Set.of(a,b)),"Cap targets only capped Pokemon");
  m.assignEffectEnergyDistribution(A,q.resolutionId(),q.step(),selected.get(0).instanceId(),a);
  m.assignEffectEnergyDistribution(A,q.resolutionId(),q.step(),selected.get(1).instanceId(),a); ModernRuleRepair2Test.reject(()->m.confirmEffectEnergyDistribution(A,q.resolutionId(),q.step()));
  m.assignEffectEnergyDistribution(A,q.resolutionId(),q.step(),selected.get(1).instanceId(),b);m.confirmEffectEnergyDistribution(A,q.resolutionId(),q.step());
  check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice(),"Cap completes attack");check(m.activePokemonView(A).attachedEnergyIds().size()==2&&m.benchPokemonView(A).getFirst().attachedEnergyIds().size()==1,"Cap attaches one per host");
 }

 static void rice()throws Exception {
  for(boolean trench:List.of(false,true)){
   var m=game(hit(),EnergyType.COLORLESS,101,98,99);UUID a=active(m,A);m.placeDamageCountersByEffect(A,A,a,20);
   var copies=m.privateView(A).hand().stream().filter(c->c.definitionId().equals(id(101))).toList();check(copies.size()>=2,"rice fixture");
   m.discardHandCardsByEffect(A,A,List.of(copies.get(0).instanceId()));if(trench)m.playTrainer(A,hand(m,id(98)).instanceId(),null);
   m.playTrainer(A,copies.get(1).instanceId(),null);check(m.activePokemonView(A).damage()==(trench?80:140),"Rice excludes resolving copy and respects Trench");
  }
 }
 static void training()throws Exception {
  for(boolean legendary:List.of(false,true)){
   var m=game(hit(),EnergyType.COLORLESS,102,98,99);if(legendary)m.playTrainer(A,hand(m,id(98)).instanceId(),null);
   var c=hand(m,id(102));int count=m.privateView(A).hand().size();m.playTrainer(A,c.instanceId(),null);
   check(m.privateView(A).hand().size()==count+(legendary?2:1),"Training draws two and returns only under Legendary");
   check(m.privateView(A).hand().contains(c)==legendary,"Training physical card destination");
  }
 }
 static void zinnia()throws Exception {
  var m=game(hit(),EnergyType.FIRE,103);UUID old=active(m,A),next=m.benchPokemonView(A).getFirst().pokemonId();var energy=hand(m,"energy");
  m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,energy.instanceId(),A,old);m.playTrainer(A,hand(m,id(103)).instanceId(),null);
  for(int step=0;step<8&&m.hasPendingEffectChoice();step++){
   var pokemon=m.hasPendingPokemonSelection()?m.effectPokemonPrompt(A):null;if(pokemon!=null){UUID choose=pokemon.targets().stream().anyMatch(t->t.pokemonId().equals(next))?next:old;m.chooseEffectPokemon(A,pokemon.resolutionId(),pokemon.step(),choose);continue;}
   var cards=m.hasPendingCardSelection()?m.effectCardPrompt(A):null;if(cards!=null){check(cards.candidates().stream().anyMatch(c->c.instanceId().equals(energy.instanceId())),"Zinnia takes old Active energy");m.submitEffectCards(A,cards.resolutionId(),cards.step(),List.of(energy.instanceId()));continue;}
   var attach=m.hasPendingAttachmentTarget()?m.effectAttachmentPrompt(A):null;if(attach!=null){m.chooseEffectAttachmentTarget(A,attach.resolutionId(),attach.step(),next);continue;}
   throw new AssertionError("Zinnia unknown prompt");
  }
  check(active(m,A).equals(next)&&m.activePokemonView(A).attachedEnergyIds().contains(energy.instanceId()),"Zinnia switches then moves old energy");check(!m.hasPendingEffectChoice(),"Zinnia continuation");
 }
 static void pyroduct()throws Exception {
  var m=game(hit(),EnergyType.PSYCHIC,46,47,94,95);var basic=hand(m,id(46));m.playBasicPokemonToBench(A,basic.instanceId());m.evolveByEffect(A,basic.instanceId(),hand(m,id(47)).instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);m.switchActiveByEffect(A,A,basic.instanceId());
  ModernRuleRepair2Test.pay(m,A,active(m,A),"energy",2);var left=hand(m,id(94));var right=hand(m,id(95));
  m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().filter(c->!c.equals(left)&&!c.equals(right)).map(CardInstance::instanceId).toList());
  check(m.activeAttacks(A).isEmpty(),"Golurk cannot attack with fewer than ten cards");m.playTrainer(A,left.instanceId(),null);check(!m.activeAttacks(A).isEmpty(),"Pyroduct suppresses evolved Golurk restriction");m.discardStadiumByEffect(A);check(m.activeAttacks(A).isEmpty(),"Golurk restriction restored");
 }
 static void poison()throws Exception {
  var attack=DATA.pokemon(id(59)).orElseThrow().attacks().getFirst();var m=game(attack,EnergyType.DARKNESS);ModernRuleRepair2Test.pay(m,A,active(m,A),"energy",1);m.attack(A,attack.id());check(m.activePokemonView(B).damage()==40,"Tormenting Poison puts four counters at checkup");
 }
 static void nitro()throws Exception {
  var attack=DATA.pokemon(id(23)).orElseThrow().attacks().get(1);var m=game(attack,EnergyType.FIRE,134);var a=active(m,A);var special=hand(m,id(134));m.attachEnergyByEffect(A,A,EffectSourceZone.HAND,special.instanceId(),A,a);ModernRuleRepair2Test.pay(m,A,a,"energy",2);
  m.attack(A,attack.id());var p=m.effectCardPrompt(A);check(p!=null,"Nitro attack energy discard choice");var basic=p.candidates().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();m.submitEffectCards(A,p.resolutionId(),p.step(),List.of(special.instanceId(),basic.instanceId()));
  check(m.privateView(A).hand().contains(special)&&!m.privateView(A).discard().contains(special),"Nitro returns after own Fire attack");check(m.privateView(A).discard().contains(basic)&&m.currentPlayer().equals(B),"Nitro preserves other discards and turn ending");
 }

 static void summit()throws Exception {
  var lethal=GeneratedRuleBatch8Test.hit(500,false);
  var m=game(lethal,EnergyType.COLORLESS,96,97);m.playTrainer(A,hand(m,id(96)).instanceId(),null);m.attack(A,lethal.id());
  check(m.phase()!=MatchPhase.PRIZE_SELECTION,"Summit single-prize KO grants zero prizes");check(m.privateView(A).prizes().size()==6,"Summit keeps prize count");
  var counters=game(hit(),EnergyType.COLORLESS,96,97);counters.playTrainer(A,hand(counters,id(96)).instanceId(),null);counters.placeDamageCountersByEffect(A,B,active(counters,B),50);
  check(counters.phase()==MatchPhase.PRIZE_SELECTION,"Summit does not reduce effect-counter KO prizes");
  var crush=DATA.pokemon(id(54)).orElseThrow().attacks().getFirst();var removed=game(crush,EnergyType.FIGHTING,96,97);removed.playTrainer(A,hand(removed,id(96)).instanceId(),null);removed.placeDamageCountersByEffect(A,B,active(removed,B),39);ModernRuleRepair2Test.pay(removed,A,active(removed,A),"energy",3);removed.attack(A,crush.id());
  check(removed.stadiumCards().isEmpty()&&removed.phase()==MatchPhase.PRIZE_SELECTION,"Stadium removed by attack before prize calculation");
 }
 static void vest()throws Exception {
  for(boolean megaAttacker:List.of(false,true)){
   var strike=GeneratedRuleBatch8Test.hit(100,false);var m=game(strike,EnergyType.COLORLESS,91);m.playTrainer(A,hand(m,id(91)).instanceId(),active(m,A));m.endTurn(A);
   if(megaAttacker){m.drawCardsByEffect(B,B,20);var mega=ModernRuleRepair2Test.hand(m,B,"mega");m.playBasicPokemonToBench(B,mega.instanceId());m.switchActiveByEffect(B,B,mega.instanceId());}
   m.attack(B,strike.id());check(m.activePokemonView(A).damage()==(megaAttacker?40:100),"Vest reduction requires attacking Mega ex");
  }
 }
 static void colorful()throws Exception {
  var attack=DATA.pokemon(id(83)).orElseThrow().attacks().getFirst();var m=game(attack,EnergyType.COLORLESS,1,2,43);ModernRuleRepair2Test.pay(m,A,active(m,A),"energy",2);
  var chosen=List.of(hand(m,id(1)),hand(m,id(2)),hand(m,id(43)));m.attack(A,attack.id());var p=m.effectCardPrompt(A);m.submitEffectCards(A,p.resolutionId(),p.step(),chosen.stream().map(CardInstance::instanceId).toList());
  if(m.effectCardReveal()!=null){var reveal=m.effectCardReveal();m.acknowledgeEffectReveal(reveal.confirmingPlayer(),reveal.id());}
  check(m.activePokemonView(B).damage()==60,"Colorful Whip counts distinct types, not Pokemon count");check(m.privateView(A).hand().containsAll(chosen),"Revealing retains cards in hand");
 }
 static void roar()throws Exception {
  for(int n:List.of(84,138,141)){
   var m=game(hit(),EnergyType.FIRE,n);var card=hand(m,id(n));var before=List.copyOf(m.privateView(A).deck());var window=before.subList(before.size()-4,before.size());
   m.playBasicPokemonToBench(A,card.instanceId());var order=m.triggerOrderPrompt();check(order!=null&&order.optionalDecision(),"Roar optional on hand-to-Bench only");m.decideTriggeredEffect(A,order.id(),true);
   var p=m.hasPendingCardSelection()?m.effectCardPrompt(A):null;var chosen=p==null?List.<CardInstance>of():p.candidates().stream().limit(1).toList();
   if(p!=null){check(p.candidates().stream().allMatch(c->window.contains(c)&&m.cardDefinition(c.definitionId()).basicEnergy()),"Roar top-four basic Energy candidates");m.submitEffectCards(A,p.resolutionId(),p.step(),chosen.stream().map(CardInstance::instanceId).toList());}
   if(m.hasPendingAttachmentTarget()){var q=m.effectAttachmentPrompt(A);check(q.targets().equals(List.of(card.instanceId())),"Roar attaches only to entering Rayquaza");m.chooseEffectAttachmentTarget(A,q.resolutionId(),q.step(),card.instanceId());}
   check(!m.hasPendingEffectChoice(),"Roar continuation");var after=m.privateView(A).deck();check(after.subList(after.size()-(before.size()-4),after.size()).equals(before.subList(0,before.size()-4)),"Roar leaves uninspected deck order intact");
   check(!m.canUseAbility(A,card.instanceId(),DATA.pokemon(id(n)).orElseThrow().abilities().getFirst().id()),"Roar is not an activated field ability");
  }
 }
 public static void main(String[] args)throws Exception {stadiums();healing();dive();cap();rice();training();zinnia();pyroduct();poison();nitro();summit();vest();colorful();roar();System.out.println("DLR runtime checks passed: "+checks);}
}
