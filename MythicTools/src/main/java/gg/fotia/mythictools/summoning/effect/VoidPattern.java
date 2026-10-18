package gg.fotia.mythictools.summoning.effect;

final class VoidPattern implements ParticlePattern {
    public void charge(EffectCanvas c) {
        double r = c.layer.radius() * (0.15 + c.progress * 0.55);
        for (int i = 0; i < c.samples * 2; i++) {
            double a = i * Math.PI / c.samples + c.angle;
            c.dot(Math.cos(a) * r, c.layer.height() * 0.5 + Math.sin(a) * c.layer.height() * 0.5, 0, i % 3 == 0);
            double t = EffectCanvas.fraction(i / (double) (c.samples * 2) + c.angle * 0.15);
            c.dot(Math.cos(a) * r * (1 + t), c.layer.height() * 0.5 + Math.sin(a) * r, t * 2, true);
        }
    }
    public void release(EffectCanvas c) {
        c.ring(c.layer.radius() * (1 - c.progress), c.layer.height() * 0.5, c.angle, true, false);
        c.ring(c.layer.radius() * (1 + c.progress), c.layer.height() * 0.5, -c.angle, true, true);
    }
}
