import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class NonPokemonBatch30Test {
    static final UUID A=GeneratedRuleBatch8Test.A,B=GeneratedRuleBatch8Test.B;
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static ServerMatch game(String id){return NonPokemonBatch2Test.game(id,EnergyType.GRASS,1);}
    static void play(ServerMatch m){NonPokemonBatch25Test.play(m);}
    static void settle(ServerMatch m){NonPokemonBatch2Test.settle(m);}
    static CardInstance card(ServerMatch m,UUID owner,String id){return DynamicPassiveModifierTest.hand(m,owner,id);}
    public static void main(String[] args){tools();prizes();karen();draws();roxie();diantha();korrina();compressor();System.out.println("NonPokemonBatch30Test passed: Tool host/name/damage rules, both owners' discard recovery and prize visibility, exact costs and live named counts, GX/legacy EX exclusions, Fairy KO gate, Item search and deck discard");}
    static void tools(){
        for(String id:List.of("xy1_en_119","sm1_en_124","swsh4-5_en_062"))for(boolean valid:List.of(false,true)){
            var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer(id).orElseThrow();c.ownName=valid?"Zacian V":"Zacian VSTAR";var m=GeneratedRuleBatch28Test.fixture(c);GeneratedRuleBatch28Test.play(m);
            if(id.equals("swsh4-5_en_062")){m.attack(A,"hit");check(m.lastAttack().finalDamage()==(valid?130:100),"Rusted Sword ignored exact printed name");}
            else {if(!valid)m.switchActiveByEffect(A,A,m.benchPokemonView(A).getFirst().pokemonId());m.endTurn(A);m.attack(B,"hit");check(m.lastAttack().finalDamage()==(id.equals("xy1_en_119")&&valid?80:100),"Hard Charm wrong host/reduction");check(m.activePokemonView(B).conditions().contains(SpecialCondition.POISONED)==(id.equals("sm1_en_124")&&valid),"Poison Barb triggered from Bench or missed attacker");}
        }
        for(boolean retrieve:List.of(false,true)){
            var m=game(retrieve?"xy3_en_101":"xy2_en_097");UUID owner=retrieve?A:B;var selected=new ArrayList<CardInstance>();
            for(UUID host:List.of(m.activePokemonView(owner).pokemonId(),m.benchPokemonView(owner).getFirst().pokemonId())){var tool=card(m,owner,"tool");selected.add(tool);m.attachToolByEffect(A,owner,EffectSourceZone.HAND,tool.instanceId(),owner,host);}
            play(m);var q=m.effectCardPrompt(A);check(q.candidates().containsAll(selected)&&q.minimum()==(retrieve?0:2),"Tool selection wrong owner or mandatory-all count");m.submitEffectCards(A,q.resolutionId(),q.step(),selected.stream().map(CardInstance::instanceId).toList());settle(m);
            check((retrieve?m.privateView(owner).hand():m.privateView(owner).discard()).containsAll(selected),"Tools moved to incorrect zone/owner");
        }
    }
    static void prizes(){var m=game("swsh7-5r_en_006");var own=List.copyOf(m.privateView(A).prizes());var opp=List.copyOf(m.privateView(B).prizes());play(m);settle(m);m.endTurn(A);m.endTurn(B);for(UUID viewer:List.of(A,B))for(var p:m.clientView(viewer).players())check(p.faceUpPrizes().size()==6,"Team Rocket did not persistently expose both sets of prizes");check(m.privateView(A).prizes().equals(own)&&m.privateView(B).prizes().equals(opp),"Public prize effect reordered cards");}
    static void karen(){var m=game("xybsp_en_177");var pokemon=new HashMap<UUID,CardInstance>();var energies=new HashMap<UUID,CardInstance>();for(UUID owner:List.of(A,B)){pokemon.put(owner,card(m,owner,"evolved"));energies.put(owner,card(m,owner,"energy"));m.discardHandCardsByEffect(A,owner,List.of(pokemon.get(owner).instanceId(),energies.get(owner).instanceId()));}play(m);settle(m);for(UUID owner:List.of(A,B))check(m.privateView(owner).deck().contains(pokemon.get(owner))&&m.privateView(owner).discard().contains(energies.get(owner)),"Karen mixed ownership/categories or lost evolution");}
    static void draws(){
        var m=game("sm7_en_126");int before=m.privateView(A).hand().size();play(m);var q=m.effectCardPrompt(A);var chosen=q.candidates().getFirst();m.submitEffectCards(A,q.resolutionId(),q.step(),List.of(chosen.instanceId()));settle(m);check(m.privateView(A).hand().size()==before+1&&!m.privateView(A).discard().contains(chosen),"Bill failed to shuffle exactly one then draw three");
        m=game("xy12_en_084");NonPokemonBatch2Test.trim(m,A,3,NonPokemonBatch2Test.trainer(m));play(m);settle(m);check(m.privateView(A).hand().size()==7&&m.currentPlayer().equals(B),"Oak failed draw-to-seven then end turn");
        for(int count:List.of(0,2)){
            var profile=DeckDiscardTriggeredAbilityTest.trainer("SUPPORTER","{\"type\":\"draw\",\"target\":\"SELF\",\"amount\":1},{\"type\":\"draw\",\"target\":\"SELF\",\"amount\":2,\"count_type\":\"DISCARD_COUNT\",\"count_target\":\"SELF\",\"definition_ids\":\"trainer\"}");
            var x=GeneratedRuleBatch8Test.fixture(List.of(),EnergyType.GRASS,EnergyType.GRASS,profile,GeneratedRuleBatch8Test.hit(10,false),1,Set.of());
            count=Math.min(count,(int)x.privateView(A).hand().stream().filter(v->v.definitionId().equals("trainer")).count()-1);var copies=x.privateView(A).hand().stream().filter(c->c.definitionId().equals("trainer")).limit(count).toList();if(!copies.isEmpty())x.discardHandCardsByEffect(A,A,copies.stream().map(CardInstance::instanceId).toList());int n=x.privateView(A).hand().size();play(x);settle(x);check(x.privateView(A).hand().size()==n+2*count,"Whitney counted the resolving source or missed earlier copies");
        }
    }
    static void roxie(){for(PokemonFamily family:List.of(PokemonFamily.GX,PokemonFamily.EX_LEGACY,PokemonFamily.EX)){
        var c=new GeneratedRuleBatch28Test.Config();c.trainer=NonPokemonBatch2Test.DATA.trainer("sm12_en_205").orElseThrow();c.ownFamilies=Set.of(family);var m=GeneratedRuleBatch28Test.fixture(c);if(family!=PokemonFamily.EX){check(!m.canPlayTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null),"Roxie accepted only excluded Pokemon");continue;}int before=m.privateView(A).hand().size();play(m);var q=m.effectCardPrompt(A);check(q.candidates().stream().anyMatch(x->x.definitionId().equals("attacker"))==(family==PokemonFamily.EX),"Roxie confused legacy EX/GX with modern ex");var picked=q.candidates().stream().limit(2).map(CardInstance::instanceId).toList();m.submitEffectCards(A,q.resolutionId(),q.step(),picked);settle(m);check(m.privateView(A).hand().size()==before-1+2*picked.size(),"Roxie failed actual discard count");
    }}
    static void diantha(){for(EnergyType type:List.of(EnergyType.GRASS,EnergyType.FAIRY)){
        var m=NonPokemonBatch2Test.game("sm6_en_105",type,1);var extra=card(m,A,"energy");m.discardHandCardsByEffect(A,A,List.of(extra.instanceId()));m.endTurn(A);m.knockOutPokemonByEffect(B,A,m.benchPokemonView(A).getFirst().pokemonId());UnrestrictedAttackPrizeTest.take(m,B);m.endTurn(B);check(m.canPlayTrainer(A,NonPokemonBatch2Test.trainer(m).instanceId(),null)==(type==EnergyType.FAIRY),"Diantha lost previous turn's Fairy type gate");if(type==EnergyType.FAIRY){play(m);var q=m.effectCardPrompt(A);check(q.minimum()==2&&q.maximum()==2,"Diantha did not require two available cards");settle(m);check(m.privateView(A).discard().size()==1,"Diantha did not recover both cards");}
    }}
    static void korrina(){var m=GeneratedRuleBatch23Test.fixture(NonPokemonBatch2Test.DATA.trainer("xy3_en_095").orElseThrow(),7,EnergyType.FIGHTING);play(m);var q=m.effectCardPrompt(A);check(q.candidates().stream().allMatch(x->m.cardDefinition(x.definitionId()).category()==CardCategory.POKEMON),"Korrina first search included non-Pokemon");m.submitEffectCards(A,q.resolutionId(),q.step(),List.of());q=m.effectCardPrompt(A);check(q.candidates().stream().allMatch(x->m.cardDefinition(x.definitionId()).trainerCategory()==TrainerCategory.ITEM),"Korrina Item search accepted modern Tools");settle(m);}
    static void compressor(){var m=game("xy4_en_092");play(m);var q=m.effectCardPrompt(A);var picked=q.candidates().stream().limit(3).toList();m.submitEffectCards(A,q.resolutionId(),q.step(),picked.stream().map(CardInstance::instanceId).toList());settle(m);check(m.privateView(A).discard().containsAll(picked),"Compressor did not discard selected deck cards");}
}
