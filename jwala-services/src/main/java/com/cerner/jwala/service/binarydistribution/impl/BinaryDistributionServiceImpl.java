package com.cerner.jwala.service.binarydistribution.impl;

import com.cerner.jwala.common.domain.model.fault.FaultType;
import com.cerner.jwala.common.domain.model.resource.EntityType;
import com.cerner.jwala.common.exception.ApplicationException;
import com.cerner.jwala.common.exception.InternalErrorException;
import com.cerner.jwala.common.properties.ApplicationProperties;
import com.cerner.jwala.exception.CommandFailureException;
import com.cerner.jwala.service.binarydistribution.BinaryDistributionControlService;
import com.cerner.jwala.service.binarydistribution.BinaryDistributionLockManager;
import com.cerner.jwala.service.binarydistribution.BinaryDistributionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Created by Arvindo Kinny on 10/11/2016.
 */
public class BinaryDistributionServiceImpl implements BinaryDistributionService {
    private static final Logger LOGGER = LoggerFactory.getLogger(BinaryDistributionServiceImpl.class);

    private static final String BINARY_LOCATION_PROPERTY_KEY = "jwala.binary.dir";
    private static final String UNZIPEXE = "unzip.exe";
    private static final String APACHE_EXCLUDE = "ReadMe.txt *--";

    private final BinaryDistributionControlService binaryDistributionControlService;
    private final BinaryDistributionLockManager binaryDistributionLockManager;

    public BinaryDistributionServiceImpl(BinaryDistributionControlService binaryDistributionControlService, BinaryDistributionLockManager binaryDistributionLockManager) {
        this.binaryDistributionControlService = binaryDistributionControlService;
        this.binaryDistributionLockManager = binaryDistributionLockManager;
    }

    @Override
    public void distributeJdk(final String hostname) {
        LOGGER.info("Start deploy jdk for {}", hostname);
        String jdkDir = ApplicationProperties.get("remote.jwala.java.root.dir");
        String binaryDeployDir = ApplicationProperties.get("remote.jwala.home");
        if (!jdkDir.isEmpty()) {
            distributeBinary(hostname, jdkDir, binaryDeployDir, "");
        } else {
            LOGGER.warn("JDK dir location is null or empty {}", jdkDir);
        }
        LOGGER.info("End deploy jdk for {}", hostname);
    }

    @Override
    public void distributeTomcat(final String hostname) {
        LOGGER.info("Start deploy tomcat binaries for {}", hostname);
        String tomcatDir = ApplicationProperties.get("remote.paths.tomcat.root.dir");
        String binaryDeployDir = ApplicationProperties.get("remote.jwala.home");
        if (tomcatDir != null && !tomcatDir.isEmpty()) {
                distributeBinary(hostname, tomcatDir, binaryDeployDir, "");
        } else {
            LOGGER.warn("Tomcat dir location is null or empty {}", tomcatDir);
        }
        LOGGER.info("End deploy tomcat binaries for {}", hostname);
    }

    @Override
    public void distributeWebServer(final String hostname) {
        String wrietLockResourceName = hostname + "-" + EntityType.WEB_SERVER.toString();
        try {
            binaryDistributionLockManager.writeLock(wrietLockResourceName);
            String webServerDir = ApplicationProperties.get("remote.paths.apache.httpd.root.dir");
            String binaryDeployDir =  ApplicationProperties.get("remote.jwala.home");
            if (webServerDir != null && !webServerDir.isEmpty()) {
                distributeBinary(hostname, webServerDir, binaryDeployDir, APACHE_EXCLUDE);
            } else {
                LOGGER.warn("WebServer dir location is null or empty {}", webServerDir);
            }
        }finally {
            binaryDistributionLockManager.writeUnlock(wrietLockResourceName);
        }
    }

    private void distributeBinary(final String hostname, final String binaryName, final String binaryDeployDir, final String exclude) {
        String binaryDir = ApplicationProperties.get(BINARY_LOCATION_PROPERTY_KEY);
        if (binaryDeployDir != null && !binaryDeployDir.isEmpty()) {
            if (!remoteFileCheck(hostname, binaryDeployDir + "/" + binaryName)) {
                LOGGER.info("Couldn't find {} on host {}. Trying to deploy it", binaryName, hostname);
                if (binaryDir != null && !binaryDir.isEmpty()) {
                    String zipFile = binaryDir + "/" + binaryName + ".zip";
                    String destinationZipFile = binaryDeployDir + "/" + binaryName + ".zip";
                    remoteCreateDirectory(hostname, binaryDeployDir);
                    remoteSecureCopyFile(hostname, zipFile, destinationZipFile);
                    try {
                        remoteUnzipBinary(hostname, ApplicationProperties.get("remote.commands.user-scripts") + "/" + UNZIPEXE, destinationZipFile, binaryDeployDir, exclude);
                    } finally {
                        remoteDeleteBinary(hostname, destinationZipFile);
                    }
                } else {
                    LOGGER.warn("Cannot find the binary directory location in jwala, value is {}", binaryDir);
                }
            } else {
                LOGGER.info("Found {} at on host {}", binaryName, hostname);
            }
        } else {
            LOGGER.warn("Binary deploy location not provided value is {}", binaryDeployDir);
            throw new ApplicationException("Binary deploy location not provided value is "+ binaryDeployDir);
        }
    }

