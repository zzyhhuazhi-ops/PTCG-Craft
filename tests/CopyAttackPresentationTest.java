import dev.hua.ptcglive.engine.*;
import dev.hua.ptcglive.view.*;
import java.nio.file.*;
import java.util.*;

/** Real issued Slowking actions feed both the view checks and native preview fixtures. */
public final class CopyAttackPresentationTest {
    static final Path OUTPUT=Path.of("outputs/pocket-animation/global/copy-review-20261119/snapshots");
    static void save(String name,MatchSession.Snapshot view)throws Exception {
        Files.createDirectories(OUTPUT);
        Files.writeString(OUTPUT.resolve(name+".json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(view));
    }
    public static void main(String[] args)throws Exception {
        var a=SlowkingOperationTest.A;var b=SlowkingOperationTest.B;
        var match=SlowkingOperationTest.fixture(SlowkingOperationTest.DONOR);var session=MatchSession.multiplayer(match,a,b);
        var before=session.view(a,a);save("before",before);
        var attack=before.options().stream().filter(o->o.enabled()&&o.commandId().equals("seek-inspiration")).findFirst().orElseThrow();
        session.submit(a,a,before.revision(),attack.index());
        var reveal=match.effectCardReveal();var shown=session.view(reveal.confirmingPlayer(),reveal.confirmingPlayer());save("reveal",session.view(a,a));
        var ack=shown.options().stream().filter(o->o.type()==ActionType.ACK_REVEAL).findFirst().orElseThrow();
        session.submit(reveal.confirmingPlayer(),reveal.confirmingPlayer(),shown.revision(),ack.index());
        var choice=session.view(a,a);save("choice",choice);
        SlowkingOperationTest.check(CopyAttackChoicePage.active(choice),"copy panel missing");
        var page=CopyAttackChoicePage.from(choice,0);
        SlowkingOperationTest.check(page.attacks().stream().noneMatch(o->o.type()==ActionType.COPY_SKIP),"decline displaced attack");
        var indices=new HashSet<Integer>();
        for(int n=0;n<page.pages();n++)CopyAttackChoicePage.from(choice,n).attacks().forEach(o->indices.add(o.index()));
        page.decline().forEach(o->indices.add(o.index()));
        SlowkingOperationTest.check(indices.equals(new HashSet<>(EffectChoicePage.all(choice).stream().map(MatchSession.Option::index).toList())),"issued options lost or reindexed");
        SlowkingOperationTest.check(CopyAttackChoicePage.from(choice,999).page()==page.pages()-1&&CopyAttackChoicePage.from(choice,-1).page()==0,"page clamp");
        var copied=page.attacks().stream().filter(o->o.cardKeys().contains("copy-attack:metallic-hammer")).findFirst().orElseThrow();
        SlowkingOperationTest.check(CopyAttackChoicePage.source(copied).equals(SlowkingOperationTest.DONOR),"donor identity");
        SlowkingOperationTest.check(!CopyAttackChoicePage.title(copied).contains("／"),"title retains redundant source prefix");
        SlowkingOperationTest.check(choice.game().resolvingAttack().pokemonId().equals(match.activePokemonView(a).pokemonId()),"panel source must remain attacker");
        session.submit(a,a,choice.revision(),copied.index());
        var follow=session.view(a,a);save("followup",follow);
        SlowkingOperationTest.check(!CopyAttackChoicePage.active(follow),"ordinary optional effect incorrectly shown as copy attack");
        var yes=EffectChoicePage.all(follow).stream().filter(o->o.type()==ActionType.CHOICE).findFirst().orElseThrow();
        session.submit(a,a,follow.revision(),yes.index());save("after",session.view(a,a));
        SlowkingOperationTest.check(match.activePokemonView(b).damage()==300,"presentation changed actual damage");
        SlowkingOperationTest.check(match.activePokemonView(a).attachedEnergyIds().size()==2,"copied cost was charged");
        System.out.println("Copy presentation passed: real server options, attacker identity, pagination, decline, 300 damage and unchanged energy; snapshots exported");
    }
}
