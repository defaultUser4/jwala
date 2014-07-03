# !!! Generated by TOC for ${webServerName} !!!
# ${comment}
<%
    def workerList = "status"
    apps.each {
        workerList = workerList + ",lb-" + it.name.replaceAll(" ", "")
    }
%>
worker.list=${workerList}
<%
    def jvmNames = ""
    jvms.each() {
        def jvmName = it.jvmName.replaceAll(" ", "")
        jvmNames = jvmNames + (jvmNames != "" ? "," + jvmName : jvmName)
%>
worker.${jvmName}.type=ajp11
worker.${jvmName}.host=${it.hostName.replaceAll(" ", "")}
worker.${jvmName}.port=${it.ajpPort}
<%  } %>
<%
    apps.each() {
        def appName = it.name.replaceAll(" ", "");
%>
worker.lb-${appName}.type=lb
worker.lb-${appName}.balance_workers=${jvmNames}
worker.lb-${appName}.sticky_session=1
<% } %>
worker.status.type=status
worker.status.css=/loadbalancer/sts/custom.css