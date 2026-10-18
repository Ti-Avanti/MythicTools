package gg.fotia.mythictools.summoning.effect;

final class ThunderPattern implements ParticlePattern {
    public void charge(EffectCanvas c) {
        c.ring(c.layer.radius(), 0, c.angle, false, false);
        for (int branch = 0; branch < 5; branch++) {
            double a = branch * Math.PI * 2 / 5;
            arc(c, Math.cos(a) * c.layer.radius(), 0, Math.sin(a) * c.layer.radius(), c.progress * c.layer.height());
        }
    }
    private void arc(EffectCanvas c, double x, double y, double z, double height) {
        for (int i = 0; i < c.samples / 2; i++) {
            double t = (double) i / (c.samples / 2);
            double jitter = Math.sin(i * 13.7 + c.angle * 7) * 0.25 * Math.sin(t * Math.PI);
            c.dot(x * (1 - t) + jitter, y + height * t, z * (1 - t) - jitter, true);
        }
    }
    public void release(EffectCanvas c) {
        arc(c, 0.1, 0, 0.1, c.layer.height() * 1.8);
        c.ring(c.layer.radius() * c.progress * 1.8, 0.1, c.angle, false, true);
    }
}
