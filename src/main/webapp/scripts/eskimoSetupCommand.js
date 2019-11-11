/*
This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
well to this individual file than to the Eskimo Project as a whole.

Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
Author : eskimo.sh / https://www.eskimo.sh

Eskimo is available under a dual licensing model : commercial and GNU AGPL.
If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
any later version.
Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
commercial license.

Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
Affero Public License for more details.

You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
Boston, MA, 02110-1301 USA.

You can be released from the requirements of the license by purchasing a commercial license. Buying such a
commercial license is mandatory as soon as :
- you develop activities involving Eskimo without disclosing the source code of your own product, software, 
  platform, use cases or scripts.
- you deploy eskimo as part of a commercial product, platform or software.
For more information, please contact eskimo.sh at https://www.eskimo.sh

The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
Software.
*/

if (typeof eskimo === "undefined" || eskimo == null) {
    eskimo = {}
}
eskimo.SetupCommand = function() {

    var that = this;

    // constants
    var MESSAGES_POLLING_STOP_DELAY = 10000;

    this.initialize = function() {
        // Initialize HTML Div from Template
        $("#setup-command-modal-wrapper").load("html/eskimoSetupCommand.html", function (responseTxt, statusTxt, jqXHR) {

            if (statusTxt == "success") {

                //

            } else if (statusTxt == "error") {
                alert("Error: " + jqXHR.status + " " + jqXHR.statusText);
            }
        });
    };


    function showCommand (command) {

        console.log (command);

        var commandDescription = "<b>Following Operations are about to be applied</b>";

        // Build of packages
        var none = true;
        if (command.buildPackage != null && command.buildPackage.length > 0) {

            commandDescription += '<br><br><b>Packages are about to be built.</b>' +
                '<br>'+
                'Building packages happens in the folder "packages_dev" under the root folder of your eskimo installation<br>'+
                'Packages are built using shell script and docker.<br>'+
                'Building packages can take several dozen of minutes<br>'+
                '<b>List of packages to be built</b><br>';


            for (var i = 0; i < command.buildPackage.length; i++) {
                commandDescription += '<b><i class="fa fa-arrow-right"></i>&nbsp;' + command.buildPackage[i] + '</b><br>';
            }

            none = false;
        }

        // Download of packages
        if (command.downloadPackages != null && command.downloadPackages.length > 0) {

            commandDescription += '<br><br><b>Packages are about to be downloaded from '+command.packageDownloadUrl+'.</b>' +
                '<br>'+
                'Downloading of packages can take several dozen of minutes depending on your internet connection<br>'+
                '<b>List of packages to be downloaded</b><br>';


            for (var i = 0; i < command.downloadPackages.length; i++) {
                commandDescription += '<b><i class="fa fa-arrow-right"></i>&nbsp;' + command.downloadPackages[i] + '</b><br>';
            }

            none = false;
        }

        // Build of mesos
        if (command.buildMesos != null && command.buildMesos.length > 0) {

            commandDescription += '<br><br><b>Mesos Packages are about to be built.</b>' +
                '<br>'+
                'Building mesos happens in the folder "packages_dev" under the root folder of your eskimo installation<br>'+
                'Mesos Packages are built using shell script and either vagrant or libvirt.<br>'+
                '<b>Building packages can take several hours.</b><br>'+
                '<b>List of Mesos packages to be built</b><br>';


            for (var i = 0; i < command.buildMesos.length; i++) {
                commandDescription += '<b><i class="fa fa-arrow-right"></i>&nbsp;' + command.buildMesos[i] + '</b><br>';
            }

            none = false;
        }

        // Download of mesos
        if (command.downloadMesos != null && command.downloadMesos.length > 0) {

            commandDescription += '<br><br><b>Mesos Packages are about to be built.</b>' +
                '<br>'+
                'Downloading of mesos packages can take several dozen of minutes depending on your internet connection<br>'+
                '<b>List of Mesos packages to be downloaded</b><br>';


            for (var i = 0; i < command.downloadMesos.length; i++) {
                commandDescription += '<b><i class="fa fa-arrow-right"></i>&nbsp;' + command.downloadMesos[i] + '</b><br>';
            }

            none = false;
        }

        $("#setup-command-body").html(commandDescription);

        $('#setup-command-modal').modal("show");
    }
    this.showCommand = showCommand;

    function validateSetupCommand() {

        eskimoMain.getMessaging().showMessages();

        eskimoMain.startOperationInProgress();

        // 1 hour timeout
        $.ajax({
            type: "POST",
            dataType: "json",
            timeout: 1000 * 7200,
            contentType: "application/json; charset=utf-8",
            url:"apply-setup",
            success: function (data, status, jqXHR) {

                console.log(data);
                if (data && data.status) {
                    if (data.status == "KO") {
                        //eskimoMain.getSetup().showSetup();
                        eskimoMain.getSetup().showSetupMessage(data.error, false);
                    } else {
                        eskimoMain.getSetup().showSetupMessage("Configuration applied successfully", true);
                        eskimoMain.handleSetupCompleted();
                    }
                } else {
                    eskimoMain.getSetup().showSetupMessage("No status received back from backend.", false);
                }

                if (data.error) {
                    eskimoMain.scheduleStopOperationInProgress (false);
                } else {
                    eskimoMain.scheduleStopOperationInProgress (true);
                }

            },

            error: function (jqXHR, status) {
                errorHandler (jqXHR, status);
                eskimoMain.scheduleStopOperationInProgress (false);
            }
        });

        $('#setup-command-modal').modal("hide");
    }
    this.validateSetupCommand = validateSetupCommand;

    function cancelSetupCommand() {
        $('#setup-command-modal').modal("hide");
    }
    this.cancelSetupCommand = cancelSetupCommand;


    // call constriuctor
    this.initialize();
};