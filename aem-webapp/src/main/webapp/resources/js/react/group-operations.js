
/** @jsx React.DOM */
var GroupOperations = React.createClass({
    pollError: false,
    getInitialState: function() {
        selectedGroup = null;

        // What does the code below do ?
        this.allJvmData = { jvms: [],
                            jvmStates: []};
        return {
            // Rationalize/unify all the groups/jvms/webapps/groupTableData/etc. stuff so it's coherent
            groupFormData: {},
            groupTableData: [],
            groups: [],
            groupStates: [],
            webServers: [],
            webServerStates: [],
            jvms: [],
            jvmStates: []
        };
    },
    render: function() {
        var btnDivClassName = this.props.className + "-btn-div";
        return  <div className={this.props.className}>
                    <table style={{width:"1084px"}}>
                        <tr>
                            <td>
                                <div>
                                    <GroupOperationsDataTable data={this.state.groupTableData}
                                                              selectItemCallback={this.selectItemCallback}
                                                              groups={this.state.groups}
                                                              groupsById={groupOperationsHelper.keyGroupsById(this.state.groups)}
                                                              webServers={this.state.webServers}
                                                              jvms={this.state.jvms}
                                                              updateWebServerDataCallback={this.updateWebServerDataCallback}
                                                              stateService={this.props.stateService}
                                                              parent={this}/>
                                </div>
                            </td>
                        </tr>
                   </table>
               </div>
    },
    retrieveData: function() {
        var self = this;
        this.props.service.getGroups(function(response){
                                        self.setState({groupTableData:response.applicationResponseContent});
                                        self.updateJvmData(self.state.groupTableData);
                                        self.setState({ groups: response.applicationResponseContent});
                                     });
    },
    updateJvmData: function(jvmDataInGroups) {
        this.setState(groupOperationsHelper.processJvmData(this.state.jvms,
                                                           groupOperationsHelper.extractJvmDataFromGroups(jvmDataInGroups),
                                                           this.state.jvmStates,
                                                           []));
    },
    updateStateData: function(response) {
        if (this.pollError) {
            // updateStateData is called when there's no longer an error therefore we do error recovery here.
            this.fetchCurrentGroupStates();
            this.fetchCurrentWebServerStates();
            this.fetchCurrentJvmStates();
            this.pollError = false;
            return;
        }

        var newStates = response.applicationResponseContent;
        var groups = [];
        var webServers = [];
        var jvms = [];

        for (var i = 0; i < newStates.length; i++) {
            if (newStates[i].type === "JVM") {
                jvms.push(newStates[i]);
            } else if (newStates[i].type === "WEB_SERVER") {
                webServers.push(newStates[i]);
            } else if (newStates[i].type === "GROUP") {
                groups.push(newStates[i]);
            }
        }

        this.updateGroupsStateData(groups);
        this.updateWebServerStateData(webServers);
        this.updateJvmStateData(jvms);
    },
    statePollingErrorHandler: function(response) {
        if (typeof response.responseText === "string" && response.responseText.indexOf("Login") > -1) {
            alert("The session has expired! You will be redirected to the login page.");
            window.location.href = "login";
            return;
        }

        this.pollError = true;
        for (var key in GroupOperations.groupStatusWidgetMap) {
            var groupStatusWidget = GroupOperations.groupStatusWidgetMap[key];
            if (groupStatusWidget !== undefined) {
                // Can't afford to slip or else the polling stops
                try {
                    groupStatusWidget.setStatus(GroupOperations.POLL_ERR_STATE,  new Date(), response.responseJSON.applicationResponseContent);
                } catch (e) {
                    console.log(e);
                }
            }
        }

        for (var key in GroupOperations.jvmStatusWidgetMap) {
            var jvmStatusWidget = GroupOperations.jvmStatusWidgetMap[key];
            if (jvmStatusWidget !== undefined) {
                // Can't afford to slip or else the polling stops
                try {
                    jvmStatusWidget.setStatus(GroupOperations.UNKNOWN_STATE,  new Date(), "");
                } catch (e) {
                    console.log(e);
                }
            }
        }

        for (var key in GroupOperations.webServerStatusWidgetMap) {
            var webServerStatusWidget = GroupOperations.webServerStatusWidgetMap[key];
            if (webServerStatusWidget !== undefined) {
                // Can't afford to slip or else the polling stops
                try {
                    webServerStatusWidget.setStatus(GroupOperations.UNKNOWN_STATE,  new Date(), "");
                } catch (e) {
                    console.log(e);
                }
            }
        }
    },
    updateGroupsStateData: function(newGroupStates) {
        var groupsToUpdate = groupOperationsHelper.getGroupStatesById(this.state.groups);

        groupsToUpdate.forEach(
        function(group) {
            var groupStatusWidget = GroupOperations.groupStatusWidgetMap["grp" + group.groupId.id];
            if (newGroupStates !== undefined) {
                for (var i = 0; i < newGroupStates.length; i++) {
                    if (newGroupStates[i].id.id === group.groupId.id) {
                        groupStatusWidget.setStatus(newGroupStates[i].stateString,
                                                    newGroupStates[i].asOf,
                                                    newGroupStates[i].message);
                    }
                }
            }
        });
    },
    updateWebServerStateData: function(newWebServerStates) {
        var webServersToUpdate = groupOperationsHelper.getWebServerStatesByGroupIdAndWebServerId(this.state.webServers);
        webServersToUpdate.forEach(
        function(webServer) {
            var webServerStatusWidget = GroupOperations.webServerStatusWidgetMap["grp" + webServer.groupId.id + "webServer" + webServer.webServerId.id];
            if (webServerStatusWidget !== undefined) {
                for (var i = 0; i < newWebServerStates.length; i++) {
                    if (newWebServerStates[i].id.id === webServer.webServerId.id) {
                        if (newWebServerStates[i].stateString === GroupOperations.FAILED) {
                            // Reroute to command status component.
                            var mountingNode = $("#" + GroupOperations.getExtDivCompId(webServer.groupId.id));
                            if (mountingNode.length > 0) {
                                var status = {stateString: newWebServerStates[i].stateString,
                                              asOf: newWebServerStates[i].asOf,
                                              message: newWebServerStates[i].message,
                                              from: webServer.name};
                                GroupOperations.pushCommandStatus(webServer.groupId.id, status);
                                React.render(<CommandStatusWidget statusDetails={GroupOperations.commandStatusMap[webServer.groupId.id]}
                                              closeCallback={
                                                  function(){
                                                      GroupOperations.commandStatusMap[webServer.groupId.id] = [];
                                                      React.unmountComponentAtNode (mountingNode.get(0));
                                                  }
                                              }/>, mountingNode.get(0));
                            }
                        } else {
                            webServerStatusWidget.setStatus(newWebServerStates[i].stateString,
                                                            newWebServerStates[i].asOf,
                                                            newWebServerStates[i].message);
                        }
                    }
                }
            }
        });
    },
    updateJvmStateData: function(newJvmStates) {
        var self = this;


        var jvmsToUpdate = groupOperationsHelper.getJvmStatesByGroupIdAndJvmId(this.state.jvms);


        jvmsToUpdate.forEach(function(jvm) {
            var jvmStatusWidget = GroupOperations.jvmStatusWidgetMap["grp" + jvm.groupId.id + "jvm" + jvm.jvmId.id];
            if (jvmStatusWidget !== undefined) {
                for (var i = 0; i < newJvmStates.length; i++) {
                    if (newJvmStates[i].id.id === jvm.jvmId.id) {
                        if (newJvmStates[i].stateString === GroupOperations.FAILED) {
                            // Reroute to command status component.
                            var mountingNode = $("#" + GroupOperations.getExtDivCompId(jvm.groupId.id));
                            if (mountingNode.length > 0) {
                                var status = {stateString: newJvmStates[i].stateString,
                                              asOf: newJvmStates[i].asOf,
                                              message: newJvmStates[i].message,
                                              from: jvm.name};
                                GroupOperations.pushCommandStatus(jvm.groupId.id, status);
                                React.render(<CommandStatusWidget statusDetails={GroupOperations.commandStatusMap[jvm.groupId.id]}
                                              closeCallback={
                                                  function(){
                                                      GroupOperations.commandStatusMap[jvm.groupId.id] = [];
                                                      React.unmountComponentAtNode (mountingNode.get(0));
                                                  }
                                              }/>, mountingNode.get(0));
                            }
                        } else {
                            jvmStatusWidget.setStatus(newJvmStates[i].stateString,
                                                      newJvmStates[i].asOf,
                                                      newJvmStates[i].message);
                        }
                    }
                }
            }
        });
    },
    pollStates: function() {
        if (GroupOperations.statePoller === null) {
            GroupOperations.statePoller = new PollerForAPromise(GroupOperations.STATE_POLLER_INTERVAL, stateService.getNextStates,
                                                                this.updateStateData, this.statePollingErrorHandler);
        }

        GroupOperations.statePoller.start();
    },
    fetchCurrentGroupStates: function() {
        var self = this;
        this.props.stateService.getCurrentGroupStates()
            .then(function(data) {self.updateGroupsStateData(data.applicationResponseContent);})
            .caught(function(e) {console.log(e);});
    },
    fetchCurrentWebServerStates: function() {
        var self = this;
        this.props.stateService.getCurrentWebServerStates()
            .then(function(data) {self.updateWebServerStateData(data.applicationResponseContent);})
            .caught(function(e) {console.log(e);});
    },
    fetchCurrentJvmStates: function() {
        var self = this;
        this.props.stateService.getCurrentJvmStates()
            .then(function(data) {self.updateJvmStateData(data.applicationResponseContent);})
            .caught(function(e) {console.log(e);});
    },
    markGroupExpanded: function(groupId, isExpanded) {
        this.setState(groupOperationsHelper.markGroupExpanded(this.state.groups,
                                                              groupId,
                                                              isExpanded));
    },
    markJvmExpanded: function(jvmId, isExpanded) {
        this.setState(groupOperationsHelper.markJvmExpanded(this.state.jvms,
                                                            jvmId,
                                                            isExpanded));
    },
    componentDidMount: function() {
        this.retrieveData();
        this.pollStates();
        this.fetchCurrentGroupStates();
    },
    componentWillUnmount: function() {
        GroupOperations.statePoller.stop();
    },
    updateWebServerDataCallback: function(webServerData) {
        this.setState(groupOperationsHelper.processWebServerData([],
                                                                 webServerData,
                                                                 this.state.webServerStates,
                                                                 []));
        this.updateWebServerStateData([]);
    },
    statics: {
        // Used in place of ref since ref will not work without a React wrapper (in the form a data table)
        groupStatusWidgetMap: {},
        webServerStatusWidgetMap: {},
        jvmStatusWidgetMap: {},
        FAILED: "FAILED",
        getExtDivCompId: function(groupId) {
            return "ext-comp-div-group-operations-table_" + groupId;
        },
        commandStatusMap: {},
        pushCommandStatus: function(groupId, status) {
            var statusArray = GroupOperations.commandStatusMap[groupId] === undefined ? [] : GroupOperations.commandStatusMap[groupId];

            // only keep the latest 30 messages
            if (statusArray.length >= 30) {
                statusArray.splice(0, 1);
            }
            statusArray.push(status);

            GroupOperations.commandStatusMap[groupId] = statusArray;
        },
        UNKNOWN_STATE: "UNKNOWN",
        POLL_ERR_STATE: "POLLING ERROR!",
        statePoller: null,
        STATE_POLLER_INTERVAL: 1
    }
});

