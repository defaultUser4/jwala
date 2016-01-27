package com.siemens.cto.aem.service.jvm.impl;

import com.siemens.cto.aem.common.properties.ApplicationProperties;
import com.siemens.cto.aem.common.request.group.AddJvmToGroupRequest;
import com.siemens.cto.aem.common.request.jvm.CreateJvmRequest;
import com.siemens.cto.aem.common.exception.BadRequestException;
import com.siemens.cto.aem.control.command.RuntimeCommandBuilder;
import com.siemens.cto.aem.common.exec.CommandOutput;
import com.siemens.cto.aem.common.exec.ExecReturnCode;
import com.siemens.cto.aem.common.exec.RuntimeCommand;
import com.siemens.cto.aem.common.domain.model.group.Group;
import com.siemens.cto.aem.common.domain.model.id.Identifier;
import com.siemens.cto.aem.common.domain.model.jvm.Jvm;
import com.siemens.cto.aem.common.domain.model.jvm.JvmState;
import com.siemens.cto.aem.common.request.jvm.CreateJvmAndAddToGroupsRequest;
import com.siemens.cto.aem.common.request.jvm.UpdateJvmRequest;
import com.siemens.cto.aem.common.domain.model.path.Path;
import com.siemens.cto.aem.common.domain.model.ssh.SshConfiguration;
import com.siemens.cto.aem.common.domain.model.user.User;
import com.siemens.cto.aem.persistence.service.JvmPersistenceService;
import com.siemens.cto.aem.service.VerificationBehaviorSupport;
import com.siemens.cto.aem.service.group.GroupService;
import com.siemens.cto.aem.service.state.StateService;
import com.siemens.cto.aem.service.webserver.component.ClientFactoryHelper;
import com.siemens.cto.toc.files.FileManager;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Matchers;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpResponse;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class JvmServiceImplVerifyTest extends VerificationBehaviorSupport {

    private JvmServiceImpl impl;
    private JvmPersistenceService jvmPersistenceService;
    private GroupService groupService;
    private User user;
    private FileManager fileManager;
    private ClientFactoryHelper factoryHelper;
    private SshConfiguration sshConfig;
    private RuntimeCommandBuilder rtCommandBuilder;
    private RuntimeCommand command;
    private StateService<Jvm, JvmState> stateService;

    @SuppressWarnings("unchecked")
    @Before
    public void setup() {
        jvmPersistenceService = mock(JvmPersistenceService.class);
        groupService = mock(GroupService.class);
        user = new User("unused");
        fileManager = mock(FileManager.class);
        factoryHelper = mock(ClientFactoryHelper.class);
        sshConfig = mock(SshConfiguration.class);
        stateService = (StateService<Jvm, JvmState>)mock(StateService.class);
        impl = new JvmServiceImpl(jvmPersistenceService, groupService, fileManager, factoryHelper, stateService, sshConfig);
        rtCommandBuilder = mock(RuntimeCommandBuilder.class);
        command = mock(RuntimeCommand.class);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testCreateValidate() {
        System.setProperty(ApplicationProperties.PROPERTIES_ROOT_PATH, "./src/test/resources");

        final CreateJvmRequest createJvmRequest = mock(CreateJvmRequest.class);
        final Jvm jvm = new Jvm(new Identifier<Jvm>(99L), "testJvm", new HashSet<Group>());
        when(jvmPersistenceService.createJvm(any(CreateJvmRequest.class))).thenReturn(jvm);

        impl.createJvm(createJvmRequest, user);

        verify(createJvmRequest, times(1)).validate();
        verify(jvmPersistenceService, times(1)).createJvm(createJvmRequest);

        System.clearProperty(ApplicationProperties.PROPERTIES_ROOT_PATH);
    }

    @Test
    public void testCreateValidateAdd() {

        final CreateJvmRequest createJvmRequest = mock(CreateJvmRequest.class);
        final CreateJvmAndAddToGroupsRequest command = mock(CreateJvmAndAddToGroupsRequest.class);
        final Jvm jvm = mockJvmWithId(new Identifier<Jvm>(-123456L));
        final Set<AddJvmToGroupRequest> addCommands = createMockedAddRequests(3);

        when(command.toAddRequestsFor(eq(jvm.getId()))).thenReturn(addCommands);
        when(command.getCreateCommand()).thenReturn(createJvmRequest);
        when(jvmPersistenceService.createJvm(createJvmRequest)).thenReturn(jvm);

        impl.createAndAssignJvm(command,
                user);

        verify(createJvmRequest, times(1)).validate();
        verify(jvmPersistenceService, times(1)).createJvm(createJvmRequest);
        for (final AddJvmToGroupRequest addCommand : addCommands) {
            verify(groupService, times(1)).addJvmToGroup(matchCommand(addCommand),
                    eq(user));
        }
    }

    @Test
    public void testUpdateJvmShouldValidateCommand() {

        final UpdateJvmRequest updateJvmRequest = mock(UpdateJvmRequest.class);
        final Set<AddJvmToGroupRequest> addCommands = createMockedAddRequests(5);

        when(updateJvmRequest.getAssignmentCommands()).thenReturn(addCommands);

        impl.updateJvm(updateJvmRequest,
                user);

        verify(updateJvmRequest, times(1)).validate();
        verify(jvmPersistenceService, times(1)).updateJvm(updateJvmRequest);
        verify(jvmPersistenceService, times(1)).removeJvmFromGroups(Matchers.<Identifier<Jvm>>anyObject());
        for (final AddJvmToGroupRequest addCommand : addCommands) {
            verify(groupService, times(1)).addJvmToGroup(matchCommand(addCommand),
                    eq(user));
        }
    }

    @Test
    public void testRemoveJvm() {

        final Identifier<Jvm> id = new Identifier<>(-123456L);

        impl.removeJvm(id);

        verify(jvmPersistenceService, times(1)).removeJvm(eq(id));
    }

    @Test
    public void testFindByName() {

        final String fragment = "unused";

        impl.findJvms(fragment);

        verify(jvmPersistenceService, times(1)).findJvms(eq(fragment));
    }

    @Test(expected = BadRequestException.class)
    public void testFindByInvalidName() {

        final String badFragment = "";

        impl.findJvms(badFragment);
    }

    @Test
    public void testFindByGroup() {

        final Identifier<Group> id = new Identifier<>(-123456L);

        impl.findJvms(id);

        verify(jvmPersistenceService, times(1)).findJvmsBelongingTo(eq(id));
    }

    @Test
    public void testGetAll() {

        impl.getJvms();

        verify(jvmPersistenceService, times(1)).getJvms();
    }


    @Test
    public void testGenerateConfig() throws IOException {

        final Jvm jvm = new Jvm(new Identifier<Jvm>(-123456L),
                "jvm-name", "host-name", new HashSet<Group>(), 80, 443, 443, 8005, 8009, new Path("/"),
                "EXAMPLE_OPTS=%someEnv%/someVal", JvmState.JVM_STOPPED.toPersistentString(), null);
        final ArrayList<Jvm> jvms = new ArrayList<>(1);
        jvms.add(jvm);

        when(jvmPersistenceService.findJvms(eq(jvm.getJvmName()))).thenReturn(jvms);
        when(jvmPersistenceService.getJvmTemplate(eq("server.xml"), eq(jvm.getId()))).thenReturn("<server>test</server>");
        String generatedXml = impl.generateConfigFile(jvm.getJvmName(), "server.xml");

        assert !generatedXml.isEmpty();
    }

    @Test
    public void testGetSpecific() {

        final Identifier<Jvm> id = new Identifier<>(-123456L);

        impl.getJvm(id);

        verify(jvmPersistenceService, times(1)).getJvm(eq(id));
    }

    protected Jvm mockJvmWithId(final Identifier<Jvm> anId) {
        final Jvm jvm = mock(Jvm.class);
        when(jvm.getId()).thenReturn(anId);
        return jvm;
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGenerateServerXmlConfig() {
        String testJvmName = "testjvm";
        List<Jvm> jvmList = new ArrayList<>();
        jvmList.add(new Jvm(new Identifier<Jvm>(99L), "testJvm", new HashSet<Group>()));
        when(jvmPersistenceService.findJvms(testJvmName)).thenReturn(jvmList);
        String expectedValue = "<server>xml-content</server>";
        when(jvmPersistenceService.getJvmTemplate(anyString(), any(Identifier.class))).thenReturn(expectedValue);

        // happy case
        String serverXml = impl.generateConfigFile(testJvmName, "server.xml");
        assertEquals(expectedValue, serverXml);

        // return too many jvms
        jvmList.add(new Jvm(new Identifier<Jvm>(999L), "testJvm2", new HashSet<Group>()));
        when(jvmPersistenceService.findJvms(testJvmName)).thenReturn(jvmList);
        boolean isBadRequest = false;
        try {
            impl.generateConfigFile(testJvmName, "server.xml");
        } catch (BadRequestException e) {
            isBadRequest = true;
        }
        assertTrue(isBadRequest);

        // return no jvms
        when(jvmPersistenceService.findJvms(testJvmName)).thenReturn(new ArrayList<Jvm>());
        isBadRequest = false;
        try {
            impl.generateConfigFile(testJvmName, "server.xml");
        } catch (BadRequestException e) {
            isBadRequest = true;
        }
        assertTrue(isBadRequest);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGenerateContextXmlConfig() {
        String testJvmName = "testjvm";
        List<Jvm> jvmList = new ArrayList<>();
        jvmList.add(new Jvm(new Identifier<Jvm>(99L), "testJvm", new HashSet<Group>()));
        when(jvmPersistenceService.findJvms(testJvmName)).thenReturn(jvmList);
        String expectedValue = "<server>xml-content</server>";
        when(jvmPersistenceService.getJvmTemplate(anyString(), any(Identifier.class))).thenReturn(expectedValue);

        // happy case
        String serverXml = impl.generateConfigFile(testJvmName, "server.xml");
        assertEquals(expectedValue, serverXml);

        // return too many jvms
        jvmList.add(new Jvm(new Identifier<Jvm>(999L), "testJvm2", new HashSet<Group>()));
        when(jvmPersistenceService.findJvms(testJvmName)).thenReturn(jvmList);
        boolean isBadRequest = false;
        try {
            impl.generateConfigFile(testJvmName, "server.xml");
        } catch (BadRequestException e) {
            isBadRequest = true;
        }
        assertTrue(isBadRequest);

        // return no jvms
        when(jvmPersistenceService.findJvms(testJvmName)).thenReturn(new ArrayList<Jvm>());
        isBadRequest = false;
        try {
            impl.generateConfigFile(testJvmName, "server.xml");
        } catch (BadRequestException e) {
            isBadRequest = true;
        }
        assertTrue(isBadRequest);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGenerateSetenvBatConfig() {
        String testJvmName = "testjvm";
        List<Jvm> jvmList = new ArrayList<>();
        jvmList.add(new Jvm(new Identifier<Jvm>(99L), "testJvm", new HashSet<Group>()));
        when(jvmPersistenceService.findJvms(testJvmName)).thenReturn(jvmList);
        String expectedValue = "<server>xml-content</server>";
        when(jvmPersistenceService.getJvmTemplate(anyString(), any(Identifier.class))).thenReturn(expectedValue);

        // happy case
        String serverXml = impl.generateConfigFile(testJvmName, "server.xml");
        assertEquals(expectedValue, serverXml);

        // return too many jvms
        jvmList.add(new Jvm(new Identifier<Jvm>(999L), "testJvm2", new HashSet<Group>()));
        when(jvmPersistenceService.findJvms(testJvmName)).thenReturn(jvmList);
        boolean isBadRequest = false;
        try {
            impl.generateConfigFile(testJvmName, "server.xml");
        } catch (BadRequestException e) {
            isBadRequest = true;
        }
        assertTrue(isBadRequest);

        // return no jvms
        when(jvmPersistenceService.findJvms(testJvmName)).thenReturn(new ArrayList<Jvm>());
        isBadRequest = false;
        try {
            impl.generateConfigFile(testJvmName, "server.xml");
        } catch (BadRequestException e) {
            isBadRequest = true;
        }
        assertTrue(isBadRequest);
    }

    @Test
    public void testPerformDiagnosis() {
        Identifier<Jvm> aJvmId = new Identifier<>(11L);
        Jvm jvm = new Jvm(aJvmId, "testJvm", new HashSet<Group>());
        when(jvmPersistenceService.getJvm(aJvmId)).thenReturn(jvm);
        impl.performDiagnosis(aJvmId);
        String diagnosis = impl.performDiagnosis(aJvmId);
        assertTrue(!diagnosis.isEmpty());
    }

    @Test
    @Ignore
    // TODO: Fix this, please see commented out codes.
    public void testSecureCopy() throws IOException, URISyntaxException {
        Jvm jvm = mock(Jvm.class);//new Jvm(new Identifier<Jvm>(99L), "testJvm", "testHostName", new HashSet<LiteGroup>(), 12, 13, 14, 15, 16,new Path("./stp-test.png"),"");
        when(jvm.getStatusUri()).thenReturn(new URI("http://server/testUri.png"));
        when(jvm.getJvmName()).thenReturn("testJvm");
        CommandOutput successReturnData = new CommandOutput(new ExecReturnCode(0), "", "");
        when(command.execute()).thenReturn(successReturnData);
        when(rtCommandBuilder.build()).thenReturn(command);
        when(factoryHelper.requestGet(any(URI.class))).thenReturn(new MockClientHttpResponse(new byte[]{}, HttpStatus.REQUEST_TIMEOUT));
//        boolean commandFailed = false;
//        CommandOutput result = null;
//        try {
//            result = impl.secureCopyFile(jvm, rtCommandBuilder);
//        } catch (CommandFailureException e) {
//            commandFailed = true;
//        }
//        assertNotNull(result);
//        assertEquals(new ExecReturnCode(0), result.getReturnCode());
    }

    @Test
    public void testGetResourceTemplateNames(){
        String testJvmName = "testJvmName";
        ArrayList<String> value = new ArrayList<>();
        when(jvmPersistenceService.getResourceTemplateNames(testJvmName)).thenReturn(value);
        value.add("testJvm.tpl");
        List<String> result = impl.getResourceTemplateNames(testJvmName);
        assertTrue(result.size() == 1);
    }

    @Test
    public void testGetResourceTemplate(){
        String testJvmName = "testJvmName";
        String resourceTemplateName = "test-resource.tpl";
        Jvm jvm = mock(Jvm.class);
        String expectedValue = "<template>resource</template>";
        when(jvmPersistenceService.getResourceTemplate(testJvmName, resourceTemplateName)).thenReturn(expectedValue);
        List<Jvm> jvmList = new ArrayList<>();
        jvmList.add(jvm);
        when(jvmPersistenceService.findJvms(testJvmName)).thenReturn(jvmList);
        String result = impl.getResourceTemplate(testJvmName, resourceTemplateName, true);
        assertEquals(expectedValue, result);
    }

    @Test
    public void testUpdateResourceTemplate() {
        String testJvmName = "testJvmName";
        String resourceTemplateName = "test-resource.tpl";
        String template = "<template>update</template>";
        when(jvmPersistenceService.updateResourceTemplate(testJvmName, resourceTemplateName, template)).thenReturn(template);
        String result = impl.updateResourceTemplate(testJvmName, resourceTemplateName, template);
        assertEquals(template, result);
    }

    @Test
    public void testGenerateInvokeBat() {
        final Jvm jvm = mock(Jvm.class);
        final List<Jvm> jvms = new ArrayList<>();
        jvms.add(jvm);
        when(jvmPersistenceService.findJvms(anyString())).thenReturn(jvms);
        when(jvmPersistenceService.getJvms()).thenReturn(jvms);
        when(fileManager.getResourceTypeTemplate(anyString())).thenReturn("template contents");
        final String result = impl.generateInvokeBat(anyString());
        assertEquals("template contents", result);
    }

}
