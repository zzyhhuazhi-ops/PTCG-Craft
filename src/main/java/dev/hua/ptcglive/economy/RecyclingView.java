package dev.hua.ptcglive.economy;
import java.util.*;
public record RecyclingView(String session,int revision,RecyclingService.Source source,Set<String> rarities,
 List<RecyclingService.Row> rows,int offset,int totalRows,long balance,int count,long coins,String confirmation) { }
