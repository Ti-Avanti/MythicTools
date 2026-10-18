package gg.fotia.mythictools.summoning.effect;

final class FrostPattern implements ParticlePattern {
    public void charge(EffectCanvas c) {
        double r = c.layer.radius() * (0.2 + 0.8 * c.progress);
        for (int branch = 0; branch < 6; branch++) {
            double a = branch * Math.PI / 3 + c.angle * 0.05;
            c.ray(0, 0, 0, Math.cos(a) * r, 0, Math.sin(a) * r, c.samples / 3, false);
            for (int sign : new int[]{-1, 1}) for (double t : new double[]{0.45, 0.7}) {
                double x = Math.cos(a) * r * t, z = Math.sin(a) * r * t;
                c.ray(x, 0, z, x + Math.cos(a + sign * Math.PI / 3) * r * 0.25,
                        c.layer.height() * 0.08, z + Math.sin(a + sign * Math.PI / 3) * r * 0.25, 5, true);
            }
        }
    }
    public void release(EffectCanvas c) {
        for (int i = 0; i < 12; i++) {
            double a = i * Math.PI / 6;
            double r = c.layer.radius() * (0.3 + c.progress * 1.3);
            c.ray(Math.cos(a) * r * 0.7, 0, Math.sin(a) * r * 0.7,
                    Math.cos(a) * r, Math.sin(c.progress * Math.PI) * c.layer.height(), Math.sin(a) * r, 8, i % 2 == 0);
        }
    }
}
