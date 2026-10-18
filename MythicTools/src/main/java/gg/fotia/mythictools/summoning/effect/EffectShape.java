package gg.fotia.mythictools.summoning.effect;

public enum EffectShape {
    ARCANE, FLAME, FROST, THUNDER, VOID, NATURE, SOUL, SAND;

    public String key() { return name().toLowerCase(java.util.Locale.ROOT); }
}
