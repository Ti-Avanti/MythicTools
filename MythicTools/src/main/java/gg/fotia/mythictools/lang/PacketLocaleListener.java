package gg.fotia.mythictools.lang;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.common.client.WrapperCommonClientSettings;
import java.util.function.Supplier;

/** 从客户端设置数据包捕获真实 Locale。 */
public final class PacketLocaleListener extends PacketListenerAbstract {
    private final Supplier<LocaleService> localeService;

    public PacketLocaleListener(LocaleService localeService) {
        this(() -> localeService);
    }

    public PacketLocaleListener(Supplier<LocaleService> localeService) {
        this.localeService = localeService;
    }

    /** 注册到 PacketEvents。 */
    public void register() {
        PacketEvents.getAPI().getEventManager().registerListener(this);
    }

    /** 从 PacketEvents 注销。 */
    public void unregister() {
        PacketEvents.getAPI().getEventManager().unregisterListener(this);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        if (event.getPacketType() != PacketType.Play.Client.CLIENT_SETTINGS
                && event.getPacketType() != PacketType.Configuration.Client.CLIENT_SETTINGS) {
            return;
        }
        WrapperCommonClientSettings<?> settings = new WrapperCommonClientSettings<>(event);
        LocaleService current = localeService.get();
        if (current != null) {
            current.updateClientLocale(event.getUser().getUUID(), settings.getLocale());
        }
    }
}