var GroupOperationsDataTable = React.createClass({
   doneCallback: {},
   shouldComponentUpdate: function(nextProps, nextState) {

       // TODO: Set status here
       this.groupsById = groupOperationsHelper.keyGroupsById(nextProps.groups);

       this.hasNoData = (this.props.data.length === 0);

       return this.hasNoData;
    },
    render: function() {
        var groupTableDef = [{sTitle:"", mData: "jvms", tocType:"control", colWidth:"14px"},
                             {sTitle:"Group ID", mData:"id.id", bVisible:false},
                             {sTitle:"Group Name", mData:"name", colWidth:"651px"},
                              [{id:"startGroup",
                                sTitle:"Start Group",
                                mData:null,
                                tocType:"button",
                                btnLabel:"Start Group",
                                btnCallback:this.startGroup,
                                className:"inline-block",
                                buttonClassName:"ui-button-height",
                                extraDataToPassOnCallback:"name",
                                onClickMessage:"Starting..."
                               },
                               {tocType:"space"},
                               {id:"stopGroup",
                                sTitle:"Stop Group",
                                mData:null,
                                tocType:"button",
                                btnLabel:"Stop Group",
                                btnCallback:this.stopGroup,
                                className:"inline-block",
                                buttonClassName:"ui-button-height",
                                extraDataToPassOnCallback:"name",
                                onClickMessage:"Stopping..."
                              }],

                              {sTitle:"State",
                               mData:null,
                               tocType:"custom",
                               tocRenderCfgFn: this.renderGroupStateRowData.bind(this, "grp"),
                               colWidth:"130px"}];

        var webServerOfGrpChildTableDef = [{sTitle:"Web Server ID", mData:"id.id", bVisible:false},
                                           {mData:null, colWidth:"10px"},
                                           {sTitle:"Name", mData:"name", colWidth:"340px", maxDisplayTextLen:45},
                                           {sTitle:"Host", mData:"host", colWidth:"140px", maxDisplayTextLen:20},
                                           {sTitle:"HTTP", mData:"port", colWidth:"41px"},
                                           {sTitle:"HTTPS", mData:"httpsPort", colWidth:"48px"},
                                           {sTitle:"Group",
                                            mData:"groups",
                                            tocType:"array",
                                            displayProperty:"name",
                                            maxDisplayTextLen:20,
                                            colWidth:"129px"},
                                           {mData:null,
                                            tocType:"custom",
                                            tocRenderCfgFn: this.renderWebServerControlPanelWidget.bind(this, "grp", "webServer")},
                                           {sTitle:"State",
                                            mData:null,
                                            tocType:"custom",
                                            tocRenderCfgFn: this.renderWebServerStateRowData.bind(this, "grp", "webServer"),
                                            colWidth:"120px"}];

        var webServerOfGrpChildTableDetails = {tableIdPrefix:"ws-child-table_",
                                               className:"simple-data-table",
                                               dataCallback:this.getWebServersOfGrp,
                                               title:"Web Servers",
                                               isCollapsible:true,
                                               headerComponents:[
                                                    {id:"startWebServers",
                                                     sTitle:"Start Web Servers",
                                                     mData:null,
                                                     tocType:"button",
                                                     btnLabel:"Start Web Servers",
                                                     btnCallback:this.startGroupWebServers,
                                                     className:"inline-block",
                                                     buttonClassName:"ui-button-height",
                                                     onClickMessage:"Starting..."},
                                                    {id:"space1", tocType:"space"},
                                                    {id:"stopWebServers",
                                                     sTitle:"Stop Web Servers",
                                                     mData:null,
                                                     tocType:"button",
                                                     btnLabel:"Stop Web Servers",
                                                     btnCallback:this.stopGroupWebServers,
                                                     className:"inline-block",
                                                     buttonClassName:"ui-button-height",
                                                     onClickMessage:"Stopping..."},
                                                    {tocType:"label", className:"inline-block header-component-label", text:""}
                                               ],
                                               initialSortColumn: [[2, "asc"]],
                                               isColResizable: true};

        webServerOfGrpChildTableDetails["tableDef"] = webServerOfGrpChildTableDef;

        var webAppOfGrpChildTableDetails = {tableIdPrefix:"web-app-child-table_",
                                            className:"simple-data-table",
                                            dataCallback:this.getApplicationsOfGrp,
                                            title:"Applications",
                                            isCollapsible:true,
                                            initialSortColumn: [[1, "asc"]],
                                            isColResizable: true};

        var webAppOfGrpChildTableDef = [{sTitle:"Web App ID", mData:"id.id", bVisible:false},
                                        {mData:null, colWidth:"10px"},
                                        {sTitle:"Name", mData:"name"},
                                        {sTitle:"War Path", mData:"warPath", tocType:"custom", tocRenderCfgFn: this.renderWebAppRowData},
                                        {sTitle:"Context", mData:"webAppContext"}];

        webAppOfGrpChildTableDetails["tableDef"] = webAppOfGrpChildTableDef;

        var webAppOfJvmChildTableDetails = {tableIdPrefix:"web-app-child-table_jvm-child-table_", /* TODO: We may need to append the group and jvm id once this table is enabled in the next release. */
                                            className:"simple-data-table",
                                            dataCallback:this.getApplicationsOfJvm,
                                            defaultSorting: {col:5, sort:"asc"},
                                            initialSortColumn: [[1, "asc"]]};

        var webAppOfJvmChildTableDef = [{sTitle:"Web App ID", mData:"id.id", bVisible:false},
                                        {sTitle:"Web App in JVM", mData:"name"},
                                        {sTitle:"War Path", mData:"warPath"},
                                        {sTitle:"Context", mData:"webAppContext"},
                                        {sTitle:"Group", mData:"group.name"},
                                        {sTitle:"Class Name", mData:"className", bVisible:false}];

        webAppOfJvmChildTableDetails["tableDef"] = webAppOfJvmChildTableDef;

        var jvmChildTableDetails = {tableIdPrefix:"jvm-child-table_",
                                    className:"simple-data-table",
                                    /* childTableDetails:webAppOfJvmChildTaapbleDetails, !!! Disable for the Aug 11, 2014 Demo */
                                    title:"JVMs",
                                    isCollapsible:true,
                                    headerComponents:[
                                         {id:"startJvms",
                                          sTitle:"Start JVMs",
                                          mData:null,
                                          tocType:"button",
                                          btnLabel:"Start JVMs",
                                          btnCallback:this.startGroupJvms,
                                          className:"inline-block",
                                          buttonClassName:"ui-button-height",
                                          onClickMessage:"Starting..."
                                         },
                                         {id:"space1", tocType:"space"},
                                         {id:"stopJvms",
                                           sTitle:"Stop JVMs",
                                           mData:null,
                                           tocType:"button",
                                           btnLabel:"Stop JVMs",
                                           btnCallback:this.stopGroupJvms,
                                           className:"inline-block",
                                           buttonClassName:"ui-button-height",
                                           onClickMessage:"Stopping..."
                                          },
                                         {tocType:"label", className:"inline-block header-component-label", text:""}
                                    ],
                                    initialSortColumn: [[2, "asc"]],
                                    isColResizable: true};

        var jvmChildTableDef = [/* {sTitle:"", mData:null, tocType:"control"}, !!! Disable for the Aug 11, 2014 Demo */
                                {mData:null, colWidth:"10px"},
                                {sTitle:"JVM ID", mData:"id.id", bVisible:false},
                                {sTitle:"Name", mData:"jvmName", colWidth:"340px", maxDisplayTextLen:48},
                                {sTitle:"Host", mData:"hostName", colWidth:"140", maxDisplayTextLen:17},
                                {sTitle:"HTTP", mData:"httpPort", colWidth:"41px"},
                                {sTitle:"HTTPS", mData:"httpsPort", colWidth:"48px"},
                                {sTitle:"Group",
                                 mData:"groups",
                                 tocType:"array",
                                 displayProperty:"name",
                                 maxDisplayTextLen:20,
                                 colWidth:"138px"},
                                [{id:"tomcatManager",
                                  sTitle:"Manager",
                                  mData:null,
                                  tocType:"button",
                                  btnLabel:"",
                                  btnCallback:this.onClickMgr,
                                  className:"inline-block",
                                  customSpanClassName:"ui-icon ui-icon-mgr",
                                  buttonClassName:"ui-button-height",
                                  extraDataToPassOnCallback:["hostName","httpPort", "httpsPort"]},
                                 {id:"spacediag", tocType:"space"},
                                 {tocType:"space"},
                                 {id:"threadDump",
                                  sTitle:"Thread Dump",
                                  mData:null,
                                  tocType:"button",
                                  btnLabel:"",
                                  btnCallback:this.onClickThreadDump,
                                  className:"inline-block",
                                  customSpanClassName:"ui-icon ui-icon-thread-dump",
                                  buttonClassName:"ui-button-height"},
                                  {tocType:"space"},
                                 {id:"heapDump",
                                  sTitle:"Heap Dump",
                                  mData:null,
                                  tocType:"button",
                                  btnLabel:"",
                                  btnCallback:this.jvmHeapDump,
                                  className:"inline-block",
                                  customSpanClassName:"ui-icon ui-icon-heap-dump",
                                  buttonClassName:"ui-button-height",
                                  extraDataToPassOnCallback:"hostName"},
                                 {tocType:"space"},
                                 {id:"generateJvmConfig",
                                  sTitle:"Generate JVM resources files",
                                  mData:"name",
                                  tocType:"button",
                                  btnLabel:"",
                                  btnCallback:this.jvmGenerateJvmConfig,
                                  className:"inline-block",
                                  customSpanClassName:"ui-icon ui-icon-gear-custom",
                                  buttonClassName:"ui-button-height",
                                  clickedStateClassName:"busy-button",
                                  busyStatusTimeout:30000,
                                  extraDataToPassOnCallback:["jvmName"]},
                                 {tocType:"space"},
                                  {id:"startJvm",
                                  sTitle:"Start",
                                  mData:null,
                                  tocType:"button",
                                  btnLabel:"",
                                  btnCallback:this.jvmStart,
                                  className:"inline-block",
                                  customSpanClassName:"ui-icon ui-icon-play",
                                  buttonClassName:"ui-button-height",
                                  extraDataToPassOnCallback:["jvmName","groups"],
                                  onClickMessage:"Starting..."},
                                 {tocType:"space"},
                                 {id:"stopJvm",
                                  sTitle:"Stop",
                                  mData:null,
                                  tocType:"button",
                                  btnLabel:"",
                                  btnCallback:this.jvmStop,
                                  className:"inline-block",
                                  customSpanClassName:"ui-icon ui-icon-stop",
                                  buttonClassName:"ui-button-height",
                                  extraDataToPassOnCallback:["jvmName","groups"],
                                  onClickMessage:"Stopping..."}],
                                {sTitle:"State",
                                 mData:null,
                                 tocType:"custom",
                                 tocRenderCfgFn: this.renderJvmStateRowData.bind(this, "grp", "jvm"),
                                 colWidth:"120px"}];

        jvmChildTableDetails["tableDef"] = jvmChildTableDef;

        var childTableDetailsArray = [webServerOfGrpChildTableDetails,
                                      jvmChildTableDetails,
                                      webAppOfGrpChildTableDetails];

        return <TocDataTable tableId="group-operations-table"
                             className="dataTable hierarchical"
                             tableDef={groupTableDef}
                             data={this.props.data}
                             rowSubComponentContainerClassName="row-sub-component-container"
                             childTableDetails={childTableDetailsArray}
                             selectItemCallback={this.props.selectItemCallback}
                             initialSortColumn={[[2, "asc"]]}
                             isColResizable={true}/>
   },
   renderGroupStateRowData: function(type, dataTable, data, aoColumnDefs, itemIndex, parentId) {
      var self= this;
      aoColumnDefs[itemIndex].bSortable = false;
      aoColumnDefs[itemIndex].fnCreatedCell = function (nTd, sData, oData, iRow, iCol) {
           var key = type + oData.id.id;
           return React.render(<StatusWidget key={key} defaultStatus=""
                                    errorMsgDlgTitle={oData.name + " State Error Messages"} />, nTd, function() {
                      GroupOperations.groupStatusWidgetMap[key] = this;

                      // Fetch and set initial state
                      var statusWidget = this;
                      self.props.stateService.getCurrentGroupStates(oData.id.id)
                                             .then(function(data) {
                                                      if (self.props.parent.pollError) {
                                                          statusWidget.setStatus(GroupOperations.POLL_ERR_STATE,  new Date(),
                                                                    response.responseJSON.applicationResponseContent);
                                                      } else {
                                                          statusWidget.setStatus(data.applicationResponseContent[0].stateString,
                                                                                 data.applicationResponseContent[0].asOf,
                                                                                 data.applicationResponseContent[0].message);
                                                      }
                                                   });

                  });
      }.bind(this);
   },
   renderWebServerGenerateBtn: function(parentPrefix, type, dataTable, data, aoColumnDefs, itemIndex, parentId) {
        var self= this;
        aoColumnDefs[itemIndex].fnCreatedCell = function (nTd, sData, oData, iRow, iCol) {
            return React.render(<a>Testing...</a>, nTd);
    }.bind(this);
   },
   renderWebServerControlPanelWidget: function(parentPrefix, type, dataTable, data, aoColumnDefs, itemIndex, parentId) {
       var self= this;
       aoColumnDefs[itemIndex].fnCreatedCell = function (nTd, sData, oData, iRow, iCol) {
            return React.render(<WebServerControlPanelWidget data={oData}
                                                             webServerService={webServerService}
                                                             webServerStartCallback={this.webServerStart}
                                                             webServerStopCallback={this.webServerStop} />, nTd, function() {
                   });
       }.bind(this);
   },
   renderWebServerStateRowData: function(parentPrefix, type, dataTable, data, aoColumnDefs, itemIndex, parentId) {
       var self= this;
       aoColumnDefs[itemIndex].fnCreatedCell = function (nTd, sData, oData, iRow, iCol) {
            var key = parentPrefix + parentId + type + oData.id.id;
            return React.render(<StatusWidget key={key} defaultStatus=""
                                     errorMsgDlgTitle={oData.name + " State Error Messages"} />, nTd, function() {
                       GroupOperations.webServerStatusWidgetMap[key] = this;

                       // Fetch and set initial state
                       var statusWidget = this;
                       self.props.stateService.getCurrentWebServerStates(oData.id.id)
                                              .then(function(data) {
                                                        if (self.props.parent.pollError) {
                                                            statusWidget.setStatus(GroupOperations.UNKNOWN_STATE,  new Date(), "");
                                                        } else {
                                                            statusWidget.setStatus(data.applicationResponseContent[0].stateString,
                                                                                   data.applicationResponseContent[0].asOf,
                                                                                   data.applicationResponseContent[0].message);
                                                        }
                                                    });

                   });
       }.bind(this);
   },
   renderJvmStateRowData: function(parentPrefix, type, dataTable, data, aoColumnDefs, itemIndex, parentId) {
        var self= this;
        aoColumnDefs[itemIndex].fnCreatedCell = function (nTd, sData, oData, iRow, iCol) {
             var key = parentPrefix + parentId + type + oData.id.id;
             return React.render(<StatusWidget key={key} defaultStatus=""
                                      errorMsgDlgTitle={oData.jvmName + " State Error Messages"} />, nTd, function() {
                        GroupOperations.jvmStatusWidgetMap[key] = this;

                        // Fetch and set initial state
                        var statusWidget = this;
                        self.props.stateService.getCurrentJvmStates(oData.id.id)
                                               .then(function(data) {
                                                        if (self.props.parent.pollError) {
                                                            statusWidget.setStatus(GroupOperations.UNKNOWN_STATE,  new Date(), "");
                                                        } else {
                                                            statusWidget.setStatus(data.applicationResponseContent[0].stateString,
                                                                                   data.applicationResponseContent[0].asOf,
                                                                                   data.applicationResponseContent[0].message);
                                                        }
                                                    });
                    });
        }.bind(this);
   },
   renderWebAppRowData: function(dataTable, data, aoColumnDefs, itemIndex) {
          dataTable.expandCollapseEnabled = true;
          aoColumnDefs[itemIndex].mDataProp = null;
          aoColumnDefs[itemIndex].sClass = "";
          aoColumnDefs[itemIndex].bSortable = false;

          aoColumnDefs[itemIndex].mRender = function (data, type, full) {

                  return React.renderComponentToStaticMarkup(
                      <WARUpload war={data} readOnly={true} full={full} row={0} />
                    );
                }.bind(this);
   },
   getWebServersOfGrp: function(idObj, responseCallback) {
        var self = this;

        webServerService.getWebServerByGroupId(idObj.parentId, function(response) {
            // This is when the row is initially opened.
            // Unlike JVMs, web server data is retrieved when the row is opened.

            if (response.applicationResponseContent !== undefined && response.applicationResponseContent !== null) {
                response.applicationResponseContent.forEach(function(o) {
                    o["parentItemId"] = idObj.parentId;
                });
            }

            responseCallback(response);

            // This will set the state which triggers DOM rendering thus the state will be updated
            // TODO: Find out if the code below is still necessary since removing it seems to have no effect whatsoever.
            self.props.updateWebServerDataCallback(response.applicationResponseContent);
        });
   },
   getApplicationsOfGrp: function(idObj, responseCallback) {
        // TODO: Verify if we need to display the applications on a group. If we need to, I think this needs fixing. For starters, we need to include the group id in the application response.
        webAppService.getWebAppsByGroup(idObj.parentId, responseCallback);
   },
   getApplicationsOfJvm: function(idObj, responseCallback) {

            webAppService.getWebAppsByJvm(idObj.parentId, function(data) {

                var webApps = data.applicationResponseContent;
                for (var i = 0; i < webApps.length; i++) {
                    if (idObj.rootId !== webApps[i].group.id.id) {
                        webApps[i]["className"] = "highlight";
                    } else {
                        webApps[i]["className"] = ""; // This is needed to prevent datatable from complaining
                                                      // for a missing "className" data since "className" is a defined
                                                      // filed in mData (please research for JQuery DataTable)
                    }
                }

                responseCallback(data);

            });

   },
   deploy: function(id) {
        alert("Deploy applications for group_" + id + "...");
   },
    enableButtonThunk: function(buttonSelector, iconClass) {
        return function() {
            $(buttonSelector).prop('disabled', false);
            if ($(buttonSelector + " span").hasClass("ui-icon")) {
                $(buttonSelector).attr("class",
                "ui-button ui-widget ui-state-default ui-corner-all ui-button-text-only ui-button-height");
                $(buttonSelector).find("span").attr("class", "ui-icon " + iconClass);
            } else {
                $(buttonSelector).removeClass("ui-state-disabled");
            }
        };
    },
    disableButtonThunk: function(buttonSelector) {
        return function() {
            $(buttonSelector).prop('disabled', true);
            if ($(buttonSelector + " span").hasClass("ui-icon")) {
                $(buttonSelector).attr("class", "busy-button");
                $(buttonSelector).find("span").removeClass();
            } else {
                $(buttonSelector).addClass("ui-state-disabled");
            }
        };
    },
   enableHeapDumpButtonThunk: function(buttonSelector) {
       return function() {
            $(buttonSelector).prop('disabled', false);
            $(buttonSelector).attr("class",
                                   "ui-button ui-widget ui-state-default ui-corner-all ui-button-text-only ui-button-height");
            $(buttonSelector).find("span").attr("class", "ui-icon ui-icon-heap-dump");
       };
   },
   disableHeapDumpButtonThunk: function(buttonSelector) {
       return function() {
           $(buttonSelector).prop('disabled', true);
           $(buttonSelector).attr("class", "busy-button");
           $(buttonSelector).find("span").removeClass();
       };
   },
   disableEnable: function(buttonSelector, func, iconClass) {
       var disable = this.disableButtonThunk(buttonSelector);
       var enable = this.enableButtonThunk(buttonSelector, iconClass);
       Promise.method(disable)().then(func).lastly(enable);
   },
   enableLinkThunk: function(linkSelector) {
        return function () {
            $(linkSelector).removeClass("disabled");
        };
    },
    disableLinkThunk: function(linkSelector) {
        return function () {
            $(linkSelector).addClass("disabled");
        };
    },
    disableEnableHeapDumpButton: function(selector, requestTask, requestCallbackTask, errHandler) {
        var disable = this.disableHeapDumpButtonThunk(selector);
        var enable = this.enableHeapDumpButtonThunk(selector);
        Promise.method(disable)().then(requestTask).then(requestCallbackTask).caught(errHandler).lastly(enable);
    },
   confirmStartStopGroupDialogBox: function(id, buttonSelector, msg, callbackOnConfirm) {
        var dialogId = "group-stop-confirm-dialog-" + id;
        $(buttonSelector).parent().append("<div id='" + dialogId +"' style='text-align:left'>" + msg + "</div>");
        $(buttonSelector).parent().find("#" + dialogId).dialog({
            title: "Confirmation",
            width: "auto",
            modal: true,
            buttons: {
                "Yes": function() {
                    callbackOnConfirm(id, buttonSelector);
                    $(this).dialog("close");
                },
                "No": function() {
                    $(this).dialog("close");
                }
            },
            open: function() {
                // Set focus to "No button"
                $(this).closest('.ui-dialog').find('.ui-dialog-buttonpane button:eq(1)').focus();
            }
        });
   },
    /**
     * Verifies and confirms to the user whether to continue the operation or not.
     * @param id the id (e.g. group id)
     * @param name the name (e.g. group name)
     * @param buttonSelector the jquery button selector
     * @param operation control operation namely "Start" and "Stop"
     * @param operationCallback operation to execute (e.g. startGroupCallback)
     * @param groupChildType a group's children to verify membership in other groups
     *                       (jvm - all JVMs, webServer - all web servers, undefined = jvms and web servers)
     */
    verifyAndConfirmControlOperation: function(id, buttonSelector, name, operation, operationCallback, groupChildType) {
        var self = this;
        groupService.getChildrenOtherGroupConnectionDetails(id, groupChildType).then(function(data) {
            if (data.applicationResponseContent instanceof Array && data.applicationResponseContent.length > 0) {
                var membershipDetails =
                    groupOperationsHelper.createMembershipDetailsHtmlRepresentation(data.applicationResponseContent);

                self.confirmStartStopGroupDialogBox(id,
                                               buttonSelector,
                                               membershipDetails
                                                    + "<br/><b>Are you sure you want to " + operation
                                                    + " <span style='color:#2a70d0'>" + name + "</span> ?</b>",
                                               operationCallback);
            } else {
                operationCallback(id, buttonSelector);
            }
        });
    },
    startGroupCallback: function(id, buttonSelector) {
        this.disableEnable(buttonSelector, function() {return groupControlService.startGroup(id)}, "ui-icon-play");
    },
    startGroup: function(id, buttonSelector, name) {
        this.verifyAndConfirmControlOperation(id, buttonSelector, name, "start", this.startGroupCallback);
    },
    stopGroupCallback: function(id, buttonSelector) {
        this.disableEnable(buttonSelector, function() {return groupControlService.stopGroup(id)}, "ui-icon-stop");
    },
    stopGroup: function(id, buttonSelector, name) {
        this.verifyAndConfirmControlOperation(id, buttonSelector, name, "stop", this.stopGroupCallback);
    },

    startGroupJvms: function(event) {
        var self = this;
        var callback = function(id, buttonSelector) {
                            self.disableEnable(event.data.buttonSelector,
                                               function() { return groupControlService.startJvms(event.data.id)},
                                               "ui-icon-play");
                       }

        this.verifyAndConfirmControlOperation(event.data.id,
                                              event.data.buttonSelector,
                                              event.data.name,
                                              "start all JVMs under",
                                              callback,
                                              "jvm");
    },

    stopGroupJvms: function(event) {
        var self = this;
        var callback = function(id, buttonSelector) {
                            self.disableEnable(event.data.buttonSelector,
                                               function() { return groupControlService.stopJvms(event.data.id)},
                                               "ui-icon-stop");
                       }

        this.verifyAndConfirmControlOperation(event.data.id,
                                              event.data.buttonSelector,
                                              event.data.name,
                                              "stop all JVMs under",
                                              callback,
                                              "jvm");
    },
    startGroupWebServers: function(event) {
        var self = this;
        var callback = function(id, buttonSelector) {
                            self.disableEnable(event.data.buttonSelector,
                                               function() { return groupControlService.startWebServers(event.data.id)},
                                               "ui-icon-play");
                       }

        this.verifyAndConfirmControlOperation(event.data.id,
                                              event.data.buttonSelector,
                                              event.data.name,
                                              "start all Web Servers under",
                                              callback,
                                              "webServer");
    },
    stopGroupWebServers: function(event) {
        var self = this;
        var callback = function(id, buttonSelector) {
                            self.disableEnable(event.data.buttonSelector,
                                               function() { return groupControlService.stopWebServers(event.data.id)},
                                               "ui-icon-stop");
                       }

        this.verifyAndConfirmControlOperation(event.data.id,
                                              event.data.buttonSelector,
                                              event.data.name,
                                              "stop all Web Servers under",
                                              callback,
                                              "webServer");
    },

   jvmHeapDump: function(id, selector, host) {
       var requestHeapDump = function() {return jvmControlService.heapDump(id);};
       var heapDumpRequestCallback = function(response){
                                        var msg;
                                        if (response.applicationResponseContent.execData.standardError === "") {
                                            msg = response.applicationResponseContent.execData.standardOutput;
                                            msg = msg.replace("Dumping heap to", "Heap dump saved to " + host + " in ");
                                            msg = msg.replace("Heap dump file created", "");
                                        } else {
                                            msg = response.applicationResponseContent.execData.standardError;
                                        }
                                        $.alert(msg, "Heap Dump", false);
                                        $(selector).attr('title', "Last heap dump status: " + msg);
                                     };
       var heapDumpErrorHandler = function(e){
                                      var errCodeAndMsg;
                                      try {
                                          var errCode = JSON.parse(e.responseText).msgCode;
                                          var errMsg = JSON.parse(e.responseText).applicationResponseContent;
                                          errCodeAndMsg = "Error: " + errCode + (errMsg !== "" ? " - " : "") + errMsg;
                                      } catch(e) {
                                          errCodeAndMsg = e.responseText;
                                      }
                                      $.alert(errCodeAndMsg, "Heap Dump Error!", false);
                                      $(selector).attr('title', "Last heap dump status: " + errCodeAndMsg);
                                  };

       this.disableEnableHeapDumpButton(selector, requestHeapDump, heapDumpRequestCallback, heapDumpErrorHandler);
   },
    jvmGenerateJvmConfig: function(jvmId,jqClassSelector,extraData,groupId,doneCallback) {
        this.doneCallback[extraData.jvmName + "__cto" + jvmId] = doneCallback;
        ServiceFactory.getJvmService().deployJvmConfAllFiles(extraData.jvmName,
                                                         this.generateJvmConfigSucccessCallback,
                                                         this.generateJvmConfigErrorCallback);
    },
    generateJvmConfigSucccessCallback: function(response) {
        this.doneCallback[response.applicationResponseContent.jvmName + "__cto" + response.applicationResponseContent.id.id]();
         $.alert("Successfully generated and deployed JVM resource files",
                 response.applicationResponseContent.jvmName, false);
    },

    generateJvmConfigErrorCallback: function(applicationResponseContent, doneCallback) {
        this.doneCallback[applicationResponseContent.jvmName + "__cto" + applicationResponseContent.jvmId]();
        $.errorAlert(applicationResponseContent.message, "Error deploying JVM resource files", false);
    },

    confirmJvmWebServerStopGroupDialogBox: function(id, parentItemId, buttonSelector, msg,callbackOnConfirm, cancelCallback) {
        var dialogId = "start-stop-confirm-dialog-for_group" + parentItemId + "_jvm_ws_" + id;
        $(buttonSelector).parent().append("<div id='" + dialogId +"' style='text-align:left'>" + msg + "</div>");
        $(buttonSelector).parent().find("#" + dialogId).dialog({
           title: "Confirmation",
           width: "auto",
           modal: true,
           buttons: {
               "Yes": function() {
                   callbackOnConfirm(id);
                   $(this).dialog("close");
               },
               "No": function() {
                   $(this).dialog("close");
                   cancelCallback();
               }
           },
           open: function() {
               // Set focus to "No button"
               $(this).closest('.ui-dialog').find('.ui-dialog-buttonpane button:eq(1)').focus();
           }
        });
    },
    verifyAndConfirmJvmWebServerControlOperation: function(id,
                                                           parentItemId,
                                                           buttonSelector,
                                                           name,
                                                           groups,
                                                           operation,
                                                           operationCallback,
                                                           cancelCallback,
                                                           serverType) {
        if (groups.length > 1) {
            var msg = "<b>" + serverType + " <span style='color:#2a70d0'>" + name + "</span> is a member of:</b><br/>" +
                              groupOperationsHelper.groupArrayToHtmlList(groups, parentItemId)
                              + "<br/><b> Are you sure you want to " + operation + " <span style='color:#2a70d0'>" + name + "</span></b> ?";
            this.confirmJvmWebServerStopGroupDialogBox(id,
                                                       parentItemId,
                                                       buttonSelector,
                                                       msg,
                                                       operationCallback,
                                                       cancelCallback);
        } else {
            operationCallback(id);
        }
    },
    jvmStart: function(id, buttonSelector, data, parentItemId, cancelCallback) {
        this.verifyAndConfirmJvmWebServerControlOperation(id,
                                                          parentItemId,
                                                          buttonSelector,
                                                          data.jvmName,
                                                          data.groups,
                                                          "start",
                                                          jvmControlService.startJvm,
                                                          cancelCallback,
                                                          "JVM");
    },
    jvmStop: function(id, buttonSelector, data, parentItemId, cancelCallback) {
        this.verifyAndConfirmJvmWebServerControlOperation(id,
                                                          parentItemId,
                                                          buttonSelector,
                                                          data.jvmName,
                                                          data.groups,
                                                          "stop",
                                                          jvmControlService.stopJvm,
                                                          cancelCallback,
                                                          "JVM");
    },
   buildHRef: function(data) {
        return  "idp?saml_redirectUrl=" +
                window.location.protocol + "//" +
                data.hostName + ":" +
                (window.location.protocol.toUpperCase() === "HTTPS:" ? data.httpsPort : data.httpPort) + "/manager/";
   },
    jvmDiagnose: function(id, buttonSelector, data, parentItemId, cancelCallback) {
        this.verifyAndConfirmJvmWebServerControlOperation(id,
                parentItemId,
                buttonSelector,
                data.jvmName,
                data.groups,
                "diganose",
                ServiceFactory.getJvmService().diagnoseJvm,
                cancelCallback,
                "JVM");
    },
    onClickMgr: function(unused1, unused2, data) {
        var url = "idp?saml_redirectUrl=" +
                  window.location.protocol + "//" +
                  data.hostName + ":" +
                  (window.location.protocol.toUpperCase() === "HTTPS:" ? data.httpsPort : data.httpPort) + "/manager/";
        window.open(url);
    },
    onClickHealthCheck: function(unused1, unused2, data) {
        var url = window.location.protocol + "//" +
                  data.hostName +
                  ":" +
                  (window.location.protocol.toUpperCase() === "HTTPS:" ? data.httpsPort : data.httpPort) +
                  tocVars.healthCheckApp;
        window.open(url)
    },
    onClickThreadDump: function(id, unused1) {
        var url = "jvmCommand?jvmId=" + id + "&operation=threadDump";
        window.open(url)
    },

    /* web server callbacks */
    buildHRefLoadBalancerConfig: function(data) {
        return "https://" + data.host + ":" + data.httpsPort + tocVars.loadBalancerStatusMount;
    },
    webServerStart: function(id, buttonSelector, data, parentItemId, cancelCallback) {
        this.verifyAndConfirmJvmWebServerControlOperation(id,
                                                          parentItemId,
                                                          buttonSelector,
                                                          data.name,
                                                          data.groups,
                                                          "start",
                                                          webServerControlService.startWebServer,
                                                          cancelCallback,
                                                          "Web Server");
    },

    webServerStop: function(id, buttonSelector, data, parentItemId, cancelCallback) {
        this.verifyAndConfirmJvmWebServerControlOperation(id,
                                                          parentItemId,
                                                          buttonSelector,
                                                          data.name,
                                                          data.groups,
                                                          "stop",
                                                          webServerControlService.stopWebServer,
                                                          cancelCallback,
                                                          "Web Server");
    }
});

