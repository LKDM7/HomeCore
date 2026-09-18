package fr.lkdm.homecore.api.network;

/** Membership roles; operation permissions are evaluated separately on the server. */
public enum NetworkRole {
    /** Unique network owner. */ OWNER,
    /** Delegated administrator. */ ADMIN,
    /** Ordinary member. */ MEMBER,
    /** Read-only member. */ VIEWER
}
