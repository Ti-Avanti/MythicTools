package gg.fotia.mythictools.runtime;

/** 可先激活、再原子发布的插件运行时。 */
public interface ManagedRuntime extends AutoCloseable {
    /** 完成所有可能失败的激活步骤；此时尚未成为当前运行时。 */
    void activate() throws Exception;

    /** 仅释放本运行时拥有的资源；实现必须幂等。 */
    @Override
    void close();
}
