package dev.hua.ptcglive.ai;

import com.google.gson.Gson;
import dev.hua.ptcglive.rules.CardInstance;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Public deck lists. Instances exist only inside a match, never in CardBoxService. */
public final class AiPresets {
    public record Preset(String id,String name,String rule,String source,String strategy,Map<String,Integer> cards) {
        public Preset { cards=Collections.unmodifiableMap(new LinkedHashMap<>(cards)); }
        public List<CardInstance> instances(UUID seat) {
            var result=new ArrayList<CardInstance>();cards.forEach((id,count)->{
                for(int i=0;i<count;i++)result.add(new CardInstance(UUID.randomUUID(),seat,id));
            });return List.copyOf(result);
        }
    }
    private static final List<Preset> ALL=load();
    private AiPresets(){}
    public static List<Preset> all(){return ALL;}
    public static Preset require(String id){return ALL.stream().filter(p->p.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("没有该电脑预设卡组"));}
    private static List<Preset> load(){
        try(var input=Objects.requireNonNull(AiPresets.class.getResourceAsStream("/data/ptcglive/ai-practice.json"));
            var reader=new InputStreamReader(input,StandardCharsets.UTF_8)){
            var result=List.of(new Gson().fromJson(reader,Preset[].class));var ids=new HashSet<String>();
            for(var p:result)if(!ids.add(p.id())||p.cards().values().stream().anyMatch(n->n<1||n>60)
                ||p.cards().values().stream().mapToInt(Integer::intValue).sum()!=60)throw new IllegalStateException("电脑卡组必须唯一且恰好60张");
            return result;
        }catch(IOException e){throw new IllegalStateException("读取电脑卡表失败",e);}
    }
}
