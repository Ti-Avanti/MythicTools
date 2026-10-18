package gg.fotia.mythictools.summoning.effect;

final class SandPattern implements ParticlePattern {
    public void charge(EffectCanvas c) {
        for (int crack = 0; crack < 9; crack++) for (int i = 0; i < c.samples / 2; i++) {
            double t = (double) i / (c.samples / 2) * (0.1 + c.progress * 0.9);
            double a = crack * Math.PI * 2 / 9 + Math.sin(i * 1.7 + crack) * 0.12;
            c.dot(Math.cos(a) * c.layer.radius() * t, 0.02, Math.sin(a) * c.layer.radius() * t, i % 4 == 0);
        }
        c.ring(c.layer.radius() * (1 - c.progress * 0.5), 0.2 + Math.sin(c.angle) * 0.1, c.angle, false, true);
    }
    public void release(EffectCanvas c) {
        for (int i = 0; i < c.samples * 2; i++) {
            double a = i * 2.39996;
            double r = c.layer.radius() * c.progress * (0.3 + (i % 11) / 11.0);
            c.dot(Math.cos(a) * r, Math.max(0, c.progress * (1 - c.progress) * 4) * c.layer.height() * (0.4 + i % 5 / 5.0), Math.sin(a) * r, i % 2 == 0);
        }
    }
}