/**
 * Displays the state and an error indicator if there are any errors in the state.
 */
var StatusWidget = React.createClass({
    getInitialState: function() {
        return {status:this.props.defaultStatus, errorMessages:[], showErrorBtn:false, newErrorMsg:false};
    },
    render: function() {
        var errorBtn = null;
        if (this.state.showErrorBtn) {
           errorBtn = <FlashingButton className="ui-button-height ui-alert-border ui-state-error error-indicator-button"
                                      spanClassName="ui-icon ui-icon-alert"
                                      flashing={this.state.newErrorMsg.toString()}
                                      flashClass="flash"
                                      callback={this.showErrorMsgCallback}/>
        }

        return <div className="status-widget-container">
                   <div ref="errorDlg" className="react-dialog-container"/>
                   <span className="status-label">{this.state.status}</span>
                   {errorBtn}
               </div>;
    },
    setStatus: function(newStatus, dateTime, errorMsg) {
        var newState = {status:newStatus};

        if (errorMsg !== "") {
            newState["newErrorMsg"] = true;
            newState["showErrorBtn"] = true;
            var errMsg = groupOperationsHelper.splitErrorMsgIntoShortMsgAndStackTrace(errorMsg);
            if (this.state.errorMessages.length === 0 || this.state.errorMessages[this.state.errorMessages.length - 1].msg !== errMsg[0]) {
                this.state.errorMessages.push({dateTime:groupOperationsHelper.getCurrentDateTime(dateTime),
                                               msg:errMsg[0],
                                               pullDown:errMsg[1]});
            }
        } else {
            newState["newErrorMsg"] = false;
            newState["showErrorBtn"] = false;
        }
        this.setState(newState);
    },
    showErrorMsgCallback: function() {
        this.setState({newErrorMsg:false});
        React.render(<DialogBox title={this.props.errorMsgDlgTitle}
                                contentDivClassName="maxHeight400px"
                                content={<ErrorMsgList msgList={this.state.errorMessages}/>} />,
                     this.refs.errorDlg.getDOMNode());
    }
});

