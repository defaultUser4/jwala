package com.siemens.cto.aem.service.group;

import com.siemens.cto.aem.domain.model.group.GroupControlHistory;
import com.siemens.cto.aem.domain.model.group.command.ControlGroupCommand;
import com.siemens.cto.aem.domain.model.temporary.User;

public interface GroupControlService {

    GroupControlHistory controlJvm(final ControlGroupCommand aCommand, final User aUser);

}
