/*
   Configure Yet Another Docker clouds provided by the Yet Another Docker
   Plugin.  This Jenkins Script Console script makes it easy to maintain large
   amounts of configured clouds.

   Note: This script deletes all yet another docker cloud configurations from
   Jenkins before configuring the clouds.  It does not affect in-process
   builds.  However, if there are previously configured yet another docker
   clouds then they will be removed.

   Yet Another Docker Plugin 0.1.0-rc31
 */

import com.github.kostyasha.yad.DockerCloud
import com.github.kostyasha.yad.DockerConnector
import com.github.kostyasha.yad.DockerContainerLifecycle
import com.github.kostyasha.yad.DockerSlaveTemplate
import com.github.kostyasha.yad.commons.DockerCreateContainer
import com.github.kostyasha.yad.commons.DockerImagePullStrategy
import com.github.kostyasha.yad.commons.DockerPullImage
import com.github.kostyasha.yad.commons.DockerRemoveContainer
import com.github.kostyasha.yad.commons.DockerStopContainer
import com.github.kostyasha.yad.launcher.DockerComputerJNLPLauncher
import com.github.kostyasha.yad.launcher.DockerComputerLauncher
import com.github.kostyasha.yad.launcher.DockerComputerSSHLauncher
import com.github.kostyasha.yad.other.ConnectorType
import com.github.kostyasha.yad.strategy.DockerOnceRetentionStrategy

import hudson.model.Node
import hudson.plugins.sshslaves.SSHConnector
import hudson.slaves.EnvironmentVariablesNodeProperty
import hudson.slaves.NodeProperty
import hudson.slaves.RetentionStrategy
import hudson.tools.ToolLocationNodeProperty
import jenkins.model.Jenkins
import net.sf.json.JSONArray
import net.sf.json.JSONObject

/*
   Configure the Yet Another Docker Plugin via this clouds_yadocker variable.

   This variable can be removed and referenced for other configurations.  All
   values are optional with defaults set.
 */

