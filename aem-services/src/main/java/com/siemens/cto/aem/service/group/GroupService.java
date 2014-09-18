package com.siemens.cto.aem.service.group;

import java.util.List;

import com.siemens.cto.aem.domain.model.group.AddJvmToGroupCommand;
import com.siemens.cto.aem.domain.model.group.AddJvmsToGroupCommand;
import com.siemens.cto.aem.domain.model.group.CreateGroupCommand;
import com.siemens.cto.aem.domain.model.group.Group;
import com.siemens.cto.aem.domain.model.group.RemoveJvmFromGroupCommand;
import com.siemens.cto.aem.domain.model.group.UpdateGroupCommand;
import com.siemens.cto.aem.domain.model.id.Identifier;
import com.siemens.cto.aem.domain.model.temporary.PaginationParameter;
import com.siemens.cto.aem.domain.model.temporary.User;

public interface GroupService {

    Group createGroup(final CreateGroupCommand aCreateGroupCommand,
                      final User aCreatingUser);

    Group getGroup(final Identifier<Group> aGroupId);

    List<Group> getGroups(final PaginationParameter aPaginationParam);

    List<Group> findGroups(final String aGroupNameFragment,
                           final PaginationParameter aPaginationParam);

    Group updateGroup(final UpdateGroupCommand anUpdateGroupCommand,
                      final User anUpdatingUser);

    void removeGroup(final Identifier<Group> aGroupId);

    Group addJvmToGroup(final AddJvmToGroupCommand aCommand,
                        final User anAddingUser);

    Group addJvmsToGroup(final AddJvmsToGroupCommand aCommand,
                         final User anAddingUser);

    Group removeJvmFromGroup(final RemoveJvmFromGroupCommand aCommand,
                             final User aRemovingUser);

    /**
     * Gets the connection details of JVMs under a group specified by id.
     * @param id the group id
     * @return A list of String that describes the connections of JVMs and Web Servers of a group
     *         specified by id. Example description: JVM1 is a member of group2, group3, group4.
     */
    List<String> getOtherGroupingDetailsOfJvms(final Identifier<Group> id);

    /**
     * Gets the connection details of Web Servers under a group specified by id.
     * @param id the group id
     * @return A list of String that describes the connections of Web Servers of a group
     *         specified by id. Example description: WebServer1 is a member of group2, group3, group4.
     */
    List<String> getOtherGroupingDetailsOfWebServers(final Identifier<Group> id);
}
