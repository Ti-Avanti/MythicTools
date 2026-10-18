package gg.fotia.mythictools.summoning.effect;

final class ArcanePattern implements ParticlePattern {
    public void charge(EffectCanvas c) {
        double r = c.layer.radius();
        c.ring(r, 0, c.angle, false, false);
        c.ring(r * 0.65, 0.08, -c.angle, false, true);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + c.angle;
            c.ray(Math.cos(a) * r * 0.8, 0, Math.sin(a) * r * 0.8,
                    Math.cos(a) * r, 0, Math.sin(a) * r, 4, true);
            double t = EffectCanvas.fraction(c.angle * 0.3 + i / 8.0);
            c.dot(Math.cos(a) * r * t, (1 - t) * c.layer.height() * 0.4, Math.sin(a) * r * t, true);
        }
    }
    public void release(EffectCanvas c) {
        c.ring(c.layer.radius() * (1 + c.progress), 0.1, 0, false, false);
        c.ray(0, 0, 0, 0, c.layer.height() * (1 - c.progress), 0, c.samples, true);
    }
}
