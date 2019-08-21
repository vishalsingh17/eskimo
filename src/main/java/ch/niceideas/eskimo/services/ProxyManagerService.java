/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

import ch.niceideas.eskimo.model.Service;
import org.apache.http.HttpHost;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
public class ProxyManagerService {

    @Autowired
    private ServicesDefinition servicesDefinition;

    private Map<String, HttpHost> serverHostMap = new ConcurrentHashMap<>();

    /** For tests */
    void setServicesDefinition (ServicesDefinition servicesDefinition) {
        this.servicesDefinition = servicesDefinition;
    }

    public HttpHost getServerHost(String service) {
        return serverHostMap.get(service);
    }

    public String getServerURI(String service) {
        HttpHost serverHost = getServerHost(service);
        if (serverHost == null) {
            throw new IllegalStateException("No host stored for " + service);
        }
        return serverHost.getSchemeName() + "://" + serverHost.getHostName() + ":" + serverHost.getPort() + "/";
    }

    public void updateServerForService(String serviceName, String host) {
        Service service = servicesDefinition.getService(serviceName);
        if (service != null && service.isProxied()) {

            // Don't bother with HTTPS, everything will go through SSH tunnels in anyway
            HttpHost newHHost = new HttpHost(host, service.getUiConfig().getProxyTargetPort(), "http");

            HttpHost prevHHost = serverHostMap.get(serviceName);

            if (prevHHost == null || !prevHHost.toURI().equals(newHHost.toURI())) {

                // TODO Handle host has changed !

                if (prevHHost != null) {

                    // TODO former tunnel needs to be closed

                } else {

                    // TODO Create new tunnel
                }
            }

            serverHostMap.put(serviceName, newHHost);
        }
    }
}