/**
 * A panel widget for web server buttons.
 */
var WebServerControlPanelWidget = React.createClass({
    doneCallback: {},
    render: function() {
        return <div className="web-server-control-panel-widget">

                    <RButton ref="stopBtn"
                            className="ui-button ui-widget ui-state-default ui-corner-all ui-button-text-only ui-button-height"
                            spanClassName="ui-icon ui-icon-stop"
                            onClick={this.webServerStop}
                            title="Stop"/>

                    <RButton ref="startBtn"
                             className="ui-button ui-widget ui-state-default ui-corner-all ui-button-text-only ui-button-height"
                             spanClassName="ui-icon ui-icon-play"
                             onClick={this.webServerStart}
                             title="Start"/>

                    <RButton className="ui-button ui-widget ui-state-default ui-corner-all ui-button-text-only ui-button-height"
                             spanClassName="ui-icon ui-icon-gear-custom"
                             onClick={this.generateHttpdConf}
                             title="Generate httpd.conf"
                             busyClassName="busy-button"/>

                    <button className="button-link anchor-font-style" onClick={this.onClickHttpdConf}>httpd.conf</button>

                    <a href={"https://" + this.props.data.host + ":" + this.props.data.httpsPort + tocVars.loadBalancerStatusMount}>status</a>

               </div>
    },

    webServerStart: function() {
        this.showFadingStatusClickedLabel("Starting...", this.refs.startBtn.getDOMNode(), this.props.data.id.id);
        this.props.webServerStartCallback(this.props.data.id.id,
                                          this.refs.stopBtn.getDOMNode(),
                                          this.props.data,
                                          WebServerControlPanelWidget.getReactId(this.refs.stopBtn.getDOMNode()).replace(/\./g, "-"),
                                          function() { /* cancel callback */ });
    },

    webServerStop: function() {
        this.showFadingStatusClickedLabel("Stopping...", this.refs.stopBtn.getDOMNode(), this.props.data.id.id);
        this.props.webServerStopCallback(this.props.data.id.id,
                                         this.refs.stopBtn.getDOMNode(),
                                         this.props.data,
                                         WebServerControlPanelWidget.getReactId(this.refs.stopBtn.getDOMNode()).replace(/\./g, "-"),
                                         function() { /* cancel callback */ });
    },

    onClickHttpdConf: function() {
        var url = "webServerCommand?webServerId=" + this.props.data.id.id + "&operation=viewHttpdConf";
        window.open(url)
    },

    generateHttpdConf: function(doneCallback) {
        this.doneCallback[this.props.data.name] = doneCallback;
        this.props.webServerService.deployHttpdConf(this.props.data.name,
                                                    this.generateHttpdConfSucccessCallback,
                                                    this.generateHttpdConfErrorCallback);
    },

    generateHttpdConfSucccessCallback: function(response) {
        this.doneCallback[response.applicationResponseContent.name]();
         $.alert("Successfully generated and deployed httpd.conf",
                 "Deploy " + this.props.data.name +  "'s httpd.conf", false);
    },

    generateHttpdConfErrorCallback: function(applicationResponseContent, doneCallback) {
        this.doneCallback[applicationResponseContent.webServerName]();
        $.errorAlert(applicationResponseContent.message, "Deploy " + this.props.data.name +  "'s httpd.conf", false);
    },

    /**
     * Uses jquery to take advantage of the fade out effect and to reuse the old code...for now.
     */
    showFadingStatusClickedLabel: function(msg, btnDom, webServerId) {
        var tooTipId = "tooltip" +  WebServerControlPanelWidget.getReactId(btnDom).replace(/\./g, "-") + webServerId;
        if (msg !== undefined && $("#" + tooTipId).length === 0) {
            var top = $(btnDom).position().top - $(btnDom).height()/2;
            var left = $(btnDom).position().left + $(btnDom).width()/2;
            $(btnDom).parent().append("<div id='" + tooTipId +
                "' role='tooltip' class='ui-tooltip ui-widget ui-corner-all ui-widget-content' " +
                "style='top:" + top + "px;left:" + left + "px'>" + msg + "</div>");

            $("#" + tooTipId).fadeOut(3000, function() {
                $("#" + tooTipId).remove();
            });

        }
    },

    statics: {
        getReactId: function(dom) {
            return $(dom).attr("data-reactid");
        }
    }

});

