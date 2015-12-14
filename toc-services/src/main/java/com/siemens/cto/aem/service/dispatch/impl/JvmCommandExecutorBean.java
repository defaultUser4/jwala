package com.siemens.cto.aem.service.dispatch.impl;

import com.siemens.cto.aem.common.dispatch.GroupJvmDispatchCommand;
import com.siemens.cto.aem.common.dispatch.JvmDispatchCommand;
import com.siemens.cto.aem.common.dispatch.JvmDispatchCommandResult;
import com.siemens.cto.aem.common.exec.CommandOutput;
import com.siemens.cto.aem.common.request.jvm.ControlJvmRequest;
import com.siemens.cto.aem.service.jvm.JvmControlService;

public class JvmCommandExecutorBean {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(JvmCommandExecutorBean.class);

    private JvmControlService jvmControlService;

    public JvmCommandExecutorBean(JvmControlService theJvmControlService) {
        jvmControlService = theJvmControlService;
    }

    public JvmDispatchCommandResult startStop(JvmDispatchCommand jvmDispatchCommand) {

        GroupJvmDispatchCommand groupDispatchCommand = jvmDispatchCommand.getGroupJvmDispatchCommand();

        LOGGER.debug("Execute command : " + jvmDispatchCommand.toString());

        Boolean wasSuccessful = false;

        try {
            ControlJvmRequest controlJvmCommand = new ControlJvmRequest(jvmDispatchCommand.getJvm().getId(),
                    groupDispatchCommand.getRequest().getControlOperation());

            CommandOutput commandOutput = jvmControlService.controlJvm(controlJvmCommand, groupDispatchCommand.getUser());
            wasSuccessful = commandOutput.getReturnCode().getWasSuccessful();
            LOGGER.debug("ControlJvmRequest complete.  Success = {}", wasSuccessful);
            
        } catch (RuntimeException e) {
            wasSuccessful = false;
            LOGGER.warn("Group dispatch ("+ groupDispatchCommand.toString() +"): ControlJvmRequest (" + jvmDispatchCommand.toString() + ") failed: ", e);
        }

        return new JvmDispatchCommandResult(wasSuccessful, groupDispatchCommand);
    }
}
