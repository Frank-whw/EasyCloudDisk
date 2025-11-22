package com.clouddisk.exception;

/**
 * 文件冲突异常，当多个用户同时修改同一文件时抛出。
 */
public class ConflictException extends BusinessException {
    
    private final String conflictType;
    private final String conflictDetails;
    
    public ConflictException(String message, String conflictType, String conflictDetails) {
        super(ErrorCode.CONFLICT, message);
        this.conflictType = conflictType;
        this.conflictDetails = conflictDetails;
    }
    
    public String getConflictType() {
        return conflictType;
    }
    
    public String getConflictDetails() {
        return conflictDetails;
    }
}
