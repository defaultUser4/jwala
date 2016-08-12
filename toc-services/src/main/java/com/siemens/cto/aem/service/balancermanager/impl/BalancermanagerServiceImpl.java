package com.siemens.cto.aem.service.balancermanager.impl;

import com.siemens.cto.aem.common.domain.model.app.Application;
import com.siemens.cto.aem.common.domain.model.balancermanager.BalancerManagerState;
import com.siemens.cto.aem.common.domain.model.balancermanager.WorkerStatusType;
import com.siemens.cto.aem.common.domain.model.fault.AemFaultType;
import com.siemens.cto.aem.common.domain.model.group.Group;
import com.siemens.cto.aem.common.domain.model.user.User;
import com.siemens.cto.aem.common.domain.model.webserver.WebServer;
import com.siemens.cto.aem.common.domain.model.webserver.message.WebServerHistoryEvent;
import com.siemens.cto.aem.common.exception.ApplicationException;
import com.siemens.cto.aem.common.exception.InternalErrorException;
import com.siemens.cto.aem.persistence.jpa.type.EventType;
import com.siemens.cto.aem.service.HistoryService;
import com.siemens.cto.aem.service.MessagingService;
import com.siemens.cto.aem.service.app.ApplicationService;
import com.siemens.cto.aem.service.balancermanager.BalancermanagerService;
import com.siemens.cto.aem.service.balancermanager.impl.xml.data.Manager;
import com.siemens.cto.aem.service.group.GroupService;
import com.siemens.cto.aem.service.webserver.WebServerService;
import com.siemens.cto.aem.service.webserver.component.ClientFactoryHelper;
import org.apache.commons.io.IOUtils;
import org.apache.http.NameValuePair;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class BalancerManagerServiceImpl implements BalancermanagerService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BalancerManagerServiceImpl.class);

    private GroupService groupService;
    private ApplicationService applicationService;
    private WebServerService webServerService;

    private ClientFactoryHelper clientFactoryHelper;
    private MessagingService messagingService;
    private HistoryService historyService;
    private BalancerManagerHtmlParser balancerManagerHtmlParser;
    private BalancerManagerXmlParser balancerManagerXmlParser;
    private BalancerManagerHttpClient balancerManagerHttpClient;

    private String balancerManagerResponseHtml;
    private String balancerManagerResponseXml;
    private User user;

    public BalancerManagerServiceImpl(final GroupService groupService,
                                      final ApplicationService applicationService,
                                      final WebServerService webServerService,
                                      final ClientFactoryHelper clientFactoryHelper,
                                      final MessagingService messagingService,
                                      final HistoryService historyService,
                                      final BalancerManagerHtmlParser balancerManagerHtmlParser,
                                      final BalancerManagerXmlParser balancerManagerXmlParser,
                                      final BalancerManagerHttpClient balancerManagerHttpClient) {
        this.groupService = groupService;
        this.applicationService = applicationService;
        this.webServerService = webServerService;
        this.clientFactoryHelper = clientFactoryHelper;
        this.messagingService = messagingService;
        this.historyService = historyService;
        this.balancerManagerHtmlParser = balancerManagerHtmlParser;
        this.balancerManagerXmlParser = balancerManagerXmlParser;
        this.balancerManagerHttpClient = balancerManagerHttpClient;
    }

    @Override
    public BalancerManagerState drainUserGroup(final String groupName, final String webServers, final User user) {
        LOGGER.info("Entering drainUserGroup, groupName: " + groupName + " webServers: " + webServers);
        this.setUser(user);
        checkGroupStatus(groupName);
        String[] webServerArray = getRequireWebServers(webServers);
        List<BalancerManagerState.WebServerDrainStatus> webServerDrainStatusList = new ArrayList<>();
        Group group = groupService.getGroup(groupName);
        List<WebServer> webServerList;
        if (webServerArray.length == 0) {
            webServerList = webServerService.findWebServers(group.getId());
        } else {
            webServerList = findMatchWebServers(webServerService.findWebServers(group.getId()), webServerArray);
        }
        for (WebServer webServer : webServerList) {
            BalancerManagerState.WebServerDrainStatus webServerDrainStatus = doDrainAndgetDrainStatus(webServer, true);
            webServerDrainStatusList.add(webServerDrainStatus);
        }
        return new BalancerManagerState(groupName, webServerDrainStatusList);
    }

    @Override
    public BalancerManagerState drainUserWebServer(final String groupName, final String webServerName, final User user) {
        LOGGER.info("Entering drainUserGroup, groupName: " + groupName + " webServerName: " + webServerName);
        this.setUser(user);
        checkStatus(webServerService.getWebServer(webServerName));
        List<BalancerManagerState.WebServerDrainStatus> webServerDrainStatusList = new ArrayList<>();
        WebServer webServer = webServerService.getWebServer(webServerName);
        BalancerManagerState.WebServerDrainStatus webServerDrainStatus = doDrainAndgetDrainStatus(webServer, true);
        webServerDrainStatusList.add(webServerDrainStatus);
        return new BalancerManagerState(groupName, webServerDrainStatusList);
    }

    @Override
    public BalancerManagerState getGroupDrainStatus(final String groupName, final User user) {
        LOGGER.info("Entering getGroupDrainStatus: " + groupName);
        this.setUser(user);
        checkGroupStatus(groupName);
        List<BalancerManagerState.WebServerDrainStatus> webServerDrainStatusList = new ArrayList<>();
        Group group = groupService.getGroup(groupName);
        for (WebServer webServer : webServerService.findWebServers(group.getId())) {
            BalancerManagerState.WebServerDrainStatus webServerDrainStatus = doDrainAndgetDrainStatus(webServer, false);
            webServerDrainStatusList.add(webServerDrainStatus);
        }
        return new BalancerManagerState(groupName, webServerDrainStatusList);
    }

    public void checkGroupStatus(final String groupName) {
        final Group group = groupService.getGroup(groupName);
        List<WebServer> webServerList = webServerService.findWebServers(group.getId());
        for (WebServer webServer : webServerList) {
            if (!webServerService.isStarted(webServer)) {
                final String message = "The target Web Server " + webServer.getName() + " in group " + groupName + " must be STARTED before attempting to drain user";
                LOGGER.error(message);
                throw new InternalErrorException(AemFaultType.INVALID_WEBSERVER_OPERATION, message);
            }
        }
    }

    public void checkStatus(WebServer webServer) {
        if (!webServerService.isStarted(webServer)) {
            final String message = "The target Web Server " + webServer.getName() + " must be STARTED before attempting to drain user";
            LOGGER.error(message);
            throw new InternalErrorException(AemFaultType.INVALID_WEBSERVER_OPERATION, message);
        }
    }

    public String[] getRequireWebServers(final String webServers) {
        if (webServers.length() != 0) {
            return webServers.split(",");
        } else {
            return new String[0];
        }
    }

    public List<WebServer> findMatchWebServers(final List<WebServer> webServers, final String[] webServerArray) {
        LOGGER.info("Entering findMatchWebServers");
        List<WebServer> webServersMatch = new ArrayList<>();
        List<String> webServerNameMatch = new ArrayList<>();
        Map<Integer, String> webServerNamesIndex = new HashMap<>();
        for (WebServer webServer : webServers) {
            webServerNamesIndex.put(webServers.indexOf(webServer), webServer.getName());
        }
        for (String webServerArrayContentName : webServerArray) {
            if (webServerNamesIndex.containsValue(webServerArrayContentName.trim())) {
                int index = 0;
                for (Map.Entry<Integer, String> entry : webServerNamesIndex.entrySet()) {
                    if (entry.getValue().equalsIgnoreCase(webServerArrayContentName.trim())) {
                        index = entry.getKey();
                    }
                }
                webServersMatch.add(webServers.get(index));
                webServerNameMatch.add(webServers.get(index).getName());
            }
        }
        for (String webServerArrayContentName : webServerArray) {
            if (!webServerNameMatch.contains(webServerArrayContentName.trim())) {
                LOGGER.warn("WebServer Name does not exist: " + webServerArrayContentName.trim());
            }
        }
        return webServersMatch;
    }

    public BalancerManagerState.WebServerDrainStatus doDrainAndgetDrainStatus(final WebServer webServer, final Boolean post) {
        List<BalancerManagerState.WebServerDrainStatus.JvmDrainStatus> jvmDrainStatusList = prepareDrainWork(webServer, post);
        return new BalancerManagerState.WebServerDrainStatus(webServer.getName(), jvmDrainStatusList);
    }

    //TODO: It seems like no mater what balancer name and nonce I pass, it always return the whole xml format for webServer level
    //TODO: In this case, jwala only needs to do it one time instead of go through all balancer name (multiple times) to find out all workers
    //TODO: but it still need to pass the balancer name and nonce in order to get xml file
    //TODO: We believe it is the issue for apache-tomcat httpd balancer manager
    public List<BalancerManagerState.WebServerDrainStatus.JvmDrainStatus> prepareDrainWork(final WebServer webServer, final Boolean post) {
        LOGGER.info("Entering prepareDrainWork");
        final String balancerManagerHtmlUrl = balancerManagerHtmlParser.getUrlPath(webServer.getHost());
        balancerManagerResponseHtml = getBalancerManagerResponse(balancerManagerHtmlUrl);
        final Map<String, String> balancers = balancerManagerHtmlParser.findBalancers(balancerManagerResponseHtml);
        Map.Entry<String, String> entry = balancers.entrySet().iterator().next();
        final String balancerManagerXmlUrl = balancerManagerXmlParser.getUrlPath(webServer.getHost(), entry.getKey(), entry.getValue());
        balancerManagerResponseXml = getBalancerManagerResponse(balancerManagerXmlUrl);
        Manager manager = balancerManagerXmlParser.getWorkerXml(balancerManagerResponseXml);
        Map<String, String> workers = balancerManagerXmlParser.getWorkers(manager, entry.getKey());
        if (post) {
            doDrain(workers, balancerManagerHtmlUrl, webServer, entry.getKey(), entry.getValue());
        }
        return getBalancerStatus(webServer.getHost(), workers, entry.getKey(), entry.getValue());
    }

    /*
    In order to get the status from each worker in HTML, need to pass b=lb-health-check-4.0, w=http://usmlvv1cds0057:9101/hct and nonce=xxxx
    from Manager object, it can find the manager.balancer.getName to find the mapping worker
    using balancers map to get the balancer.getName to get the associated nonce id
     */
    public List<BalancerManagerState.WebServerDrainStatus.JvmDrainStatus> getBalancerStatus(final String hostName, final Map<String, String> workers,
                                                                                            final String balancerName, final String nonce) {
        List<BalancerManagerState.WebServerDrainStatus.JvmDrainStatus> jvmDrainStatusList = new ArrayList<>();
        for (String worker : workers.keySet()) {
            String workerUrl = balancerManagerHtmlParser.getWorkerUrlPath(hostName, balancerName, nonce, worker);
            String workerHtml = getBalancerManagerResponse(workerUrl);
            Map<String, String> workerStatusMap = balancerManagerHtmlParser.findWorkerStatus(workerHtml);
            BalancerManagerState.WebServerDrainStatus.JvmDrainStatus jvmDrainStatus = new BalancerManagerState.WebServerDrainStatus.JvmDrainStatus(worker,
                    workerStatusMap.get(WorkerStatusType.IGNORE_ERRORS.name()),
                    workerStatusMap.get(WorkerStatusType.DRAINING_MODE.name()),
                    workerStatusMap.get(WorkerStatusType.DISABLED.name()),
                    workerStatusMap.get(WorkerStatusType.HOT_STANDBY.name()));
            jvmDrainStatusList.add(jvmDrainStatus);
        }
        return jvmDrainStatusList;
    }

    public String getBalancerManagerResponse(final String statusUri) {
        System.out.println("Entering getBalancerManagerResponse: " + statusUri);
        try {
            return IOUtils.toString(clientFactoryHelper.requestGet(new URI(statusUri)).getBody(), "UTF-8");
        } catch (IOException e) {
            LOGGER.error(e.toString());
            throw new ApplicationException("Failed to get the response for Balancer Manager ", e);
        } catch (URISyntaxException e) {
            LOGGER.error(e.toString());
            throw new ApplicationException("Failed to cannot convert this path to URI ", e);
        }
    }

    public void doDrain(final Map<String, String> workers,
                        final String balancerManagerurl,
                        final WebServer webServer,
                        final String balancerName,
                        final String nonce) {
        LOGGER.info("Entering doDrain");
        for (String workerUrl : workers.keySet()) {
            final String message = "Set Drain mode for JVM " + workerUrl;
            sendMessage(webServer, message);
            try {
                CloseableHttpResponse response = balancerManagerHttpClient.doHttpClientPost(balancerManagerurl, getNvp(workerUrl, balancerName, nonce));
                LOGGER.info("response code: " + response.getStatusLine().getStatusCode());
                response.close();
            } catch (KeyManagementException e) {
                LOGGER.error(e.toString());
                throw new ApplicationException(e);
            } catch (IOException e) {
                LOGGER.error(e.toString());
                throw new ApplicationException(e);
            } catch (NoSuchAlgorithmException e) {
                LOGGER.error(e.toString());
                throw new ApplicationException(e);
            }
        }
    }

    public void sendMessage(final WebServer webServer, final String message) {
        LOGGER.info(message);
        messagingService.send(new WebServerHistoryEvent(webServer.getId(), "history", getUser().getId(), message));
        historyService.createHistory(webServer.getName(), new ArrayList<>(webServer.getGroups()), message, EventType.USER_ACTION, getUser().getId());
    }

    public List<NameValuePair> getNvp(final String worker, final String balancerName, final String nonce) {
        List<NameValuePair> nvps = new ArrayList<>();
        nvps.add(new BasicNameValuePair("w_status_N", "1"));
        nvps.add(new BasicNameValuePair("b", balancerName));
        nvps.add(new BasicNameValuePair("w", worker));
        nvps.add(new BasicNameValuePair("nonce", nonce));
        return nvps;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }
}