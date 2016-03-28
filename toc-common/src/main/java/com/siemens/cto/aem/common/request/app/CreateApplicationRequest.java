package com.siemens.cto.aem.common.request.app;

import com.siemens.cto.aem.common.domain.model.group.Group;
import com.siemens.cto.aem.common.domain.model.id.Identifier;
import com.siemens.cto.aem.common.request.Request;
import com.siemens.cto.aem.common.rule.MultipleRules;
import com.siemens.cto.aem.common.rule.app.ApplicationContextRule;
import com.siemens.cto.aem.common.rule.app.ApplicationNameRule;
import com.siemens.cto.aem.common.rule.group.GroupIdRule;

import java.io.Serializable;

public class CreateApplicationRequest implements Serializable, Request {

    private static final long serialVersionUID = 1L;

    private String name;
    private String webAppContext;
    private Identifier<Group> groupId;
    private final boolean unpackWar;
    private boolean secure;
    private boolean loadBalanceAcrossServers;
    
    public CreateApplicationRequest(Identifier<Group> groupId,
                                    String name,
                                    String webAppContext,
                                    boolean secure,
                                    boolean loadBalanceAcrossServers,
                                    boolean unpackWar) {
        this.name = name;
        this.webAppContext = webAppContext;
        this.groupId = groupId;
        this.secure = secure;
        this.loadBalanceAcrossServers = loadBalanceAcrossServers;
        this.unpackWar = unpackWar;
    }

    public Identifier<Group> getGroupId() {
        return groupId;
    }

    public String getName() {
        return name;
    }
    public String getWebAppContext() {
        return webAppContext;
    }

    public boolean isSecure() {
        return secure;
    }

    public boolean isLoadBalanceAcrossServers() {
        return loadBalanceAcrossServers;
    }

    @Override
    public void validate() {
        new MultipleRules(new GroupIdRule(groupId),
                                new ApplicationNameRule(name),
                                new ApplicationContextRule(webAppContext)).validate();
    }

    public boolean isUnpackWar() {
        return unpackWar;
    }
}
