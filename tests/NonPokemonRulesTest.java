import com.google.gson.*;
import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.ai.AiSupport;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonRulesTest {
    static final UUID A=new UUID(0,90001),B=new UUID(0,90002);
    static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
    public static void main(String[] args) {
        var definitions=BundledRules.loadCardDefinitions();var combat=BundledCombatData.load();int count=0;var invalidPrograms=new ArrayList<String>();
        for(var entry:CardCatalog.load()) {
            var d=definitions.find(entry.id()).orElse(null);
            if(d==null||d.category()==CardCategory.POKEMON||d.effectSupport()!=EffectSupport.SUPPORTED)continue;
            check(d.category()==CardCategory.ENERGY?combat.energyProfile(d.id()).isPresent():combat.trainer(d.id()).isPresent(),"Supported card lacks valid rules: "+d.id());
            if(d.category()==CardCategory.TRAINER) {
                var profile=combat.trainer(d.id()).orElseThrow();
                if(profile.effect().opcode()==TrainerEffect.Opcode.COMPOSED)try {
                    dev.hua.ptcglive.engine.effect.TrainerProgramRules.validate(profile.effectProgram());
                } catch(RuntimeException invalid) {invalidPrograms.add(d.id()+": "+invalid.getMessage());}
                try {
                    var grant=dev.hua.ptcglive.engine.effect.ToolGrantedAttack.fromProgram(profile.ongoingProgram());
                    if(grant!=null)dev.hua.ptcglive.engine.effect.AttackProgramRules.validate(
                        dev.hua.ptcglive.engine.effect.ToolGrantedAttack.materialize(grant,EnergyType.COLORLESS));
                } catch(RuntimeException invalid) {invalidPrograms.add(d.id()+" granted attack: "+invalid.getMessage());}
            }
            var deck=List.of(new CardInstance(new UUID(0,1),A,d.id()));
            check(AiSupport.matchProblems(deck,deck,definitions,combat).isEmpty(),"Non-Pokemon AI preflight failed: "+d.id());count++;
        }
        try {java.nio.file.Files.write(java.nio.file.Path.of("outputs/nonpokemon-invalid-programs.txt"),invalidPrograms,java.nio.charset.StandardCharsets.UTF_8);}
        catch(java.io.IOException failure){throw new java.io.UncheckedIOException(failure);}
        check(invalidPrograms.isEmpty(),"Supported Trainers cannot execute: "+invalidPrograms);
        for(int retreat:List.of(3,4))for(boolean bench:List.of(false,true))padding(retreat,bench);
        mixedSearch("swsh6_en_140",EnergyType.PSYCHIC);
        mixedSearch("sd100_cn_371",EnergyType.PSYCHIC);
        mixedSearch("sm8_en_187",EnergyType.GRASS);
        malasada();
        for(String id:List.of("sd100_cn_369","swsh5_en_139")) {
            var effect=combat.trainer(id).orElseThrow().effectProgram().triggers().getFirst().effects().getFirst();
            var selection=dev.hua.ptcglive.engine.effect.ZoneSelectionEffect.from(effect);
            for(String energy:List.of("sd100_cn_412","swsh5_en_141","swsh5_en_183"))
                check(selection.allows(definitions.find(energy).orElseThrow()),"Urn rejected another Single Strike Energy printing: "+id+" "+energy);
        }
        for(String id:List.of("sd100_cn_388","swsh9_en_149")) {
            var effect=combat.trainer(id).orElseThrow().effectProgram().triggers().getFirst().effects().getFirst();
            var selection=dev.hua.ptcglive.engine.effect.ZoneSelectionEffect.from(effect);
            for(String excluded:List.of("sd100_cn_388","swsh9_en_149"))
                check(!selection.allows(definitions.find(excluded).orElseThrow()),"Team Yell's Cheer recovered its own reprint: "+excluded);
        }
        System.out.println("NonPokemonRulesTest passed: "+count+" supported printings have loadable rules and AI preflight; Buff Padding checks Active/Bench and exact retreat cost");
    }
    static void malasada() {
        var owner=GeneratedRuleBatch8Test.A;
        var m=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,BundledCombatData.load().trainer("sm1_en_114").orElseThrow(),GeneratedRuleBatch8Test.hit(10,false));
        var host=m.activePokemonView(owner).pokemonId();
        m.placeDamageCountersByEffect(owner,owner,host,3);
        m.applySpecialConditionByEffect(owner,owner,SpecialCondition.POISONED);
        m.applySpecialConditionByEffect(owner,owner,SpecialCondition.BURNED);
        m.playTrainer(owner,DynamicPassiveModifierTest.hand(m,owner,"trainer").instanceId(),null);
        var q=m.effectChoicePrompt(owner);
        check(q!=null&&m.canChooseEffectOption(owner,0)&&!m.canChooseEffectOption(owner,2),"One-condition cure choices do not reflect current statuses");
        m.chooseEffectOption(owner,q.resolutionId(),q.step(),0);
        check(m.activePokemonView(owner).damage()==10&&m.activePokemonView(owner).conditions().equals(List.of(SpecialCondition.BURNED)),"Big Malasada cleared more than one condition or healed wrong amount");
    }
    static void mixedSearch(String id,EnergyType type) {
        var owner=GeneratedRuleBatch8Test.A;
        var m=GeneratedRuleBatch8Test.fixture(List.of(),type,type,BundledCombatData.load().trainer(id).orElseThrow(),GeneratedRuleBatch8Test.hit(10,false));
        m.playTrainer(owner,DynamicPassiveModifierTest.hand(m,owner,"trainer").instanceId(),null);
        var prompt=m.effectCardPrompt(owner);var ids=prompt.candidates().stream().map(CardInstance::definitionId).collect(java.util.stream.Collectors.toSet());
        check(ids.contains("energy")&&ids.contains("basic"),"Mixed search lost either basic Pokemon or basic Energy: "+id+" "+ids);
        check(!ids.contains("evolved")&&!ids.contains("wrong")&&!ids.contains("special"),"Mixed search admitted ineligible card: "+id);
        var energy=prompt.candidates().stream().filter(c->c.definitionId().equals("energy")).findFirst().orElseThrow();
        m.submitEffectCards(owner,prompt.resolutionId(),prompt.step(),List.of(energy.instanceId()));
        AiCardOperationTest.settle(MatchSession.multiplayer(m,owner,GeneratedRuleBatch8Test.B),dev.hua.ptcglive.ai.AiDifficulty.NORMAL);
        check(m.privateView(owner).hand().contains(energy)&&!m.hasPendingEffectChoice(),"Mixed search did not resolve selected Energy");
    }
    static void padding(int retreat,boolean bench) {
        var defs=new ArrayList<CardDefinition>();
        for(String id:List.of("basic","tool","energy"))defs.add(new CardDefinition(id,id,id.equals("basic")?CardCategory.POKEMON:id.equals("energy")?CardCategory.ENERGY:CardCategory.TRAINER,id.equals("tool")?TrainerCategory.TOOL:TrainerCategory.NONE,"H",Set.of("international"),id.equals("basic"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null));
        var combat=new CombatCatalog(Map.of("basic",new PokemonProfile(100,retreat,null,null,0,null,0,1,List.of(GeneratedRuleBatch8Test.hit(10,false)))),Map.of("energy",EnergyType.GRASS),Map.of("tool",BundledCombatData.load().trainer("sm9_en_136").orElseThrow()));
        ServerMatch match=null;
        for(int seed=0;seed<1000;seed++) {
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID owner:List.of(A,B)){var deck=new ArrayList<CardInstance>();for(int i=0;i<60;i++)deck.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,i%3==0?"basic":i%3==1?"tool":"energy"));decks.add(deck);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),combat,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals("basic")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,1);m.drawCardsByEffect(A,A,15);match=m;break;
        }
        var m=Objects.requireNonNull(match);var host=bench?m.benchPokemonView(A).getFirst().pokemonId():m.activePokemonView(A).pokemonId();
        var tool=m.privateView(A).hand().stream().filter(c->c.definitionId().equals("tool")).findFirst().orElseThrow();
        m.playTrainer(A,tool.instanceId(),host);
        var view=bench?m.benchPokemonView(A).getFirst():m.activePokemonView(A);
        check(view.hitPoints()==(retreat==4?150:100),"Buff Padding used the wrong host/cost");
        m.modifyRetreatCostByEffect(A,A,host,1);
        view=bench?m.benchPokemonView(A).getFirst():m.activePokemonView(A);
        check(view.hitPoints()==(retreat==3?150:100),"Buff Padding ignored live retreat-cost changes");
    }
}