JSONArray clouds_yadocker = [
    //YET ANOTHER DOCKER CLOUD (this is an item in a list of clouds)
    [
        cloud_name: 'docker-local',
        docker_url: 'tcp://localhost:2375',
        docker_api_version: '',
        host_credentials_id: '',
        //valid values: netty, jersey
        connection_type: 'netty',
        connect_timeout: 0,
        max_containers: 50,
        docker_templates: [
            //DOCKER TEMPLATE (this is an item in a list of templates)
            [
                max_instances: 10,
                //DOCKER CONTAINER LIFECYCLE
                docker_image_name: '',
                //PULL IMAGE SETTINGS
                //valid values: pull_latest, pull_always, pull_once, pull_never
                pull_strategy: 'pull_latest',
                pull_registry_credentials_id: '',
                //CREATE CONTAINER SETTINGS
                docker_command: '',
                hostname: '',
                dns: '',
                //volumes can be a string or list of strings
                volumes: '',
                //volumes_from can be a string or list of strings
                volumes_from: '',
                //environment can be a string or list of strings
                environment: '',
                port_bindings: '',
                bind_all_declared_ports: false,
                //0 is unlimited
                memory_limit_in_mb: 0,
                //0 is unlimited
                cpu_shares: 0,
                run_container_privileged: false,
                allocate_pseudo_tty: false,
                mac_address: '',
                //extra_hosts can be a string or list of strings
                extra_hosts: '',
                network_mode: '',
                //devices can be a string or list of strings
                devices: '',
                cpuset_constraint_cpus: '',
                cpuset_constraint_mems: '',
                //links can be a string or list of strings
                links: '',
                //STOP CONTAINER SETTINGS
                stop_container_timeout: 10,
                //STOP CONTAINER SETTINGS
                remove_volumes: false,
                force_remove_containers: false,
                //JENKINS SLAVE CONFIG
                remote_fs_root: '/home/jenkins',
                labels: 'docker',
                //valid values: exclusive or normal
                usage: 'exclusive',
                availability_strategy: 'docker_once_retention_strategy',
                availability_idle_timeout: 10,
                executors: 1,
                //LAUNCH METHOD
                //valid values: launch_ssh or launch_jnlp
                launch_method: 'launch_jnlp',
                //settings specific to launch_ssh (you only need one or the other)
                launch_ssh_credentials_id: '',
                launch_ssh_port: 22,
                launch_ssh_java_path: '',
                launch_ssh_jvm_options: '',
                launch_ssh_prefix_start_slave_command: '',
                launch_ssh_suffix_start_slave_command: '',
                launch_ssh_connection_timeout: 120,
                launch_ssh_max_num_retries: 10,
                launch_ssh_time_wait_between_retries: 10,
                //settings specific to launch_jnlp
                launch_jnlp_linux_user: 'jenkins',
                launch_jnlp_lauch_timeout: 120,
                launch_jnlp_slave_jar_options: '',
                launch_jnlp_slave_jvm_options: '',
                launch_jnlp_different_jenkins_master_url: '',
                launch_jnlp_ignore_certificate_check: false,
                //NODE PROPERTIES
                //environment_variables is a HashMap of key/value pairs
                environment_variables: [:],
                //tool location key/value pairs from https://github.com/jenkinsci/jenkins/blob/master/core/src/main/java/hudson/tools/ToolLocationNodeProperty.java
                //The key is type@name = home where type is typically the class name of the tool and name is the name given in the Global Tools configuration.
                //For example let's say you have a global tool configuration named OracleJDK8 for JDK installations
                //tool_locations would be something like ['hudson.model.JDK$DescriptorImpl@OracleJDK8': '/path/to/java_home']
                //If you're unsure of the tool@name then check config.xml where YADocker configurations are saved.
                tool_locations: [:],
                remote_fs_root_mapping: ''
            ]
        ]

    ]
] as JSONArray

//detect an existing global tool
def detectGlobalToolExists(String location) {
    def (toolType, toolName) = location.split('@')
    boolean found_installation = Jenkins.instance.getExtensionList(toolType)[0].installations.findAll { it.name == toolName } as boolean
    return found_installation
}

//return a launcher
def selectLauncher(String launcherType, JSONObject obj) {
    switch(launcherType) {
        case 'launch_ssh':
            SSHConnector sshConnector = new SSHConnector(obj.optInt('launch_ssh_port', 22),
                    obj.optString('launch_ssh_credentials_id'),
                    obj.optString('launch_ssh_jvm_options'),
                    obj.optString('launch_ssh_java_path'),
                    obj.optString('launch_ssh_prefix_start_slave_command'),
                    obj.optString('launch_ssh_suffix_start_slave_command'),
                    obj.optInt('launch_ssh_connection_timeout'),
                    obj.optInt('launch_ssh_max_num_retries'),
                    obj.optInt('launch_ssh_time_wait_between_retries')
                    )
            return new DockerComputerSSHLauncher(sshConnector)
        case 'launch_jnlp':
            DockerComputerJNLPLauncher dockerComputerJNLPLauncher = new DockerComputerJNLPLauncher()
            dockerComputerJNLPLauncher.setUser(obj.optString('launch_jnlp_linux_user','jenkins'))
            dockerComputerJNLPLauncher.setLaunchTimeout(obj.optLong('launch_jnlp_lauch_timeout', 120L))
            dockerComputerJNLPLauncher.setSlaveOpts(obj.optString('launch_jnlp_slave_jar_options'))
            dockerComputerJNLPLauncher.setJvmOpts(obj.optString('launch_jnlp_slave_jvm_options'))
            dockerComputerJNLPLauncher.setJenkinsUrl(obj.optString('launch_jnlp_different_jenkins_master_url'))
            dockerComputerJNLPLauncher.setNoCertificateCheck(obj.optBoolean('launch_jnlp_ignore_certificate_check', false))
            return dockerComputerJNLPLauncher
        default:
            return null
    }
}

