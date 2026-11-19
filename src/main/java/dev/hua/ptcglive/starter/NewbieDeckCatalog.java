package dev.hua.ptcglive.starter;

import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.collection.LiveDeckParser;
import dev.hua.ptcglive.engine.ServerRandom;
import dev.hua.ptcglive.rules.CardBoxService;
import dev.hua.ptcglive.rules.CurrentFormats;
import dev.hua.ptcglive.rules.DeckBox;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/** Author-supplied deck lists; exact printed IDs use the existing Live importer. */
public final class NewbieDeckCatalog {
    public record Deck(String name, Map<String,Integer> cards) {
        public Deck { cards = Collections.unmodifiableMap(new LinkedHashMap<>(cards)); }
        public List<String> expanded() {
            var result = new ArrayList<String>(60);
            cards.forEach((id,count) -> { for(int i=0;i<count;i++) result.add(id); });
            return List.copyOf(result);
        }
    }
    private final List<Deck> decks;
    private NewbieDeckCatalog(List<Deck> decks) { this.decks=List.copyOf(decks); }
    public List<Deck> decks() { return decks; }
    public static NewbieDeckCatalog loadBundled() {
        try(var stream=NewbieDeckCatalog.class.getResourceAsStream("/ptcg-newbie-decks.txt")) {
            if(stream==null) throw new IllegalStateException("缺少新手卡组卡表");
            return parse(new String(stream.readAllBytes(),StandardCharsets.UTF_8),
                CardCatalog.load().stream().map(CardCatalog.Entry::id).collect(Collectors.toSet()));
        } catch(IOException failure) { throw new IllegalStateException("无法读取新手卡组卡表",failure); }
    }
    public static NewbieDeckCatalog parse(String text, Set<String> availableIds) {
        var result=new ArrayList<Deck>();
        var names=new HashSet<String>();
        for(String group:text.replace("\uFEFF", "").replace("\r\n","\n").replace('\r','\n').strip().split("\n[\\t ]*\n")) {
            String[] parts=group.strip().split("\\R",2);
            if(parts.length!=2 || parts[0].isBlank() || !names.add(parts[0].strip()))
                throw new IllegalArgumentException("新手卡组缺少名称或名称重复");
            try { result.add(new Deck(parts[0].strip(),LiveDeckParser.parse(parts[1],availableIds))); }
            catch(IllegalArgumentException failure) {
                throw new IllegalArgumentException("新手卡组“"+parts[0]+"”："+failure.getMessage(),failure);
            }
        }
        if(result.size()!=10) throw new IllegalArgumentException("新手卡组必须恰好十套，实际 "+result.size()+" 套");
        return new NewbieDeckCatalog(result);
    }
    /** Uniform draw without replacement: every unordered pair has probability 1/45. */
    public List<Deck> selectPair(ServerRandom random) {
        int first=random.nextInt("newbie-deck-first",decks.size());
        int second=random.nextInt("newbie-deck-second",decks.size()-1);
        if(second>=first) second++;
        return List.of(decks.get(first),decks.get(second));
    }
    public List<DeckBox> grantPair(UUID owner,CardBoxService boxes,ServerRandom random) {
        Objects.requireNonNull(owner);
        var pair=selectPair(random);
        return boxes.transaction(() -> pair.stream().map(deck ->
            boxes.createPopulated(owner,deck.name(),CurrentFormats.OPEN,deck.expanded())).toList());
    }
}
