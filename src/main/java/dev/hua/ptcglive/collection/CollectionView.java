package dev.hua.ptcglive.collection;
import dev.hua.ptcglive.CardCatalog;
import java.util.List;
public record CollectionView(String sessionId,int revision,int page,int pages,String query,boolean allCards,
    String selectedBox,List<Box> boxes,List<Row> rows,List<CollectionProgress.Summary> progress,
    String progressPack,CollectionProgress.Filter progressFilter,List<Row> updatedRows,
    CollectionFilters filters,List<CollectionSearch.Facet> facets,List<CollectionSearch.Option> formats,int total,
    CollectionFilters previewFilters,String previewQuery,int previewCount) {
    public record Box(String id,String name) { }
    public record Row(CardCatalog.Entry card,String type,int inventory,int collection,int boxed,boolean discovered) { }
}
