import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Legacy inventory IDs use the archived printing's exact executable profile. */
public final class NonPokemonCnPrintingTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static final Map<String,String> SOURCES=Map.ofEntries(
        Map.entry("sc_cbb3c_1904_triple_star","sv4-5_en_235"),
        Map.entry("sc_cs1ac_211_hr","sc_cs1ac_192_sr"),
        Map.entry("sc_cs2_5c_070_sr","swsh11a_en_024"),
        Map.entry("sc_cs2ac_131_sr","swsh4_en_182"),
        Map.entry("sc_cs2bc_132_sr","swsh4_en_183"),
        Map.entry("sc_cs3_5c_078_sr","swsh6_en_194"),
        Map.entry("sc_cs3_5c_079_sr","swsh6_en_187"),
        Map.entry("sc_cs3_5c_087_hr","swsh6_en_197"),
        Map.entry("sc_cs3ac_151_sr","swsh6_en_196"),
        Map.entry("sc_cs3bc_149_sr","swsh6_en_195"),
        Map.entry("sc_cs4_5c_074_sr","swsh11a_en_023"),
        Map.entry("sc_cs4_5c_076_sr","swsh9a_en_026"),
        Map.entry("sc_cs4_5c_081_hr","swsh7_en_199"),
        Map.entry("sc_cs4ac_155_sr","swsh7_en_202"),
        Map.entry("sc_cs4bc_154_sr","swsh7_en_203"),
        Map.entry("sc_cs5_5c_075_sr","swsh9_en_168"),
        Map.entry("sc_cs5_5c_080_sar","swsh12-5a_en_066"),
        Map.entry("sc_cs5ac_157_sar","swsh12-5a_en_060"),
        Map.entry("sc_cs6_5c_086_sar","swsh12-5a_en_065"),
        Map.entry("sc_cs6_5c_088_sar","swsh12-5a_en_064"),
        Map.entry("sc_cs6bc_158_sar","swsh12-5a_en_059"),
        Map.entry("sc_csv1c_159_sar","sv3-5_en_204"),
        Map.entry("sc_csv1c_163_sar","sv1_en_251"),
        Map.entry("sc_csv2c_145_sr","sv3-5_en_196"),
        Map.entry("sc_csv2c_148_sr","sv2_en_249"),
        Map.entry("sc_csv3c_149_sr","sv2_en_254"),
        Map.entry("sc_csv4c_149_sr","sv4_en_241"),
        Map.entry("sc_csv7c_235_sr","sv5_en_197"),
        Map.entry("sc_csv8c_239_sr","sv6_en_206"),
        Map.entry("sc_csv9_5c_249_sar","sv8-5_en_171"),
        Map.entry("sc_csv9_5c_251_sar","sv8-5_en_172"),
        Map.entry("sc_csv9_5c_253_sar","sv8-5_en_175"),
        Map.entry("sc_csv9_5c_254_sar","sv8-5_en_170"),
        Map.entry("sc_csv9c_237_sr","sv7_en_166"));
    static CardInstance card(ServerMatch m,UUID p,String id){return DynamicPassiveModifierTest.hand(m,p,id);}
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static ServerMatch game(String id){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();c.ownTypes=Set.of(EnergyType.COLORLESS);c.evolvedTypes=c.ownTypes;c.ownFamilies=Set.of(PokemonFamily.V);c.energyType=EnergyType.WATER;var m=GeneratedRuleBatch28Test.fixture(c);
        while(m.benchPokemonView(A).size()<4)m.playBasicPokemonToBench(A,card(m,A,"attacker").instanceId());
        m.discardHandCardsByEffect(A,A,List.of(card(m,A,"energy").instanceId(),card(m,A,"attacker").instanceId()));
        m.evolveByEffect(A,m.activePokemonView(A).pokemonId(),card(m,A,"evolved").instanceId(),EvolutionPermission.CARD_EFFECT_ALL_TIMING);
        for(int counters:id.equals("sc_csv7c_235_sr")?List.of(50,47):List.of(10))m.placeDamageCountersByEffect(A,A,m.activePokemonView(A).pokemonId(),counters);
        if(id.equals("sc_csv1c_159_sar"))m.attachEnergyByEffect(A,B,EffectSourceZone.HAND,card(m,B,"energy").instanceId(),B,m.activePokemonView(B).pokemonId());
        m.endTurn(A);while(m.benchPokemonView(B).size()<4)m.playBasicPokemonToBench(B,card(m,B,"defender").instanceId());for(int i=0;i<3;i++){m.knockOutPokemonByEffect(B,A,m.benchPokemonView(A).getFirst().pokemonId());UnrestrictedAttackPrizeTest.take(m,B);}m.endTurn(B);
        var keep=new LinkedHashSet<UUID>();keep.add(card(m,A,"trainer").instanceId());keep.add(card(m,A,"energy").instanceId());m.privateView(A).hand().stream().filter(x->!x.definitionId().equals("trainer")).limit(2).forEach(x->keep.add(x.instanceId()));m.discardHandCardsByEffect(A,A,m.privateView(A).hand().stream().filter(x->!keep.contains(x.instanceId())).map(CardInstance::instanceId).toList());return m;
    }
    public static void main(String[] args){var defs=BundledRules.loadCardDefinitions();for(var e:SOURCES.entrySet()){var d=defs.find(e.getKey()).orElseThrow();check(d.effectSupport()==EffectSupport.SUPPORTED&&d.trainerCategory()==TrainerCategory.SUPPORTER,"Legacy printing not executable: "+e.getKey());check(NonPokemonBatch2Test.DATA.trainer(e.getKey()).orElseThrow().equals(NonPokemonBatch2Test.DATA.trainer(e.getValue()).orElseThrow()),"Archived printing changed behavior: "+e.getKey());}System.out.println("NonPokemonCnPrintingTest passed: "+SOURCES.size()+" archived inventory IDs retain their exact source programs");}
}
