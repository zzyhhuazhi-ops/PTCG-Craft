import com.google.gson.*;
import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.nio.file.*;
import java.util.*;

/** Isolates each reviewed attachment node, retaining its count, zone and Energy filter. */
public final class SelfAttachmentSourceTest {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    record Case(String id,JsonObject effect) { }
    static List<Case> cases(){try{var out=new ArrayList<Case>();var rows=JsonParser.parseString(Files.readString(Path.of("outputs/pokemon-self-attachment-diff.json"))).getAsJsonArray();for(var r:rows){var row=r.getAsJsonObject();for(var c:row.getAsJsonArray("changes"))out.add(new Case(row.get("id").getAsString(),c.getAsJsonObject().getAsJsonObject("after")));}return out;}catch(Exception e){throw new RuntimeException(e);}}
    static void check(boolean b,String why){GeneratedRuleBatch8Test.check(b,why);}
    static UUID other(UUID p){return p.equals(A)?B:A;}
    static List<CardInstance> zone(ServerMatch m,UUID p,String name){return PairedTrainerPlayTest.zone(m,p,name);}
    static AttackDefinition attack(Case c,boolean switched){var a=JsonParser.parseString("{\"id\":\"source-attachment\",\"nameZh\":\"来源附能测试\",\"energyCost\":{},\"baseDamage\":0,\"damageType\":\"COLORLESS\",\"effectSupport\":\"SUPPORTED\",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[]}]}").getAsJsonObject();var effects=a.getAsJsonArray("triggers").get(0).getAsJsonObject().getAsJsonArray("effects");if(switched)effects.add(JsonParser.parseString("{\"type\":\"switch\",\"target\":\"SELECT_OWN_POKEMON\"}"));effects.add(c.effect.deepCopy());return BundledCombatData.attack(a,EffectRegistry.standard());}
    static void run(Case c,UUID p,boolean switched,int available){
        var type=c.effect.has("energy_type")?EnergyType.valueOf(c.effect.get("energy_type").getAsString()):EnergyType.GRASS;
        var attack=attack(c,switched);var m=GeneratedRuleBatch8Test.fixture(List.of(),type,EnergyType.GRASS,GeneratedRuleBatch8Test.trainer("swsh5_en_124"),attack);if(p.equals(B))m.endTurn(A);
        boolean deck=c.effect.get("source").getAsString().equals("DECK"),basic=c.effect.get("basic_energy_only").getAsBoolean(),typed=c.effect.has("energy_type");var from=zone(m,p,deck?"deck":"discard");var hand=zone(m,p,"hand");
        var old=from.stream().filter(x->m.cardDefinition(x.definitionId()).category()==CardCategory.ENERGY).toList();from.removeAll(old);hand.addAll(old);
        var desired=new ArrayList<CardInstance>();for(int i=0;i<available;i++){var e=DynamicPassiveModifierTest.hand(m,p,"energy");hand.remove(e);from.add(e);desired.add(e);}
        // A special card has the same provided type in play, but no off-field basic type.
        for(String id:List.of("wrong","special")){var e=DynamicPassiveModifierTest.hand(m,p,id);hand.remove(e);from.add(e);if(!typed&&(id.equals("wrong")||!basic))desired.add(e);}
        var source=m.activePokemonView(p).pokemonId();var replacement=m.benchPokemonView(p).getFirst().pokemonId();int rng=m.randomTranscript().size(),before=from.size();m.attack(p,attack.id());
        if(switched){var q=m.effectPokemonPrompt(p);m.chooseEffectPokemon(p,q.resolutionId(),q.step(),replacement);}
        var chosen=new ArrayList<CardInstance>();if(m.hasPendingCardSelection()){
            var q=m.effectCardPrompt(p);check(new HashSet<>(q.candidates()).equals(new HashSet<>(desired)),"Energy filter changed "+c.id);
            var opponent=m.privateView(other(p)).hand().getFirst();AttackDiscardUnitsTest.atomic(m,()->m.submitEffectCards(p,q.resolutionId(),q.step(),List.of(opponent.instanceId())));
            int n=Math.min(c.effect.get("amount").getAsInt(),desired.size());chosen.addAll(desired.subList(0,n));var ids=chosen.stream().map(CardInstance::instanceId).toList();
            AttackDiscardUnitsTest.atomic(m,()->m.submitEffectCards(other(p),q.resolutionId(),q.step(),ids));m.submitEffectCards(p,q.resolutionId(),q.step(),ids);
            GeneratedRuleBatch8Test.reject(()->m.submitEffectCards(p,q.resolutionId(),q.step(),ids));
        }else check(desired.isEmpty(),"Available source attachment was skipped "+c.id);
        check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(other(p)),"Source attachment stranded turn "+c.id);
        var host=EventTargetBindingTest.field(m).pokemonView(p,source);check(new HashSet<>(host.attachedEnergyIds()).equals(new HashSet<>(chosen.stream().map(CardInstance::instanceId).toList())),"Attachment did not retain original source "+c.id);
        if(switched)check(m.activePokemonView(p).pokemonId().equals(replacement)&&m.activePokemonView(p).attachedEnergyIds().isEmpty(),"Attachment rebound to replacement Active "+c.id);
        check(from.size()==before-chosen.size(),"Energy moved from wrong zone "+c.id);
        check(m.randomTranscript().size()==rng+(deck?Math.max(0,from.size()-1):0),"Deck shuffle or discard-only RNG changed "+c.id);
    }
    public static void main(String[] args){var cases=cases();check(cases.size()==135,"Source repair inventory changed");for(var c:cases)for(UUID p:List.of(A,B))for(boolean switched:List.of(false,true))for(int available:List.of(0,1,3))run(c,p,switched,available);System.out.println("SelfAttachmentSourceTest passed: 135 reviewed attachment nodes, both seats, active/switched source, deck/discard sources, typed/basic/any Energy filters, zero/partial/full availability, stale/foreign/wrong-player rejection, and exact shuffle behavior");}
}
