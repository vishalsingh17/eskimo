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

package ch.niceideas.eskimo.controlers;

import ch.niceideas.common.json.JsonWrapper;
import ch.niceideas.common.utils.Pair;
import ch.niceideas.common.utils.StringUtils;
import ch.niceideas.eskimo.model.OperationsMonitoringStatusWrapper;
import ch.niceideas.eskimo.services.MessagingManager;
import ch.niceideas.eskimo.services.OperationsMonitoringService;
import ch.niceideas.eskimo.utils.ReturnStatusHelper;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.annotation.Resource;
import java.util.AbstractMap;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;


@Controller
public class OperationsMonitoringController extends AbstractInformationController<String, String> {

    private static final Logger logger = Logger.getLogger(OperationsMonitoringController.class);

    @Resource
    private OperationsMonitoringService operationsMonitoringService;

    /* For tests */
    void setOperationsMonitoringService(OperationsMonitoringService operationsMonitoringService) {
        this.operationsMonitoringService = operationsMonitoringService;
    }

    @GetMapping("/fetch-operations-status")
    @ResponseBody
    public String fetchOperationsStatus(@RequestParam(name="last-lines") String lastLinesJsonString) {

        try {

            JsonWrapper lastLinesJson = StringUtils.isBlank(lastLinesJsonString) ?
                    JsonWrapper.empty() :
                    new JsonWrapper(lastLinesJsonString);

            Map<String, Integer> lastLinesMap = lastLinesJson.toMap().keySet().stream()
                    .collect(Collectors.toMap(opId -> opId, opId -> (Integer) lastLinesJson.toMap().get(opId)));

            OperationsMonitoringStatusWrapper status = operationsMonitoringService.getOperationsMonitoringStatus (lastLinesMap);

            status.setValueForPath("result", "OK");

            return status.getFormattedValue();

        } catch (JSONException e) {
            logger.error(e, e);
            return ReturnStatusHelper.createErrorStatus(e);
        }
    }

}