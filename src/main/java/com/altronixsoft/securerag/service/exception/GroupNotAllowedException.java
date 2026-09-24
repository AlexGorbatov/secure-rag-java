package com.altronixsoft.securerag.service.exception;

import lombok.Getter;

/**
 * The caller tried to share a document with a group they are not a member of.
 */
@Getter
public class GroupNotAllowedException extends RuntimeException {

    private final String group;

    public GroupNotAllowedException(String group) {
        super("Caller is not a member of group " + group);
        this.group = group;
    }

}
