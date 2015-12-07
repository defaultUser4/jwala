package com.siemens.cto.aem.persistence.dao.impl;

import com.siemens.cto.aem.persistence.dao.HistoryDao;
import com.siemens.cto.aem.persistence.jpa.domain.JpaGroup;
import com.siemens.cto.aem.persistence.jpa.domain.JpaHistory;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;
import java.util.List;

/**
 * {@link HistoryDao} implementation.
 *
 * Created by JC043760 on 11/30/2015.
 */
public class HistoryDaoImpl implements HistoryDao {

    private static final String PARAM_GROUP_NAME = "groupName";

    @PersistenceContext(unitName = "aem-unit")
    private EntityManager em;

    @Override
    public void createHistory(final String name, final JpaGroup group, final String event, String user) {
        em.persist(new JpaHistory(name, group, event, user));
    }

    @Override
    public List<JpaHistory> findHistory(final String groupName, final int numOfRecs) {
        final Query q = em.createNamedQuery(JpaHistory.QRY_GET_HISTORY_BY_GROUP_NAME);
        q.setParameter(PARAM_GROUP_NAME, groupName);
        q.setMaxResults(numOfRecs);
        return q.getResultList();
    }
}
