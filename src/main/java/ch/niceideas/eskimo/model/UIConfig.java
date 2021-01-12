/*
 * This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
 * well to this individual file than to the Eskimo Project as a whole.
 *
 * Copyright 2019 - 2021 eskimo.sh / https://www.eskimo.sh - All rights reserved.
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

package ch.niceideas.eskimo.model;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class UIConfig {

    private final Service service;

    private String urlTemplate;
    private Integer proxyTargetPort;
    private int waitTime;
    private String title;

    private boolean applyStandardProxyReplacements = true;
    private String statusPageLinkTitle;
    private final List<ProxyReplacement> proxyReplacements = new ArrayList<>();

    public UIConfig (Service service) {
        this.service = service;
    }

    public String getStatusPageLinkTitle() {
        return statusPageLinkTitle;
    }

    public void setStatusPageLinkTitle(String statusPageLinkTitle) {
        this.statusPageLinkTitle = statusPageLinkTitle;
    }

    public boolean isApplyStandardProxyReplacements() {
        return applyStandardProxyReplacements;
    }

    public void setApplyStandardProxyReplacements(boolean applyStandardProxyReplacements) {
        this.applyStandardProxyReplacements = applyStandardProxyReplacements;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getWaitTime() {
        return waitTime;
    }

    public void setWaitTime(int waitTime) {
        this.waitTime = waitTime;
    }

    public String getUrlTemplate() {
        return urlTemplate;
    }

    public void setUrlTemplate(String urlTemplate) {
        this.urlTemplate = urlTemplate;
    }

    public JSONObject toJSON () {
        return new JSONObject(new HashMap<String, Object>() {{
            put("urlTemplate", urlTemplate == null ? "" : urlTemplate);
            put("proxyContext", "./"+service.getName()+"/");
            put("waitTime", waitTime);
            put("title", title);
            put("unique", service.isUnique());
        }});
    }

    public String getServiceName() {
        return service.getName();
    }

    public Integer getProxyTargetPort() {
        return proxyTargetPort;
    }
    public void setProxyTargetPort(Integer proxyTargetPort) {
        this.proxyTargetPort = proxyTargetPort;
    }

    public void addProxyReplacement(ProxyReplacement pr) {
        proxyReplacements.add (pr);
    }

    public List<ProxyReplacement> getProxyReplacements() {
        return proxyReplacements;
    }
}
