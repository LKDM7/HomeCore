package fr.lkdm.homecore.api.action;

/** Semantic control types exposed to dashboard consumers. */
public enum ActionType {
    /** A button without a parameter; its value is {@link Unit#INSTANCE}. */
    BUTTON,
    /** Boolean switch. */
    TOGGLE,
    /** Integer input. */
    INTEGER,
    /** Floating point input. */
    DOUBLE,
    /** Bounded floating point input. */
    SLIDER,
    /** Selection among explicit options. */
    SELECT,
    /** Bounded text input. */
    TEXT,
    /** Block position input. */
    POSITION
}
