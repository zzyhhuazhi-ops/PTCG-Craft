import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Real Night Joker / Gemstone Mimicry, sharing the existing authoritative copy resolver. */
public final class ModernCopyRepairTest {
    static final UUID A=CopyAttackTest.A,B=CopyAttackTest.B;
    static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean ok,String why){CopyAttackTest.check(ok,why);}
    static void reject(Runnable op){CopyAttackTest.reject(op);}
    static AttackDefinition declared(String id){var a=DATA.pokemon(id).orElseThrow().attacks().getFirst();AttackProgramRules.validate(a);return a;}
    static ServerMatch game(AttackDefinition copy,boolean matches,int start){
        var hit=CopyAttackTest.selected(",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"damage_counter\",\"target\":\"SELF\",\"amount\":2}]}]");
        var defs=new ArrayList<CardDefinition>();for(String id:List.of("copy","provider","other","energy"))defs.add(new CardDefinition(id,id,id.equals("energy")?CardCategory.ENERGY:CardCategory.POKEMON,
            TrainerCategory.NONE,"I",Set.of("international"),!id.equals("energy"),id.equals("energy"),60,Map.of(),EffectSupport.SUPPORTED,null,
            id.equals("other")&&matches?Set.of(PokemonFamily.TERA,PokemonFamily.EX,PokemonFamily.RULE_BOX):Set.of(),Set.of()));
        var combat=new CombatCatalog(Map.of(
            "copy",profile(copy.damageType(),List.of(copy),"Copier"),
            "provider",profile(EnergyType.FIRE,List.of(hit),matches?"N's Reshiram":"Reshiram"),
            "other",profile(EnergyType.FIRE,List.of(hit),"Opponent")),Map.of("energy",copy.damageType()));
        for(int seed=start;seed<start+100;seed++){
            var decks=new ArrayList<List<CardInstance>>();for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,i<30?(owner.equals(A)?(i<15?"copy":"provider"):"other"):"energy"));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),combat,new ServerRandom(seed));OpeningTestSupport.decideOrder(m,A,B);
            if(m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals("copy"))||m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals("provider"))||m.privateView(B).hand().stream().filter(c->c.definitionId().equals("other")).count()<2)continue;
            m.chooseOpeningActive(A,hand(m,A,"copy").instanceId());m.placeOpeningBench(A,hand(m,A,"provider").instanceId());m.startAfterOpening(A);
            m.chooseOpeningActive(B,hand(m,B,"other").instanceId());m.placeOpeningBench(B,hand(m,B,"other").instanceId());m.startAfterOpening(B);
            OpeningTestSupport.complete(m,A,B,0);m.endTurn(A);m.endTurn(B);return m;
        }throw new AssertionError("copy fixture");
    }
    static PokemonProfile profile(EnergyType type,List<AttackDefinition> attacks,String name){return new PokemonProfile(300,0,null,EnergyType.PSYCHIC,2,null,0,1,attacks,List.of(),type,name,null);}
    static CardInstance hand(ServerMatch m,UUID owner,String id){return m.privateView(owner).hand().stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow();}
    static void pay(ServerMatch m){for(int i=0;i<2;i++){var c=m.privateView(A).deck().stream().filter(x->x.definitionId().equals("energy")).findFirst().orElseThrow();m.attachEnergyByEffect(A,A,EffectSourceZone.DECK,c.instanceId(),A,m.activePokemonView(A).pokemonId());}}
    public static void main(String[] args){
        for(String id:List.of("sv10_en_087","sv9_en_098","sv9_en_175","sv9_en_185","sv9_en_189"))for(int seed:List.of(0,7,19)){
            var a=declared(id);var wrong=game(a,false,seed);pay(wrong);check(wrong.copyAttackOptions(A,a.id()).isEmpty(),"unqualified provider listed: "+id);
            var m=game(a,true,seed);var c=m.copyAttackOptions(A,a.id()).stream().filter(x->x.attackId().equals("hit")).findFirst().orElseThrow();
            check(c.pokemonId().equals(id.startsWith("sv9")?m.benchPokemonView(A).getFirst().pokemonId():m.activePokemonView(B).pokemonId()),"wrong copy source scope");
            reject(()->m.copyAttackWithEnergyDiscards(A,a.id(),c.pokemonId(),c.entrySequence(),c.attackId(),List.of(),List.of()));pay(m);
            var before=m.clientView(A);var rng=m.randomTranscript();
            reject(()->m.copyAttackWithEnergyDiscards(A,a.id(),c.pokemonId(),c.entrySequence()+1,c.attackId(),List.of(),List.of()));
            check(before.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"stale copy mutated match");
            m.copyAttackWithEnergyDiscards(A,a.id(),c.pokemonId(),c.entrySequence(),c.attackId(),List.of(),List.of());
            check(m.activePokemonView(B).damage()==(id.startsWith("sv10")?80:40),"borrowed provider type instead of actual attacker");
            check(m.activePokemonView(A).damage()==20&&m.benchPokemonView(A).getFirst().damage()==0,"copied SELF bound to provider");
            check(m.currentPlayer().equals(B)&&!m.hasPendingEffectChoice()&&rng.equals(m.randomTranscript()),"copy resolution stalled/extra RNG");
        }
        System.out.println("ModernCopyRepairTest passed: five real printings, 15 matches and 15 rejected source-filter scenarios; actual costs/type/SELF, stale source and turn continuation");
    }
}
