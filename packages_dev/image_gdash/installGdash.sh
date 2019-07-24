#!/usr/bin/env bash

#
# This file is part of the eskimo project referenced at www.eskimo.sh. The licensing information below apply just as
# well to this individual file than to the Eskimo Project as a whole.
#
# Copyright 2019 eskimo.sh / https://www.eskimo.sh - All rights reserved.
# Author : eskimo.sh / https://www.eskimo.sh
#
# Eskimo is available under a dual licensing model : commercial and GNU AGPL.
# If you did not acquire a commercial licence for Eskimo, you can still use it and consider it free software under the
# terms of the GNU Affero Public License. You can redistribute it and/or modify it under the terms of the GNU Affero
# Public License  as published by the Free Software Foundation, either version 3 of the License, or (at your option)
# any later version.
# Compliance to each and every aspect of the GNU Affero Public License is mandatory for users who did no acquire a
# commercial license.
#
# Eskimo is distributed as a free software under GNU AGPL in the hope that it will be useful, but WITHOUT ANY
# WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# Affero Public License for more details.
#
# You should have received a copy of the GNU Affero Public License along with Eskimo. If not,
# see <https://www.gnu.org/licenses/> or write to the Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
# Boston, MA, 02110-1301 USA.
#
# You can be released from the requirements of the license by purchasing a commercial license. Buying such a
# commercial license is mandatory as soon as :
# - you develop activities involving Eskimo without disclosing the source code of your own product, software,
#   platform, use cases or scripts.
# - you deploy eskimo as part of a commercial product, platform or software.
# For more information, please contact eskimo.sh at https://www.eskimo.sh
#
# The above copyright notice and this licensing notice shall be included in all copies or substantial portions of the
# Software.
#

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
. $SCRIPT_DIR/common.sh "$@"

echo "-- INSTALLING GDASH -----------------------------------------------------------"

saved_dir=`pwd`
function returned_to_saved_dir() {
     cd $saved_dir
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT

echo " - Changing to temp directory"
mkdir -p /tmp/gdash_setup
cd /tmp/gdash_setup


echo " - Configuring nodejs repo"
curl -sL https://deb.nodesource.com/setup_8.x | sudo -E bash -  > /tmp/gdash_install_log 2>&1
fail_if_error $? "/tmp/gdash_install_log" -2

echo " - Installing nodejs"
apt-get -y install nodejs  > /tmp/gdash_install_log 2>&1
fail_if_error $? "/tmp/gdash_install_log" -2

echo " - Installing bower"
npm install -g bower  > /tmp/gdash_install_log 2>&1
fail_if_error $? "/tmp/gdash_install_log" -2

echo " - Installing python pip"
apt-get -y install python-pip > /tmp/gdash_install_log 2>&1
fail_if_error $? "/tmp/gdash_install_log" -2

echo " - Installing gdash from python repo"
pip install gdash > /tmp/gdash_install_log 2>&1
fail_if_error $? "/tmp/gdash_install_log" -2

echo " - Fixing flask.ext.cache -> flask_cache"
sed -i s/"flask.ext.cache"/"flask_cache"/g /usr/local/lib/python2.7/dist-packages/gdash/app.py
rm -f /usr/local/lib/python2.7/dist-packages/gdash/app.pyc

sed -i s/"flask.ext.cache"/"flask_cache"/g /usr/local/lib/python2.7/dist-packages/flask_cache/jinja2ext.py
rm -f /usr/local/lib/python2.7/dist-packages/flask_cache/jinja2ext.pyc


# run with /usr/local/bin/gdash --port 28180 --host 192.168.10.11 --gluster /usr/local/sbin/gluster


sudo rm -Rf /tmp/gdash_setup
returned_to_saved_dir




# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container install SUCCESS"