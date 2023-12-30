/**
 * 
 */
package org.sagacity.sqltoy.plugins.ddl.impl;

import org.sagacity.sqltoy.model.ColumnMeta;
import org.sagacity.sqltoy.model.TableMeta;
import org.sagacity.sqltoy.plugins.ddl.DDLUtils;
import org.sagacity.sqltoy.plugins.ddl.DialectDDLGenerator;
import org.sagacity.sqltoy.utils.StringUtil;

/**
 * @project sagacity-sqltoy
 * @description mysql数据库通过POJO生成创建表结构的ddl语句
 * @author zhongxuchen
 * @version v1.0, Date:2023年12月17日
 * @modify 2023年12月17日,修改说明
 */
public class MySqlDDLGenerator implements DialectDDLGenerator {
	private String NEWLINE = "\r\n";
	private String TAB = "   ";

	@Override
	public String createTableSql(TableMeta tableMeta, String schema, int dbType) {
		if (tableMeta == null) {
			return null;
		}
		StringBuilder tableSql = new StringBuilder();
		tableSql.append("create table ").append(tableMeta.getTableName()).append(NEWLINE);
		tableSql.append("(").append(NEWLINE);
		int index = 0;
		for (ColumnMeta colMeta : tableMeta.getColumns()) {
			if (index > 0) {
				tableSql.append(",").append(NEWLINE);
			}
			// 字段名
			tableSql.append(TAB).append(colMeta.getColName());
			// 类型
			tableSql.append(" ").append(DDLUtils.convertType(colMeta, dbType));
			// 是否为null
			if (!colMeta.isNullable()) {
				tableSql.append(" not null");
			}
			// 自增
			if (colMeta.isAutoIncrement()) {
				tableSql.append(" auto_increment");
			} else if (StringUtil.isNotBlank(colMeta.getDefaultValue())) {
				tableSql.append(" default ");
				if (DDLUtils.isNotChar(colMeta.getDataType())) {
					tableSql.append(colMeta.getDefaultValue());
				} else {
					tableSql.append("'").append(colMeta.getDefaultValue()).append("'");
				}
			}
			// 列注释
			if (StringUtil.isNotBlank(colMeta.getComments())) {
				tableSql.append(" comment '").append(colMeta.getComments()).append("'");
			}
			index++;
		}
		// 主键
		DDLUtils.wrapTablePrimaryKeys(tableMeta, dbType, tableSql);
		// 索引
		DDLUtils.wrapTableIndexes(tableMeta, dbType, tableSql, false);
		// 外键
		DDLUtils.wrapForeignKeys(tableMeta, dbType, tableSql, false);
		tableSql.append(NEWLINE);
		tableSql.append(")");
		// 表备注
		if (StringUtil.isNotBlank(tableMeta.getRemarks())) {
			tableSql.append(" comment '").append(tableMeta.getRemarks()).append("'");
		}
		return tableSql.toString();
	}

}
