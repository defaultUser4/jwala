package com.cerner.jwala.common.request.group;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import com.cerner.jwala.common.exception.BadRequestException;
import com.cerner.jwala.common.request.Request;
import com.cerner.jwala.common.rule.group.GroupNameRule;

import java.io.Serializable;

public class CreateGroupRequest implements Serializable, Request {

    private static final long serialVersionUID = 1L;

    private final String groupName;

    public CreateGroupRequest(final String theGroupName) {
        groupName = theGroupName;
    }

    public String getGroupName() {
        return groupName;
    }

    @Override
    public void validate() {
        new GroupNameRule(groupName).validate();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }
        CreateGroupRequest rhs = (CreateGroupRequest) obj;
        return new EqualsBuilder()
                .append(this.groupName, rhs.groupName)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(groupName)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("groupName", groupName)
                .toString();
    }
}