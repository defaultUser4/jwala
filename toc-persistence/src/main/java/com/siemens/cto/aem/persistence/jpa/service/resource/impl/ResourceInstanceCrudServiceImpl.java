package com.siemens.cto.aem.persistence.jpa.service.resource.impl;

import com.siemens.cto.aem.common.request.resource.ResourceInstanceRequest;
import com.siemens.cto.aem.common.exception.NotFoundException;
import com.siemens.cto.aem.common.exception.NotUniqueException;
import com.siemens.cto.aem.common.domain.model.audit.AuditEvent;
import com.siemens.cto.aem.common.domain.model.event.Event;
import com.siemens.cto.aem.common.domain.model.fault.AemFaultType;
import com.siemens.cto.aem.common.domain.model.id.Identifier;
import com.siemens.cto.aem.common.domain.model.resource.ResourceInstance;
import com.siemens.cto.aem.persistence.jpa.domain.JpaGroup;
import com.siemens.cto.aem.persistence.jpa.domain.JpaResourceInstance;
import com.siemens.cto.aem.persistence.jpa.service.group.GroupCrudService;
import com.siemens.cto.aem.persistence.jpa.service.resource.ResourceInstanceCrudService;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.Calendar;
import java.util.List;

/**
 * Created by z003e5zv on 3/25/2015.
 */
public class ResourceInstanceCrudServiceImpl implements ResourceInstanceCrudService {

    @PersistenceContext(unitName = "aem-unit")
    private EntityManager entityManager;
    private final GroupCrudService groupCrudService;

    public ResourceInstanceCrudServiceImpl(GroupCrudService groupCrudService) {
        this.groupCrudService = groupCrudService;
    }

    @Override
    public JpaResourceInstance createResourceInstance(final Event<ResourceInstanceRequest> aResourceInstanceToCreate) throws NotUniqueException {

        JpaResourceInstance jpaResourceInstance = new JpaResourceInstance();
        final AuditEvent auditEvent = aResourceInstanceToCreate.getAuditEvent();
        final Calendar updateTime = auditEvent.getDateTime().getCalendar();
        final String userId = auditEvent.getUser().getUserId();
        ResourceInstanceRequest command = aResourceInstanceToCreate.getRequest();

        final JpaGroup group = groupCrudService.getGroup(command.getGroupName());

        jpaResourceInstance.setName(command.getName());
        jpaResourceInstance.setGroup(group);
        jpaResourceInstance.setAttributes(command.getAttributes());
        jpaResourceInstance.setResourceTypeName(command.getResourceTypeName());

        jpaResourceInstance.setCreateBy(userId);
        jpaResourceInstance.setCreateDate(updateTime);
        jpaResourceInstance.setUpdateBy(userId);
        jpaResourceInstance.setLastUpdateDate(updateTime);

        entityManager.persist(jpaResourceInstance);
        entityManager.flush();
        return jpaResourceInstance;
    }

    @Override
    public JpaResourceInstance updateResourceInstanceAttributes(final Identifier<ResourceInstance> resourceInstanceId, final Event<ResourceInstanceRequest> aResourceInstanceToUpdate) {
        ResourceInstanceRequest command = aResourceInstanceToUpdate.getRequest();

        JpaResourceInstance jpaResourceInstance = getJpaResourceInstance(resourceInstanceId);
        jpaResourceInstance.setAttributes(command.getAttributes());
        entityManager.persist(jpaResourceInstance);
        entityManager.flush();
        return jpaResourceInstance;
    }
    @Override
    public JpaResourceInstance updateResourceInstanceName(final Identifier<ResourceInstance> resourceInstanceId, final Event<ResourceInstanceRequest> updateResourceInstanceNameCommandEvent) {
        ResourceInstanceRequest command = updateResourceInstanceNameCommandEvent.getRequest();

        JpaResourceInstance jpaResourceInstance = getJpaResourceInstance(resourceInstanceId);
        jpaResourceInstance.setName(command.getName());
        entityManager.persist(jpaResourceInstance);
        entityManager.flush();
        return jpaResourceInstance;
    }


    @Override
    public JpaResourceInstance getResourceInstance(final Identifier<ResourceInstance> aResourceInstanceId) throws NotFoundException {
        return getJpaResourceInstance(aResourceInstanceId);
    }

    @SuppressWarnings("unchecked")
	@Override
    public List<JpaResourceInstance> getResourceInstancesByGroupId(final Long groupId) {
        final Query query = entityManager.createQuery("SELECT r from JpaResourceInstance r where r.group.id = :groupId");
        query.setParameter("groupId", groupId);
        return query.getResultList();
    }


    @Override
    public JpaResourceInstance getResourceInstanceByGroupIdAndName(final Long groupId, final String name) throws NotFoundException, NotUniqueException {
        final Query query = entityManager.createQuery("SELECT r from JpaResourceInstance r where r.resourceInstanceName = :name and r.group.id = :groupId");
        query.setParameter("name", name);
        query.setParameter("groupId", groupId);
        @SuppressWarnings("unchecked")
		List<JpaResourceInstance> list = query.getResultList();
        if (list == null || list.size() < 1) {
            throw new NotFoundException(AemFaultType.RESOURCE_INSTANCE_NOT_FOUND, "ResourceInstance (group: " + groupId + ", name: " + name + ") not found");
        } else if (list.size() > 1){
            throw new NotUniqueException(AemFaultType.DATA_CONTROL_ERROR, "ResourceInstance (group: " + groupId + ", name: " + name + ") not unique");
        }
        return list.get(0);
    }
    
    @SuppressWarnings("unchecked")
	@Override
    public List<JpaResourceInstance> getResourceInstancesByGroupIdAndResourceTypeName(final Long groupId, final String resourceTypeName) {
        final Query query = entityManager.createQuery("SELECT r from JpaResourceInstance r where r.resourceTypeName = :resourceTypeName and r.group.id = :groupId");
        query.setParameter("resourceTypeName", resourceTypeName);
        query.setParameter("groupId", groupId);
        return query.getResultList();
    }

    @Override
    public void deleteResourceInstance(final Identifier<ResourceInstance> aResourceInstanceId) {
        JpaResourceInstance jpaResourceInstance = getJpaResourceInstance(aResourceInstanceId);
        entityManager.remove(jpaResourceInstance);
        entityManager.flush();
    }

    protected JpaResourceInstance getJpaResourceInstance(final Identifier<ResourceInstance> aResourceInstanceId) throws NotFoundException {

        final JpaResourceInstance jpaResourceInstance = entityManager.find(JpaResourceInstance.class,
                aResourceInstanceId.getId());

        if (jpaResourceInstance == null) {
            throw new NotFoundException(AemFaultType.RESOURCE_INSTANCE_NOT_FOUND,
                    "Resource Instance not found: " + jpaResourceInstance);
        }
        return jpaResourceInstance;
    }
}
