import dev.hua.ptcglive.starter.NewbieDeckCatalog;
import dev.hua.ptcglive.collection.LiveDeckParser;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.*;
import java.time.Clock;
import java.nio.file.*;
import java.util.*;

public final class NewbieDeckTest {
    public static void main(String[] args) throws Exception {
        var catalog=NewbieDeckCatalog.loadBundled();
        var names=List.of("自爆多龙","玛俐的长毛巨魔","沙奈朵","赛富豪","竹兰的烈咬陆鲨","海豚侠","幸福蛋","火箭队的超梦","猛雷鼓","电友");
        require(catalog.decks().stream().map(NewbieDeckCatalog.Deck::name).toList().equals(names),"names");
        var db=BundledRules.loadCardDefinitions();
        var combat=BundledCombatData.load();
        var format=BundledRules.loadCurrentRulesets().get(CurrentFormats.OPEN);
        StringBuilder report=new StringBuilder("新手随机卡组：用户提供的十套卡表\n\n");
        for(var deck:catalog.decks()) {
            require(deck.expanded().size()==60,"size "+deck.name());
            var boxes=new CardBoxService(Clock.systemUTC()); var owner=UUID.randomUUID();
            var box=boxes.createPopulated(owner,deck.name(),CurrentFormats.OPEN,deck.expanded());
            var validation=new DeckValidator(db,combat).validate(format,boxes.contentsForValidation(owner,box.id()));
            require(validation.canStartMatch(),"deck validation "+deck.name()+": "+validation);
            report.append(deck.name()).append("\n").append(LiveDeckParser.export(deck.cards())).append("\n规则检查：").append(validation).append("\n\n");
        }
        var seen=new HashSet<String>();
        for(int seed=0;seed<500;seed++) {
            var pair=catalog.selectPair(new ServerRandom(seed));
            require(!pair.get(0).name().equals(pair.get(1).name()),"duplicate pair");
            require(pair.equals(catalog.selectPair(new ServerRandom(seed))),"determinism");
            seen.add(pair.stream().map(NewbieDeckCatalog.Deck::name).sorted().toList().toString());
        }
        require(seen.size()==45,"all pairs covered");
        for(var entry:Map.of("sm4_en_091","sv4_en_160","swsh1_en_185","sv1_en_197","sm1_en_118","sv1_en_174").entrySet()) {
            require(combat.trainer(entry.getKey()).orElseThrow().equals(combat.trainer(entry.getValue()).orElseThrow()),"reused trainer program differs "+entry);
        }
        var owner=UUID.randomUUID(); var boxes=new CardBoxService(Clock.systemUTC());
        var pair=catalog.grantPair(owner,boxes,new ServerRandom(12));
        require(pair.size()==2 && boxes.snapshot().cards().size()==120,"grant count");
        require(boxes.snapshot().cards().stream().map(CardBoxService.SavedCard::instanceId).distinct().count()==120,"unique cards");
        var restored=CardBoxService.restore(Clock.systemUTC(),boxes.snapshot());
        for(var box:pair) require(restored.contentsForValidation(owner,box.id()).size()==60,"persistent deck");
        int before=boxes.snapshot().cards().size();
        boxes.onChanged(()->{throw new IllegalStateException("injected persistence failure");});
        try { catalog.grantPair(owner,boxes,new ServerRandom(18)); throw new AssertionError("no rollback"); }
        catch(IllegalStateException expected) { }
        require(boxes.snapshot().cards().size()==before && boxes.snapshot().boxes().size()==2,"rollback");
        Files.createDirectories(Path.of("outputs/newbie-decks-0.7.35"));
        Files.writeString(Path.of("outputs/newbie-decks-0.7.35/卡表核对.txt"),report.toString());
        System.out.println("NewbieDeckTest passed: 10 exact decks, 60 cards each, all 45 distinct pairs, independent instances, persistence and rollback.");
    }
    private static void require(boolean ok,String what) { if(!ok) throw new AssertionError(what); }
}
