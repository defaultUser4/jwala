package com.cerner.jwala.service.jvm.impl;

import com.cerner.jwala.common.FileUtility;
import com.cerner.jwala.common.domain.model.app.Application;
import com.cerner.jwala.common.domain.model.fault.FaultType;
import com.cerner.jwala.common.domain.model.group.Group;
import com.cerner.jwala.common.domain.model.id.Identifier;
import com.cerner.jwala.common.domain.model.jvm.Jvm;
import com.cerner.jwala.common.domain.model.jvm.JvmControlOperation;
import com.cerner.jwala.common.domain.model.jvm.JvmState;
import com.cerner.jwala.common.domain.model.resource.ResourceGroup;
import com.cerner.jwala.common.domain.model.resource.ResourceIdentifier;
import com.cerner.jwala.common.domain.model.resource.ResourceTemplateMetaData;
import com.cerner.jwala.common.domain.model.state.CurrentState;
import com.cerner.jwala.common.domain.model.state.StateType;
import com.cerner.jwala.common.domain.model.user.User;
import com.cerner.jwala.common.exception.ApplicationException;
import com.cerner.jwala.common.exception.InternalErrorException;
import com.cerner.jwala.common.exec.CommandOutput;
import com.cerner.jwala.common.exec.CommandOutputReturnCode;
import com.cerner.jwala.common.exec.ExecReturnCode;
import com.cerner.jwala.common.properties.ApplicationProperties;
import com.cerner.jwala.common.properties.PropertyKeys;
import com.cerner.jwala.common.request.group.AddJvmToGroupRequest;
import com.cerner.jwala.common.request.jvm.ControlJvmRequest;
import com.cerner.jwala.common.request.jvm.CreateJvmAndAddToGroupsRequest;
import com.cerner.jwala.common.request.jvm.CreateJvmRequest;
import com.cerner.jwala.common.request.jvm.UpdateJvmRequest;
import com.cerner.jwala.control.AemControl;
import com.cerner.jwala.exception.CommandFailureException;
import com.cerner.jwala.persistence.jpa.domain.resource.config.template.JpaJvmConfigTemplate;
import com.cerner.jwala.persistence.jpa.service.exception.NonRetrievableResourceTemplateContentException;
import com.cerner.jwala.persistence.jpa.service.exception.ResourceTemplateUpdateException;
import com.cerner.jwala.persistence.jpa.type.EventType;
import com.cerner.jwala.persistence.service.JvmPersistenceService;
import com.cerner.jwala.service.HistoryFacadeService;
import com.cerner.jwala.service.app.ApplicationService;
import com.cerner.jwala.service.binarydistribution.BinaryDistributionLockManager;
import com.cerner.jwala.service.binarydistribution.BinaryDistributionService;
import com.cerner.jwala.service.group.GroupService;
import com.cerner.jwala.service.group.GroupStateNotificationService;
import com.cerner.jwala.service.jvm.JvmControlService;
import com.cerner.jwala.service.jvm.JvmService;
import com.cerner.jwala.service.jvm.exception.JvmServiceException;
import com.cerner.jwala.service.resource.ResourceService;
import com.cerner.jwala.service.resource.impl.ResourceGeneratorType;
import com.cerner.jwala.service.webserver.component.ClientFactoryHelper;
import groovy.text.SimpleTemplateEngine;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.mime.MediaType;
import org.codehaus.groovy.control.CompilationFailedException;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.*;
import java.util.*;

