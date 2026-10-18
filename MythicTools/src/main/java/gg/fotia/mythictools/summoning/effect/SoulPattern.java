package gg.fotia.mythictools.summoning.effect;

final class SoulPattern implements ParticlePattern {
    public void charge(EffectCanvas c) {
        for (int soul = 0; soul < 7; soul++) for (int i = 0; i < c.samples / 3; i++) {
            double t = EffectCanvas.fraction(i / (double) (c.samples / 3) + soul / 7.0 + c.angle * 0.08);
            double a = soul * Math.PI * 2 / 7 + Math.sin(t * 8 + c.angle) * 0.25;
            double r = c.layer.radius() * (1 - t);
            c.dot(Math.cos(a) * r, 0.1 + Math.pow(t, 4) * c.layer.height(), Math.sin(a) * r, i % 3 == 0);
        }
    }
    public void release(EffectCanvas c) {
        for (int i = 0; i < c.samples; i++) {
            double t = (double) i / c.samples;
            c.dot(Math.sin(t * 20 + c.angle) * 0.3, (t + c.progress) * c.layer.height(), Math.cos(t * 20 + c.angle) * 0.3, true);
        }
    }
}
