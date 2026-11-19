package dev.hua.ptcglive.collectibles;

import dev.hua.ptcglive.CardItem;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

/** Physical print condition, independent of rarity and battle rules. Missing data means ungraded. */
public enum CardCondition {
    RUINED("残破不堪",50), FLAWED("略有瑕疵",75), ORDINARY("平平无奇",100),
    PRESERVED("保存完好",125), PERFECT("完美无瑕",150), TREASURE("稀世珍宝",200);
    public final String label; public final int percent;
    CardCondition(String label,int percent){this.label=label;this.percent=percent;}
    public static CardCondition of(ItemStack stack){
        if(!(stack.getItem() instanceof CardItem))return null;
        var data=stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();
        if(!data.contains("ptcglive_condition"))return null;
        try{return valueOf(data.getString("ptcglive_condition"));}
        catch(IllegalArgumentException e){throw new IllegalStateException("卡牌品质数据无效，请勿转换或出售此卡",e);}
    }
    public static boolean graded(ItemStack stack){return of(stack)!=null;}
    public static void apply(ItemStack stack,CardCondition condition){
        if(!(stack.getItem() instanceof CardItem)||stack.getCount()!=1||graded(stack))
            throw new IllegalArgumentException("每张实体卡只能鉴定一次，请放入一张未鉴定卡牌");
        NbtCompound data=stack.getOrDefault(DataComponentTypes.CUSTOM_DATA,NbtComponent.DEFAULT).copyNbt();
        data.putString("ptcglive_condition",condition.name());
        stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(data));
    }
    /** Retire the unused per-print serial from older saves; retain grade and every other component. */
    public static boolean normalizeLegacyStack(ItemStack stack){
        if(!(stack.getItem() instanceof CardItem))return false;
        var component=stack.get(DataComponentTypes.CUSTOM_DATA);
        if(component==null||!component.getNbt().contains("ptcglive_condition_id"))return false;
        try{if(of(stack)==null)return false;}catch(IllegalStateException invalid){return false;}
        var data=component.copyNbt();data.remove("ptcglive_condition_id");
        stack.set(DataComponentTypes.CUSTOM_DATA,NbtComponent.of(data));return true;
    }
    public static int percent(ItemStack stack){var c=of(stack);return c==null?100:c.percent;}
    /** Prices round once per completed order; no minimum overrides the -50% rule. */
    public static long sale(long base,ItemStack stack){return Math.multiplyExact(base,percent(stack))/100;}
}
