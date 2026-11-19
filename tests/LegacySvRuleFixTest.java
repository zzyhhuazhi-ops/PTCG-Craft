import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.engine.effect.*;
import dev.hua.ptcglive.engine.field.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

/** Real bundled programs; synthetic public boards isolate choice ownership and resolution. */
public final class LegacySvRuleFixTest {
    static final UUID A=CopyAttackTest.A,B=CopyAttackTest.B,WATCH=CopyAttackTest.WATCH;
    static final CombatCatalog DATA=BundledCombatData.load();
    static final List<String> CATS=List.of("sv2_en_015","sv2_en_231","sv2_en_256","sv2_en_271","svbsp_en_033","svbsp_en_078");
    static final List<String> MIMES=List.of("sv4-5_en_031","sv4-5_en_157");
    static void check(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void reject(Runnable r){CopyAttackTest.reject(r);}
    public static void main(String[] args) {
        bundledDecks();
        for(String id:CATS)for(int seed:List.of(2,7,19))bouquet(id,seed);
        bouquetBoundaries();bouquetKnockout();
        for(String id:MIMES)for(int seed:List.of(2,7,19))for(boolean bench:List.of(false,true))mime(id,seed,bench);
        copiedTargetsAndCosts();copiedContinuation();concedeWhileChoosing();unsupportedCopyFailsBeforeDeclaration();
        System.out.println("LegacySvRuleFixTest passed: 8 real printings; 18 Bouquet Magic and 12 Mimed Games scenarios; target/cost/authority/stale/continuation/cleanup boundaries");
    }
    static void bundledDecks() {
        var db=BundledRules.loadCardDefinitions();var rules=BundledRules.loadCurrentRulesets().get(CurrentFormats.CHINESE);
        for(String id:java.util.stream.Stream.concat(CATS.stream(),MIMES.stream()).toList()) {
            var deck=new ArrayList<CardInstance>();
            for(int i=0;i<60;i++)deck.add(new CardInstance(new UUID(1,i+1),A,i<4?"sv1_en_084":i<6?id:"sve_en_005"));
            var report=new DeckValidator(db,DATA).validate(rules,deck);
            check(report.issues().stream().noneMatch(i->i.severity()==DeckIssue.Severity.ERROR),id+": "+report.issues());
        }
    }
    static AbilityDefinition bouquetAbility(String id){return DATA.pokemon(id).orElseThrow().abilities().getFirst();}
    static ServerMatch bouquetFixture(String id,int start,boolean opponentBench) {
        var ability=bouquetAbility(id);ActivatedAbilityRules.validate(ability);
        var defs=List.of(CopyAttackTest.card("cat",true),CopyAttackTest.card("target",true),CopyAttackTest.card("grass",false),CopyAttackTest.card("fire",false));
        var catalog=new CombatCatalog(Map.of("cat",new PokemonProfile(310,0,null,null,0,null,0,2,DATA.pokemon(id).orElseThrow().attacks(),List.of(ability),EnergyType.GRASS),
            "target",new PokemonProfile(100,0,null,EnergyType.GRASS,2,null,0,1,List.of(),List.of(),EnergyType.PSYCHIC)),Map.of("grass",EnergyType.GRASS,"fire",EnergyType.FIRE));
        for(int seed=start;seed<start+100;seed++) {
            var decks=new ArrayList<List<CardInstance>>();
            for(UUID owner:List.of(A,B)){var cards=new ArrayList<CardInstance>();for(int i=0;i<60;i++)cards.add(new CardInstance(new UUID(owner.getLeastSignificantBits(),i+1),owner,
                i<20?(owner.equals(A)?"cat":"target"):i<50?"grass":"fire"));decks.add(cards);}
            var m=new ServerMatch(A,B,decks.get(0),decks.get(1),BundledRules.loadRulesets().get("standard-international-2026"),new CardDatabase(defs),catalog,new ServerRandom(seed));
            OpeningTestSupport.decideOrder(m,A,B);
            if(List.of(A,B).stream().anyMatch(p->m.privateView(p).hand().stream().filter(c->c.definitionId().equals(p.equals(A)?"cat":"target")).count()<2))continue;
            OpeningTestSupport.complete(m,A,B,opponentBench?1:0);m.drawCardsByEffect(A,A,20);m.endTurn(A);m.endTurn(B);return m;
        }throw new AssertionError("unable to build fixture");
    }
    static void bouquet(String id,int seed) {
        var m=bouquetFixture(id,seed,true);UUID source=m.activePokemonView(A).pokemonId(),target=m.benchPokemonView(B).getFirst().pokemonId();
        int grass=(int)m.privateView(A).hand().stream().filter(c->c.definitionId().equals("grass")).count();
        var rng=m.randomTranscript();m.useAbility(A,source,"bouquet-magic");
        var cost=m.effectCardPrompt(A);check(cost!=null&&cost.minimum()==1&&cost.maximum()==1,"exact Grass cost missing");
        check(cost.candidates().stream().allMatch(c->c.definitionId().equals("grass")),"non-Grass cost allowed");
        reject(()->m.submitEffectCards(A,cost.resolutionId(),cost.step(),List.of()));
        m.submitEffectCards(A,cost.resolutionId(),cost.step(),List.of(cost.candidates().getFirst().instanceId()));
        var prompt=m.effectPokemonPrompt(A);check(prompt.targets().size()==1&&prompt.targets().getFirst().pokemonId().equals(target),"not opponent Bench only");
        reject(()->m.chooseEffectPokemon(B,prompt.resolutionId(),prompt.step(),target));
        reject(()->m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),m.activePokemonView(B).pokemonId()));
        m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),target);
        reject(()->m.chooseEffectPokemon(A,prompt.resolutionId(),prompt.step(),target));
        check(m.benchPokemonView(B).getFirst().damage()==30&&m.activePokemonView(B).damage()==0,"counter count/weakness incorrect");
        check(m.privateView(A).hand().stream().filter(c->c.definitionId().equals("grass")).count()==grass-1,"cost not paid exactly once");
        check(!m.hasPendingEffectChoice()&&m.currentPlayer().equals(A)&&rng.equals(m.randomTranscript()),"ability stuck/ended turn/consumed RNG");
        check(!m.canUseAbility(A,source,"bouquet-magic"),"same source used twice");
        m.endTurn(A);m.endTurn(B);check(m.canUseAbility(A,source,"bouquet-magic"),"not reusable next turn");
    }
    static void bouquetBoundaries() {
        var m=bouquetFixture(CATS.getFirst(),2,false);UUID source=m.activePokemonView(A).pokemonId();
        var before=m.privateView(A);check(!m.canUseAbility(A,source,"bouquet-magic"),"empty opposing Bench accepted");
        reject(()->m.useAbility(A,source,"bouquet-magic"));check(before.equals(m.privateView(A)),"invalid use consumed energy");
        var noGrass=bouquetFixture(CATS.getFirst(),2,true);
        noGrass.discardHandCardsByEffect(A,A,noGrass.privateView(A).hand().stream().filter(c->c.definitionId().equals("grass")).map(CardInstance::instanceId).toList());
        check(!noGrass.canUseAbility(A,noGrass.activePokemonView(A).pokemonId(),"bouquet-magic"),"Fire paid Grass cost");
    }
    static void bouquetKnockout() {
        var m=bouquetFixture(CATS.getFirst(),2,true);var source=m.activePokemonView(A).pokemonId();
        var target=m.benchPokemonView(B).getFirst().pokemonId();m.placeDamageCountersByEffect(A,B,target,7);
        m.useAbility(A,source,"bouquet-magic");var cost=m.effectCardPrompt(A);
        m.submitEffectCards(A,cost.resolutionId(),cost.step(),List.of(cost.candidates().getFirst().instanceId()));
        var p=m.effectPokemonPrompt(A);m.chooseEffectPokemon(A,p.resolutionId(),p.step(),target);
        check(m.phase()==MatchPhase.PRIZE_SELECTION&&!m.hasPendingEffectChoice(),"counter KO did not finish ability before claiming Prize");
        m.takePrizeAt(A,0);check(m.phase()==MatchPhase.TURN&&m.currentPlayer().equals(A)&&m.benchPokemonView(B).isEmpty(),"ability KO failed to resume own turn");
    }
    static AttackDefinition mimeAttack(String id){var a=DATA.pokemon(id).orElseThrow().attacks().getFirst();AttackProgramRules.validate(a);return a;}
    static void mime(String id,int seed,boolean bench) {
        var declared=mimeAttack(id);var m=CopyAttackTest.fixture(declared,CopyAttackTest.selected(""),EnergyType.PSYCHIC,seed);
        var session=MatchSession.multiplayer(m,A,B);session.addSpectator(WATCH);
        var v=session.view(A,A);var attack=v.options().stream().filter(o->o.labelZh().startsWith("攻击：")&&o.labelZh().contains(declared.nameZh())).findFirst().orElseThrow();
        session.submit(A,A,v.revision(),attack.index());reject(()->session.submit(A,A,v.revision(),attack.index()));
        check(m.opponentChoosingCopiedAttack()&&m.pendingChoicePlayer().equals(B),"opponent must choose attack");
        reject(()->m.topDeckAttackCopyPrompt(A));var prompt=m.topDeckAttackCopyPrompt(B);
        UUID source=bench?m.benchPokemonView(B).getFirst().pokemonId():m.activePokemonView(B).pokemonId();
        var chosen=prompt.options().stream().filter(o->o.pokemonId().equals(source)).findFirst().orElseThrow();
        reject(()->m.chooseOpponentCopiedAttack(A,prompt.resolutionId(),prompt.step(),source,chosen.entrySequence(),chosen.attackId()));
        reject(()->m.chooseOpponentCopiedAttack(B,prompt.resolutionId(),prompt.step(),source,chosen.entrySequence()+1,chosen.attackId()));
        reject(()->m.declineTopDeckCopiedAttack(B,prompt.resolutionId(),prompt.step()));
        var rng=m.randomTranscript();
        var choosing=session.view(B,B);
        var listed=dev.hua.ptcglive.view.CopyAttackChoicePage.from(choosing,0);
        var issued=java.util.stream.IntStream.range(0,listed.pages())
            .boxed().flatMap(page->dev.hua.ptcglive.view.CopyAttackChoicePage.from(choosing,page).attacks().stream())
            .filter(o->o.cardKeys().contains("field:"+source)&&o.cardKeys().contains("copy-attack:"+chosen.attackId())).findFirst().orElseThrow();
        check(dev.hua.ptcglive.view.CopyAttackChoicePage.active(choosing),"opponent cannot see the copied attack panel");
        check(dev.hua.ptcglive.view.CopyAttackChoicePage.detail(choosing,issued).contains(bench?"备战位 1":"战斗位"),
            "same attack from different field positions is indistinguishable");
        session.submit(B,B,choosing.revision(),issued.index());
        reject(()->m.chooseOpponentCopiedAttack(B,prompt.resolutionId(),prompt.step(),source,chosen.entrySequence(),chosen.attackId()));
        check(m.pendingChoicePlayer().equals(A),"attacker must execute chosen attack");
        reject(()->m.declineTopDeckCopiedAttack(A,prompt.resolutionId(),prompt.step()));
        reject(()->m.chooseTopDeckCopiedAttack(A,prompt.resolutionId(),prompt.step(),UUID.randomUUID(),chosen.attackId(),null,List.of(),List.of(),null));
        check(session.view(B,B).options().stream().noneMatch(o->o.labelZh().startsWith("结算对手指定招式")),"opponent controls attacker target/cost");
        var execute=session.view(A,A);var action=execute.options().stream().filter(o->o.labelZh().startsWith("结算对手指定招式")).findFirst().orElseThrow();
        check(dev.hua.ptcglive.view.CopyAttackChoicePage.active(execute)&&action.cardKeys().contains("copy-source:"+chosen.topCardId()),"attacker lost chosen attack source");
        session.submit(A,A,execute.revision(),action.index());
        check(m.activePokemonView(B).damage()==80&&m.currentPlayer().equals(B),"copied type/cost/turn incorrect");
        check(!m.hasPendingEffectChoice()&&rng.equals(m.randomTranscript()),"copy stuck or consumed extra RNG");
    }
    static void selectOpponent(ServerMatch m,AttackDefinition a) {
        m.attack(A,a.id());var p=m.topDeckAttackCopyPrompt(B);var o=p.options().getFirst();
        m.chooseOpponentCopiedAttack(B,p.resolutionId(),p.step(),o.pokemonId(),o.entrySequence(),o.attackId());
    }
    static void copiedTargetsAndCosts() {
        var a=mimeAttack(MIMES.getFirst());var m=CopyAttackTest.fixture(a,CopyAttackTest.selected(",\"discardSelectedAttachedEnergyAfterAttack\":2,\"damageTarget\":\"OPPONENT_BENCH\""),EnergyType.PSYCHIC,2);
        selectOpponent(m,a);var s=MatchSession.multiplayer(m,A,B);var v=s.view(A,A);
        var action=v.options().stream().filter(o->o.labelZh().startsWith("结算对手指定招式")).findFirst().orElseThrow();s.submit(A,A,v.revision(),action.index());
        check(m.benchPokemonView(B).getFirst().damage()==40&&m.currentPlayer().equals(B),"zero attached energy prevented after-attack discard resolution");
    }
    static void copiedContinuation() {
        var a=mimeAttack(MIMES.getFirst());var m=CopyAttackTest.fixture(a,CopyAttackTest.selected(",\"triggers\":[{\"trigger\":\"AFTER_ATTACK\",\"effects\":[{\"type\":\"choice\",\"effects\":[{\"type\":\"draw\",\"amount\":1},{\"type\":\"draw\",\"amount\":2}]}]}]"),EnergyType.PSYCHIC,2);
        selectOpponent(m,a);var p=m.topDeckAttackCopyPrompt(A);var o=p.options().getFirst();
        m.chooseTopDeckCopiedAttack(A,p.resolutionId(),p.step(),o.pokemonId(),o.attackId(),null,List.of(),List.of(),null);
        var choice=m.effectChoicePrompt(A);check(choice!=null,"copied follow-up choice missing");
        m.chooseEffectOption(A,choice.resolutionId(),choice.step(),0);check(m.currentPlayer().equals(B)&&m.activePokemonView(B).damage()==80,"follow-up failed or duplicate damage");
    }
    static void concedeWhileChoosing() {
        var a=mimeAttack(MIMES.getFirst());var m=CopyAttackTest.fixture(a,CopyAttackTest.selected(""),EnergyType.PSYCHIC,2);m.attack(A,a.id());
        var p=m.topDeckAttackCopyPrompt(B);var o=p.options().getFirst();m.concede(A);
        check(!m.hasPendingTopDeckAttackCopy()&&m.phase()==MatchPhase.FINISHED,"copy prompt survived concession");
        reject(()->m.chooseOpponentCopiedAttack(B,p.resolutionId(),p.step(),o.pokemonId(),o.entrySequence(),o.attackId()));
    }
    static void unsupportedCopyFailsBeforeDeclaration() {
        var a=mimeAttack(MIMES.getFirst());var m=CopyAttackTest.fixture(a,CopyAttackTest.copier(),EnergyType.PSYCHIC,2);
        var before=m.clientView(A);var rng=m.randomTranscript();reject(()->m.attack(A,a.id()));
        check(before.equals(m.clientView(A))&&rng.equals(m.randomTranscript()),"unsupported nested copy stranded a pending attack");
    }
}
