package com.siemens.cto.aem.domain.model.group.command;

import java.io.Serializable;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;

import com.siemens.cto.aem.common.exception.BadRequestException;
import com.siemens.cto.aem.domain.model.command.Command;
import com.siemens.cto.aem.domain.model.group.Group;
import com.siemens.cto.aem.domain.model.group.GroupControlOperation;
import com.siemens.cto.aem.domain.model.id.Identifier;

public class ControlGroupCommand implements Serializable, Command {

    private static final long serialVersionUID = 1L;

    private final Identifier<Group> groupId;
    private final GroupControlOperation controlOperation;

    public ControlGroupCommand(final Identifier<Group> theId,
                             final GroupControlOperation theControlOperation) {
        groupId = theId;
        controlOperation = theControlOperation;
    }

    public Identifier<Group> getGroupId() {
        return groupId;
    }

    public GroupControlOperation getControlOperation() {
        return controlOperation;
    }

    @Override
    public void validateCommand() throws BadRequestException {
//        new JvmIdRule(jvmId).validate();  // TODO : need to add this
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
        ControlGroupCommand rhs = (ControlGroupCommand) obj;
        return new EqualsBuilder()
                .append(this.groupId, rhs.groupId)
                .append(this.controlOperation, rhs.controlOperation)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(groupId)
                .append(controlOperation)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("groupId", groupId)
                .append("controlOperation", controlOperation)
                .toString();
    }
}
