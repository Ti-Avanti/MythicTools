package gg.fotia.mythictools.summoning.effect;

final class FlamePattern implements ParticlePattern {
    public void charge(EffectCanvas c) {
        for (int strand = 0; strand < 3; strand++) for (int i = 0; i < c.samples; i++) {
            double t = (double) i / c.samples;
            double a = t * Math.PI * 4 + c.angle + strand * Math.PI * 2 / 3;
            double r = c.layer.radius() * (1 - t * 0.8) * (1 - c.progress * 0.3);
            c.dot(Math.cos(a) * r, t * c.layer.height(), Math.sin(a) * r, i % 3 == 0);
        }
    }
    public void release(EffectCanvas c) {
        double r = c.layer.radius() * c.progress * 1.6;
        c.ring(r, Math.sin(c.progress * Math.PI) * c.layer.height() * 0.5, c.angle, false, false);
        c.ring(r * 0.7, c.layer.height() * (1 - c.progress), -c.angle, false, true);
    }
}