/**
 * Display command state.
 */
var CommandStatusWidget = React.createClass({
    getInitialState: function() {
        return {xBtnHover: false};
    },
    render: function() {
        var self = this;
        var statusRows = [];
        this.props.statusDetails.forEach(function(status) {
            var errMsg = groupOperationsHelper.splitErrorMsgIntoShortMsgAndStackTrace(status.message);
            if (errMsg[1]) {
                statusRows.push(<tr><td className="command-status-td">{status.from}</td>
                                                    <td className="command-status-td">{moment(status.asOf).format("MM/DD/YYYY hh:mm:ss")}</td>
                                                    <td className="command-status-td" style={{textDecoration: "underline", cursor: "pointer"}} onClick={self.showDetails.bind(this, errMsg[1])}>{errMsg[0]}</td></tr>);
            } else {
                statusRows.push(<tr><td className="command-status-td">{status.from}</td>
                                    <td className="command-status-td">{moment(status.asOf).format("MM/DD/YYYY hh:mm:ss")}</td>
                                    <td className="command-status-td">{errMsg[0]}</td></tr>);
            }
        });

        var xBtnHoverClass = this.state.xBtnHover ? "hover" : "";
        return  <div className="ui-dialog ui-widget ui-widget-content ui-front command-status-container">
                    <div className="ui-dialog-titlebar ui-widget-header ui-helper-clearfix command-status-header">
                        <span className="ui-dialog-title">Error</span>
                        <span ref="xBtn" className={"command-status-close-btn " + xBtnHoverClass}
                            onClick={this.onXBtnClick} onMouseOver={this.onXBtnMouseOver} onMouseOut={this.onXBtnMouseOut}
                            title="Closing the error message window will also clear the error message list related to this group."/>
                    </div>
                    <div className="ui-dialog-content ui-widget-content command-status-background command-status-content">
                        <table>
                            {statusRows}
                        </table>
                    </div>
                </div>;

    },
    showDetails: function(msg) {
        var myWindow = window.open("", "Error Details", "width=500, height=500");
        myWindow.document.write(msg);
    },
    onXBtnClick: function() {
        this.props.closeCallback();
    },
    onXBtnMouseOver: function() {
        this.setState({xBtnHover: true});
    },
    onXBtnMouseOut: function() {
        this.setState({xBtnHover: false});
    }
});
