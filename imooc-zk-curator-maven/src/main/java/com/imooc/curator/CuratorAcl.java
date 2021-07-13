package com.imooc.curator;

import java.util.ArrayList;
import java.util.List;

import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.RetryNTimes;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs.Ids;
import org.apache.zookeeper.ZooDefs.Perms;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Id;

import com.imooc.utils.AclUtils;
import org.apache.zookeeper.data.Stat;

public class CuratorAcl {

	public CuratorFramework client = null;
	public static final String zkServerPath = "192.168.1.110:2181";

	public CuratorAcl() {
		RetryPolicy retryPolicy = new RetryNTimes(3, 5000);
		//进行权限登录认证
		client = CuratorFrameworkFactory.builder().authorization("digest", "imooc1:123456".getBytes())
				.connectString(zkServerPath)
				.sessionTimeoutMs(10000).retryPolicy(retryPolicy)
				.namespace("workspace").build();
		client.start();
	}
	
	public void closeZKClient() {
		if (client != null) {
			this.client.close();
		}
	}
	
	public static void main(String[] args) throws Exception {
		// 实例化
		CuratorAcl cto = new CuratorAcl();
		boolean isZkCuratorStarted = cto.client.isStarted();
		System.out.println("当前客户的状态：" + (isZkCuratorStarted ? "连接中" : "已关闭"));
		
		String nodePath = "/acl/father/child/sub";
		
		List<ACL> acls = new ArrayList<ACL>();
		Id imooc1 = new Id("digest", AclUtils.getDigestUserPwd("imooc1:123456"));
		Id imooc2 = new Id("digest", AclUtils.getDigestUserPwd("imooc2:123456"));
		acls.add(new ACL(Perms.ALL, imooc1));
		acls.add(new ACL(Perms.READ, imooc2));
		acls.add(new ACL(Perms.DELETE | Perms.CREATE, imooc2));
		
		// 创建节点
		byte[] data = "spiderman".getBytes();
		cto.client.create().creatingParentsIfNeeded()
				.withMode(CreateMode.PERSISTENT)
				// 				是否支持递归设置权限到当前创建目录
				.withACL(acls, true)
				//原生acl
//				.withACL(Ids.OPEN_ACL_UNSAFE)
				.forPath(nodePath, data);
		

		//直接设置权限（当前节点没有权限时 有权限需要认证）
		cto.client.setACL().withACL(acls).forPath("/curatorNode");
		
		// 更新节点数据
		byte[] newData = "batman".getBytes();
		cto.client.setData().withVersion(0).forPath(nodePath, newData);
		
		// 删除节点
		cto.client.delete().guaranteed().deletingChildrenIfNeeded().withVersion(0).forPath(nodePath);
		
		// 读取节点数据
		Stat stat = new Stat();
		byte[] data1 = cto.client.getData().storingStatIn(stat).forPath(nodePath);
		System.out.println("节点" + nodePath + "的数据为: " + new String(data1));
		System.out.println("该节点的版本号为: " + stat.getVersion());
		
		
		cto.closeZKClient();
		boolean isZkCuratorStarted2 = cto.client.isStarted();
		System.out.println("当前客户的状态：" + (isZkCuratorStarted2 ? "连接中" : "已关闭"));
	}
	
}
