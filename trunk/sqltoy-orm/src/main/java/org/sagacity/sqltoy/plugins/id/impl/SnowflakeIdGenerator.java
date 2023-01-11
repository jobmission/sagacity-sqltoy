/**
 * 
 */
package org.sagacity.sqltoy.plugins.id.impl;

import java.util.Date;

import org.sagacity.sqltoy.SqlToyConstants;
import org.sagacity.sqltoy.SqlToyContext;
import org.sagacity.sqltoy.plugins.id.IdGenerator;
import org.sagacity.sqltoy.utils.SnowflakeIdWorker;
import org.sagacity.sqltoy.utils.SqlUtil;

/**
 * @project sagacity-sqltoy
 * @description 基于twitter的分布式自增ID生成策略
 * @author zhongxuchen
 * @version v1.0,Date:2017年3月21日
 */
public class SnowflakeIdGenerator implements IdGenerator {

	private static SnowflakeIdWorker idWorker = null;

	private static IdGenerator me = new SnowflakeIdGenerator();

	/**
	 * @TODO 获取对象单例
	 * @return
	 */
	public static IdGenerator getInstance() {
		return me;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.sagacity.sqltoy.plugin.IdGenerator#getId(java.lang.String,
	 * java.lang.String, java.lang.Object[], int)
	 */
	@Override
	public Object getId(String tableName, String signature, String[] relatedColumns, Object[] relatedColValue,
			Date bizDate, String idJavaType, int length, int sequencSize) {
		if (idWorker == null) {
			idWorker = new SnowflakeIdWorker(SqlToyConstants.WORKER_ID, SqlToyConstants.DATA_CENTER_ID);
		}
		return SqlUtil.convertIdValueType(idWorker.nextId(), idJavaType);
	}

	//update 2022-12-27 修复并发线程安全问题
	@Override
	public void initialize(SqlToyContext sqlToyContext) throws Exception {
		if (idWorker == null) {
			idWorker = new SnowflakeIdWorker(SqlToyConstants.WORKER_ID, SqlToyConstants.DATA_CENTER_ID);
		}
	}
}
