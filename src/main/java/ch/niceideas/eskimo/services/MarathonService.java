package ch.niceideas.eskimo.services;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.*;
import ch.niceideas.eskimo.model.*;
import ch.niceideas.eskimo.proxy.ProxyManagerService;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class MarathonService {

    private static final Logger logger = Logger.getLogger(ServicesConfigService.class);

    public static final String MARATHON_NODE = "MARATHON_NODE";

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private SystemService systemService;

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private SSHCommandService sshCommandService;

    @Autowired
    private SystemOperationService systemOperationService;

    @Autowired
    private ProxyManagerService proxyManagerService;

    @Autowired
    private MemoryComputer memoryComputer;

    @Autowired
    private MessagingService messagingService;

    @Autowired
    private NotificationService notificationService;

    @Value("${system.packageDistributionPath}")
    private String packageDistributionPath = "./packages_distrib";

    @Value("${system.servicesSetupPath}")
    private String servicesSetupPath = "./services_setup";

    @Value("${system.parallelismInstallThreadCount}")
    private int parallelismInstallThreadCount = 10;

    @Value("${system.operationWaitTimoutSeconds}")
    private int operationWaitTimout = 800;

    @Value("${system.baseInstallWaitTimoutSeconds}")
    private int baseInstallWaitTimout = 1000;

    private HttpClient httpClient;

    /* For tests */
    void setServicesDefinition(ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }
    void setConfigurationService (ConfigurationService configurationService) {
        this.configurationService = configurationService;
    }
    void setSystemService(SystemService systemService) {
        this.systemService = systemService;
    }
    void setSshCommandService(SSHCommandService sshCommandService) {
        this.sshCommandService = sshCommandService;
    }
    void setSystemOperationService(SystemOperationService systemOperationService) {
        this.systemOperationService = systemOperationService;
    }
    void setProxyManagerService(ProxyManagerService proxyManagerService) {
        this.proxyManagerService = proxyManagerService;
    }
    void setMemoryComputer(MemoryComputer memoryComputer) {
        this.memoryComputer = memoryComputer;
    }
    void setMessagingService(MessagingService messagingService) {
        this.messagingService = messagingService;
    }
    void setNotificationService(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    public MarathonService() {
        HttpClientBuilder clientBuilder = HttpClientBuilder.create()
                .setDefaultRequestConfig(buildRequestConfig())
                .setDefaultSocketConfig(buildSocketConfig());

        clientBuilder.setMaxConnTotal(2);

        httpClient = clientBuilder.build();
    }

    protected RequestConfig buildRequestConfig() {
        return RequestConfig.custom()
                .setRedirectsEnabled(true)
                .setCookieSpec(CookieSpecs.IGNORE_COOKIES) // we handle them in the servlet instead
                .setConnectTimeout(10000)
                .setSocketTimeout(20000)
                .setConnectionRequestTimeout(10000)
                .build();
    }

    protected SocketConfig buildSocketConfig() {
        return SocketConfig.custom()
                .setSoTimeout(20000)
                .build();
    }

    protected String queryMarathon (String endpoint) throws MarathonException {
        return queryMarathon(endpoint, "GET");
    }

    protected String queryMarathon (String endpoint, String method) throws MarathonException {

        try {
            ProxyTunnelConfig marathonTunnelConfig = proxyManagerService.getTunnelConfig("marathon");
            if (marathonTunnelConfig == null) {
                return null;
            }

            // apps/cerebro
            BasicHttpRequest request = new BasicHttpRequest(method, "http://localhost:" + marathonTunnelConfig.getLocalPort() + "/v2/" + endpoint);

            return sendHttpRequestAndGetResult(marathonTunnelConfig, request);

        } catch (IOException e) {
            logger.error (e, e);
            throw new MarathonException(e);
        }
    }

    protected String updateMarathon (String endpoint, String method, String content) throws MarathonException {

        try {
            ProxyTunnelConfig marathonTunnelConfig = proxyManagerService.getTunnelConfig("marathon");

            // apps/cerebro
            BasicHttpEntityEnclosingRequest request = new BasicHttpEntityEnclosingRequest(method, "http://localhost:" + marathonTunnelConfig.getLocalPort() + "/v2/" + endpoint);
            //request.setHeader("Content-Type", "application/json");

            BasicHttpEntity requestContent = new BasicHttpEntity();
            requestContent.setContentType("application/json");
            requestContent.setContent(new ByteArrayInputStream(content.getBytes("UTF-8")));
            request.setEntity(requestContent);

            return sendHttpRequestAndGetResult(marathonTunnelConfig, request);

        } catch (IOException e) {
            logger.error (e, e);
            throw new MarathonException(e);
        }
    }

    private String sendHttpRequestAndGetResult(ProxyTunnelConfig marathonTunnelConfig, BasicHttpRequest request) throws IOException {
        HttpResponse response = httpClient.execute(
                new HttpHost("localhost", marathonTunnelConfig.getLocalPort(), "http"),
                request);

        InputStream result = response.getEntity().getContent();

        Header contentencodingHeader = response.getEntity().getContentEncoding();

        return StreamUtils.getAsString(result, contentencodingHeader != null ? contentencodingHeader.getValue() : "UTF-8");
    }

    private Pair<String,String> getServiceRuntimeNode(String service) throws MarathonException, FileException, SetupException  {
        return getAndWaitServiceRuntimeNode(service, 1);
    }

    private Pair<String, String> getAndWaitServiceRuntimeNode (String service,int numberOfAttempts) throws
            MarathonException  {

        for (int i = 0; i < numberOfAttempts; i++) {
            String serviceJson = queryMarathon("apps/" + service);
            if (StringUtils.isBlank(serviceJson)) {
                return new Pair<>(null, "NA");
            }

            JsonWrapper serviceResult = new JsonWrapper(serviceJson);

            if (StringUtils.isNotBlank(serviceResult.getValueForPathAsString("message"))
                    && serviceResult.getValueForPathAsString("message").contains("does not exist")) {
                return new Pair<>(null, "NA");
            }

            String nodeIp = serviceResult.getValueForPathAsString("app.tasks.0.host");
            if (StringUtils.isBlank(nodeIp) && i < numberOfAttempts - 1) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    logger.debug (e, e);
                }
                continue;
            }

            if (StringUtils.isBlank(nodeIp)) {

                // service is not started by marathon
                // need to find the previous nodes that was running it
                try {
                    nodeIp = findUniqueServiceIP("marathon");
                } catch (FileException | SetupException e) {
                    logger.error (e.getMessage());
                    logger.debug (e, e);
                }
            }

            String status = "notOK";

            //Integer tasksUnhealthy = (Integer) serviceResult.getValueForPath("app.tasksUnhealthy");

            Integer tasksRunning = (Integer) serviceResult.getValueForPath("app.tasksRunning");
            if (tasksRunning != null && tasksRunning.intValue() == 1) {
                status = "running";
            }

            return new Pair<>(nodeIp, status);
        }

        return new Pair<>(null, "notOK");
    }

    public void applyMarathonServicesConfig(MarathonOperationsCommand command) throws SystemException {

        logger.info ("Starting Marathon Deployment Operations");
        boolean success = false;
        systemService.setProcessingPending();
        try {

            // Find out node running marathon

            // TODO

            String marathonIpAddress = findUniqueServiceIP("marathon");
            if (StringUtils.isBlank(marathonIpAddress)) {
                throw new SystemException("Marathon doesn't seem to be installed");
            }

            Set<String> liveIps = new HashSet<>();
            Set<String> deadIps = new HashSet<>();

            // handle potential interruption request
            if (systemService.isInterrupted()) {
                return;
            }

            NodesConfigWrapper nodesConfig = configurationService.loadNodesConfig();

            List<Pair<String, String>> nodesSetup = systemService.buildDeadIps(new HashSet<String>(){{add(marathonIpAddress);}}, nodesConfig, liveIps, deadIps);

            if (deadIps.contains(marathonIpAddress)) {
                String message = "The marathon node is dead. cannot proceed any further with installation";
                notificationService.addError(message);
                messagingService.addLines(message);
                throw new SystemException(message);
            }

            if (systemService.isInterrupted()) {
                return;
            }

            if (nodesSetup == null) {
                return;
            }

            MemoryModel memoryModel = memoryComputer.buildMemoryModel(nodesConfig, deadIps);

            if (systemService.isInterrupted()) {
                return;
            }

            // Nodes re-setup (topology)
            systemService.performPooledOperation (new ArrayList<String> (liveIps), parallelismInstallThreadCount, baseInstallWaitTimout,
                    (operation, error) -> {
                        // topology
                        if (error.get() == null) {
                            systemOperationService.applySystemOperation("Installation of Topology and settings on " + operation,
                                    builder -> systemService.installTopologyAndSettings(nodesConfig, command.getRawConfig(), memoryModel, operation, deadIps), null);
                        }

                    });

            // Installation in batches (groups following dependencies)

            // TODO deploying on marathon 1 service at a time for now
            systemService.performPooledOperation(command.getInstallations(), 1, operationWaitTimout,
                    (operation, error) -> {
                        installMarathonService(operation, marathonIpAddress);
                    });

            // uninstallations
            // TODO deploying on marathon 1 service at a time for now
            systemService.performPooledOperation(command.getUninstallations(), 1, operationWaitTimout,
                    (operation, error) -> {
                        uninstallMarathonService(operation, marathonIpAddress);
                    });

            /*
            // restarts
            for (List<Pair<String, String>> restarts : servicesInstallationSorter.orderOperations (command.getRestarts(), nodesConfig, deadIps)) {
                performPooledOperation(restarts, parallelismInstallThreadCount, operationWaitTimout,
                        (operation, error) -> {
                            String service = operation.getKey();
                            String ipAddress = operation.getValue();
                            if (liveIps.contains(ipAddress)) {
                                restartServiceForSystem(service, ipAddress);
                            }
                        });
            }
            */

            success = true;
        } catch (FileException | SetupException e) {
            logger.error (e, e);
            throw new SystemException(e);
        } finally {
            systemService.setLastOperationSuccess (success);
            systemService.releaseProcessingPending();
            logger.info ("Marathon Deployment Operations Completed.");
        }
    }

    String findUniqueServiceIP(String service) throws FileException, SetupException {

        String uniqueServiceNodeName = findUniqueServiceNodeName(service);
        if (StringUtils.isBlank(uniqueServiceNodeName)) {
            return null;
        }

        return uniqueServiceNodeName.replace("-", ".");
    }

    private String findUniqueServiceNodeName(String service) throws FileException, SetupException {

        ServicesInstallStatusWrapper installStatus = configurationService.loadServicesInstallationStatus();

        for (String installFlag : installStatus.getRootKeys()) {

            if (installFlag.startsWith(service)
                    && installStatus.getValueForPathAsString(installFlag) != null
                    && installStatus.getValueForPathAsString(installFlag).equals("OK")) {

                String ipAddress = installFlag.substring(installFlag.indexOf(OperationsCommand.INSTALLED_ON_IP_FLAG) + OperationsCommand.INSTALLED_ON_IP_FLAG.length());
                return ipAddress.replace(".", "-");
            }
        }

        return null;
    }

    void uninstallMarathonService(String service, String marathonIpAddress) throws SystemException {
        String nodeIp = null;
        try {
            Pair<String, String> nodeNameAndStatus = this.getServiceRuntimeNode(service);
            nodeIp = nodeNameAndStatus.getKey();
        } catch (MarathonException | FileException | SetupException e) {
            logger.warn (e.getMessage());
            logger.debug (e, e);
        }
        systemOperationService.applySystemOperation("Uninstallation of " + service + " on marathon node " + marathonIpAddress,
                builder -> {
                    try {
                        proceedWithMarathonServiceUninstallation(builder, marathonIpAddress, service);
                    } catch (MarathonException e) {
                        logger.error (e, e);
                        throw new SystemException(e);
                    }
                },
                status -> status.removeRootKey(service + OperationsCommand.INSTALLED_ON_IP_FLAG + MARATHON_NODE));
        if (nodeIp != null) {
            proxyManagerService.removeServerForService(service, nodeIp);
        } else {
            logger.warn ("No previous IP could be found for service " + service);
        }
    }


    void installMarathonService(String service, String marathonIpAddress)
            throws SystemException {
        systemOperationService.applySystemOperation("installation of " + service + " on marathon node " + marathonIpAddress,
                builder -> proceedWithMarathonServiceInstallation(builder, marathonIpAddress, service),
                status -> status.setValueForPath(service + OperationsCommand.INSTALLED_ON_IP_FLAG + MARATHON_NODE, "OK") );
    }

    private String proceedWithMarathonServiceUninstallation(StringBuilder sb, String marathonIpAddress, String service)
            throws SSHCommandException, SystemException, MarathonException {

        // 1. Calling uninstall.sh script if it exists
        File containerFolder = new File(servicesSetupPath + "/" + service);
        if (!containerFolder.exists()) {
            throw new SystemException("Folder " + servicesSetupPath + "/" + service + " doesn't exist !");
        }

        try {
            File uninstallScriptFile = new File(containerFolder, "uninstall.sh");
            if (uninstallScriptFile.exists()) {
                sb.append(" - Calling uninstall script\n");

                sb.append(sshCommandService.runSSHScriptPath(marathonIpAddress, uninstallScriptFile.getAbsolutePath()));
            }
        } catch (SSHCommandException e) {
            logger.warn (e, e);
            sb.append (e.getMessage());
        }

        // 2. Stop service
        sb.append("Killing tasks for " + service + "\n");
        String killResultString = queryMarathon("apps/"+service, "DELETE");
        JsonWrapper killResult = new JsonWrapper(killResultString);

        String deploymentId = killResult.getValueForPathAsString("deploymentId");
        if (StringUtils.isBlank(deploymentId)) {
            sb.append("WARNING : Could not find any deployment ID when killing tasks for " + service + "\n");
        } else {
            sb.append("Tasks killing deployment ID for " + service + " is " + deploymentId + "\n");
        }

        // 4. Delete docker container
        sb.append(" - Removing docker container \n");
        sshCommandService.runSSHCommand(marathonIpAddress, "sudo docker rm -f " + service + " || true ");

        // 5. Delete docker image
        sb.append(" - Removing docker image \n");
        sshCommandService.runSSHCommand(marathonIpAddress, "sudo docker image rm -f eskimo:" + servicesDefinition.getService(service).getImageName());

        return sb.toString();
    }

    private void proceedWithMarathonServiceInstallation(StringBuilder sb, String marathonIpAddress, String service)
            throws IOException, SystemException, SSHCommandException {

        String imageName = servicesDefinition.getService(service).getImageName();

        sb.append(" - Creating archive and copying it over to marathon node \n");
        File tmpArchiveFile = systemService.createRemotePackageFolder(sb, marathonIpAddress, service, imageName);

        // 4. call setup script
        systemService.installationSetup(sb, marathonIpAddress, service);

        // 5. cleanup
        systemService.installationCleanup(sb, marathonIpAddress, service, imageName, tmpArchiveFile);
    }

    public void fetchMarathonServicesStatus
            (Map<String, String> statusMap, ServicesInstallStatusWrapper servicesInstallationStatus)
            throws MarathonException {

        // 3.1 Node answers
        try {

            String marathonIpAddress = findUniqueServiceIP("marathon");
            String ping = null;
            if (!StringUtils.isBlank(marathonIpAddress)) {

                // find out if SSH connection to host can succeeed
                try {
                    ping = systemService.sendPing(marathonIpAddress);
                } catch (SSHCommandException e) {
                    logger.warn(e.getMessage());
                    logger.debug(e, e);
                }

                for (String service : servicesDefinition.listMarathonServices()) {

                    // should service be installed on marathon ?
                    boolean shall = this.shouldInstall(service);

                    // check if service is installed ?
                    //check if service installed using SSH
                    Pair<String, String> nodeNameAndStatus = new Pair<>(null, "NA");
                    if (StringUtils.isNotBlank(ping) && ping.startsWith("OK")) {
                        nodeNameAndStatus = this.getServiceRuntimeNode(service);
                    }

                    String nodeIp = nodeNameAndStatus.getKey();

                    boolean installed = StringUtils.isNotBlank(nodeIp);
                    boolean running = nodeNameAndStatus.getValue().equals("running");

                    String nodeName = nodeIp != null ? nodeIp.replace(".", "-") : null;

                    // uninstalled services are identified on the marathon node
                    if (StringUtils.isBlank(nodeName)) {
                        if (StringUtils.isNotBlank(marathonIpAddress)) {
                            nodeName = marathonIpAddress.replace(".", "-");
                        } else {
                            nodeName = servicesInstallationStatus.getFirstNodeName("marathon");
                        }
                        // last attempt, get it from theoretical perspective
                        if (StringUtils.isBlank(nodeName)) {
                            nodeName = configurationService.loadNodesConfig().getFirstNodeName("marathon");
                        }
                    }

                    systemService.feedInServiceStatus (
                            statusMap, servicesInstallationStatus, nodeIp, nodeName,
                            MARATHON_NODE,
                            service, shall, installed, running);
                }
            }
        } catch (JSONException | ConnectionManagerException | SystemException | SetupException | FileException e) {
            logger.error(e, e);
            throw new MarathonException(e.getMessage(), e);
        }
    }

    boolean shouldInstall(String service) throws SetupException, SystemException {
        MarathonServicesConfigWrapper marathonConfig = configurationService.loadMarathonServicesConfig();
        if (marathonConfig != null) {

            // search it in config
            return StringUtils.isNotBlank ((String)marathonConfig.getValueForPath(service + "_install"))
                    && marathonConfig.getValueForPath(service + "_install").equals("on");
        }

        return false;
    }

    public void showJournalMarathon(Service service) throws MarathonException, SSHCommandException {
        systemService.applyServiceOperation(service.getName(), "marathon node", "Showing journal", () -> {
            StringBuilder log = new StringBuilder();
            log.append("(Showing journal is not supported for marathon)");
            return log.toString();
        });
    }

    public void startServiceMarathon(Service service) throws MarathonException, SSHCommandException {
        systemService.applyServiceOperation(service.getName(), "marathon node", "Starting", () -> startServiceMarathonInternal(service));
    }

    String startServiceMarathonInternal(Service service) throws MarathonException {
        try {
            StringBuilder log = new StringBuilder();

            Pair<String, String> nodeNameAndStatus = this.getServiceRuntimeNode(service.getName());

            String nodeIp = nodeNameAndStatus.getKey();

            boolean installed = StringUtils.isNotBlank(nodeIp);
            boolean running = nodeNameAndStatus.getValue().equals("running");

            if (!installed) {
                log.append("ERROR - Service " + service.getName() + " is not installed." + "\n");
                throw new MarathonException("Service " + service.getName() + " is not installed.");

            } else if (running) {

                log.append("WARNING - Service " + service.getName() + " is already started" + "\n");

            } else {

                // TODO

                // curl -XPATCH  -H 'Content-Type: application/json' http://$MASTER_MARATHON_1:28080/v2/apps/cerebro -d '{ "id": "/cerebro", "instances": 1}'
                // {"version":"2020-03-25T17:25:19.572Z","deploymentId":"bd866d4d-5bc0-4097-8385-7b0957143d24"}

                String startResultString = updateMarathon("apps/" + service.getName(), "PATCH", "{ \"id\": \"/" + service.getName() + "\", \"instances\": 1}");
                JsonWrapper startResult = new JsonWrapper(startResultString);

                String deploymentId = startResult.getValueForPathAsString("deploymentId");
                if (StringUtils.isBlank(deploymentId)) {
                    log.append("WARNING : Could not find any deployment ID when starting tasks for " + service.getName() + "\n");
                } else {
                    log.append("Tasks starting deployment ID for " + service.getName() + " is " + deploymentId + "\n");
                }
            }

            return log.toString();
        } catch (FileException | SetupException e) {
            logger.error (e, e);
            throw new MarathonException(e);
        }
    }

    public void  stopServiceMarathon(Service service) throws MarathonException, SSHCommandException {
        systemService.applyServiceOperation(service.getName(), "marathon node", "Stopping", () -> stopServiceMarathonInternal(service));
    }

    String stopServiceMarathonInternal(Service service) throws MarathonException {

        StringBuilder log = new StringBuilder();

        // 1. Kill all tasks for service
        log.append("Killing tasks for " + service.getName() + "\n");
        String killResultString = queryMarathon("apps/"+service.getName()+"/tasks?scale=true", "DELETE");
        JsonWrapper killResult = new JsonWrapper(killResultString);

        String deploymentId = killResult.getValueForPathAsString("deploymentId");
        if (StringUtils.isBlank(deploymentId)) {
            log.append("WARNING : Could not find any deployment ID when killing tasks for " + service.getName());
        } else {
            log.append("Tasks killing deployment ID for " + service.getName() + " is " + deploymentId);
        }

        return log.toString();
    }

    public void restartServiceMarathon(Service service) throws MarathonException, SSHCommandException {
        systemService.applyServiceOperation(service.getName(), "marathon node", "Stopping", () -> {
            return restartServiceMarathonInternal(service);
        });
    }

    String restartServiceMarathonInternal(Service service) throws MarathonException {
        StringBuilder log = new StringBuilder();

        log.append(stopServiceMarathonInternal(service));

        try {
            for (int i = 0; i < 100; i++) { // 100 attemots
                Pair<String, String> nodeNameAndStatus = this.getServiceRuntimeNode(service.getName());

                String nodeIp = nodeNameAndStatus.getKey();

                boolean installed = StringUtils.isNotBlank(nodeIp);
                boolean running = nodeNameAndStatus.getValue().equals("running");
                if (!running) {
                    break;
                }

                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    logger.debug(e.getMessage());
                }
            }
        } catch (SetupException | FileException e) {
            logger.error (e, e);
            throw new MarathonException(e);
        }


        log.append("\n");
        log.append(startServiceMarathonInternal(service));

        return log.toString();
    }
}