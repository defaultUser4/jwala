# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

#
# List of comma-separated packages that start with or equal this string
# will cause a security exception to be thrown when
# passed to checkPackageAccess unless the
# corresponding RuntimePermission ("accessClassInPackage."+package) has
# been granted.
package.access=sun.,org.apache.catalina.,org.apache.coyote.,org.apache.tomcat.,org.apache.jasper.
#
# List of comma-separated packages that start with or equal this string
# will cause a security exception to be thrown when
# passed to checkPackageDefinition unless the
# corresponding RuntimePermission ("defineClassInPackage."+package) has
# been granted.
#
# by default, no packages are restricted for definition, and none of
# the class loaders supplied with the JDK call checkPackageDefinition.
#
package.definition=sun.,java.,org.apache.catalina.,org.apache.coyote.,org.apache.tomcat.,org.apache.jasper.

#
#
# List of comma-separated paths defining the contents of the "common"
# classloader. Prefixes should be used to define what is the repository type.
# Path may be relative to the CATALINA_HOME or CATALINA_BASE path or absolute.
# If left as blank,the JVM system loader will be used as Catalina's "common"
# loader.
# Examples:
#     "foo": Add this folder as a class repository
#     "foo/*.jar": Add all the JARs of the specified folder as class
#                  repositories
#     "foo/bar.jar": Add bar.jar as a class repository
common.loader=\${catalina.base}/lib/*.jar,\${catalina.home}/lib/*.jar,/opt/ctp/app/lib/ctp_platform_tc7-1.2.20/conf,/opt/ctp/app/lib/ctp_platform_tc7-1.2.20/cerner/*.jar,/opt/ctp/app/lib/ctp_platform_tc7-1.2.20/ext/*.jar,/opt/ctp/app/lib/ctp_platform_tc7-1.2.20/ext/*.jar

#
# List of comma-separated paths defining the contents of the "server"
# classloader. Prefixes should be used to define what is the repository type.
# Path may be relative to the CATALINA_HOME or CATALINA_BASE path or absolute.
# If left as blank, the "common" loader will be used as Catalina's "server"
# loader.
# Examples:
#     "foo": Add this folder as a class repository
#     "foo/*.jar": Add all the JARs of the specified folder as class
#                  repositories
#     "foo/bar.jar": Add bar.jar as a class repository
server.loader=

#
# List of comma-separated paths defining the contents of the "shared"
# classloader. Prefixes should be used to define what is the repository type.
# Path may be relative to the CATALINA_BASE path or absolute. If left as blank,
# the "common" loader will be used as Catalina's "shared" loader.
# Examples:
#     "foo": Add this folder as a class repository
#     "foo/*.jar": Add all the JARs of the specified folder as class
#                  repositories
#     "foo/bar.jar": Add bar.jar as a class repository
# Please note that for single jars, e.g. bar.jar, you need the URL form
# starting with file:.
shared.loader=

# List of JAR files that should not be scanned using the JarScanner
# functionality. This is typically used to scan JARs for configuration
# information. JARs that do not contain such information may be excluded from
# the scan to speed up the scanning process. This is the default list. JARs on
# this list are excluded from all scans. Scan specific lists (to exclude JARs
# from individual scans) follow this. The list must be a comma separated list of
# JAR file names.
# The JARs listed below include:
# - Tomcat Bootstrap JARs
# - Tomcat API JARs
# - Catalina JARs
# - Jasper JARs
# - Tomcat JARs
# - Common non-Tomcat JARs
tomcat.util.scan.DefaultJarScanner.jarsToSkip=\
bootstrap.jar,commons-daemon.jar,tomcat-juli.jar,\
annotations-api.jar,el-api.jar,jsp-api.jar,servlet-api.jar,websocket-api.jar,\
catalina.jar,catalina-ant.jar,catalina-ha.jar,catalina-tribes.jar,\
jasper.jar,jasper-el.jar,ecj-*.jar,\
tomcat-api.jar,tomcat-util.jar,tomcat-coyote.jar,tomcat-dbcp.jar,\
tomcat-jni.jar,tomcat-spdy.jar,\
tomcat-i18n-en.jar,tomcat-i18n-es.jar,tomcat-i18n-fr.jar,tomcat-i18n-ja.jar,\
tomcat-juli-adapters.jar,catalina-jmx-remote.jar,catalina-ws.jar,\
tomcat-jdbc.jar,\
tools.jar,\
commons-beanutils*.jar,commons-codec*.jar,commons-collections*.jar,\
commons-dbcp*.jar,commons-digester*.jar,commons-fileupload*.jar,\
commons-httpclient*.jar,commons-io*.jar,commons-lang*.jar,commons-logging*.jar,\
commons-math*.jar,commons-pool*.jar,\
jstl.jar,\
geronimo-spec-jaxrpc*.jar,wsdl4j*.jar,\
ant.jar,ant-junit*.jar,aspectj*.jar,jmx.jar,h2*.jar,hibernate*.jar,httpclient*.jar,\
jmx-tools.jar,jta*.jar,log4j.jar,log4j-1*.jar,mail*.jar,slf4j*.jar,\
xercesImpl.jar,xmlParserAPIs.jar,xml-apis.jar,\
junit.jar,junit-*.jar,hamcrest*.jar,org.hamcrest*.jar,ant-launcher.jar

# Additional JARs (over and above the default JARs listed above) to skip when
# scanning for Servlet 3.0 pluggability features. These features include web
# fragments, annotations, SCIs and classes that match @HandlesTypes. The list
# must be a comma separated list of JAR file names.
org.apache.catalina.startup.ContextConfig.jarsToSkip=

# Additional JARs (over and above the default JARs listed above) to skip when
# scanning for TLDs. The list must be a comma separated list of JAR file names.
# Updated with Tomcat 8 file (now lists the tomcat7 and tomcat8 files)
org.apache.catalina.startup.TldConfig.jarsToSkip=tomcat7-websocket.jar,tomcat-websocket.jar

#
# String cache configuration.
tomcat.util.buf.StringCache.byte.enabled=true
#tomcat.util.buf.StringCache.char.enabled=true
#tomcat.util.buf.StringCache.trainThreshold=500000
#tomcat.util.buf.StringCache.cacheSize=5000

org.apache.tomcat.util.digester.PROPERTY_SOURCE=com.siemens.cto.infrastructure.properties.StpPropertySource
