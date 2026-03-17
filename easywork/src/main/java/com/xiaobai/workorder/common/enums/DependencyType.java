package com.xiaobai.workorder.common.enums;

/**
 * Operation dependency relationship type.
 * SERIAL: the predecessor must be completed before this operation can start.
 * PARALLEL: the predecessor does not block this operation's start.
 */
public enum DependencyType {
    SERIAL,
    PARALLEL
}