public class JvmServiceImpl implements JvmService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JvmServiceImpl.class);
    private static final String REMOTE_COMMANDS_USER_SCRIPTS = ApplicationProperties.get("remote.commands.user-scripts");
    private static final String MEDIA_TYPE_TEXT = "text";

    private final BinaryDistributionLockManager binaryDistributionLockManager;
    private String topicServerStates;
    private final JvmPersistenceService jvmPersistenceService;
    private final GroupService groupService;
    private final ApplicationService applicationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final GroupStateNotificationService groupStateNotificationService;
    private final ResourceService resourceService;
    private final ClientFactoryHelper clientFactoryHelper;
    private final JvmControlService jvmControlService;
    private final HistoryFacadeService historyFacadeService;
    private final BinaryDistributionService binaryDistributionService;
    private final FileUtility fileUtility;
    private static final String JWALA_SCRIPTS_PATH = ApplicationProperties.get("remote.commands.user-scripts");

    private static final String DIAGNOSIS_INITIATED = "Diagnosis Initiated on JVM ${jvm.jvmName}, host ${jvm.hostName}";

    public JvmServiceImpl(final JvmPersistenceService jvmPersistenceService,
                          final GroupService groupService,
                          final ApplicationService applicationService,
                          final SimpMessagingTemplate messagingTemplate,
                          final GroupStateNotificationService groupStateNotificationService,
                          final ResourceService resourceService,
                          final ClientFactoryHelper clientFactoryHelper,
                          final String topicServerStates,
                          final JvmControlService jvmControlService,
                          final BinaryDistributionService binaryDistributionService,
                          final BinaryDistributionLockManager binaryDistributionLockManager,
                          final HistoryFacadeService historyFacadeService,
                          final FileUtility fileUtility) {
        this.jvmPersistenceService = jvmPersistenceService;
        this.groupService = groupService;
        this.applicationService = applicationService;
        this.messagingTemplate = messagingTemplate;
        this.groupStateNotificationService = groupStateNotificationService;
        this.resourceService = resourceService;
        this.clientFactoryHelper = clientFactoryHelper;
        this.jvmControlService = jvmControlService;
        this.topicServerStates = topicServerStates;
        this.binaryDistributionService = binaryDistributionService;
        this.binaryDistributionLockManager = binaryDistributionLockManager;
        this.historyFacadeService = historyFacadeService;
        this.fileUtility = fileUtility;
    }


    protected Jvm createJvm(final CreateJvmRequest aCreateJvmRequest) {
        return jvmPersistenceService.createJvm(aCreateJvmRequest);
    }

    protected Jvm createAndAssignJvm(final CreateJvmAndAddToGroupsRequest aCreateAndAssignRequest,
                                     final User aCreatingUser) {
        aCreateAndAssignRequest.validate();

        // The commands are validated in createJvm() and groupService.addJvmToGroup()
        final Jvm newJvm = createJvm(aCreateAndAssignRequest.getCreateCommand());

        if (!aCreateAndAssignRequest.getGroups().isEmpty()) {
            final Set<AddJvmToGroupRequest> addJvmToGroupRequests = aCreateAndAssignRequest.toAddRequestsFor(newJvm.getId());
            addJvmToGroups(addJvmToGroupRequests, aCreatingUser);
        }

        return getJvm(newJvm.getId());
    }

    @Override
    @Transactional
    public Jvm createJvm(CreateJvmAndAddToGroupsRequest createJvmAndAddToGroupsRequest, User user) {
        // create the JVM in the database
        final Jvm jvm = createAndAssignJvm(createJvmAndAddToGroupsRequest, user);

        // inherit the templates from the group
        if (null != jvm.getGroups() && jvm.getGroups().size() > 0) {
            final Group parentGroup = jvm.getGroups().iterator().next();
            createDefaultTemplates(jvm.getJvmName(), parentGroup);
            if (jvm.getGroups().size() > 1) {
                LOGGER.warn("Multiple groups were associated with the JVM, but the JVM was created using the templates from group " + parentGroup.getName());
            }
        }

        return jvm;
    }

    @Override
    @Transactional
    public void createDefaultTemplates(final String jvmName, Group parentGroup) {
        final String groupName = parentGroup.getName();
        // get the group JVM templates
        List<String> templateNames = groupService.getGroupJvmsResourceTemplateNames(groupName);
        for (final String templateName : templateNames) {
            String templateContent = groupService.getGroupJvmResourceTemplate(groupName, templateName, resourceService.generateResourceGroup(), false);
            String metaDataStr = groupService.getGroupJvmResourceTemplateMetaData(groupName, templateName);
            try {
                ResourceTemplateMetaData metaData = resourceService.getTokenizedMetaData(templateName, jvmPersistenceService.findJvmByExactName(jvmName), metaDataStr);
                final ResourceIdentifier resourceIdentifier = new ResourceIdentifier.Builder()
                        .setResourceName(metaData.getDeployFileName())
                        .setJvmName(jvmName)
                        .setGroupName(groupName)
                        .build();
                resourceService.createResource(resourceIdentifier, metaData, IOUtils.toInputStream(templateContent));

            } catch (IOException e) {
                LOGGER.error("Failed to map meta data for JVM {} in group {}", jvmName, groupName, e);
                throw new InternalErrorException(FaultType.BAD_STREAM, "Failed to map meta data for JVM " + jvmName + " in group " + groupName, e);
            }
        }

        // get the group App templates
        templateNames = groupService.getGroupAppsResourceTemplateNames(groupName);
        for (String templateName : templateNames) {
            String metaDataStr = groupService.getGroupAppResourceTemplateMetaData(groupName, templateName);
            try {
                ResourceTemplateMetaData metaData = resourceService.getMetaData(metaDataStr);
                if (metaData.getEntity().getDeployToJvms()) {
                    final String template = resourceService.getAppTemplate(groupName, metaData.getEntity().getTarget(), templateName);
                    final ResourceIdentifier resourceIdentifier = new ResourceIdentifier.Builder()
                            .setResourceName(metaData.getTemplateName()).setJvmName(jvmName)
                            .setWebAppName(metaData.getEntity().getTarget()).build();
                    resourceService.createResource(resourceIdentifier, metaData, new ByteArrayInputStream(template.getBytes()));
                }
            } catch (IOException e) {
                LOGGER.error("Failed to map meta data while creating JVM for template {} in group {}", templateName, groupName, e);
                throw new InternalErrorException(FaultType.BAD_STREAM, "Failed to map data for template " + templateName + " in group " + groupName, e);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Jvm getJvm(final Identifier<Jvm> aJvmId) {
        return jvmPersistenceService.getJvm(aJvmId);
    }

    @Override
    @Transactional(readOnly = true)
    public Jvm getJvm(final String jvmName) {
        return jvmPersistenceService.findJvmByExactName(jvmName);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Jvm> getJvms() {

        return jvmPersistenceService.getJvms();
    }

    @Override
    @Transactional
    public Jvm updateJvm(final UpdateJvmRequest updateJvmRequest,
                         final User anUpdatingUser) {

        updateJvmRequest.validate();

        jvmPersistenceService.removeJvmFromGroups(updateJvmRequest.getId());

        addJvmToGroups(updateJvmRequest.getAssignmentCommands(), anUpdatingUser);

        return jvmPersistenceService.updateJvm(updateJvmRequest);
    }

    @Override
    @Transactional
    public void removeJvm(final Identifier<Jvm> aJvmId, User user) {
        final Jvm jvm = getJvm(aJvmId);
        if (!jvm.getState().isStartedState()) {
            LOGGER.info("Removing JVM from the database and deleting the service for id {}", aJvmId.getId());
            if (!jvm.getState().equals(JvmState.JVM_NEW)) {
                deleteJvmWindowsService(new ControlJvmRequest(aJvmId, JvmControlOperation.DELETE_SERVICE), jvm, user);
            }
            jvmPersistenceService.removeJvm(aJvmId);
        } else {
            LOGGER.error("The target JVM {} must be stopped before attempting to delete it", jvm.getJvmName());
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE,
                    "The target JVM must be stopped before attempting to delete it");
        }

    }

    @Override
    public void deleteJvmWindowsService(ControlJvmRequest controlJvmRequest, Jvm jvm, User user) {
        if (!jvm.getState().equals(JvmState.JVM_NEW)) {
            CommandOutput commandOutput = jvmControlService.controlJvm(controlJvmRequest, user);
            final String jvmName = jvm.getJvmName();
            if (commandOutput.getReturnCode().wasSuccessful()) {
                LOGGER.info("Delete of windows service {} was successful", jvmName);
            } else if (ExecReturnCode.JWALA_EXIT_CODE_SERVICE_DOES_NOT_EXIST == commandOutput.getReturnCode().getReturnCode()) {
                LOGGER.info("No such service found for {} during delete. Continuing with request.", jvmName);
            } else {
                String standardError =
                        commandOutput.getStandardError().isEmpty() ?
                                commandOutput.getStandardOutput() : commandOutput.getStandardError();
                LOGGER.error("Deleting windows service {} failed :: ERROR: {}", jvmName, standardError);
                throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, standardError.isEmpty() ? CommandOutputReturnCode.fromReturnCode(commandOutput.getReturnCode().getReturnCode()).getDesc() : standardError);
            }
        }
    }


    protected void addJvmToGroups(final Set<AddJvmToGroupRequest> someAddCommands,
                                  final User anAddingUser) {
        for (final AddJvmToGroupRequest command : someAddCommands) {
            LOGGER.info("Adding jvm {} to group {}", command.getJvmId(), command.getGroupId());
            groupService.addJvmToGroup(command, anAddingUser);
        }
    }

    @Override
    public Jvm generateAndDeployJvm(String jvmName, User user) {
        boolean didSucceed = true;
        Jvm jvm = getJvm(jvmName);
        // only one at a time per JVM
        //TODO return error if .jwala directory is already being written to
        LOGGER.debug("Start generateAndDeployJvm for {} by user {}", jvmName, user.getId());

        historyFacadeService.write(jvm.getHostName(), jvm.getGroups(), "Starting to generate remote JVM " + jvm.getJvmName(), EventType.USER_ACTION_INFO, user.getId());

        //add write lock for multiple write
        binaryDistributionLockManager.writeLock(jvmName + "-" + jvm.getId().toString());

        try {
            if (jvm.getState().isStartedState()) {
                final String errorMessage = "The target JVM " + jvm.getJvmName() + " must be stopped before attempting to update the resource files";
                LOGGER.error(errorMessage);
                throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, errorMessage);
            }

            validateJvmAndAppResources(jvm);

            distributeBinaries(jvm.getHostName());

            // create the scripts directory if it doesn't exist
            createScriptsDirectory(jvm);

            // copy the install and deploy scripts
            deployScriptsToUserJwalaScriptsDir(jvm, user);

            // delete the service, needs service.bat
            // TODO make generic to support multiple OSs
            deleteJvmWindowsService(new ControlJvmRequest(jvm.getId(), JvmControlOperation.DELETE_SERVICE), jvm, user);

            // create the tar file
            //
            final String jvmConfigJar = generateJvmConfigJar(jvm);

            // copy the tar file
            secureCopyJvmConfigJar(jvm, jvmConfigJar, user);

            // call script to backup and tar the current directory and
            // then untar the new tar, needs jar
            deployJvmConfigJar(jvm, user, jvmConfigJar);

            // copy the individual jvm templates to the destination
            deployJvmResourceFiles(jvm, user);

            // deploy any application context xml's in the group
            deployApplicationContextXMLs(jvm, user);

            // re-install the service
            installJvmWindowsService(jvm, user);

            // set the state to stopped
            updateState(jvm.getId(), JvmState.JVM_STOPPED);

        } catch (CommandFailureException | IOException e) {
            didSucceed = false;
            LOGGER.error("Failed to generate the JVM config for {} :: ERROR: {}", jvm.getJvmName(), e);
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, "Failed to generate the JVM config: " + jvm.getJvmName(), e);
        } finally {
            binaryDistributionLockManager.writeUnlock(jvmName + "-" + jvm.getId().toString());
            LOGGER.debug("End generateAndDeployJvm for {} by user {}", jvmName, user.getId());

            String historyMessage = didSucceed ? "Remote generation of jvm " + jvm.getJvmName() + " succeeded" :
                    "Remote generation of jvm " + jvm.getJvmName() + " failed";

            historyFacadeService.write(jvm.getHostName(), jvm.getGroups(), historyMessage, EventType.USER_ACTION_INFO, user.getId());
        }
        return jvm;
    }

    private void validateJvmAndAppResources(Jvm jvm) {
        String jvmName = jvm.getJvmName();
        Map<String, List<String>> jvmAndAppResourcesExceptions = new HashMap<>();

        // validate the JVM resources
        try {
            final ResourceIdentifier resourceIdentifier = new ResourceIdentifier.Builder()
                    .setJvmName(jvmName)
                    .setResourceName("*")
                    .build();
            resourceService.validateAllResourcesForGeneration(resourceIdentifier);
        } catch (InternalErrorException iee) {
            LOGGER.info("Catching known JVM resource generation exception, and now validating application resources");
            LOGGER.debug("This JVM resource generation exception should have already been logged previously", iee);
            jvmAndAppResourcesExceptions.putAll(iee.getErrorDetails());
        }

        // now validate and app resources for the JVM
        List<Group> groupList = jvmPersistenceService.findGroupsByJvm(jvm.getId());
        for (Group group : groupList) {
            for (Application app : applicationService.findApplications(group.getId())) {
                final String appName = app.getName();
                for (String templateName : applicationService.getResourceTemplateNames(appName, jvmName)) {
                    try {
                        final ResourceIdentifier resourceIdentifier = new ResourceIdentifier.Builder()
                                .setResourceName(templateName)
                                .setGroupName(group.getName())
                                .setJvmName(jvmName)
                                .setWebAppName(appName)
                                .build();
                        resourceService.validateSingleResourceForGeneration(resourceIdentifier);
                    } catch (InternalErrorException iee) {
                        LOGGER.info("Catching known app resource generation exception, and now consolidating with the JVM resource exceptions");
                        LOGGER.debug("This application resource generation exception should have already been logged previously", iee);
                        jvmAndAppResourcesExceptions.putAll(iee.getErrorDetails());
                    }
                }
            }
        }

        if (!jvmAndAppResourcesExceptions.isEmpty()) {
            throw new InternalErrorException(FaultType.RESOURCE_GENERATION_FAILED, "Failed to generate the resources for JVM " + jvmName, null, jvmAndAppResourcesExceptions);
        }
    }

    private void distributeBinaries(String hostName) {
        try {
            binaryDistributionLockManager.writeLock(hostName);
            binaryDistributionService.distributeUnzip(hostName);
            binaryDistributionService.distributeJdk(hostName);
        } finally {
            binaryDistributionLockManager.writeUnlock(hostName);
        }
    }

    protected void createScriptsDirectory(Jvm jvm) throws CommandFailureException {
        final String scriptsDir = REMOTE_COMMANDS_USER_SCRIPTS;
        final CommandOutput commandOutput = jvmControlService.executeCreateDirectoryCommand(jvm, scriptsDir);
        ExecReturnCode resultReturnCode = commandOutput.getReturnCode();
        if (!resultReturnCode.wasSuccessful()) {
            LOGGER.error("Creating scripts directory {} FAILED ", scriptsDir);
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, commandOutput.getStandardError().isEmpty() ? CommandOutputReturnCode.fromReturnCode(resultReturnCode.getReturnCode()).getDesc() : commandOutput.getStandardError());
        }

    }

    protected void deployScriptsToUserJwalaScriptsDir(Jvm jvm, User user) throws CommandFailureException, IOException {
        final ControlJvmRequest secureCopyRequest = new ControlJvmRequest(jvm.getId(), JvmControlOperation.SCP);
        final String commandsScriptsPath = ApplicationProperties.get("commands.scripts-path");

        final String deployConfigJarPath = commandsScriptsPath + "/" + AemControl.Properties.DEPLOY_CONFIG_ARCHIVE_SCRIPT_NAME.getValue();
        final String jvmName = jvm.getJvmName();
        final String userId = user.getId();

        final String stagingArea = JWALA_SCRIPTS_PATH + "/" + jvmName;

        createParentDir(jvm, stagingArea);
        final String failedToCopyMessage = "Failed to secure copy ";
        final String duringCreationMessage = " during the creation of ";
        final String destinationDeployJarPath = stagingArea + "/" + AemControl.Properties.DEPLOY_CONFIG_ARCHIVE_SCRIPT_NAME.getValue();
        if (!jvmControlService.secureCopyFile(secureCopyRequest, deployConfigJarPath, destinationDeployJarPath, userId).getReturnCode().wasSuccessful()) {
            String message = failedToCopyMessage + deployConfigJarPath + duringCreationMessage + jvmName;
            LOGGER.error(message);
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, message);
        }

        final String installServicePath = commandsScriptsPath + "/" + AemControl.Properties.INSTALL_SERVICE_SCRIPT_NAME.getValue();
        final String destinationInstallServicePath = stagingArea + "/" + AemControl.Properties.INSTALL_SERVICE_SCRIPT_NAME.getValue();
        if (!jvmControlService.secureCopyFile(secureCopyRequest, installServicePath, destinationInstallServicePath, userId).getReturnCode().wasSuccessful()) {
            String message = failedToCopyMessage + installServicePath + duringCreationMessage + jvmName;
            LOGGER.error(message);
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, message);
        }

        // make sure the scripts are executable
        if (!jvmControlService.executeChangeFileModeCommand(jvm, "a+x", stagingArea, "*.sh").getReturnCode().wasSuccessful()) {
            String message = "Failed to change the file permissions in " + stagingArea + duringCreationMessage + jvmName;
            LOGGER.error(message);
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, message);
        }
    }

    protected String generateJvmConfigJar(Jvm jvm) throws CommandFailureException {
        ManagedJvmBuilder managedJvmBuilder =
                new ManagedJvmBuilder().
                        jvm(jvmPersistenceService.findJvmByExactName(jvm.getJvmName())).
                        fileUtility(fileUtility).
                        resourceService(resourceService).
                        build();

        return managedJvmBuilder.getStagingDir().getAbsolutePath();
    }

    protected void createParentDir(final Jvm jvm, final String parentDir) throws CommandFailureException {
        final CommandOutput commandOutput = jvmControlService.executeCreateDirectoryCommand(jvm, parentDir);
        if (commandOutput.getReturnCode().wasSuccessful()) {
            LOGGER.info("created {} directory successfully", parentDir);
        } else {
            final String standardError = commandOutput.getStandardError().isEmpty() ? commandOutput.getStandardOutput() : commandOutput.getStandardError();
            LOGGER.error("create command failed with error trying to create parent directory {} on {} :: ERROR: {}", parentDir, jvm.getHostName(), standardError);
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, standardError.isEmpty() ? CommandOutputReturnCode.fromReturnCode(commandOutput.getReturnCode().getReturnCode()).getDesc() : standardError);
        }
    }

    protected void secureCopyJvmConfigJar(Jvm jvm, String jvmConfigTar, User user) throws CommandFailureException {
        String configTarName = jvm.getJvmName() + ".jar";
        secureCopyFileToJvm(jvm, ApplicationProperties.get("paths.generated.resource.dir") + "/" + jvm.getJvmName() + "/" + configTarName, REMOTE_COMMANDS_USER_SCRIPTS + "/" + configTarName, user);
        LOGGER.info("Copy of config tar successful: {}", jvmConfigTar);
    }

    protected void deployJvmConfigJar(Jvm jvm, User user, String jvmJar) throws CommandFailureException {
        final String parentDir = ApplicationProperties.get("remote.paths.instances");
        CommandOutput execData = jvmControlService.executeCreateDirectoryCommand(jvm, parentDir);
        if (execData.getReturnCode().wasSuccessful()) {
            LOGGER.info("Successfully created the parent directory {}", parentDir);
        } else {
            String standardError = execData.getStandardError().isEmpty() ? execData.getStandardOutput() : execData.getStandardError();
            LOGGER.error("Deploy command completed with error trying to extract and back up JVM config {} :: ERROR: {}", jvm.getJvmName(), standardError);
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, standardError.isEmpty() ? CommandOutputReturnCode.fromReturnCode(execData.getReturnCode().getReturnCode()).getDesc() : standardError);
        }
        execData = jvmControlService.controlJvm(
                new ControlJvmRequest(jvm.getId(), JvmControlOperation.DEPLOY_CONFIG_ARCHIVE), user);
        if (execData.getReturnCode().wasSuccessful()) {
            LOGGER.info("Deployment of config tar was successful: {}", jvmJar);
        } else {
            String standardError =
                    execData.getStandardError().isEmpty() ? execData.getStandardOutput() : execData.getStandardError();
            LOGGER.error(
                    "Deploy command completed with error trying to extract and back up JVM config {} :: ERROR: {}",
                    jvm.getJvmName(), standardError);
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, standardError.isEmpty() ? CommandOutputReturnCode.fromReturnCode(execData.getReturnCode().getReturnCode()).getDesc() : standardError);
        }

        // make sure the start/stop scripts are executable
        String instancesDir = ApplicationProperties.getRequired(PropertyKeys.REMOTE_PATH_INSTANCES_DIR);
        String tomcatDirName = ApplicationProperties.getRequired(PropertyKeys.REMOTE_TOMCAT_DIR_NAME);

        final String targetAbsoluteDir = instancesDir + "/" + jvm.getJvmName() + "/" + tomcatDirName + "/bin";

        if (!jvmControlService.executeChangeFileModeCommand(jvm, "a+x", targetAbsoluteDir, "*.sh").getReturnCode().wasSuccessful()) {
            String message = "Failed to change the file permissions in " + targetAbsoluteDir + " for jvm " + jvm.getJvmName();
            LOGGER.error(message);
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, message);
        }

    }

    protected void deployJvmResourceFiles(Jvm jvm, User user) throws IOException, CommandFailureException {
        final Map<String, String> generatedFiles = generateResourceFiles(jvm.getJvmName());
        if (generatedFiles != null) {
            for (Map.Entry<String, String> entry : generatedFiles.entrySet()) {
                secureCopyFileToJvm(jvm, entry.getKey(), entry.getValue(), user);
            }
        }
    }

    protected void installJvmWindowsService(Jvm jvm, User user) {
        CommandOutput execData = jvmControlService.controlJvm(new ControlJvmRequest(jvm.getId(), JvmControlOperation.INSTALL_SERVICE),
                user);
        if (execData.getReturnCode().wasSuccessful()) {
            LOGGER.info("Install of windows service {} was successful", jvm.getJvmName());
        } else {
            updateState(jvm.getId(), JvmState.JVM_FAILED);
            String standardError =
                    execData.getStandardError().isEmpty() ? execData.getStandardOutput() : execData.getStandardError();
            LOGGER.error("Installing windows service {} failed :: ERROR: {}", jvm.getJvmName(), standardError);
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, "Installing windows service failed for " + AemControl.Properties.INSTALL_SERVICE_SCRIPT_NAME  +".  Please refer to the history window.");
        }
    }

    /**
     * This method copies a given file to the destination location on the remote machine.
     *
     * @param jvm             Jvm which requires the file
     * @param sourceFile      The source file, which needs to be copied.
     * @param destinationFile The destination file, where the source file should be copied.
     * @param user
     * @throws CommandFailureException If the command fails, this exception contains the details of the failure.
     */
    protected void secureCopyFileToJvm(final Jvm jvm, final String sourceFile, final String destinationFile, User user) throws CommandFailureException {
        final String parentDir;
        if (destinationFile.startsWith("~")) {
            parentDir = destinationFile.substring(0, destinationFile.lastIndexOf("/"));
        } else {
            parentDir = new File(destinationFile).getParentFile().getAbsolutePath().replaceAll("\\\\", "/");
        }
        createParentDir(jvm, parentDir);
        final ControlJvmRequest controlJvmRequest = new ControlJvmRequest(jvm.getId(), JvmControlOperation.SCP);
        final CommandOutput commandOutput = jvmControlService.secureCopyFile(controlJvmRequest, sourceFile, destinationFile, user.getId());
        if (commandOutput.getReturnCode().wasSuccessful()) {
            LOGGER.info("Successfully copied {} to destination location {} on {}", sourceFile, destinationFile, jvm.getHostName());
        } else {
            final String standardError = commandOutput.getStandardError().isEmpty() ? commandOutput.getStandardOutput() : commandOutput.getStandardError();
            LOGGER.error("Copy command failed with error trying to copy file {} to {} :: ERROR: {}", sourceFile, jvm.getHostName(), standardError);
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, standardError.isEmpty() ? CommandOutputReturnCode.fromReturnCode(commandOutput.getReturnCode().getReturnCode()).getDesc() : standardError);
        }
    }

    @Override
    public Jvm generateAndDeployFile(String jvmName, String fileName, User user) {
        Jvm jvm = getJvm(jvmName);
        // only one at a time per jvm
        binaryDistributionLockManager.writeLock(jvmName + "-" + jvm.getId().getId().toString());
        try {
            if (jvm.getState().isStartedState()) {
                LOGGER.error("The target JVM {} must be stopped before attempting to update the resource files", jvm.getJvmName());
                throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE,
                        "The target JVM must be stopped before attempting to update the resource files");
            }
            ResourceIdentifier resourceIdentifier = new ResourceIdentifier.Builder()
                    .setResourceName(fileName)
                    .setJvmName(jvmName)
                    .build();
            resourceService.validateSingleResourceForGeneration(resourceIdentifier);
            resourceService.generateAndDeployFile(resourceIdentifier, jvm.getJvmName(), fileName, jvm.getHostName());
        } finally {
            binaryDistributionLockManager.writeUnlock(jvmName + "-" + jvm.getId().getId().toString());
            LOGGER.debug("End generateAndDeployFile for {} by user {}", jvmName, user.getId());
        }
        return jvm;
    }

    @Override
    @Transactional
    public String performDiagnosis(Identifier<Jvm> aJvmId, final User user) {
        // if the Jvm does not exist, we'll get a 404 NotFoundException
        Jvm jvm = jvmPersistenceService.getJvm(aJvmId);

        pingAndUpdateJvmState(jvm);

        final String message = "Diagnose and resolve state";
        historyFacadeService.write(jvm.getJvmName(), new ArrayList<>(jvm.getGroups()), message, EventType.USER_ACTION_INFO, user.getId());
        SimpleTemplateEngine engine = new SimpleTemplateEngine();
        Map<String, Object> binding = new HashMap<>();
        binding.put("jvm", jvm);

        try {
            return engine.createTemplate(DIAGNOSIS_INITIATED).make(binding).toString();
        } catch (CompilationFailedException | ClassNotFoundException | IOException e) {
            throw new ApplicationException(DIAGNOSIS_INITIATED, e);
            // why do this? Because if there was a problem with the template that made
            // it past initial testing, then it is probably due to the jvm in the binding
            // so just dump out the diagnosis template and the exception so it can be
            // debugged.
        }
    }

    @Override
    public void checkForSetenvBat(String jvmName) {
        try {
            jvmPersistenceService.getResourceTemplate(jvmName, "setenv.bat");
        } catch (NonRetrievableResourceTemplateContentException e) {
            LOGGER.error("No setenv.bat configured for JVM: {}", jvmName, e);
            throw new InternalErrorException(FaultType.TEMPLATE_NOT_FOUND, "No setenv.bat template found for " + jvmName + ". Unable to continue processing.");
        }
        LOGGER.debug("Found setenv.bat for JVM: {}. Continuing with process. ", jvmName);
    }

    /**
     * Sets the web server state if the web server is not starting or stopping.
     *
     * @param jvm   the jvm
     * @param state {@link JvmState}
     * @param msg   a message
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private void setState(final Jvm jvm,
                          final JvmState state,
                          final String msg) {
        jvmPersistenceService.updateState(jvm.getId(), state, msg);
        messagingTemplate.convertAndSend(topicServerStates, new CurrentState<>(jvm.getId(), state, DateTime.now(), StateType.JVM));
        groupStateNotificationService.retrieveStateAndSend(jvm.getId(), Jvm.class);
    }

    @Override
    @Transactional
    public String previewResourceTemplate(String fileName, String jvmName, String groupName, String template) {
        return resourceService.generateResourceFile(fileName, template, resourceService.generateResourceGroup(), jvmPersistenceService.findJvm(jvmName, groupName), ResourceGeneratorType.PREVIEW);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getResourceTemplateNames(final String jvmName) {
        return jvmPersistenceService.getResourceTemplateNames(jvmName);
    }

    @Override
    @Transactional
    public String getResourceTemplate(final String jvmName,
                                      final String resourceTemplateName,
                                      final boolean tokensReplaced) {
        final String template = jvmPersistenceService.getResourceTemplate(jvmName, resourceTemplateName);
        if (tokensReplaced) {
            return resourceService.generateResourceFile(resourceTemplateName, template, resourceService.generateResourceGroup(), jvmPersistenceService.findJvmByExactName(jvmName), ResourceGeneratorType.TEMPLATE);
        }
        return template;
    }

    @Override
    @Transactional
    public String updateResourceTemplate(final String jvmName, final String resourceTemplateName, final String template) {
        String retVal = null;
        try {
            retVal = jvmPersistenceService.updateResourceTemplate(jvmName, resourceTemplateName, template);
        } catch (ResourceTemplateUpdateException | NonRetrievableResourceTemplateContentException e) {
            LOGGER.error("Failed to update the template {}", resourceTemplateName, e);
        }
        return retVal;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateState(final Identifier<Jvm> id, final JvmState state) {
        jvmPersistenceService.updateState(id, state, "");
        messagingTemplate.convertAndSend(topicServerStates, new CurrentState<>(id, state, DateTime.now(), StateType.JVM));
    }

    @Override
    @Transactional
    public void pingAndUpdateJvmState(final Jvm jvm) {
        ClientHttpResponse response = null;
        try {
            response = clientFactoryHelper.requestGet(jvm.getStatusUri());
            LOGGER.info(">>> Response = {} from jvm {}", response.getStatusCode(), jvm.getId().getId());
            if (response.getStatusCode() == HttpStatus.OK) {
                setState(jvm, JvmState.JVM_STARTED, StringUtils.EMPTY);
            } else {
                setState(jvm, JvmState.JVM_STOPPED,
                        "Request for '" + jvm.getStatusUri() + "' failed with a response code of '" +
                                response.getStatusCode() + "'");
            }
        } catch (IOException ioe) {
            LOGGER.info(ioe.getMessage(), ioe);
            setState(jvm, JvmState.JVM_STOPPED, StringUtils.EMPTY);
        } catch (RuntimeException rte) {
            LOGGER.error(rte.getMessage(), rte);
        } finally {
            if (response != null) {
                response.close();
            }
        }
    }

    @Override
    @Transactional
    public void deployApplicationContextXMLs(Jvm jvm, User user) {
        List<Group> groupList = jvmPersistenceService.findGroupsByJvm(jvm.getId());
        for (Group group : groupList) {
            for (Application app : applicationService.findApplications(group.getId())) {
                for (String templateName : applicationService.getResourceTemplateNames(app.getName(), jvm.getJvmName())) {
                    LOGGER.info("Deploying application xml {} for JVM {} in group {}", templateName, jvm.getJvmName(), group.getName());
                    applicationService.deployConf(app.getName(), group.getName(), jvm.getJvmName(), templateName, resourceService.generateResourceGroup(), user);
                }
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Long getJvmStartedCount(final String groupName) {
        return jvmPersistenceService.getJvmStartedCount(groupName);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getJvmCount(final String groupName) {
        return jvmPersistenceService.getJvmCount(groupName);
    }

    @Override
    public Long getJvmStoppedCount(final String groupName) {
        return jvmPersistenceService.getJvmStoppedCount(groupName);
    }

    @Override
    public Long getJvmForciblyStoppedCount(final String groupName) {
        return jvmPersistenceService.getJvmForciblyStoppedCount(groupName);
    }

    @Override
    public Map<String, String> generateResourceFiles(final String jvmName) throws IOException {
        Map<String, String> generatedFiles = null;
        final List<JpaJvmConfigTemplate> jpaJvmConfigTemplateList = jvmPersistenceService.getConfigTemplates(jvmName);
        for (final JpaJvmConfigTemplate jpaJvmConfigTemplate : jpaJvmConfigTemplateList) {
            final ResourceGroup resourceGroup = resourceService.generateResourceGroup();
            final Jvm jvm = jvmPersistenceService.findJvmByExactName(jvmName);
            String resourceTemplateMetaDataString = "";
            resourceTemplateMetaDataString = resourceService.generateResourceFile(jpaJvmConfigTemplate.getTemplateName(), jpaJvmConfigTemplate.getMetaData(), resourceGroup, jvm, ResourceGeneratorType.METADATA);
            final ResourceTemplateMetaData resourceTemplateMetaData = resourceService.getMetaData(resourceTemplateMetaDataString);
            final String deployFileName = resourceTemplateMetaData.getDeployFileName();
            if (generatedFiles == null) {
                generatedFiles = new HashMap<>();
            }

            if (resourceTemplateMetaData.getContentType().getType().equalsIgnoreCase(MEDIA_TYPE_TEXT) ||
                    MediaType.APPLICATION_XML.equals(resourceTemplateMetaData.getContentType())) {
                final String generatedResourceStr = resourceService.generateResourceFile(jpaJvmConfigTemplate.getTemplateName(), jpaJvmConfigTemplate.getTemplateContent(),
                        resourceGroup, jvm, ResourceGeneratorType.TEMPLATE);
                generatedFiles.put(createConfigFile(ApplicationProperties.get("paths.generated.resource.dir") + "/" + jvmName, deployFileName, generatedResourceStr),
                        resourceTemplateMetaData.getDeployPath() + "/" + deployFileName);
            } else {
                if (generatedFiles == null) {
                    generatedFiles = new HashMap<>();
                }
                generatedFiles.put(jpaJvmConfigTemplate.getTemplateContent(),
                        resourceTemplateMetaData.getDeployPath() + "/" + deployFileName);
            }
        }
        return generatedFiles;
    }

    /**
     * This method creates a temp file .tpl file, with the generatedResourceString as the input data for the file.
     *
     * @param generatedResourcesTempDir
     * @param configFileName            The file name that apprears at the destination.
     * @param generatedResourceString   The contents of the file.
     * @return the location of the newly created temp file
     * @throws IOException
     */
    protected String createConfigFile(String generatedResourcesTempDir, final String configFileName, String generatedResourceString) throws IOException {
        File templateFile = new File(generatedResourcesTempDir + "/" + configFileName);
        if (configFileName.endsWith(".bat")) {
            generatedResourceString = generatedResourceString.replaceAll("\n", "\r\n");
        }
        FileUtils.writeStringToFile(templateFile, generatedResourceString);
        return templateFile.getAbsolutePath();
    }

    @Override
    @Transactional
    public void deleteJvm(final String name, final String userName) {
        final Jvm jvm = getJvm(name);
        if (!jvm.getState().isStartedState()) {
            LOGGER.info("Removing JVM from the database and deleting the service for jvm {}", name);
            if (!jvm.getState().equals(JvmState.JVM_NEW)) {
                deleteJvmWindowsService(new ControlJvmRequest(jvm.getId(), JvmControlOperation.DELETE_SERVICE), jvm,
                        new User(userName));
            }
            jvmPersistenceService.removeJvm(jvm.getId());
        } else {
            LOGGER.error("The target JVM {} must be stopped before attempting to delete it", jvm.getJvmName());
            throw new JvmServiceException("The target JVM must be stopped before attempting to delete it");
        }
    }
}
