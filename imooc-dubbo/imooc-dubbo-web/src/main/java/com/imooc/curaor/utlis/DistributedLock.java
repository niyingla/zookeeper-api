package com.imooc.curaor.utlis;

import com.sun.org.apache.regexp.internal.RE;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.recipes.cache.PathChildrenCache;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheEvent;
import org.apache.curator.framework.recipes.cache.PathChildrenCacheListener;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.PostConstruct;
import java.sql.PreparedStatement;
import java.util.concurrent.CountDownLatch;

public class DistributedLock {
    /**
     * 客户端
     */
    private CuratorFramework client = null;
    /**
     * 日志打印类
     */
    final static Logger log = LoggerFactory.getLogger(DistributedLock.class);
    //用于挂起当前请求 并且等待上一个分布式锁释放
    private static CountDownLatch zkLockLatch = new CountDownLatch(1);
    //分布式锁总的节点名字
    private static String ZK_LOCK_PROJECT = "imooc-locks";
    //分布式锁节点
    private static String DISTRIBUTED_LOCK = "DistributedLock";

    public DistributedLock(CuratorFramework client) {
        this.client = client;
    }

    /**
     * 初始化方法
     */
    @PostConstruct
    public void init() {
        //指定
        client.usingNamespace("ZKLocks-namespace");
        /**
         * 创建zk锁的总结点。相当于
         * nameSpace/ZK_LOCK_PROJECT/DISTRIBUTED_LOCK
         */
        try {
            //判断父节点是否存在
            if (client.checkExists().forPath("/" + ZK_LOCK_PROJECT) == null) {
                //不存在就创建
                client.create().creatingParentsIfNeeded()
                        .withMode(CreateMode.PERSISTENT).withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE).forPath("/" + ZK_LOCK_PROJECT);
                //针对zk的分布式锁节点，创建相对应的watcher事件监听(给锁上级节点)
                addWatcherToLock("/" + ZK_LOCK_PROJECT);
            }
        } catch (Exception e) {
            log.error("连接服务器失败，请重试。。。。", e);
        }
    }

    /**
     * 释放锁
     *
     * @return
     * @throws Exception
     */
    public boolean releaseLock() throws Exception {
        try {
            //判断锁是否存在
            if (client.checkExists().forPath("/" + ZK_LOCK_PROJECT + "/" + DISTRIBUTED_LOCK) != null) {
                //删除锁节点
                client.delete().forPath("/" + ZK_LOCK_PROJECT + "/" + DISTRIBUTED_LOCK);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * 给锁天剑监听
     *
     * @param path
     */
    private void addWatcherToLock(String path) throws Exception {
        //创建子节点缓存
        final PathChildrenCache cache = new PathChildrenCache(client, path, true);
        //指定初始化模式 异步初始化，初始化之后会触发事件
        cache.start(PathChildrenCache.StartMode.POST_INITIALIZED_EVENT);
        //添加节点监听
        cache.getListenable().addListener((client, event) -> {
            //判断节点通知时间类型 为子节点变化
            if (event.getType().equals(PathChildrenCacheEvent.Type.CHILD_UPDATED)) {
                //获取节点数据路径
                String path1 = event.getData().getPath();
                log.info("上一个会话已释放，节点路径为：" + path1);
                if (path1.contains(DISTRIBUTED_LOCK)) {
                    log.info("释放计数器，让当前请求来获取分布式锁。。。");
                    zkLockLatch.countDown();
                }
            }
        });
    }

    /**
     * 获取锁
     */
    public void getLock() {
        while (true) {
            try {
                //创建锁节点
                client.create().creatingParentsIfNeeded()
                        //临时节点 挂了就自动释放
                        .withMode(CreateMode.EPHEMERAL)
                        //开放权限
                        .withACL(ZooDefs.Ids.OPEN_ACL_UNSAFE)
                        //指定锁名称（路径）
                        .forPath("/" + ZK_LOCK_PROJECT + "/" + DISTRIBUTED_LOCK);

                log.info("获取分布式锁成功");
                return;
            } catch (Exception e) {
                log.info("获取分布式锁失败。。。");
                try {
                    //如果没有获取到锁，需要重新同步资源值
                    if (zkLockLatch.getCount() <= 0) {
                        zkLockLatch = new CountDownLatch(1);
                    }
                    //阻塞线程(等待监听器唤醒)
                    zkLockLatch.await();
                } catch (Exception ie) {
                    log.info("重置资源信息。。。");
                    ie.printStackTrace();
                }
            }
        }
    }
}
