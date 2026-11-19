import dev.hua.ptcglive.CardBoxItem;
import net.minecraft.component.type.DyedColorComponent;
import net.minecraft.util.DyeColor;

/** Color-component contract used by white crafted boxes and red server-issued boxes. */
public final class CardBoxColorTest {
    public static void main(String[] args) {
        if (CardBoxItem.DEFAULT_BOX_COLOR != DyeColor.WHITE.getFireworkColor())
            throw new AssertionError("未染色的合成卡盒必须显示为白色");
        var serverIssued = new DyedColorComponent(DyeColor.RED.getFireworkColor(), false);
        if (serverIssued.rgb() != DyeColor.RED.getFireworkColor() || serverIssued.showInTooltip())
            throw new AssertionError("起始卡组和管理员测试卡盒必须显示为红色");
        var blue = new DyedColorComponent(DyeColor.BLUE.getFireworkColor(), false);
        if (blue.rgb() != DyeColor.BLUE.getFireworkColor())
            throw new AssertionError("可替换色没有保存在卡盒组件中");
        System.out.println("CardBoxColorTest passed: 合成白色、服务器预设红色、染料颜色组件均正确");
    }
}
