/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2023 eskimo.sh / https://www.eskimo.sh - All rights reserved.
 * Author : eskimo.sh / https://www.eskimo.sh
 *
 * Eskimo is available under a dual licensing model : commercial and GNU AGPL.
 * If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
 * terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
 * Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
 * commercial license.
 *
 * Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Affero Public License for more details.
 *
 * You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
 * see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA, 02110-1301 USA.
 *
 * You can be released from the requirements of the license by purchasing a commercial license. Buying such a
 * commercial license is mandatory as soon as :
 * - you develop activities involving Eskimo without disclosing the source code of your own product, software,
 *   platform, use cases or scripts.
 * - you deploy eskimo as part of a commercial product, platform or software.
 * For more information, please contact eskimo.sh at https://www.eskimo.sh
 *
 * The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
 * Software.
 */

package ch.niceideas.eskimo.services;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.FileException;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.KubernetesServicesConfigWrapper;
import ch.niceideas.eskimo.model.NodesConfigWrapper;
import ch.niceideas.eskimo.services.satellite.NodeRangeResolver;
import ch.niceideas.eskimo.services.satellite.NodesConfigurationException;
import org.apache.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Scope;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import javax.annotation.PreDestroy;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
@Profile("!test-app-status")
public class ApplicationStatusServiceImpl implements ApplicationStatusService {

    private static final Logger logger = Logger.getLogger(ApplicationStatusServiceImpl.class);

    public static final String SSH_USERNAME_FIELD = "sshUsername";
    public static final String AVAILABLE_KUBE_SERVICES = "availableKubeServices";
    public static final String INSTALLED_KUBE_SERVICES = "installedKubeServices";
    public static final String AVAILABLE_NODE_SERVICES = "availableNodeServices";
    public static final String NODES_COUNT = "nodesCount";
    public static final String INSTALLED_NODE_SERVICES = "installedNodeServices";
    public static final String UNKNOWN = "(unknown)";
    public static final String ERROR = "(ERROR)";
    public static final String SETUP_INCOMPLETE = "(Setup incomplete)";

    @Autowired
    private ConfigurationService configurationService;

    @Autowired
    private ServicesDefinition servicesDefinition;

    @Autowired
    private NodeRangeResolver nodeRangeResolver;

    @Value("${status.monitoringDashboardID:NONE}")
    private String monitoringDashboardId = null;

    @Value("${status.monitoringDashboardRefreshPeriod}")
    private String monitoringDashboardRefreshPeriod = "30s";

    @Value("${build.version}")
    private String buildVersion = "DEV-SNAPSHOT";

    @Value("${build.timestamp}")
    private String buildTimestamp = "LATEST DEV";

    @Value("${eskimo.enableKubernetesSubsystem}")
    private String enableKubernetes = "true";

    private final ThreadLocal<SimpleDateFormat> localDateFormatter
            = ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd HH:mm:ss"));

    private final ReentrantLock statusUpdateLock = new ReentrantLock();
    private final ScheduledExecutorService scheduler;
    private final AtomicReference<JsonWrapper> lastStatus = new AtomicReference<>();

