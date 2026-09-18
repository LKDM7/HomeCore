package fr.lkdm.homecore.api.metric;

/** Scheduling hint for consumers; it does not start polling by itself. */
public enum UpdatePolicy {
    /** Every server tick when subscribed. */ REALTIME,
    /** Frequent refresh. */ FAST,
    /** Ordinary refresh. */ NORMAL,
    /** Infrequent refresh. */ SLOW,
    /** Refresh only after a value change. */ ON_CHANGE,
    /** Value is normally constant. */ STATIC
}
