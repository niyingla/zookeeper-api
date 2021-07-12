package com.imooc.zk.demo;


import org.apache.zookeeper.AsyncCallback.StringCallback;

public class CreateCallBack implements StringCallback {

	/**
	 *
	 * @param rc
	 * @param path
	 * @param ctx 外部传入参数
	 * @param name
	 */
	@Override
	public void processResult(int rc, String path, Object ctx, String name) {
		System.out.println("创建节点: " + path);
		System.out.println((String)ctx);
	}

}
