package com.siemens.cto.aem.persistence.service.app.impl;

import com.siemens.cto.aem.common.configuration.TestExecutionProfile;
import com.siemens.cto.aem.persistence.configuration.TestJpaConfiguration;
import com.siemens.cto.aem.persistence.jpa.service.ApplicationCrudService;
import com.siemens.cto.aem.persistence.jpa.service.impl.ApplicationCrudServiceImpl;
import com.siemens.cto.aem.persistence.jpa.service.GroupCrudService;
import com.siemens.cto.aem.persistence.jpa.service.impl.GroupCrudServiceImpl;
import com.siemens.cto.aem.persistence.jpa.service.GroupJvmRelationshipService;
import com.siemens.cto.aem.persistence.jpa.service.impl.GroupJvmRelationshipServiceImpl;
import com.siemens.cto.aem.persistence.jpa.service.JvmCrudService;
import com.siemens.cto.aem.persistence.jpa.service.impl.JvmCrudServiceImpl;
import com.siemens.cto.aem.persistence.service.app.AbstractApplicationPersistenceServiceTest;
import com.siemens.cto.aem.persistence.service.ApplicationPersistenceService;
import com.siemens.cto.aem.persistence.service.GroupPersistenceService;
import com.siemens.cto.aem.persistence.service.impl.JpaGroupPersistenceServiceImpl;
import com.siemens.cto.aem.persistence.service.impl.JpaApplicationPersistenceServiceImpl;
import com.siemens.cto.aem.persistence.service.JvmPersistenceService;
import com.siemens.cto.aem.persistence.service.impl.JpaJvmPersistenceServiceImpl;
import org.junit.runner.RunWith;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.IfProfileValue;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.support.AnnotationConfigContextLoader;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringJUnit4ClassRunner.class)
@Transactional
@EnableTransactionManagement
@IfProfileValue(name = TestExecutionProfile.RUN_TEST_TYPES, value = TestExecutionProfile.INTEGRATION)
@ContextConfiguration(loader = AnnotationConfigContextLoader.class,
                      classes = {JpaApplicationPersistenceServiceImplTest.Config.class
                      })
public class JpaApplicationPersistenceServiceImplTest extends AbstractApplicationPersistenceServiceTest {

    @Configuration
    @Import(TestJpaConfiguration.class)
    static class Config {

        @Bean
        public GroupPersistenceService getGroupPersistenceService() {
            return new JpaGroupPersistenceServiceImpl(getGroupCrudService(),
                                                      getGroupJvmRelationshipService());
        }

        @Bean
        public JvmPersistenceService getJvmPersistenceService() {
            return new JpaJvmPersistenceServiceImpl(getJvmCrudService(), getApplicationCrudService(), getGroupJvmRelationshipService());
        }

        @Bean
        public ApplicationPersistenceService getApplicationPersistenceService() {
            return new JpaApplicationPersistenceServiceImpl(getApplicationCrudService(), getGroupCrudService());
        }

        @Bean
        public GroupCrudService getGroupCrudService() {
            return new GroupCrudServiceImpl();
        }

        @Bean
        public ApplicationCrudService getApplicationCrudService() {
            return new ApplicationCrudServiceImpl();
        }

        @Bean
        public GroupJvmRelationshipService getGroupJvmRelationshipService() {
            return new GroupJvmRelationshipServiceImpl(getGroupCrudService(),
                                                       getJvmCrudService());
        }

        @Bean
        public JvmCrudService getJvmCrudService() {
            return new JvmCrudServiceImpl();
        }
    }
}