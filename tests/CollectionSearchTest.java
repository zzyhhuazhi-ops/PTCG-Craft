import com.google.gson.Gson;
import dev.hua.ptcglive.CardCatalog;
import dev.hua.ptcglive.collection.*;
import dev.hua.ptcglive.rules.*;
import java.util.*;

public final class CollectionSearchTest {
    private static final Map<String,CardCatalog.Entry> CARDS=new HashMap<>();
    public static void main(String[] args) {
        CardCatalog.load().forEach(c->CARDS.put(c.id(),c));
        check(CARDS.size()==14784&&CollectionSearch.allFacts().keySet().containsAll(CARDS.keySet()),"full catalogue after 13 explicit removals, not only playable cards");
        var all=CollectionFilters.ALL;
        check(count(all)==CARDS.size(),"unrestricted collection");
        var grass=all.toggle("type","GRASS").toggle("kind","POKEMON");
        check(matches("bw1_en_001",grass)&&matches("bw1_en_003",grass),"known grass printings");
        check(matches("bw1_en_001",grass.toggle("stage","BASIC"))&&!matches("bw1_en_003",grass.toggle("stage","BASIC")),"basic versus stage two");
        var both=grass.toggle("type","FIRE");check(count(both)>count(grass),"OR within type");
        check(count(both.toggle("era","bw"))<count(both),"AND across facets");
        check(CollectionSearch.facts("bw3_en_025").stage().equals("RESTORED"),"restored not basic");
        check(CollectionSearch.facts("swshbsp_en_254").stage().equals("VSTAR"),"VSTAR promo");
        check(matches("sc_cs3ac_161_hr",all.toggle("stage","VMAX").toggle("trait","V")),"Chinese printed VMAX subtype");
        check(CollectionSearch.facts("sc_cbb5c_0101_circle").category().equals("POKEMON"),"collectible card has real category");
        check(matches("sc_cbb5c_0101_circle",all.toggle("type","LIGHTNING").toggle("stage","BASIC")),"collectible searchable without fabricated battle data");
        check(BundledRules.loadCardDefinitions().find("sc_cbb5c_0101_circle").isPresent(),"reviewed exclusive combat rules missing");
        for(var entry:CollectionSearch.allFacts().entrySet())if(entry.getValue().types().size()>1)
            for(String type:entry.getValue().types())check(matches(entry.getKey(),all.toggle("type",type)),"both types searchable");
        check(matches("sc_csm1ac_182_sr",all.toggle("kind","SUPPORTER").toggle("rarity","SR")),"Chinese supporter and exact rarity");
        check(matches("sve_en_001",all.withFormat(CurrentFormats.INTERNATIONAL)),"basic energy format exception");
        check(matches("swsh2_en_154",all.withFormat(CurrentFormats.INTERNATIONAL)),"reviewed old reprint format exception");
        check(matches("bw1_en_001",all.withFormat(CurrentFormats.INTERNATIONAL)),"old Snivy inherits current same-name eligibility");
        check(!matches("sm10_en_001",all.withFormat(CurrentFormats.INTERNATIONAL)),"unmarked card without a current same-name printing remains ineligible");
        check(matches("sc_cbb5c_0101_circle",all.withFormat(CurrentFormats.INTERNATIONAL)),"format is pool eligibility, separate from combat support");
        check(CollectionSearch.matches(CARDS.get("bw1_en_001"),0,0,all,CollectionSearch.queryTerms("Snivy 草")),"localized and English combined search");
        check(CollectionSearch.matches(CARDS.get("sve_en_001"),0,0,all,CollectionSearch.queryTerms("朱紫 energy")),"era punctuation and English category search");
        check(CollectionSearch.matches(CARDS.get("sc_cbb5c_0101_circle"),0,0,all,CollectionSearch.queryTerms("标记：H")),"existing mark syntax");
        check(!CollectionFilters.DEFAULT.acceptsOwnership(0,0),"history and boxed cards do not imply ownership");
        check(all.withOwnership("MISSING").acceptsOwnership(0,0)&&!all.withOwnership("MISSING").acceptsOwnership(0,1),"missing uses live counts");
        check(all.withOwnership("DUPLICATES").acceptsOwnership(1,1)&&!all.withOwnership("DUPLICATES").acceptsOwnership(0,1),"inventory plus collection duplicate count");
        check(all.withOwnership("NOT_DEPOSITED").acceptsOwnership(1,0)&&!all.withOwnership("NOT_DEPOSITED").acceptsOwnership(0,1),"deposit changes classification without reset");
        var draft=all.toggle("era","sv");check(all.facets().isEmpty(),"cancelled draft cannot mutate applied filters");
        check(CollectionSearch.parseFilters(new Gson().toJson(draft)).equals(draft),"network roundtrip");
        var rows=CARDS.values().stream().limit(120).map(c->new CollectionView.Row(c,CollectionSearch.typeLabel(c.id()),0,0,0,false)).toList();
        var view=new CollectionView(UUID.randomUUID().toString(),0,0,124,"",true,"",List.of(),rows,List.of(),"",CollectionProgress.Filter.ALL,List.of(),all,CollectionSearch.facets(),CollectionSearch.formats(),14784,draft,"",50);
        check(new Gson().toJson(view).length()<200000,"network response stays within existing codec limit");
        check(new Gson().fromJson(new Gson().toJson(view),CollectionView.class).filters().equals(all),"view and draft filters roundtrip");
        for(String bad:List.of("null","[]","{}","{\"facets\":null}","x".repeat(8193)))reject(bad);
        reject(new Gson().toJson(all.toggle("era","made-up")));
        reject(new Gson().toJson(all.withFormat("unregistered")));
        for(String sort:CollectionFilters.SORTS) {
            var items=new ArrayList<>(CARDS.values());items.sort(CollectionSearch.comparator(all.withSort(sort)));
            var reversed=new ArrayList<>(items);Collections.reverse(reversed);
            var actual=new ArrayList<>(CARDS.values());actual.sort(CollectionSearch.comparator(all.withSort(sort).reverse()));
            check(actual.equals(reversed),"deterministic ascending/descending "+sort);
            check(new HashSet<>(items).size()==14784,"sort does not merge distinct printings");
        }
        long start=System.nanoTime();for(int i=0;i<10;i++)count(grass);
        long millis=(System.nanoTime()-start)/1_000_000;
        System.out.println("CollectionSearchTest passed: 14784 printings, facets, OR/AND, dual types, evolution, exact rarities, pool exceptions, ownership, malformed requests, stable ordering; 10 full scans "+millis+" ms");
    }
    static int count(CollectionFilters f){return (int)CARDS.values().stream().filter(c->CollectionSearch.matches(c,0,0,f,List.of())).count();}
    static boolean matches(String id,CollectionFilters f){return CollectionSearch.matches(CARDS.get(id),0,0,f,List.of());}
    static void reject(String value){try{CollectionSearch.parseFilters(value);throw new AssertionError("invalid filter accepted");}catch(IllegalArgumentException expected){}}
    static void check(boolean b,String message){if(!b)throw new AssertionError(message);}
}
