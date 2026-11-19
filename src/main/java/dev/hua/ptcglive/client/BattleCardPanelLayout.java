package dev.hua.ptcglive.client;

import dev.hua.ptcglive.client.PtcgLiveTableLayout.Rect;

/** Landscape adaptation: the opponent remains on the table behind the lifted card. */
public record BattleCardPanelLayout(Rect card, Rect panel, int scale, int page, int pages, int first, int end) {
    public static BattleCardPanelLayout of(int width, int height, int count, int requestedPage) {
        int k = width >= 960 && height >= 640 ? 2 : 1, margin = 10 * k, gap = 12 * k;
        int panelWidth = Math.min(248 * k, Math.max(174 * k, width * 43 / 100));
        int cardHeight = Math.min(218 * k, Math.min(height - 64 * k,
            (int)((width - panelWidth - gap - 2 * margin) / PtcgLiveTheme.CARD_ASPECT)));
        int cardWidth = Math.round(cardHeight * PtcgLiveTheme.CARD_ASPECT);
        int capacity = Math.max(1, Math.min(4, (height - 106 * k) / (52 * k)));
        int pages = Math.max(1, (count + capacity - 1) / capacity), page = Math.clamp(requestedPage, 0, pages - 1);
        int first = page * capacity, end = Math.min(count, first + capacity), rows = Math.max(1, end - first);
        int panelHeight = (86 + rows * 52) * k;
        int x = (width - cardWidth - gap - panelWidth) / 2;
        int y = Math.clamp(height * 3 / 5 - panelHeight / 2, margin, height - panelHeight - margin);
        int cy = Math.clamp(y + panelHeight / 2 - cardHeight / 2, margin, height - cardHeight - margin);
        return new BattleCardPanelLayout(new Rect(x, cy, cardWidth, cardHeight),
            new Rect(x + cardWidth + gap, y, panelWidth, panelHeight), k, page, pages, first, end);
    }
    public Rect row(int index) { return new Rect(panel.x() + 8 * scale, panel.y() + (49 + (index - first) * 52) * scale,
        panel.width() - 16 * scale, 46 * scale); }
    public Rect close() { return new Rect(panel.x() + panel.width() / 2 - 36 * scale, panel.bottom() - 28 * scale, 72 * scale, 21 * scale); }
    /** Wide screens center the lifted face; narrow screens place the same controls beneath it. */
    public static BattleCardPanelLayout centered(int width,int height,int count,int requestedPage){
        var base=of(width,height,count,requestedPage);int k=base.scale(),gap=12*k,margin=10*k;
        int cw=base.card().width(),ch=base.card().height(),right=width/2+cw/2+gap,room=width-margin-right;
        if(room>=174*k)return new BattleCardPanelLayout(new Rect((width-cw)/2,(height-ch)/2,cw,ch),
            new Rect(right,(height-base.panel().height())/2,Math.min(room,base.panel().width()),base.panel().height()),k,base.page(),base.pages(),base.first(),base.end());
        int capacity=Math.max(1,Math.min(3,(height-250*k)/(52*k)));
        int pages=Math.max(1,(count+capacity-1)/capacity),page=Math.clamp(requestedPage,0,pages-1),first=page*capacity,end=Math.min(count,first+capacity);
        int ph=(86+Math.max(1,end-first)*52)*k,pw=Math.min(width-2*margin,248*k);
        ch=Math.min(218*k,height-ph-gap-2*margin);cw=Math.round(ch*PtcgLiveTheme.CARD_ASPECT);
        int top=(height-ch-gap-ph)/2;
        return new BattleCardPanelLayout(new Rect((width-cw)/2,top,cw,ch),new Rect((width-pw)/2,top+ch+gap,pw,ph),k,page,pages,first,end);
    }
}