    // constructor for spring
    public ApplicationStatusServiceImpl() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        logger.info ("Initializing Application Status update scheduler ...");
        scheduler.scheduleAtFixedRate(this::updateStatus, 15L * 1000L, 15L * 1000L, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void destroy() {
        logger.info ("Cancelling status updater scheduler");
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public static boolean isSnapshot(String buildVersion) {
        return StringUtils.isBlank(buildVersion) || buildVersion.endsWith("SNAPSHOT");
    }

    @Override
    public JsonWrapper getStatus(){

        // special case at application startup : if the UI request comes before the first status update
        if (lastStatus.get() == null) {
            updateStatus();
        }

        JsonWrapper systemStatus = lastStatus.get();

        // this is thread local, cannot be done in timer
        feedInSecurityContextInfo(systemStatus);

        return systemStatus;
    }

    @Override
    public void updateStatus() {

        try {
            statusUpdateLock.lock();

            // 0. Build returned status
            JsonWrapper systemStatus = JsonWrapper.empty();

            systemStatus.setValueForPath("monitoringDashboardId", monitoringDashboardId);

            systemStatus.setValueForPath("monitoringDashboardRefreshPeriod", monitoringDashboardRefreshPeriod);

            systemStatus.setValueForPath("buildVersion", buildVersion);

            systemStatus.setValueForPath("buildTimestamp", buildTimestamp);

            systemStatus.setValueForPath("enableKubernetes", StringUtils.isNotBlank(enableKubernetes) && enableKubernetes.equals("true"));

            systemStatus.setValueForPath("isSnapshot", isSnapshot(buildVersion));

            feedInSetupConfigInfo(systemStatus);

            feedInNodesConfigInfo(systemStatus);

            feedInKubeConfigInfo(systemStatus);

            // Get JVM's thread system bean
            RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
            long startTime = runtimeMXBean.getStartTime();
            Date startDate = new Date(startTime);

            systemStatus.setValueForPath("startTimestamp", localDateFormatter.get().format(startDate));

            lastStatus.set (systemStatus);

        } finally {
            statusUpdateLock.unlock();
        }
    }

    private void feedInKubeConfigInfo(JsonWrapper systemStatus) {
        systemStatus.setValueForPath(AVAILABLE_KUBE_SERVICES, servicesDefinition.countKubernetesServices());
        try {
            KubernetesServicesConfigWrapper kubeConfig = configurationService != null ? configurationService.loadKubernetesServicesConfig() : null;
            if (kubeConfig != null) {
                systemStatus.setValueForPath(INSTALLED_KUBE_SERVICES, kubeConfig.countServices());
            } else {
                systemStatus.setValueForPath(INSTALLED_KUBE_SERVICES, UNKNOWN);
            }

        } catch (SystemException e) {
            logger.warn (e.getMessage());
            logger.debug (e, e);
            systemStatus.setValueForPath(INSTALLED_KUBE_SERVICES, ERROR);
        }
    }

    private void feedInNodesConfigInfo(JsonWrapper systemStatus) {
        systemStatus.setValueForPath(AVAILABLE_NODE_SERVICES, servicesDefinition.countAllNodesServices());
        try {
            NodesConfigWrapper rawNodesConfig = configurationService != null ? configurationService.loadNodesConfig() : null;
            if (rawNodesConfig != null) {
                NodesConfigWrapper nodesConfig = nodeRangeResolver.resolveRanges(rawNodesConfig);
                systemStatus.setValueForPath(NODES_COUNT, nodesConfig.countNodes());
                systemStatus.setValueForPath(INSTALLED_NODE_SERVICES, nodesConfig.countServices());
            } else {
                systemStatus.setValueForPath(NODES_COUNT, UNKNOWN);
                systemStatus.setValueForPath(INSTALLED_NODE_SERVICES, UNKNOWN);
            }

        } catch (SetupException | SystemException | NodesConfigurationException e) {
            logger.warn (e.getMessage());
            logger.debug (e, e);
            systemStatus.setValueForPath(NODES_COUNT, ERROR);
            systemStatus.setValueForPath(INSTALLED_NODE_SERVICES, ERROR);
        }
    }

    private void feedInSetupConfigInfo(JsonWrapper systemStatus) {
        try {
            JsonWrapper setupConfig = configurationService != null ? configurationService.loadSetupConfig() : null;
            if (setupConfig != null && !setupConfig.isEmpty()) {
                systemStatus.setValueForPath(SSH_USERNAME_FIELD, setupConfig.getValueForPath(SetupService.SSH_USERNAME_FIELD));
            } else {
                systemStatus.setValueForPath(SSH_USERNAME_FIELD, SETUP_INCOMPLETE);
            }
        } catch (FileException e) {

            logger.error (e, e);
            systemStatus.setValueForPath(SSH_USERNAME_FIELD, ERROR);
        } catch (SetupException e) {

            logger.warn (e.getMessage());
            logger.debug (e, e);
            systemStatus.setValueForPath(SSH_USERNAME_FIELD, SETUP_INCOMPLETE);
        }
    }

    private void feedInSecurityContextInfo(JsonWrapper systemStatus) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null) {
            systemStatus.setValueForPath("username", auth.getName());

            @SuppressWarnings({"unchecked"})
            Collection<SimpleGrantedAuthority> authorities = (Collection<SimpleGrantedAuthority>) auth.getAuthorities();

            systemStatus.setValueForPath("roles", authorities.stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.joining(",")));
        }
    }
}
