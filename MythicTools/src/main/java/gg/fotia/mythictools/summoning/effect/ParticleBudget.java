package gg.fotia.mythictools.summoning.effect;

/** 一次更新中所有仪式与预览共用的实际玩家发送预算。 */
public final class ParticleBudget {
    private int remaining;
    public void reset(int maximum) { remaining = maximum; }
    public boolean take() { if (remaining <= 0) return false; remaining--; return true; }
}