//create a new instance of the DockerCloud class from a JSONObject
def newDockerCloud(JSONObject obj) {
    DockerConnector connector = new DockerConnector(obj.optString('docker_url', 'tcp://localhost:2375'))
    if(obj.optInt('connect_timeout')) {
        connector.setConnectTimeout(obj.optInt('connect_timeout'))
    }
    connector.setApiVersion(obj.optString('docker_api_version'))
    connector.setCredentialsId(obj.optString('host_credentials_id'))
    //select connection_type
    List<String> connection_types = ['NETTY', 'JERSEY']
    String connection_type_default = 'NETTY'
    String user_selected_connection_type = obj.optString('connection_type', connection_type_default).toUpperCase()
    String connection_type = (user_selected_connection_type in connection_types)? user_selected_connection_type : connection_type_default
    connector.setConnectorType(ConnectorType."${connection_type}")

    DockerCloud cloud = new DockerCloud(obj.optString('cloud_name'),
                       bindJSONToList(DockerSlaveTemplate.class, obj.opt('docker_templates')),
                       obj.optInt('max_containers', 50),
                       connector)

    return cloud
}

def newDockerSlaveTemplate(JSONObject obj) {
    //DockerPullImage
    DockerPullImage pullImage = new DockerPullImage()
    String pullStrategy = obj.optString('pull_strategy', 'PULL_LATEST').toUpperCase()
    if(pullStrategy in ['PULL_LATEST', 'PULL_ALWAYS', 'PULL_ONCE', 'PULL_NEVER']) {
        pullImage.setPullStrategy(DockerImagePullStrategy."${pullStrategy}")
    } else {
        pullImage.setPullStrategy(DockerImagePullStrategy.PULL_LATEST)
    }
    pullImage.setCredentialsId(obj.optString('pull_registry_credentials_id'))

    //DockerCreateContainer
    DockerCreateContainer createContainer = new DockerCreateContainer()
    createContainer.setBindPorts(obj.optString('port_bindings'))
    createContainer.setBindAllPorts(obj.optBoolean('bind_all_declared_ports', false))
    createContainer.setDnsString(obj.optString('dns'))
    createContainer.setHostname(obj.optString('hostname'))
    if(obj.optLong('memory_limit_in_mb')) {
        createContainer.setMemoryLimit(obj.optLong('memory_limit_in_mb'))
    }
    createContainer.setPrivileged(obj.optBoolean('run_container_privileged', false))
    createContainer.setTty(obj.optBoolean('allocate_pseudo_tty', false))
    if(obj.optJSONArray('volumes')) {
        createContainer.setVolumes(obj.optJSONArray('volumes') as List<String>)
    } else {
        createContainer.setVolumesString(obj.optString('volumes'))
    }
    if(obj.optJSONArray('volumes_from')) {
        createContainer.setVolumesFrom(obj.optJSONArray('volumes_from') as List<String>)
    } else {
        createContainer.setVolumesFromString(obj.optString('volumes_from'))
    }
    createContainer.setMacAddress(obj.optString('mac_address'))
    if(obj.optInt('cpu_shares')) {
        createContainer.setCpuShares(obj.optInt('cpu_shares'))
    }
    createContainer.setCommand(obj.optString('docker_command'))
    if(obj.optJSONArray('environment')) {
        createContainer.setEnvironment(obj.optJSONArray('environment') as List<String>)
    } else {
        createContainer.setEnvironmentString(obj.optString('environment'))
    }
    if(obj.optJSONArray('extra_hosts')) {
        createContainer.setExtraHosts(obj.optJSONArray('extra_hosts') as List<String>)
    } else {
        createContainer.setExtraHostsString(obj.optString('extra_hosts'))
    }
    createContainer.setNetworkMode(obj.optString('network_mode'))
    if(obj.optJSONArray('devices')) {
        createContainer.setDevices(obj.optJSONArray('devices'))
    } else {
        createContainer.setDevicesString(obj.optString('devices'))
    }
    createContainer.setCpusetCpus(obj.optString('cpuset_constraint_cpus'))
    createContainer.setCpusetMems(obj.optString('cpuset_constraint_mems'))
    if(obj.optJSONArray('links')) {
        createContainer.setLinks(obj.optJSONArray('links'))
    } else {
        createContainer.setLinksString(obj.optString('links'))
    }

    //DockerStopContainer
    DockerStopContainer stopContainer = new DockerStopContainer()
    stopContainer.setTimeout(obj.optInt('stop_container_timeout', 10))

    //DockerRemoveContainer
    DockerRemoveContainer removeContainer = new DockerRemoveContainer()
    removeContainer.setRemoveVolumes(obj.optBoolean('remove_volumes', false))
    removeContainer.setForce(obj.optBoolean('force_remove_containers', false))

    //DockerContainerLifecycle
    DockerContainerLifecycle dockerContainerLifecycle = new DockerContainerLifecycle()
    dockerContainerLifecycle.setImage(obj.optString('docker_image_name'))
    dockerContainerLifecycle.setPullImage(pullImage)
    dockerContainerLifecycle.setCreateContainer(createContainer)
    dockerContainerLifecycle.setStopContainer(stopContainer)
    dockerContainerLifecycle.setRemoveContainer(removeContainer)

    //DockerOnceRetentionStrategy
    //this availability_strategy is for "run_once".  We can customize it later
    RetentionStrategy retentionStrategy = new DockerOnceRetentionStrategy(obj.optInt('availability_idle_timeout', 10))

    //DockerComputerLauncher
    //select a launch method from the list of available launch methods
    List<String> launch_methods = ['launch_ssh', 'launch_jnlp']
    String default_launch_method = 'launch_jnlp'
    String user_selected_launch_method = obj.optString('launch_method', default_launch_method).toLowerCase()
    String launch_method = (user_selected_launch_method in launch_methods)? user_selected_launch_method : default_launch_method
    DockerComputerLauncher launcher = selectLauncher(launch_method, obj)

    //DockerSlaveTemplate
    DockerSlaveTemplate dockerSlaveTemplate = new DockerSlaveTemplate()
    dockerSlaveTemplate.setDockerContainerLifecycle(dockerContainerLifecycle)
    dockerSlaveTemplate.setLabelString(obj.optString('labels', 'docker'))
    String node_usage = (obj.optString('usage', 'EXCLUSIVE').toUpperCase().equals('NORMAL'))? 'NORMAL' : 'EXCLUSIVE'
    dockerSlaveTemplate.setMode(Node.Mode."${node_usage}")
    dockerSlaveTemplate.setNumExecutors(obj.optInt('executors', 1))
    dockerSlaveTemplate.setRetentionStrategy(retentionStrategy)
    dockerSlaveTemplate.setLauncher(launcher)
    dockerSlaveTemplate.setRemoteFs(obj.optString('remote_fs_root', '/srv/jenkins'))
    dockerSlaveTemplate.setMaxCapacity(obj.optInt('max_instances', 10))
    //dockerSlaveTemplate.setRemoteFsMapping(obj.optString('remote_fs_root_mapping'))
    dockerSlaveTemplate.remoteFsMapping = obj.optString('remote_fs_root_mapping')
    //define NODE PROPERTIES
    List<NodeProperty> nodeProperties = [] as List<NodeProperty>
    if(obj.optJSONObject('environment_variables')) {
        HashMap<String,String> env = obj.optJSONObject('environment_variables') as HashMap<String,String>
        List<EnvironmentVariablesNodeProperty.Entry> envEntries = [] as List<EnvironmentVariablesNodeProperty.Entry>
        (env.keySet() as String[]).each { var ->
            envEntries << (new EnvironmentVariablesNodeProperty.Entry(var, env[var]))
        }
        //add environment_variables to nodeProperties
        nodeProperties << (new EnvironmentVariablesNodeProperty(envEntries))
    }
    if(obj.optJSONObject('tool_locations')) {
        HashMap<String,String> tool = obj.optJSONObject('tool_locations') as HashMap<String,String>
        List<ToolLocationNodeProperty.ToolLocation> toolLocations = [] as List<ToolLocationNodeProperty.ToolLocation>
        (tool.keySet() as String[]).each { location ->
            //tool location is only valid if it contains a type i.e. @ symbol
            if(location.contains('@') && detectGlobalToolExists(location)) {
                toolLocations << (new ToolLocationNodeProperty.ToolLocation(location, tool[location]))
            } else {
                //alert the user they configured an invalid tool type or name in tool_locations
                println "WARNING: Invalid tool: '${location}'.  Format should be 'type@name' where both type and name already exist in global tool configurations."
            }
        }
        nodeProperties << (new ToolLocationNodeProperty(toolLocations))
    }
    //set NODE PROPERTIES
    if(nodeProperties) {
        dockerSlaveTemplate.setNodeProperties(nodeProperties)
    }
    return dockerSlaveTemplate
}

