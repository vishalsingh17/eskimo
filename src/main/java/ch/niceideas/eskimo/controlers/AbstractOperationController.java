package ch.niceideas.eskimo.controlers;

import ch.niceideas.eskimo.model.JSONOpCommand;
import ch.niceideas.eskimo.services.NotificationService;
import ch.niceideas.eskimo.services.OperationsMonitoringService;
import ch.niceideas.eskimo.services.SystemService;
import ch.niceideas.eskimo.utils.ReturnStatusHelper;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.HashMap;

public class AbstractOperationController {

    @Autowired
    protected NotificationService notificationService;

    @Autowired
    protected SystemService systemService;

    @Autowired
    protected OperationsMonitoringService operationsMonitoringService;

    @Value("${eskimo.demoMode}")
    private boolean demoMode = false;

    /* for tests */
    void setDemoMode (boolean demoMode) {
        this.demoMode = demoMode;
    }

    protected String returnCommand(JSONOpCommand command) {
        return ReturnStatusHelper.createOKStatus(map -> map.put("command", command.toJSON()));
    }

    protected JSONObject checkOperations(String demoMessage) {

        JSONObject checkObject = null;

        if (operationsMonitoringService.isProcessingPending()) {

            String message = "Some backend operations are currently running. Please retry after they are completed.";

            notificationService.addError("Operation In Progress");

            checkObject = new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("messages", message);
            }});
        }

        if (demoMode) {

            notificationService.addError("Demo Mode");

            checkObject = new JSONObject(new HashMap<String, Object>() {{
                put("status", "OK");
                put("messages", demoMessage);
            }});
        }

        return checkObject;
    }
}
