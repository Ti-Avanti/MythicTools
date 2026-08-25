package gg.fotia.mythictools.storage;

/** 异步待领取奖励入库的最终结果。 */
public enum QueueResult {
    STORED,
    SERIALIZATION_FAILED,
    JOURNAL_FAILED,
    CLOSING
}
