package com.xiaobai.workorder.modules.mesintegration.constant;

public final class MesSyncDirection {
    /** External MES system → this system */
    public static final String INBOUND  = "INBOUND";
    /** This system → External MES system */
    public static final String OUTBOUND = "OUTBOUND";

    private MesSyncDirection() {}
}
