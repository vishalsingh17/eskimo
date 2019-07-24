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


echo "-- INSTALLING ELASTICSEARCH ----------------------------------------------------"

if [ -z "$ES_VERSION" ]; then
    echo "Need to set ES_VERSION environment variable before calling this script !"
    exit 1
fi

saved_dir=`pwd`
function returned_to_saved_dir() {
     cd $saved_dir
}
trap returned_to_saved_dir 15
trap returned_to_saved_dir EXIT

echo " - Changing to temp directory"
mkdir -p /tmp/es_setup
cd /tmp/es_setup

echo " - Downloading elasticsearch-$ES_VERSION"
wget https://artifacts.elastic.co/downloads/elasticsearch/elasticsearch-$ES_VERSION.tar.gz > /tmp/es_install_log 2>&1
if [[ $? != 0 ]]; then
    echo " -> Failed to downolad elasticsearch-$ES_VERSION from https://artifacts.elastic.co/downloads/. Trying to download from niceideas.ch"
    exit -1
    #wget http://niceideas.ch/mes/elasticsearch-$ES_VERSION.tar.gz >> /tmp/es_install_log 2>&1
    #fail_if_error $? "/tmp/es_install_log" -1
fi

echo " - Extracting elasticsearch-$ES_VERSION"
tar -xvf elasticsearch-$ES_VERSION.tar.gz > /tmp/es_install_log 2>&1
fail_if_error $? "/tmp/es_install_log" -2

echo " - Installing ElasticSearch"
chown root.staff -R elasticsearch-$ES_VERSION
mv elasticsearch-$ES_VERSION /usr/local/lib
mkdir -p /usr/local/lib/elasticsearch-$ES_VERSION/data
chmod -R 777 /usr/local/lib/elasticsearch-$ES_VERSION/data
mkdir -p /usr/local/lib/elasticsearch-$ES_VERSION/logs
chmod -R 777 /usr/local/lib/elasticsearch-$ES_VERSION/logs
chmod -R 777 /usr/local/lib/elasticsearch-$ES_VERSION/config/

echo " - symlinking /usr/local/lib/elasticsearch/ to /usr/local/lib/elasticsearch-$ES_VERSION/"
ln -s /usr/local/lib/elasticsearch-$ES_VERSION /usr/local/lib/elasticsearch

echo " - Registering test cleaning traps"
export ES_PROC_ID=-1
function check_stop_es(){
    if [[ $ES_PROC_ID != -1 ]]; then
        echo " - Stopping ES !!"
        kill -15 $ES_PROC_ID
    fi
}
trap check_stop_es 15
trap check_stop_es EXIT

echo " - creating test user to test elasticsearch startup"
useradd estest


echo " - Starting ElasticSearch"
export ES_JAVA_OPTS="-Dlog4j2.disable.jmx=true -Xmx1g -Xms800m"
sudo -u estest /usr/local/lib/elasticsearch-$ES_VERSION/bin/elasticsearch > /tmp/es_run_log 2>&1 &
export ES_PROC_ID=$!

echo " - Checking Elasticsearch startup"
sleep 10
if [[ `ps -e | grep $ES_PROC_ID` == "" ]]; then
    echo " !! Failed to start Elasticsearch !!"
    exit -8
fi

echo " - Stopping Elasticsearch"
kill -15 $ES_PROC_ID
export ES_PROC_ID=-1

echo " - removing test user"
userdel estest

rm -Rf /tmp/es_setup
returned_to_saved_dir




# Caution : the in container setup script must mandatorily finish with this log"
echo " - In container install SUCCESS"