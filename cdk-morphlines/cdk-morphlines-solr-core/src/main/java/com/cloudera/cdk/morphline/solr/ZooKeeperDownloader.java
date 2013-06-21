/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloudera.cdk.morphline.solr;

import java.io.File;
import java.io.IOException;

import org.apache.solr.cloud.ZkController;
import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.solr.common.cloud.ZkNodeProps;
import org.apache.solr.common.cloud.ZkStateReader;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;

/**
 * Downloads SolrCloud information from ZooKeeper.
 */
final class ZooKeeperDownloader {
  
  private static final Logger LOG = LoggerFactory.getLogger(ZooKeeperDownloader.class);
  
  public SolrZkClient getZkClient(String zkHost) {
    if (zkHost == null) {
      throw new IllegalArgumentException("zkHost must not be null");
    }

    SolrZkClient zkClient;
    try {
      zkClient = new SolrZkClient(zkHost, 30000);
    } catch (Exception e) {
      throw new IllegalArgumentException("Cannot connect to ZooKeeper: " + zkHost, e);
    }
    return zkClient;
  }
  
  /**
   * Returns config value given collection name
   * Borrowed heavily from Solr's ZKController.
   */
  public String readConfigName(SolrZkClient zkClient, String collection)
  throws KeeperException, InterruptedException {
    if (collection == null) {
      throw new IllegalArgumentException("collection must not be null");
    }
    String configName = null;

    String path = ZkStateReader.COLLECTIONS_ZKNODE + "/" + collection;
    if (LOG.isInfoEnabled()) {
      LOG.info("Load collection config from:" + path);
    }
    byte[] data = zkClient.getData(path, null, null, true);
    
    if(data != null) {
      ZkNodeProps props = ZkNodeProps.load(data);
      configName = props.getStr(ZkController.CONFIGNAME_PROP);
    }
    
    if (configName != null && !zkClient.exists(ZkController.CONFIGS_ZKNODE + "/" + configName, true)) {
      LOG.error("Specified config does not exist in ZooKeeper:" + configName);
      throw new IllegalArgumentException("Specified config does not exist in ZooKeeper:"
        + configName);
    }

    return configName;
  }

  /**
   * Download and return the config directory from ZK
   */
  public File downloadConfigDir(SolrZkClient zkClient, String configName)
  throws IOException, InterruptedException, KeeperException {
    File dir = Files.createTempDir();
    dir.deleteOnExit();
    ZkController.downloadConfigDir(zkClient, configName, dir);
    File confDir = new File(dir, "conf");
    if (!confDir.isDirectory()) {
      // create a temporary directory with "conf" subdir and mv the config in there.  This is
      // necessary because of CDH-11188; solrctl does not generate nor accept directories with e.g.
      // conf/solrconfig.xml which is necessary for proper solr operation.  This should work
      // even if solrctl changes.
      confDir = new File(Files.createTempDir().getAbsolutePath(), "conf");
      confDir.getParentFile().deleteOnExit();
      Files.move(dir, confDir);
      dir = confDir.getParentFile();
    }
    return dir;
  }

}