    public void changeFileMode(final String hostname, final String mode, final String targetDir, final String target) {
        try {
            if (binaryDistributionControlService.changeFileMode(hostname, mode, targetDir, target).getReturnCode().wasSuccessful()) {
                LOGGER.info("change file mode " + mode + " at targetDir " + targetDir);
            } else {
                String message = "Failed to change the file permissions in " + targetDir + "/" + UNZIPEXE;
                LOGGER.error(message);
                throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, message);
            }
        } catch (CommandFailureException e) {
            final String message = "Error in change file mode at host: " + hostname + " mode: " + mode + " target: " + target;
            LOGGER.error(message, e);
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, message, e);
        }
    }

    public void remoteDeleteBinary(final String hostname, final String destination) {
        try {
            if (binaryDistributionControlService.deleteBinary(hostname, destination).getReturnCode().wasSuccessful()) {
                LOGGER.info("successfully delete the binary {}", destination);
            } else {
                final String message = "error in deleting file " + destination;
                LOGGER.error(message);
                throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, message);
            }
        } catch (CommandFailureException e) {
            final String message = "Error in delete remote binary at host: " + hostname + " destination: " + destination;
            LOGGER.error(message, e);
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, message, e);
        }
    }

    public void remoteUnzipBinary(final String hostname, final String zipPath, final String binaryLocation, final String destination, final String exclude) {
        try {
            if (binaryDistributionControlService.unzipBinary(hostname, zipPath, binaryLocation, destination, exclude).getReturnCode().wasSuccessful()) {
                LOGGER.info("successfully unzipped the binary {}", binaryLocation);
            } else {
                final String message = "cannot unzip from " + binaryLocation + " to " + destination;
                LOGGER.error(message);
                throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, message);
            }
        } catch (CommandFailureException e) {
            final String message = "Error in remote unzip binary at host: " + hostname + " binaryLocation: " + binaryLocation + " destination: " + destination;
            LOGGER.error(message, e);
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, message, e);
        }
    }

    public void remoteSecureCopyFile(final String hostname, final String source, final String destination) {
        try {
            if (binaryDistributionControlService.secureCopyFile(hostname, source, destination).getReturnCode().wasSuccessful()) {
                LOGGER.info("successfully copied the binary {} over to {}", source, destination);
            } else {
                final String message = "error with scp of binary " + source + " to destination " + destination;
                LOGGER.error(message);
                throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, message);
            }
        } catch (CommandFailureException e) {
            final String message = "Error in remote secure copy at host: " + hostname + " source: " + source + " destination: " + destination;
            LOGGER.error(message, e);
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, message, e);
        }
    }

    public void remoteCreateDirectory(final String hostname, final String destination) {
        try {
            if (binaryDistributionControlService.createDirectory(hostname, destination).getReturnCode().wasSuccessful()) {
                LOGGER.info("successfully created directories {}", destination);
            } else {
                final String message = "User does not have permission to create the directory " + destination;
                LOGGER.error(message);
                throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, message);
            }
        } catch (CommandFailureException e) {
            final String message = "Error in create remote directory at host: " + hostname + " destination: " + destination;
            LOGGER.error(message, e);
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, message, e);
        }
    }

    public boolean remoteFileCheck(final String hostname, final String destination) {
        LOGGER.info("remoteFileCheck hostname: " + hostname + " destination: " + destination);
        boolean result;
        try {
            result = binaryDistributionControlService.checkFileExists(hostname, destination).getReturnCode().wasSuccessful();
        } catch (CommandFailureException e) {
            final String message = "Error in check remote File at host: " + hostname + " destination: " + destination;
            LOGGER.error(message, e);
            throw new InternalErrorException(FaultType.REMOTE_COMMAND_FAILURE, message, e);
        }
        LOGGER.info("result: " + result);
        return result;
    }

    @Override
    public void prepareUnzip(String hostname) {
        LOGGER.info("Start deploy unzip for {}", hostname);
        final String jwalaScriptsPath = ApplicationProperties.get("remote.commands.user-scripts");
        if (remoteFileCheck(hostname, jwalaScriptsPath)) {
            LOGGER.info(jwalaScriptsPath + " exists at " + hostname);
        } else {
            remoteCreateDirectory(hostname, jwalaScriptsPath);
        }
        final String unzipFileDestination = jwalaScriptsPath;
        if (remoteFileCheck(hostname, unzipFileDestination + "/" + UNZIPEXE)) {
            LOGGER.info(unzipFileDestination + "/" + UNZIPEXE + " exists at " + hostname);
        } else {
            final String unzipFileSource = ApplicationProperties.get(BINARY_LOCATION_PROPERTY_KEY) + "/" + UNZIPEXE;
            LOGGER.info("unzipFileSource: " + unzipFileSource);
            remoteSecureCopyFile(hostname, unzipFileSource, unzipFileDestination);
            changeFileMode(hostname, "a+x", jwalaScriptsPath, UNZIPEXE);
        }
        LOGGER.info("End deploy unzip for {}", hostname);
    }
}