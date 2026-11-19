package dev.hua.ptcglive.client;

import java.util.List;

/** Verifies that phone scrolling eases monotonically, interpolates frames and respects bounds. */
public final class SmoothMotionTest {
    public static void main(String[] args) {
        SmoothMotion motion = new SmoothMotion();
        motion.move(80, 0, 100);
        double previous = motion.value();
        for (int i = 0; i < 40; i++) {
            motion.tick();
            check(motion.value() >= previous, "向下滚动不能回跳");
            check(motion.value() <= 80, "缓动不能越过目标");
            previous = motion.value();
        }
        check(motion.settled(), "有限帧内应收敛到目标");
        check(Math.abs(motion.render(0.5f) - 80) < 0.1, "渲染插值应落在最终目标");

        motion.move(50, 0, 100);
        check(motion.target() == 100, "目标值必须受上界限制");
        motion.jump(25);
        motion.move(-100, 0, 100);
        check(motion.target() == 0, "目标值必须受下界限制");

        var rows = new PagedRows<Integer>();
        rows.accept("all", 0, 3, List.of(1, 2), true);
        rows.accept("all", 2, 3, List.of(5, 6), false);
        check(rows.rows().equals(List.of(1, 2)), "中间批次未到时不能产生可见断层");
        check(rows.nextBatch() == 1, "应优先请求缺少的连续批次");
        rows.accept("all", 1, 3, List.of(3, 4), false);
        check(rows.rows().equals(List.of(1, 2, 3, 4, 5, 6)), "服务端批次必须拼成一个连续列表");
        check(!rows.hasMore(), "所有批次拼接后不应继续请求");
        var updated=new PagedRows<Integer>();
        motion.jump(80);
        updated.accept("all",0,3,List.of(1,3),false);
        check(rows.rows().equals(List.of(1,2,3,4,5,6))&&motion.value()==80,"后台刷新中不能缩短可见列表或跳回顶部");
        updated.accept("all",1,3,List.of(4,5),false);
        updated.accept("all",2,3,List.of(6),false);
        rows=updated;
        check(rows.rows().equals(List.of(1,3,4,5,6))&&motion.value()==80,"移走最后一张后批次边界存在重复/遗漏或重置滚动");
        rows.accept("filtered", 0, 1, List.of(9), false);
        check(rows.rows().equals(List.of(9)), "筛选条件变化必须清空旧批次");
        System.out.println("SmoothMotionTest passed: eased motion and continuous batch joining are valid");
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
