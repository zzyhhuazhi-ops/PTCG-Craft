package dev.hua.ptcglive.view;

import dev.hua.ptcglive.engine.ActionType;
import dev.hua.ptcglive.engine.MatchSession;
import java.util.List;

/** The server's continuation options, with the optional decline kept outside attack pagination. */
public record CopyAttackChoicePage(List<MatchSession.Option> attacks, List<MatchSession.Option> decline,
                                   int page, int pages) {
    public CopyAttackChoicePage { attacks=List.copyOf(attacks);decline=List.copyOf(decline); }
    public static boolean active(MatchSession.Snapshot view) {
        return EffectChoicePage.all(view).stream().anyMatch(o -> !source(o).isEmpty());
    }
    public static CopyAttackChoicePage from(MatchSession.Snapshot view,int requested) {
        var all=EffectChoicePage.all(view);
        var attacks=all.stream().filter(o -> o.type()!=ActionType.COPY_SKIP).toList();
        var decline=all.stream().filter(o -> o.type()==ActionType.COPY_SKIP).toList();
        int pages=Math.max(1,(attacks.size()+1)/2),page=Math.clamp(requested,0,pages-1);
        return new CopyAttackChoicePage(attacks.subList(Math.min(attacks.size(),page*2),Math.min(attacks.size(),page*2+2)),decline,page,pages);
    }
    public static String source(MatchSession.Option option) {
        return option.cardKeys().stream().filter(k -> k.startsWith("copy-source:"))
            .map(k -> k.substring("copy-source:".length())).findFirst().orElse("");
    }
    public static String title(MatchSession.Option option) {
        String label=option.labelZh();int slash=label.indexOf('／');
        return slash<0?label:label.substring(slash+1);
    }
    public static String detail(MatchSession.Snapshot view,MatchSession.Option option) {
        String id=source(option);
        String label=option.labelZh();
        int prefix=label.indexOf('：'),end=label.indexOf('／');
        if(!id.isEmpty()&&prefix>=0&&end>prefix+1)return "招式来源 · "+label.substring(prefix+1,end);
        return id.isEmpty()?option.labelZh():"招式来源 · "+view.cardNames().getOrDefault(id,id);
    }
}
