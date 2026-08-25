package gg.fotia.mythictools.reward;

/** 怪物对掉落组的权重与本次容量配置。 */
public record DropGroupRef(String groupId, long weight, int minAmount, int maxAmount) {
}
