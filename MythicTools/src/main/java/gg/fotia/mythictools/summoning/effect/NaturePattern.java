package gg.fotia.mythictools.summoning.effect;

final class NaturePattern implements ParticlePattern {
    public void charge(EffectCanvas c) {
        for (int vine = 0; vine < 4; vine++) for (int i = 0; i < c.samples; i++) {
            double t = (double) i / c.samples * (0.2 + 0.8 * c.progress);
            double a = vine * Math.PI / 2 + t * 4 + c.angle * 0.2;
            double r = c.layer.radius() * Math.cos(t * Math.PI / 2);
            c.dot(Math.cos(a) * r, Math.sin(t * Math.PI / 2) * c.layer.height(), Math.sin(a) * r, i % 5 < 2);
        }
    }
    public void release(EffectCanvas c) {
        for (int i = 0; i < c.samples * 2; i++) {
            double a = i * Math.PI / c.samples;
            double r = c.layer.radius() * (1 + c.progress) * (0.7 + Math.sin(a * 5) * 0.3);
            c.dot(Math.cos(a) * r, Math.sin(c.progress * Math.PI) * c.layer.height() * 0.5, Math.sin(a) * r, true);
        }
    }
}
