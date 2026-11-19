package dev.hua.ptcglive.client;

/** Small tick-driven easing value with render-tick interpolation for phone lists and pages. */
final class SmoothMotion {
    private static final double EPSILON = 0.05;
    private static final double EASING = 0.34;
    private double previous;
    private double value;
    private double target;

    double value() { return value; }
    double target() { return target; }

    double render(float tickDelta) {
        double delta = Math.max(0.0, Math.min(1.0, tickDelta));
        return previous + (value - previous) * delta;
    }

    void jump(double next) {
        previous = value = target = next;
    }

    void target(double next) {
        target = next;
    }

    void move(double amount, double minimum, double maximum) {
        target = clamp(target + amount, minimum, maximum);
    }

    void clamp(double minimum, double maximum) {
        target = clamp(target, minimum, maximum);
        value = clamp(value, minimum, maximum);
        previous = clamp(previous, minimum, maximum);
    }

    boolean settled() {
        return Math.abs(target - value) <= EPSILON;
    }

    void tick() {
        previous = value;
        double distance = target - value;
        if (Math.abs(distance) <= EPSILON) value = target;
        else value += distance * EASING;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
