package com.clouddisk.entity;

/**
 * 共享权限类型。
 */
public enum SharePermission {
    READ,
    WRITE;

    public boolean allows(SharePermission required) {
        if (this == WRITE) {
            return true;
        }
        return this == required;
    }
}

