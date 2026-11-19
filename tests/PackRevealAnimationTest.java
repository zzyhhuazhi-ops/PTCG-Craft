import dev.hua.ptcglive.client.PackRevealAnimation;
import static dev.hua.ptcglive.client.PackRevealAnimation.Stage.*;

public final class PackRevealAnimationTest {
    public static void main(String[] args) {
        for (int fps : new int[]{30,60,120,240}) for (int count : new int[]{1,5,20,320,640}) {
            var animation = new PackRevealAnimation(); animation.reset(count);
            int openingEntries = 0, frames = 0; var previous = animation.stage();
            while (animation.stage() != HOLD && frames < fps * 2) {
                animation.update(1d / fps); frames++;
                if (animation.stage() == OPENING && previous != OPENING) openingEntries++;
                previous = animation.stage();
            }
            check(openingEntries == 1, "Batch must tear only once");
            check(Math.abs(frames / (double) fps - 1.03) <= 1d / fps + 1e-8, "Opening duration changed with frame rate or card count");
            animation.update(30); check(animation.cardIndex() == 0 && animation.stage() == HOLD, "Viewing skipped a card");
            for (int i = 0; i < count; i++) {
                check(animation.cardIndex() == i, "Server result order changed");
                animation.proceed(); animation.proceed();
                animation.update(.18 + 1e-8);
            }
            check(animation.stage() == SUMMARY, "Last card did not finish");
        }
        var a = new PackRevealAnimation(); a.reset(0); check(a.stage() == SUMMARY, "Empty result");
        a.reset(5); a.update(.2); a.skip(); check(a.stage() == SUMMARY, "Skip");
        a.reset(5); a.update(.15); double last = -1; int distinct = 0;
        for (int i=0;i<50;i++) { a.update(1d/120); if(a.progress()!=last)distinct++;last=a.progress(); }
        check(distinct==50, "120 Hz animation stepped at tick rate");
        System.out.println("PackRevealAnimationTest passed: 30/60/120/240 FPS, one tear, fixed 1.03s start, 1..640 cards, order and skip");
    }
    private static void check(boolean ok,String message) { if(!ok)throw new AssertionError(message); }
}