def bindJSONToList(Class type, Object src) {
    if(!(type == DockerCloud) && !(type == DockerSlaveTemplate)) {
        throw new Exception("Must use DockerCloud or DockerSlaveTemplate class.")
    }
    //docker_array should be a DockerCloud or DockerSlaveTemplate
    ArrayList<?> docker_array
    if(type == DockerCloud){
        docker_array = new ArrayList<DockerCloud>()
    } else {
        docker_array = new ArrayList<DockerSlaveTemplate>()
    }
    //cast the configuration object to a Docker instance which Jenkins will use in configuration
    if (src instanceof JSONObject) {
        //uses string interpolation to call a method
        //e.g instead of newDockerCloud(src) we use instead...
        docker_array.add("new${type.getSimpleName()}"(src))
    } else if(src instanceof JSONArray) {
        for (Object o : src) {
            if (o instanceof JSONObject) {
                docker_array.add("new${type.getSimpleName()}"(o))
            }
        }
    }
    return docker_array
}

if(!Jenkins.instance.isQuietingDown()) {
    ArrayList<DockerCloud> clouds = new ArrayList<DockerCloud>()
    clouds = bindJSONToList(DockerCloud.class, clouds_yadocker)
    if(clouds.size() > 0) {
        dockerConfigUpdated = true
        Jenkins.instance.clouds.removeAll(DockerCloud)
        Jenkins.instance.clouds.addAll(clouds)
        clouds*.name.each { cloudName ->
            println "Configured Yet Another Docker cloud ${cloudName}"
        }
        Jenkins.instance.save()
    } else {
        println 'Nothing changed.  No Yet Another docker clouds to configure.'
    }
} else {
    println 'Shutdown mode enabled.  Configure Yet Another Docker clouds SKIPPED.'
}

//return null so there's no result value in the script console
null

/*
   The MIT License (MIT)

   Copyright 2017 Sam Gleske - https://github.com/samrocketman/jenkins-bootstrap-jervis

   Permission is hereby granted, free of charge, to any person obtaining a copy
   of this software and associated documentation files (the "Software"), to
   deal in the Software without restriction, including without limitation the
   rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
   sell copies of the Software, and to permit persons to whom the Software is
   furnished to do so, subject to the following conditions:

   The above copyright notice and this permission notice shall be included in
   all copies or substantial portions of the Software.

   THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
   IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
   FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
   AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
   LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
   FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS
   IN THE SOFTWARE.
 */
