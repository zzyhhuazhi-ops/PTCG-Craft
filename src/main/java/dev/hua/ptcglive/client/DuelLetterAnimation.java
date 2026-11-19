package dev.hua.ptcglive.client;

import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public final class DuelLetterAnimation {
    private static int start=-100;
    private static Hand hand;
    private static ItemStack stack=ItemStack.EMPTY;
    public static void start(AbstractClientPlayerEntity player,Hand used,ItemStack item){start=player.age;hand=used;stack=item.copyWithCount(1);}
    public static float progress(AbstractClientPlayerEntity player,Hand used,float delta){
        float age=player.age-start+delta;return hand==used&&age>=0&&age<9?age/9:-1;
    }
    public static ItemStack stack(){return stack;}
    private DuelLetterAnimation(){}
}
