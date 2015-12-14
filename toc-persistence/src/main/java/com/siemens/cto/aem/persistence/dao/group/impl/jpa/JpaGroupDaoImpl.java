package com.siemens.cto.aem.persistence.dao.group.impl.jpa;

import com.siemens.cto.aem.common.request.group.CreateGroupRequest;
import com.siemens.cto.aem.common.request.group.UpdateGroupRequest;
import com.siemens.cto.aem.common.exception.BadRequestException;
import com.siemens.cto.aem.common.exception.NotFoundException;
import com.siemens.cto.aem.common.domain.model.audit.AuditEvent;
import com.siemens.cto.aem.common.domain.model.event.Event;
import com.siemens.cto.aem.common.domain.model.fault.AemFaultType;
import com.siemens.cto.aem.common.domain.model.group.Group;
import com.siemens.cto.aem.common.domain.model.id.Identifier;
import com.siemens.cto.aem.persistence.dao.group.GroupDao;
import com.siemens.cto.aem.persistence.jpa.domain.JpaGroup;
import com.siemens.cto.aem.persistence.jpa.domain.builder.JpaGroupBuilder;

import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class JpaGroupDaoImpl implements GroupDao {

    @PersistenceContext(unitName = "aem-unit")
    private EntityManager entityManager;


    public JpaGroupDaoImpl() {
    }

    @Override
    public Group createGroup(final Event<CreateGroupRequest> aGroupToCreate) {

        try {
            final CreateGroupRequest createGroupCommand = aGroupToCreate.getRequest();
            final AuditEvent auditEvent = aGroupToCreate.getAuditEvent();
            final String userId = auditEvent.getUser().getUserId();
            final Calendar updateDate = auditEvent.getDateTime().getCalendar();

            final JpaGroup jpaGroup = new JpaGroup();
            jpaGroup.setName(createGroupCommand.getGroupName());
            jpaGroup.setCreateBy(userId);
            jpaGroup.setCreateDate(updateDate);
            jpaGroup.setUpdateBy(userId);
            jpaGroup.setLastUpdateDate(updateDate);

            entityManager.persist(jpaGroup);
            entityManager.flush();

            return groupFrom(jpaGroup);
        } catch (final EntityExistsException eee) {
            throw new BadRequestException(AemFaultType.INVALID_GROUP_NAME,
                                          "Group Name already exists: " + aGroupToCreate.getRequest().getGroupName(),
                                          eee);
        }
    }

    @Override
    public Group updateGroup(final Event<UpdateGroupRequest> aGroupToUpdate) {

        try {
            final UpdateGroupRequest updateGroupCommand = aGroupToUpdate.getRequest();
            final AuditEvent auditEvent = aGroupToUpdate.getAuditEvent();
            final Identifier<Group> groupId = updateGroupCommand.getId();
            final JpaGroup jpaGroup = getJpaGroup(groupId);

            jpaGroup.setName(updateGroupCommand.getNewName());
            jpaGroup.setUpdateBy(auditEvent.getUser().getUserId());
            jpaGroup.setLastUpdateDate(auditEvent.getDateTime().getCalendar());

            entityManager.flush();

            return groupFrom(jpaGroup);
        } catch (final EntityExistsException eee) {
            throw new BadRequestException(AemFaultType.INVALID_GROUP_NAME,
                                          "Group Name already exists: " + aGroupToUpdate.getRequest().getNewName(),
                                          eee);
        }
    }

    @Override
    public Group getGroup(final Identifier<Group> aGroupId) throws NotFoundException {
        return groupFrom(getJpaGroup(aGroupId));
    }

    @Override
    public List<Group> getGroups() {

        final CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        final CriteriaQuery<JpaGroup> criteria = builder.createQuery(JpaGroup.class);
        final Root<JpaGroup> root = criteria.from(JpaGroup.class);

        criteria.select(root);

        final TypedQuery<JpaGroup> query = entityManager.createQuery(criteria);

        return groupsFrom(query.getResultList());
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Group> findGroups(final String aName) {

        final Query query = entityManager.createQuery("SELECT g FROM JpaGroup g WHERE g.name LIKE :groupName");
        query.setParameter("groupName", "?" + aName + "?");
        return groupsFrom(query.getResultList());
    }

    @Override
    public void removeGroup(final Identifier<Group> aGroupId) {

        final JpaGroup group = getJpaGroup(aGroupId);
        entityManager.remove(group);
    }

    protected List<Group> groupsFrom(final List<JpaGroup> someJpaGroups) {

        final List<Group> groups = new ArrayList<>();

        for (final JpaGroup jpaGroup : someJpaGroups) {
            groups.add(groupFrom(jpaGroup));
        }

        return groups;
    }

    protected JpaGroup getJpaGroup(final Identifier<Group> aGroup) {

        final JpaGroup jpaGroup = entityManager.find(JpaGroup.class,
                                                     aGroup.getId());

        if (jpaGroup == null) {
            throw new NotFoundException(AemFaultType.GROUP_NOT_FOUND,
                                        "Group not found: " + aGroup);
        }

        return jpaGroup;
    }

    protected Group groupFrom(final JpaGroup aJpaGroup) {
        return new JpaGroupBuilder(aJpaGroup).build();
    }
}
