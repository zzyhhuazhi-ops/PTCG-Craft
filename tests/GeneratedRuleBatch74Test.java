import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Actual Backtrack Badge, Pikipek and Trumbeak, with printed type, evolution and attack costs. */
public final class GeneratedRuleBatch74Test {
    static final UUID A=new UUID(0,7471),B=new UUID(0,7472);
    static final String BADGE="me5_en_074",BASIC="me5_en_066",EVOLVED="me5_en_067";
    static final CardDatabase DB=BundledRules.loadCardDefinitions();static final CombatCatalog DATA=BundledCombatData.load();
    static void check(boolean b,String message){BeforeDamageAttackTest.check(b,message);}
    static CardInstance hand(ServerMatch m,UUID p,String id){return m.privateView(p).hand().stream().filter(c->c.definitionId().equals(id)).findFirst().orElseThrow();}
    static void choose(ServerMatch m,int option){var prompt=m.effectChoicePrompt(A);m.chooseEffectOption(A,prompt.resolutionId(),prompt.step(),option);}
    static ServerMatch game(boolean evolve,boolean attach) {
        var definitions=new ArrayList<CardDefinition>();for(String id:List.of(BADGE,BASIC,EVOLVED))definitions.add(DB.find(id).orElseThrow());
        definitions.add(new CardDefinition("target","测试目标",CardCategory.POKEMON,TrainerCategory.NONE,"J",Set.of("international"),true,false,4,Map.of(),EffectSupport.SUPPORTED,null));
        definitions.add(new CardDefinition("energy","测试基本能量",CardCategory.ENERGY,TrainerCategory.NONE,"J",Set.of("international"),false,true,999,Map.of(),EffectSupport.SUPPORTED,null));
        var dummy=new PokemonProfile(300,1,null,null,0,null,0,1,List.of(DynamicPassiveModifierTest.attack(20,false,false)),List.of(),EnergyType.GRASS);
        var combat=new CombatCatalog(Map.of(BASIC,DATA.pokemon(BASIC).orElseThrow(),EVOLVED,DATA.pokemon(EVOLVED).orElseThrow(),"target",dummy),Map.of("energy",EnergyType.GRASS),Map.of(BADGE,DATA.trainer(BADGE).orElseThrow()));
        for(int seed=0;seed<1000;seed++) {
            var own=new ArrayList<CardInstance>();var other=new ArrayList<CardInstance>();
            for(int i=0;i<60;i++) {own.add(new CardInstance(new UUID(7471,i+1),A,i<4?BASIC:i<8?EVOLVED:i<12?BADGE:"energy"));other.add(new CardInstance(new UUID(7472,i+1),B,i<4?"target":"energy"));}
            var m=new ServerMatch(A,B,own,other,BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(definitions),combat,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().noneMatch(c->m.cardDefinition(c.definitionId()).basicPokemon())))continue;
            OpeningTestSupport.complete(m,A,B,0);m.drawCardsByEffect(A,A,30);m.endTurn(A);m.endTurn(B);
            if(m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals(BADGE))||m.privateView(A).hand().stream().noneMatch(c->c.definitionId().equals(EVOLVED)))continue;
            var host=m.activePokemonView(A).pokemonId();if(evolve)m.evolve(A,host,hand(m,A,EVOLVED).instanceId());
            if(attach)m.playTrainer(A,hand(m,A,BADGE).instanceId(),host);
            return m;
        }
        throw new AssertionError("Could not prepare actual-card fixture");
    }
    public static void main(String[] args) {
        printed();for(boolean retry:List.of(false,true))for(boolean evolved:List.of(false,true))attack(evolved,retry);disabled();
        System.out.println("GeneratedRuleBatch74Test passed: actual supported Backtrack Badge, real Colorless Pikipek/Trumbeak evolution and printed costs, optional full/early retry, fresh Fly damage/protection, no second retry, Tool suppression and turn completion.");
    }
    static void printed() {
        var card=DB.find(BADGE).orElseThrow();var profile=DATA.trainer(BADGE).orElseThrow();
        check(card.effectSupport()==EffectSupport.SUPPORTED&&card.nameZh().equals("重试徽章")&&card.category()==CardCategory.TRAINER&&card.trainerCategory()==TrainerCategory.TOOL&&card.copyLimit()==4&&card.regulationMark().equals("J"),"Badge printed profile changed");
        check(AttackCoinRetry.fromProgram(profile.ongoingProgram()).pokemonType()==EnergyType.COLORLESS,"Badge host permission incorrect");
        check(DATA.pokemon(BASIC).orElseThrow().hitPoints()==70&&DATA.pokemon(EVOLVED).orElseThrow().hitPoints()==90&&DATA.pokemon(EVOLVED).orElseThrow().evolvesFromDefinitionId().equals(BASIC),"Real evolution/HP missing");
        for(String id:List.of(BASIC,EVOLVED))check(DATA.pokemon(id).orElseThrow().pokemonType()==EnergyType.COLORLESS&&DATA.pokemon(id).orElseThrow().attacks().getFirst().energyCost().equals(Map.of(EnergyType.COLORLESS,1)),"Actual type/cost changed");
    }
    static void attack(boolean evolved,boolean retry) {
        var m=game(evolved,true);var host=m.activePokemonView(A).pokemonId();String attack=evolved?"fly":"double-stab";
        var before=m.clientView(A);var random=m.randomTranscript();BeforeDamageAttackTest.reject(()->m.attack(A,attack));
        check(before.equals(m.clientView(A))&&random.equals(m.randomTranscript()),"Unpaid actual attack changed state or consumed coins");m.attachEnergy(A,hand(m,A,"energy").instanceId(),host);
        int n=m.randomTranscript().size();m.attack(A,attack);check(m.hasPendingEffectChoice()&&m.activePokemonView(B).damage()==0,"Actual attack did not wait before resolving coins");
        if(!evolved&&!retry)choose(m,2);
        int first=retry?m.randomTranscript().size():n;choose(m,retry?0:1);
        var draws=m.randomTranscript().subList(first,m.randomTranscript().size());long heads=draws.stream().filter(e->e.result()==0).count();
        check(draws.size()==(evolved?1:2)&&!m.hasPendingEffectChoice()&&m.currentPlayer().equals(B),"Actual retry duplicated or omitted flips/turn end");
        check(m.activePokemonView(B).damage()==(evolved?(heads==1?30:0):heads*10),"Actual attack kept an old result");
        check(m.replayTranscript().stream().filter(e->e.operation().startsWith("发动重投道具：")).count()==(retry?1:0),"Actual Tool usage was not optional/exactly once");
        if(evolved){m.attack(B,"hit");check(m.activePokemonView(A).damage()==(heads==1?0:20),"Fly protection used a discarded coin result");}
    }
    static void disabled() {
        var m=game(false,true);var host=m.activePokemonView(A).pokemonId();m.disableToolEffectsByEffect(A,A,host);m.attachEnergy(A,hand(m,A,"energy").instanceId(),host);
        int n=m.randomTranscript().size();m.attack(A,"double-stab");check(!m.hasPendingEffectChoice()&&m.randomTranscript().size()==n+2&&m.currentPlayer().equals(B),"Disabled actual Tool still offered retry");
    }
}
